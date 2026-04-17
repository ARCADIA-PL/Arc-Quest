package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.api.*;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 单次对话会话（服务端状态）。
 * <p>
 * 生命周期：创建 → 推进节点 → 玩家选择 → ... → 结束（关闭/终端节点）。
 *
 * <h3>变更记录</h3>
 * <ul>
 *   <li>[新增] {@link #context} —— 运行时上下文，支持 {@code {key}} 变量替换</li>
 *   <li>[新增] {@link #entityId} —— 关联的 NPC 实体 ID（-1 = 无实体）</li>
 *   <li>[新增] {@link #execute(DialogueAction)} —— 带会话上下文的动作执行</li>
 *   <li>[改动] {@link #processText(String)} —— 增加 context 变量替换</li>
 * </ul>
 */
public class DialogueSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final UUID sessionId;
    private final ServerPlayer player;
    private final DialogueTree tree;
    private DialogueNode currentNode;
    private boolean ended = false;

    /** [新增] 运行时上下文 */
    private final DialogueContext context;

    /** [新增] 关联的 NPC 实体 ID，-1 表示无实体（命令触发） */
    private final int entityId;

    /** 过滤后的当前可见选择列表（条件已评估）。 */
    private List<DialogueChoice> visibleChoices = List.of();

    // ═══════════════════════════════════════════════════════
    //  构造器
    // ═══════════════════════════════════════════════════════

    /** 原有构造器（向后兼容）。 */
    public DialogueSession(ServerPlayer player, DialogueTree tree) {
        this(player, tree, new DialogueContext(), -1);
    }

    /**
     * [新增] 完整构造器（带上下文和实体关联）。
     *
     * @param player   对话玩家
     * @param tree     对话树
     * @param context  运行时上下文（不可为 null）
     * @param entityId 关联的 NPC 实体 ID（-1 = 无实体）
     */
    public DialogueSession(ServerPlayer player, DialogueTree tree,
                           DialogueContext context, int entityId) {
        this.sessionId = UUID.randomUUID();
        this.player = player;
        this.tree = tree;
        this.context = context != null ? context : new DialogueContext();
        this.entityId = entityId;
        this.currentNode = tree.getStartNode();
        evaluateVisibleChoices();
    }

    // ═══════════════════════════════════════════════════════
    //  公开查询
    // ═══════════════════════════════════════════════════════

    public UUID getSessionId() { return sessionId; }
    public ServerPlayer getPlayer() { return player; }
    public DialogueTree getTree() { return tree; }
    public DialogueNode getCurrentNode() { return currentNode; }
    public boolean isEnded() { return ended; }
    public List<DialogueChoice> getVisibleChoices() { return visibleChoices; }

    /** [新增] 获取运行时上下文。 */
    public DialogueContext getContext() { return context; }

    /** [新增] 获取关联的 NPC 实体 ID（-1 = 无实体）。 */
    public int getEntityId() { return entityId; }

    // ═══════════════════════════════════════════════════════
    // 核心逻辑
    // ═══════════════════════════════════════════════════════

    /**
     * 玩家做出选择。
     *
     * @param choiceIndex 在 {@link #getVisibleChoices()} 中的索引
     * @return 新的当前节点，null = 对话结束
     */
    public DialogueNode choose(int choiceIndex) {
        if (ended) return null;

        if (choiceIndex < 0 || choiceIndex >= visibleChoices.size()) {
            LOGGER.warn("[Dialogue] Invalid choice index {} for session {}", choiceIndex, sessionId);
            return currentNode;
        }

        DialogueChoice choice = visibleChoices.get(choiceIndex);
        
        // [新增] 检查选项是否可重复及冷却（使用持久化数据）
        var cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
        if (cap != null && !checkChoiceCooldown(cap, currentNode.nodeId(), choiceIndex, choice)) {
            return currentNode;  // 冷却中，不执行
        }

        // 执行动作 [改动: 使用带 session 上下文的 execute]
        for (DialogueAction action : choice.actions()) {
            executeAction(action);
        }

        // 跳转
        if (choice.nextNodeId() == null) {
            end();
            return null;
        }

        DialogueNode nextNode = tree.getNode(choice.nextNodeId());
        if (nextNode == null) {
            LOGGER.warn("[Dialogue] Next node '{}' not found, ending session.", choice.nextNodeId());
            end();
            return null;
        }

        currentNode = nextNode;
        evaluateVisibleChoices();

        if (currentNode.isTerminal()) {
            end();
        }

        return currentNode;
    }

    /**
     * 处理自动跳转（无选择节点）。
     *
     * @return 新节点，null = 结束
     */
    public DialogueNode autoAdvance() {
        if (ended || currentNode == null) return null;
        if (currentNode.hasChoices()) return currentNode;

        // [新增] 检查节点是否可重复及冷却（使用持久化数据）
        var cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
        if (cap != null && !checkNodeCooldown(cap, currentNode)) {
            return null;  // 冷却中，无法访问
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

        currentNode = nextNode;
        evaluateVisibleChoices();

        if (currentNode.isTerminal() && !currentNode.hasChoices()) {
            // 终端但可能需要显示最后文本后再结束
        }

        return currentNode;
    }

    public void end() {
        ended = true;
        LOGGER.debug("[Dialogue] Session {} ended for player {}.",
                sessionId, player.getName().getString());
    }

    // ═══════════════════════════════════════════════════════
    //  内部
    // ═══════════════════════════════════════════════════════

    private void evaluateVisibleChoices() {
        if (currentNode == null || !currentNode.hasChoices()) {
            visibleChoices = List.of();
            return;
        }

        List<DialogueChoice> visible = new ArrayList<>();
        for (DialogueChoice choice : currentNode.choices()) {
            boolean pass = choice.conditions().isEmpty()
                    || choice.conditions().stream().allMatch(c -> c.test(player));
            if (pass) {
                visible.add(choice);
            }
        }
        visibleChoices = List.copyOf(visible);
    }

    /**
     * [改动] 执行单个动作（带会话上下文）。
     * <p>
     * 优先使用 {@link DialogueAction#execute(ServerPlayer, DialogueSession)}，
     * 让 {@link DialogueAction.Custom} 和 {@link DialogueAction.RunCommand}
     * 等类型能够获取会话上下文。
     */
    private void executeAction(DialogueAction action) {
        try {
            action.execute(player, this);
        } catch (Exception e) {
            LOGGER.error("[Dialogue] Error executing action {} in session {}",
                    action.getClass().getSimpleName(), sessionId, e);
        }
    }

    /**
     * [改动] 替换文本变量。
     * <p>
     * 替换顺序：
     * <ol>
     *   <li>{@code %player%} → 玩家名</li>
     *   <li>{@code %npc%} → 默认 NPC 名</li>
     *   <li>{@code {key}} → context 中对应的值</li>
     * </ol>
     */
    public String processText(String raw) {
        if (raw == null) return "";
        String result = raw
                .replace("%player%", player.getName().getString())
                .replace("%npc%", tree.defaultNpc());
        // [新增] context 变量替换
        if (!context.isEmpty()) {
            result = context.resolve(result);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  节点和选项的冷却管理
    // ═══════════════════════════════════════════════════════

    /**
     * 检查节点是否可访问（考虑可重复性和冷却）。
     */
    private boolean checkNodeCooldown(IQuestCapability cap, DialogueNode node) {
        if (!node.repeatable()) {
            // 一次性节点：检查是否已经访问过
            if (cap.hasVisitedNode(node.nodeId())) {
                LOGGER.debug("[Dialogue] One-time node '{}' already visited.", node.nodeId());
                return false;
            }
        } else if (node.cooldownSeconds() > 0) {
            // 可重复节点但有冷却
            long lastTime = cap.getLastNodeVisit(node.nodeId());
            long currentTime = System.currentTimeMillis();
            long cooldownMs = node.cooldownSeconds() * 1000;

            if (lastTime > 0 && (currentTime - lastTime) < cooldownMs) {
                long remainingSeconds = (cooldownMs - (currentTime - lastTime)) / 1000;
                LOGGER.debug("[Dialogue] Node '{}' on cooldown. Remaining: {}s",
                    node.nodeId(), remainingSeconds);
                return false;
            }
        }

        // 记录访问时间
        cap.recordNodeVisit(node.nodeId(), System.currentTimeMillis());
        return true;
    }

    /**
     * 检查选项是否可选择（考虑可重复性和冷却）。
     */
    private boolean checkChoiceCooldown(IQuestCapability cap, String nodeId, int choiceIndex, DialogueChoice choice) {
        String key = nodeId + ":" + choiceIndex;

        if (!choice.repeatable()) {
            // 一次性选项：检查是否已经选择过
            if (cap.hasSelectedChoice(key)) {
                LOGGER.debug("[Dialogue] One-time choice at node '{}' index {} already selected.",
                    nodeId, choiceIndex);
                return false;
            }
        } else if (choice.cooldownSeconds() > 0) {
            // 可重复选项但有冷却
            long lastTime = cap.getLastChoiceSelection(key);
            long currentTime = System.currentTimeMillis();
            long cooldownMs = choice.cooldownSeconds() * 1000;

            if (lastTime > 0 && (currentTime - lastTime) < cooldownMs) {
                long remainingSeconds = (cooldownMs - (currentTime - lastTime)) / 1000;
                LOGGER.debug("[Dialogue] Choice at node '{}' index {} on cooldown. Remaining: {}s",
                    nodeId, choiceIndex, remainingSeconds);
                return false;
            }
        }

        // 记录选择时间
        cap.recordChoiceSelection(key, System.currentTimeMillis());
        return true;
    }
}