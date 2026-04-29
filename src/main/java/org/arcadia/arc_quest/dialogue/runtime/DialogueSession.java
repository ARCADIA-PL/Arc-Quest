package org.arcadia.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.slf4j.Logger;

import java.util.*;

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

    private static final Logger LOGGER = LogUtils.getLogger();

    private final UUID sessionId;
    private final ServerPlayer player;
    private final DialogueTree tree;
    private final DialogueContext context;
    private final int entityId;

    private final String namespace;
    private final DialogueProgressStore progress;

    private DialogueNode currentNode;
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
        this.sessionId = UUID.randomUUID();
        this.player = player;
        this.tree = tree;
        this.context = context != null ? context : new DialogueContext();
        this.entityId = entityId;
        this.currentNode = tree.getStartNode();

        this.namespace = resolveNamespace();

        // 显式校验 Capability，避免创建临时实例导致数据丢失
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

        this.progress = cap.getDialogueProgress();

        evaluateVisibleChoices();
    }

    // ═══════════════════════════════════════════════
    //  公开查询
    // ═══════════════════════════════════════════════

    public UUID getSessionId() { return sessionId; }
    public ServerPlayer getPlayer() { return player; }
    public DialogueTree getTree() { return tree; }
    public DialogueNode getCurrentNode() { return currentNode; }
    public boolean isEnded() { return ended; }
    public List<DialogueChoice> getVisibleChoices() { return visibleChoices; }
    public DialogueContext getContext() { return context; }
    public int getEntityId() { return entityId; }
    public String getNamespace() { return namespace; }

    /**
     * 设置当前节点（包级私有，仅供 DialogueSessionManager 调用）。
     */
    void setCurrentNode(DialogueNode node) {
        this.currentNode = node;
        evaluateVisibleChoices();
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
     * @return 数组，长度与 visibleChoices 相同，0 = 可用，>0 = 剩余秒数
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
            if (UnifiedCooldownManager.clearIfTimeRegressed(progress, choiceKey, ts.dayTime())) {
                cooldowns[i] = 0;
                continue;
            }
            
            boolean onCooldown = progress.isOnCooldown(
                    choiceKey, choice.cooldownType(), (int) choice.cooldownSeconds(),
                    choice.resetTimeTicks(), ts);

            if (!onCooldown) {
                cooldowns[i] = 0;
                continue;
            }

            // 计算剩余时间
            var entry = progress.getChoiceSelection(choiceKey);
            cooldowns[i] = computeRemainingSeconds(entry, choice, ts);
        }

        return cooldowns;
    }

    /**
     * 获取选项的原始冷却数据（用于客户端实时计算）。
     * <p>
     * 返回四个数组，分别对应每个可见选项的：
     * <ul>
     *   <li>lastSelectTimes - 最后选择时间戳（毫秒）</li>
     *   <li>cooldownTypes - 冷却类型 ordinal</li>
     *   <li>cooldownValues - 冷却值</li>
     *   <li>resetTimeTicks - 重置刻</li>
     * </ul>
     */
    public record ChoiceCooldownData(
            long[] lastSelectTimes,
            long[] purchaseGameTimes,
            long[] purchaseDayTimes,
            int[] cooldownTypes,
            long[] cooldownValues,
            int[] resetTimeTicks
    ) {}

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
            if (UnifiedCooldownManager.clearIfTimeRegressed(progress, choiceKey, ts.dayTime())) {
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
    private int computeRemainingSeconds(DialogueProgressStore.Entry entry,
                                        DialogueChoice choice, DialogueProgressStore.TimeSnapshot ts) {
        if (!entry.exists()) return (int) choice.cooldownSeconds();

        return switch (choice.cooldownType()) {
            case NONE -> 0;

            case SECONDS -> {
                long cooldownMs = choice.cooldownSeconds() * 1000L;
                long elapsed = ts.realTime() - entry.realTime();
                long remainingMs = cooldownMs - elapsed;
                yield (int) Math.max(1, remainingMs / 1000);
            }

            case GAME_DAY -> {
                long currentDayTick = ts.dayTime() % 24000;
                int remainingTicks = (int) (24000 - currentDayTick);
                yield Math.max(1, remainingTicks / 20);
            }

            case GAME_TICK -> {
                // 用 tick 数除以 20 转秒
                int remainTicks = UnifiedCooldownManager.getGameTickCooldownRemainingTicks(
                        entry, choice.resetTimeTicks(), ts.gameTime(), ts.dayTime());
                yield Math.max(1, remainTicks / 20);
            }
        };
    }

    // ═══════════════════════════════════════════════
    //  核心逻辑
    // ═══════════════════════════════════════════════

    /**
     * 玩家做出选择。
     */
    public DialogueNode choose(int choiceIndex) {
        if (ended) return null;
        if (choiceIndex < 0 || choiceIndex >= visibleChoices.size()) {
            LOGGER.warn("[Dialogue] Invalid choice index {} for session {}", choiceIndex, sessionId);
            return currentNode;
        }

        DialogueChoice choice = visibleChoices.get(choiceIndex);
        int originalIndex = visibleChoiceOriginalIndices[choiceIndex];

        // 一次性采样时间
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
            LOGGER.warn("[Dialogue] Next node '{}' not found, ending.", choice.nextNodeId());
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

    // ═══════════════════════════════════════════════
    //  文本处理
    // ═══════════════════════════════════════════════

    public String processText(String raw) {
        if (raw == null) return "";
        String result = raw
                .replace("%player%", player.getName().getString())
                .replace("%npc%", tree.defaultNpc());
        if (!context.isEmpty()) {
            result = context.resolve(result);
        }
        return result;
    }

    public Component processDialogueText(DialogueText text) {
        Entity npc = (entityId != -1) ? player.level().getEntity(entityId) : null;
        var cap = QuestCapabilityProvider.getOrNull(player);
        Map<String, Object> vars = buildDialogueVars(cap);
        DialogueTextContext ctx = new DialogueTextContext(
                player,
                npc,
                tree.dialogueId(),
                currentNode != null ? currentNode.nodeId() : null,
                cap,
                vars
        );
        Component resolved = (text == null ? DialogueText.literal("") : text).resolve(ctx);
        return Component.literal(processText(resolved.getString()));
    }

    private Map<String, Object> buildDialogueVars(IQuestCapability cap) {
        Map<String, Object> vars = new LinkedHashMap<>();

        if (cap == null) {
            return Map.copyOf(vars);
        }

        QuestRuntimeData firstActive = cap.getAllActiveQuests().values().stream().findFirst().orElse(null);
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

    // ═══════════════════════════════════════════════
    // 内部：命名空间解析
    // ═══════════════════════════════════════════════

    private String resolveNamespace() {
        if (entityId == -1) {
            return tree.dialogueId();
        }

        Entity npc = player.level().getEntity(entityId);
        if (npc == null) {
            return tree.dialogueId();
        }

        var manager = EntityDialogueExtensionManager.INSTANCE;
        var extensions = manager.getExtensionsForEntityType(npc.getType());

        for (var extObj : extensions) {
            @SuppressWarnings("unchecked")
            IEntityDialogueExtension<Entity> ext = (IEntityDialogueExtension<Entity>) extObj;

            String dialogueId = ext.getDialogueTreeId(player, npc, null);
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
    //  内部：构建评估上下文
    // ═══════════════════════════════════════════════

    private DialogueEvalContext buildEvalContext() {
        Entity npc = (entityId != -1) ? player.level().getEntity(entityId) : null;
        return DialogueEvalContext.of(player, npc, namespace, progress);
    }

    // ═══════════════════════════════════════════════
    // 内部：条件评估
    // ═══════════════════════════════════════════════

    private void evaluateVisibleChoices() {
        if (currentNode == null || !currentNode.hasChoices()) {
            visibleChoices = List.of();
            visibleChoiceOriginalIndices = new int[0];
            return;
        }

        //开始评估周期,启用缓存
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
                            cache.computeIfAbsent(c, () -> c.test(ctx)));

                LOGGER.debug("[Dialogue] Choice '{}' pass={}, ns={}", choice.text(), pass, namespace);

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
}