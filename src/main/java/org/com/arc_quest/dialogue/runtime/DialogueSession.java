package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.api.DialogueAction;
import org.com.arc_quest.dialogue.api.DialogueChoice;
import org.com.arc_quest.dialogue.api.DialogueNode;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 单次对话会话（服务端状态）。
 * <p>
 * 生命周期：创建 → 推进节点 → 玩家选择 → ... → 结束（关闭/终端节点）。
 */
public class DialogueSession {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final UUID sessionId;
    private final ServerPlayer player;
    private final DialogueTree tree;
    private DialogueNode currentNode;
    private boolean ended = false;

    /** 过滤后的当前可见选择列表（条件已评估）。 */
    private List<DialogueChoice> visibleChoices = List.of();

    public DialogueSession(ServerPlayer player, DialogueTree tree) {
        this.sessionId = UUID.randomUUID();
        this.player = player;
        this.tree = tree;
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

        // 执行动作
        for (DialogueAction action : choice.actions()) {
            try {
                action.execute(player);
            } catch (Exception e) {
                LOGGER.error("[Dialogue] Error executing action {} in session {}",
                        action.getClass().getSimpleName(), sessionId, e);
            }
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
        if (currentNode.hasChoices()) return currentNode; // 等待玩家选择

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
     * 替换文本变量。
     */
    public String processText(String raw) {
        if (raw == null) return "";
        return raw.replace("%player%", player.getName().getString())
                .replace("%npc%", tree.defaultNpc());
    }
}