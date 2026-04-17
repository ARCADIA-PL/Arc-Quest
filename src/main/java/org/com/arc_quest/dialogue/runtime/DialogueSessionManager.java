package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.com.arc_quest.dialogue.api.DialogueContext;
import org.com.arc_quest.dialogue.api.DialogueNode;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.com.arc_quest.dialogue.api.IDialogueNpc;
import org.com.arc_quest.dialogue.capability.DialogueNpcPatch;
import org.com.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有活跃的对话会话（服务端全局单例）。
 * <p>
 * 每个玩家同时只能有一个活跃对话。
 *
 * <h3>变更记录</h3>
 * <ul>
 *   <li>[新增] {@link #startDialogue(ServerPlayer, Entity, String, DialogueContext)}
 *       —— 实体感知的对话开启，支持上下文和 NPC 行为控制</li>
 *   <li>[改动] {@link #endDialogue(ServerPlayer)}
 *       —— 自动清理关联实体的 {@link DialogueNpcPatch} 状态</li>
 * </ul>
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
     * [原有] 为玩家开始新对话（无实体关联）。
     * <p>
     * 向后兼容，用于命令触发等场景。
     */
    public DialogueSession startDialogue(ServerPlayer player, DialogueTree tree) {
        return startDialogue(player, null, tree, new DialogueContext());
    }

    /**
     * [新增] 为玩家开始新对话（带实体关联和上下文）。
     * <p>
     * 由 {@link IDialogueNpc#startDialogueWith(ServerPlayer)} 或
     * {@link NpcDialogueHandler} 调用。
     *
     * @param player     对话玩家
     * @param npcEntity  NPC 实体（可为 null，命令触发时）
     * @param dialogueId 对话树 ID
     * @param context    运行时上下文
     * @return 创建的会话，或 null（对话树不存在时）
     */
    @Nullable
    public DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                         String dialogueId, DialogueContext context) {
        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            LOGGER.error("[Dialogue] Dialogue tree '{}' not found.", dialogueId);
            return null;
        }
        return startDialogue(player, npcEntity, tree, context);
    }

    /**
     * [新增] 内部核心方法。
     */
    private DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                          DialogueTree tree, DialogueContext context) {
        // 结束旧会话
        endDialogue(player);

        int entityId = npcEntity != null ? npcEntity.getId() : -1;

        // 创建会话
        DialogueSession session = new DialogueSession(player, tree, context, entityId);
        sessions.put(player.getUUID(), session);

        // [新增] 设置 NPC 对话状态
        if (npcEntity instanceof IDialogueNpc) {
            DialogueNpcPatch patch = DialogueNpcPatch.get(npcEntity);
            if (patch != null) {
                patch.setConversing(player);
            }
        }

        LOGGER.info("[Dialogue] Started dialogue '{}' for player '{}' (entityId={}).",
                tree.dialogueId(), player.getName().getString(), entityId);

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
     * [改动] 强制结束玩家的对话。
     * <p>
     * 现在会自动清理关联实体的 {@link DialogueNpcPatch} 对话状态。
     */
    public void endDialogue(ServerPlayer player) {
        DialogueSession session = sessions.remove(player.getUUID());
        if (session != null) {
            // [新增] 清理 NPC 实体对话状态
            if (session.getEntityId() != -1) {
                Entity entity = player.level().getEntity(session.getEntityId());
                if (entity instanceof IDialogueNpc) {
                    DialogueNpcPatch patch = DialogueNpcPatch.get(entity);
                    if (patch != null) {
                        patch.clearConversing();
                    }
                }
            }

            if (!session.isEnded()) {
                session.end();
            }
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
     * [新增] 获取玩家的活跃会话。
     */
    @Nullable
    public DialogueSession getSession(ServerPlayer player) {
        DialogueSession session = sessions.get(player.getUUID());
        return (session != null && !session.isEnded()) ? session : null;
    }

    /**
     * 玩家断线清理。
     */
    public void onPlayerLogout(ServerPlayer player) {
        endDialogue(player); // [改动] 改为调用 endDialogue 以正确清理 NPC 状态
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
                node.delayMs(),
                session.getEntityId()
        );

        ArcQuestNetwork.sendToPlayer(player, packet);
    }

    private void sendClose(ServerPlayer player) {
        S2COpenDialoguePacket closePacket = S2COpenDialoguePacket.close();
        ArcQuestNetwork.sendToPlayer(player, closePacket);
    }
}