package org.arcadia.arc_quest.dialogue.runtime;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.npc.NpcBinding;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * 单次对话会话（服务端状态）。
 * <p>
 * <b>v2.1 时间系统重构变更</b>:
 * <ul>
 *   <li>所有时间获取统一通过 {@link #snapshot()} 一次性采样三个时钟</li>
 *   <li>所有 record/cooldown 调用传递完整的三时钟快照</li>
 *   <li>GAME_TICK 冷却剩余时间正确计算</li>
 * </ul>
 */
public class DialogueSession {
    private final UUID sessionId;
    private final long playerSessionEpoch;
    private final ServerPlayer player;
    private final DialogueTree tree;
    private final DialogueContext context;
    private final int entityId;
    @Nullable
    private final EntityRef entityRef;

    private final String namespace;
    private final DialogueProgressStore progress;

    private DialogueNode currentNode;
    private long revision;
    @Nullable
    private UUID npcLeaseId;
    private boolean ended = false;
    private List<DialogueChoice> visibleChoices = List.of();
    private int[] visibleChoiceOriginalIndices = new int[0];

    // ═══════════════════════════════════════════════
    //  构造器
    // ═══════════════════════════════════════════════

    public DialogueSession(ServerPlayer player, DialogueTree tree) {
        this(player, tree, new DialogueContext(), -1);
    }

    public DialogueSession(ServerPlayer player, DialogueTree tree,
                           DialogueContext context, int entityId) {
        this(player, tree, context, entityId,
                entityId >= 0 && player.level().getEntity(entityId) != null
                        ? EntityRef.of(player.level().getEntity(entityId)) : null);
    }

    public DialogueSession(ServerPlayer player, DialogueTree tree,
                           DialogueContext context, @Nullable Entity entity) {
        this(player, tree, context, entity != null ? entity.getId() : -1,
                entity != null ? EntityRef.of(entity) : null);
    }

    private DialogueSession(ServerPlayer player, DialogueTree tree, DialogueContext context,
                            int entityId, @Nullable EntityRef entityRef) {
        sessionId = UUID.randomUUID();
        playerSessionEpoch = PlayerSessionEpochManager.getOrCreate(player);
        this.player = player;
        this.tree = tree;
        this.context = context != null ? context : new DialogueContext();
        this.entityId = entityId;
        this.entityRef = entityRef;
        currentNode = tree.getStartNode();

        namespace = resolveNamespace();

        // 显式校验 Capability，避免创建临时实例导致数据丢失
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);

        progress = data.getDialogueProgress();

        evaluateVisibleChoices();
    }

    // ═══════════════════════════════════════════════
    //  公开查询
    // ═══════════════════════════════════════════════

    public UUID getSessionId() {
        return sessionId;
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public long getRevision() {
        return revision;
    }

    long advanceRevision() {
        return ++revision;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public DialogueTree getTree() {
        return tree;
    }

    public DialogueNode getCurrentNode() {
        return currentNode;
    }

    /**
     * 设置当前节点（包级私有，仅供 DialogueSessionManager 调用）。
     */
    void setCurrentNode(DialogueNode node) {
        currentNode = node;
        evaluateVisibleChoices();
    }

    public boolean isEnded() {
        return ended;
    }

    public List<DialogueChoice> getVisibleChoices() {
        return visibleChoices;
    }

    public DialogueContext getContext() {
        return context;
    }

    public int getEntityId() {
        return entityId;
    }

    @Nullable
    public EntityRef getEntityRef() {
        return entityRef;
    }

    @Nullable
    public Entity getEntity() {
        if (entityRef != null && player.getServer() != null) {
            return entityRef.resolve(player.getServer());
        }
        return entityId >= 0 ? player.level().getEntity(entityId) : null;
    }

    @Nullable
    public UUID getNpcLeaseId() {
        return npcLeaseId;
    }

    void bindNpcLease(UUID npcLeaseId) {
        this.npcLeaseId = npcLeaseId;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * 一次性采样三个时钟，确保同一操作内的所有记录和检查使用一致的时间值。
     */
    private DialogueProgressStore.TimeSnapshot snapshot() {
        return DialogueProgressStore.TimeSnapshot.capture(player);
    }

    /**
     * 获取每个可见选项的冷却剩余时间（秒）。
     *
     * @return 数组，长度与 visibleChoices 相同；0 = 可用；>0 = 剩余秒数
     */
    public int[] getChoiceCooldowns() {
        if (visibleChoices.isEmpty()) {
            return new int[0];
        }

        DialogueProgressStore.TimeSnapshot ts = snapshot();
        int[] cooldowns = new int[visibleChoices.size()];

        for (int i = 0; i < visibleChoices.size(); i++) {
            DialogueChoice choice = visibleChoices.get(i);
            int originalIndex = visibleChoiceOriginalIndices[i];  // 直接使用预计算的索引

            if (choice.cooldownType() == CooldownType.NONE) {
                cooldowns[i] = 0;
                continue;
            }

            // 使用 ProgressKey + TimeSnapshot 检查冷却
            ProgressKey choiceKey = ProgressKey.ofChoice(namespace, currentNode.nodeId(), originalIndex);

            // 先检测时间回退，如果检测到则清除记录
            if (progress.clearIfTimeRegressed(choiceKey, ts.dayTime())) {
                cooldowns[i] = 0;
                continue;
            }

            var status = progress.evaluateCooldown(
                    choiceKey, choice.cooldownType(), (int) choice.cooldownSeconds(),
                    choice.resetTimeTicks(), ts);

            if (!status.active()) {
                cooldowns[i] = 0;
                continue;
            }

            cooldowns[i] = switch (choice.cooldownType()) {
                case NONE -> 0;
                case SECONDS -> Math.max(1, status.remainingRealSecondsFloor());
                case GAME_DAY, GAME_TICK -> Math.max(1, status.remainingGameSecondsFloor());
            };
        }

        return cooldowns;
    }

    public ChoiceCooldownData getChoiceCooldownRawData() {
        if (visibleChoices.isEmpty()) {
            return new ChoiceCooldownData(new long[0], new long[0], new long[0], new int[0], new long[0], new int[0]);
        }

        DialogueProgressStore.TimeSnapshot ts = snapshot();
        int count = visibleChoices.size();
        long[] lastSelectTimes = new long[count];
        long[] purchaseGTs = new long[count];
        long[] purchaseDTs = new long[count];
        int[] cooldownTypes = new int[count];
        long[] cooldownValues = new long[count];
        int[] resetTimeTicks = new int[count];

        for (int i = 0; i < count; i++) {
            DialogueChoice choice = visibleChoices.get(i);
            int originalIndex = visibleChoiceOriginalIndices[i];  // 直接使用预计算的索引

            if (choice.cooldownType() == CooldownType.NONE) {
                lastSelectTimes[i] = 0;
                purchaseGTs[i] = 0;
                purchaseDTs[i] = 0;
                cooldownTypes[i] = CooldownType.NONE.ordinal();
                cooldownValues[i] = 0;
                resetTimeTicks[i] = 0;
                continue;
            }

            ProgressKey choiceKey = ProgressKey.ofChoice(namespace, currentNode.nodeId(), originalIndex);

            // 检测时间回退
            if (progress.clearIfTimeRegressed(choiceKey, ts.dayTime())) {
                lastSelectTimes[i] = 0;
                purchaseGTs[i] = 0;
                purchaseDTs[i] = 0;
                cooldownTypes[i] = CooldownType.NONE.ordinal();
                cooldownValues[i] = 0;
                resetTimeTicks[i] = 0;
                continue;
            }

            var entry = progress.getChoiceSelection(choiceKey);
            if (entry.exists()) {
                lastSelectTimes[i] = entry.realTime();
                purchaseGTs[i] = entry.gameTime();
                purchaseDTs[i] = entry.dayTime();
            } else {
                lastSelectTimes[i] = 0;
                purchaseGTs[i] = 0;
                purchaseDTs[i] = 0;
            }

            cooldownTypes[i] = choice.cooldownType().ordinal();
            cooldownValues[i] = choice.cooldownSeconds();
            resetTimeTicks[i] = choice.resetTimeTicks();
        }

        return new ChoiceCooldownData(lastSelectTimes, purchaseGTs, purchaseDTs, cooldownTypes, cooldownValues, resetTimeTicks);
    }

    /**
     * 根据冷却类型计算剩余秒数。
     */

    /**
     * 玩家做出选择。
     */
    public DialogueNode choose(int choiceIndex) {
        if (ended) return null;
        if (choiceIndex < 0 || choiceIndex >= visibleChoices.size()) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Invalid choice index {} for session {}", choiceIndex, sessionId);
            return currentNode;
        }

        DialogueChoice choice = visibleChoices.get(choiceIndex);
        int originalIndex = visibleChoiceOriginalIndices[choiceIndex];

        // 一次性采样时钟
        DialogueProgressStore.TimeSnapshot ts = snapshot();

        if (!DialogueActionExecutor.checkChoiceAvailable(this, choice, originalIndex, progress, ts)) {
            return currentNode;
        }

        // 检查目标节点
        String nextNodeId = choice.nextNodeId();
        if (nextNodeId != null) {
            DialogueNode nextNode = tree.getNode(nextNodeId);
            if (nextNode != null && !DialogueActionExecutor.checkNodeAvailable(this, nextNode, progress, ts)) {
                return currentNode;
            }
        }

        // 记录选项选择（使用 ProgressKey）
        ProgressKey choiceKey = ProgressKey.ofChoice(namespace, currentNode.nodeId(), originalIndex);
        progress.recordChoiceSelection(choiceKey, ts.realTime(), ts.gameTime(), ts.dayTime());

        // 执行动作 (转移给执行器)
        DialogueActionExecutor.executeActions(player, this, choice);

        // 跳转
        if (choice.nextNodeId() == null) {
            // 如果动作中包含打开商店，不结束会话（等待商店关闭后恢复）
            boolean hasShopAction = choice.actions().stream()
                    .anyMatch(a -> a instanceof DialogueAction.OpenTrade || a instanceof DialogueAction.OpenSimpleTrade);

            if (!hasShopAction) {
                end();
                return null;
            }
            // 有商店动作，保持会话活跃，返回当前节点
            return currentNode;
        }

        DialogueNode nextNode = tree.getNode(choice.nextNodeId());
        if (nextNode == null) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Next node '{}' not found, ending.", choice.nextNodeId());
            end();
            return null;
        }

        // 记录目标节点访问（使用 ProgressKey）
        ProgressKey nodeKey = ProgressKey.ofNode(namespace, nextNode.nodeId());
        progress.recordNodeVisit(nodeKey, ts.realTime(), ts.gameTime(), ts.dayTime());

        currentNode = nextNode;
        evaluateVisibleChoices();

        if (currentNode.isTerminal()) {
            end();
        }
        return currentNode;
    }

    // ═══════════════════════════════════════════════
    //  核心逻辑
    // ═══════════════════════════════════════════════

    /**
     * 自动跳转（无选择节点）。
     */
    public DialogueNode autoAdvance() {
        if (ended || currentNode == null) return null;
        if (currentNode.hasChoices()) return currentNode;

        DialogueProgressStore.TimeSnapshot ts = snapshot();

        if (!DialogueActionExecutor.checkNodeAvailable(this, currentNode, progress, ts)) {
            return null;
        }

        String nextId = currentNode.autoNextId();
        if (nextId == null) {
            end();
            return null;
        }

        DialogueNode nextNode = tree.getNode(nextId);
        if (nextNode == null) {
            end();
            return null;
        }

        // 确认要跳转后，记录目标节点的访问
        ProgressKey nodeKey = ProgressKey.ofNode(namespace, nextNode.nodeId());
        progress.recordNodeVisit(nodeKey, ts.realTime(), ts.gameTime(), ts.dayTime());

        currentNode = nextNode;
        evaluateVisibleChoices();
        return currentNode;
    }

    public void end() {
        ended = true;
    }

    public String processText(String raw) {
        if (raw == null) return "";
        String result = raw
                .replace("%player%", player.getName().getString())
                .replace("%npc%", processDialogueText(tree.defaultNpc()).getString());
        if (!context.isEmpty()) {
            result = context.resolve(result);
        }
        return result;
    }

    /**
     * 相关处理说明。
     * 相关处理说明。
     * 相关处理说明。
     */
    Component processDialogueComponent(Component component) {
        return preserveComponentUnlessChanged(component, this::processText);
    }

    static Component preserveComponentUnlessChanged(
            Component component, UnaryOperator<String> legacyProcessor) {
        if (component == null) return Component.empty();
        String original = component.getString();
        String processed = legacyProcessor.apply(original);
        return Objects.equals(original, processed) ? component : Component.literal(processed);
    }

    // ═══════════════════════════════════════════════
    //  文本处理
    // ═══════════════════════════════════════════════

    public Component processDialogueText(DialogueText text) {
        Entity npc = getEntity();
        IDialogueNpc dialogueNpc = (npc instanceof IDialogueNpc d) ? d : null;

        var cap = ArcQuestPlayerManager.get(player);
        Map<String, Object> vars = buildDialogueVars(cap);

        DialogueTextContext ctx = new DialogueTextContext(
                player,
                npc,
                dialogueNpc,
                tree.dialogueId(),
                currentNode != null ? currentNode.nodeId() : null,
                cap,
                vars
        );

        Component resolved = (text == null ? DialogueText.literal("") : text).resolve(ctx);
        return resolved;
    }

    private Map<String, Object> buildDialogueVars(ArcQuestPlayer data) {
        Map<String, Object> vars = new LinkedHashMap<>();

        if (data == null) {
            return Map.copyOf(vars);
        }

        QuestRuntimeData firstActive = data.getAllActiveQuests().values().stream().findFirst().orElse(null);
        if (firstActive == null) {
            return Map.copyOf(vars);
        }

        vars.put("questId", firstActive.getQuestId());

        String phaseId = firstActive.getActivePhaseIds().stream().findFirst().orElse(null);
        if (phaseId != null) {
            vars.put("phaseId", phaseId);

            int[] progress = firstActive.getAllProgress(phaseId);
            vars.put("objectiveCount", progress.length);
            vars.put("currentObjectiveIndex", progress.length > 0 ? 0 : -1);
            vars.put("currentObjectiveProgress", progress.length > 0 ? progress[0] : 0);
            vars.put("objectiveProgressList", Arrays.toString(progress));
        } else {
            vars.put("phaseId", "");
            vars.put("objectiveCount", 0);
            vars.put("currentObjectiveIndex", -1);
            vars.put("currentObjectiveProgress", 0);
            vars.put("objectiveProgressList", "[]");
        }

        return Map.copyOf(vars);
    }

    private String resolveNamespace() {
        Entity npc = getEntity();
        if (npc == null) {
            return tree.dialogueId();
        }

        var manager = EntityDialogueExtensionManager.INSTANCE;
        var extensions = manager.getExtensionsForEntityType(npc.getType());

        for (var extObj : extensions) {
            @SuppressWarnings("unchecked")
            IEntityDialogueExtension<Entity> ext = (IEntityDialogueExtension<Entity>) extObj;

            String dialogueId = ext.getDialogueTreeId(player, npc, null, new NpcBinding());
            if (dialogueId != null && dialogueId.equals(tree.dialogueId())) {
                ProgressScope scope = ext.getProgressScope();

                return switch (scope) {
                    case INSTANCE -> npc.getStringUUID();
                    case CUSTOM -> {
                        String custom = ext.getProgressNamespace(npc);
                        yield custom != null ? custom : tree.dialogueId();
                    }
                    case DIALOGUE_TREE -> tree.dialogueId();
                };
            }
        }

        return tree.dialogueId();
    }

    // ═══════════════════════════════════════════════
    // 内部：命名空间解析
    // ═══════════════════════════════════════════════

    private DialogueEvalContext buildEvalContext() {
        Entity npc = getEntity();
        return DialogueEvalContext.of(player, npc, namespace, progress);
    }

    // ═══════════════════════════════════════════════
    //  内部：构建评估上下文
    // ═══════════════════════════════════════════════

    private void evaluateVisibleChoices() {
        if (currentNode == null || !currentNode.hasChoices()) {
            visibleChoices = List.of();
            visibleChoiceOriginalIndices = new int[0];
            return;
        }

        //开始评估周期，启用缓存
        EvalCache cache = EvalCache.current();
        cache.beginCycle();

        try {
            DialogueEvalContext ctx = buildEvalContext();

            List<DialogueChoice> passing = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            List<DialogueChoice> allChoices = currentNode.choices();
            for (int i = 0; i < allChoices.size(); i++) {
                DialogueChoice choice = allChoices.get(i);
                boolean pass = choice.conditions().isEmpty()
                        || choice.conditions().stream().allMatch(c ->
                        cache.computeIfAbsent(c,
                                () -> CoreProcessors.get().conditions().evaluate(c, ctx)));

                ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Choice '{}' pass={}, ns={}", choice.text(), pass, namespace);

                if (pass) {
                    passing.add(choice);
                    indices.add(i);  // 记录原始索引
                }
            }

            if (passing.isEmpty()) {
                visibleChoices = List.of();
                visibleChoiceOriginalIndices = new int[0];
            } else {
                int maxPriority = passing.stream()
                        .mapToInt(DialogueChoice::priority)
                        .max().orElse(0);

                // 构建 choice -> originalIndex 的快速查找表
                Map<DialogueChoice, Integer> choiceToIndexMap = new HashMap<>();
                for (int i = 0; i < passing.size(); i++) {
                    choiceToIndexMap.put(passing.get(i), indices.get(i));
                }

                // 优先级过滤后同步更新 indices 数组
                List<DialogueChoice> finalChoices = passing.stream()
                        .filter(c -> c.priority() == maxPriority)
                        .toList();

                // 根据最终选择的 choice 重新构建 indices 数组（O(1) 查找）
                List<Integer> finalIndices = new ArrayList<>();
                for (DialogueChoice fc : finalChoices) {
                    finalIndices.add(choiceToIndexMap.get(fc));
                }

                visibleChoices = finalChoices;
                visibleChoiceOriginalIndices = finalIndices.stream().mapToInt(Integer::intValue).toArray();
            }
        } finally {
            //结束评估周期
            cache.endCycle();
        }
    }

    // ═══════════════════════════════════════════════
    // 内部：条件评估
    // ═══════════════════════════════════════════════

    /**
     * 获取选项的原始冷却数据（用于客户端实时计算）。
     * <p>
     * 返回四个数组，分别对应每个可见选项的：
     * <ul>
     *   <li>lastSelectTimes - 最后选择时间戳（毫秒）</li>
     *   <li>cooldownTypes - 冷却类型 ordinal</li>
     *   <li>cooldownValues - 冷却值</li>
     *   <li>resetTimeTicks - 重置时间</li>
     * </ul>
     */
    public record ChoiceCooldownData(
            long[] lastSelectTimes,
            long[] purchaseGameTimes,
            long[] purchaseDayTimes,
            int[] cooldownTypes,
            long[] cooldownValues,
            int[] resetTimeTicks
    ) {
    }
}
