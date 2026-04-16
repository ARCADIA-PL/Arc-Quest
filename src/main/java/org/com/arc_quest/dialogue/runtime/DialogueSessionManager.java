package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.api.DialogueNode;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.com.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有活跃的对话会话（服务端全局单例）。
 * <p>
 * 每个玩家同时只能有一个活跃对话。
 */
public final class DialogueSessionManager {

    public static final DialogueSessionManager INSTANCE = new DialogueSessionManager();
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 玩家 UUID → 活跃会话。 */
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();

    private DialogueSessionManager() {}

    // ═══════════════════════════════════════════════════════
    //  公共 API
    // ═══════════════════════════════════════════════════════

    /**
     * 为玩家开始新对话。如果已有活跃对话则先结束。
     */
    public DialogueSession startDialogue(ServerPlayer player, DialogueTree tree) {
        // 结束旧会话
        endDialogue(player);

        DialogueSession session = new DialogueSession(player, tree);
        sessions.put(player.getUUID(), session);

        LOGGER.info("[Dialogue] Started dialogue '{}' for player '{}'.",
                tree.dialogueId(), player.getName().getString());

        // 发送初始状态到客户端
        sendNodeToClient(session);

        return session;
    }

    /**
     * 处理玩家的选择。
     *
     * @param player      玩家
     * @param choiceIndex 选择索引
     */
    public void handleChoice(ServerPlayer player, int choiceIndex) {
        DialogueSession session = sessions.get(player.getUUID());
        if (session == null || session.isEnded()) {
            LOGGER.debug("[Dialogue] No active session for player {}", player.getName().getString());
            sendClose(player);
            return;
        }

        DialogueNode next = session.choose(choiceIndex);

        if (session.isEnded() || next == null) {
            endDialogue(player);
            sendClose(player);
            return;
        }

        // 发送新节点到客户端
        sendNodeToClient(session);
    }

    /**
     * 处理自动跳转请求（客户端在无选择节点显示完毕后调用）。
     */
    public void handleAutoAdvance(ServerPlayer player) {
        DialogueSession session = sessions.get(player.getUUID());
        if (session == null || session.isEnded()) {
            sendClose(player);
            return;
        }

        DialogueNode next = session.autoAdvance();

        if (session.isEnded() || next == null) {
            endDialogue(player);
            sendClose(player);
            return;
        }

        sendNodeToClient(session);
    }

    /**
     * 强制结束玩家的对话。
     */
    public void endDialogue(ServerPlayer player) {
        DialogueSession session = sessions.remove(player.getUUID());
        if (session != null && !session.isEnded()) {
            session.end();
        }
    }

    /**
     * 查询玩家是否在对话中。
     */
    public boolean isInDialogue(ServerPlayer player) {
        DialogueSession session = sessions.get(player.getUUID());
        return session != null && !session.isEnded();
    }

    /**
     * 玩家断线清理。
     */
    public void onPlayerLogout(ServerPlayer player) {
        sessions.remove(player.getUUID());
    }

    // ═══════════════════════════════════════════════════════
    //  网络发送
    // ═══════════════════════════════════════════════════════

    private void sendNodeToClient(DialogueSession session) {
        ServerPlayer player = session.getPlayer();
        DialogueNode node = session.getCurrentNode();
        if (node == null) return;

        String speaker = node.speaker().isEmpty() ? session.getTree().defaultNpc() : node.speaker();
        String text = session.processText(node.text());

        // 构建可见选择文本列表
        var visibleChoices = session.getVisibleChoices();
        String[] choiceTexts = new String[visibleChoices.size()];
        for (int i = 0; i < visibleChoices.size(); i++) {
            choiceTexts[i] = session.processText(visibleChoices.get(i).text());
        }

        boolean isTerminal = node.isTerminal();
        boolean hasAutoNext = !node.hasChoices() && node.autoNextId() != null;

        S2COpenDialoguePacket packet = new S2COpenDialoguePacket(
                session.getTree().dialogueId(),
                node.nodeId(),
                speaker,
                text,
                choiceTexts,
                isTerminal,
                hasAutoNext,
                node.delayMs()
        );

        ArcQuestNetwork.sendToPlayer(player, packet);
    }

    private void sendClose(ServerPlayer player) {
        S2COpenDialoguePacket closePacket = S2COpenDialoguePacket.close();
        ArcQuestNetwork.sendToPlayer(player, closePacket);
    }
}