package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.api.event.DialogueEndedEvent;
import org.com.arc_quest.api.event.DialogueStartedEvent;
import org.com.arc_quest.dialogue.api.*;
import org.com.arc_quest.dialogue.capability.DialogueNpcPatch;
import org.com.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
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
 */

public final class DialogueSessionManager {

    public static final DialogueSessionManager INSTANCE = new DialogueSessionManager();
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 玩家 UUID → 活跃会话。
     */
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();

    /**
     * 玩家 UUID → 商店退出后恢复的目标节点 ID。
     */
    private final Map<UUID, String> restoreNodeMap = new ConcurrentHashMap<>();

    private DialogueSessionManager() {
    }

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
     * 为玩家开始新对话（带实体关联和上下文）。
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
     * 内部核心方法。
     */
    private DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                          DialogueTree tree, DialogueContext context) {
        var cap = QuestCapabilityProvider.getOrNull(player);

        String dialogueId = tree.dialogueId();
        
        var progress = cap.getDialogueProgress();
        long nowReal = TimeSanitizer.getCurrentRealTime();
        long nowGame = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);
        
        int entityId = npcEntity != null ? npcEntity.getId() : -1;
        // 先构建会话以解析 namespace（namespace 依赖实体扩展查找，必须在此时完成）
        DialogueSession session = new DialogueSession(player, tree, context, entityId);
        String namespace = session.getNamespace();
        
        LOGGER.debug("[Dialogue] Resolved namespace='{}' for dialogue='{}'", namespace, dialogueId);

        if (!tree.repeatable() && progress.hasCompletedDialogue(namespace, dialogueId)) {
            LOGGER.debug("[Dialogue] One-time dialogue '{}' already completed for player {}.",
                    dialogueId, player.getName().getString());
            return null;
        }

        if (tree.cooldownSeconds() != 0 || tree.cooldownType() != CooldownType.NONE) {
            boolean onCooldown = progress.isDialogueOnCooldown(
                    namespace, dialogueId,
                    tree.cooldownType(), (int) tree.cooldownSeconds(), tree.resetTimeTicks(),
                    nowReal, nowGame, nowDayTime);
            
            if (onCooldown) {
                LOGGER.debug("[Dialogue] Dialogue '{}' on cooldown for player {}.",
                        dialogueId, player.getName().getString());
                return null;
            }
        }

        endDialogue(player);

        sessions.put(player.getUUID(), session);

        if (npcEntity instanceof IDialogueNpc) {
            DialogueNpcPatch patch = DialogueNpcPatch.get(npcEntity);
            patch.setConversing(player);
        }

        LOGGER.info("[Dialogue] Started dialogue '{}' for player '{}' (entityId={}, namespace={}).",
                tree.dialogueId(), player.getName().getString(), entityId, namespace);

        progress.recordDialogueVisit(namespace, dialogueId, nowReal, nowGame, nowDayTime);

        sendNodeToClient(session);
        
        // 发布 Forge 事件（供附属模组监听）
        MinecraftForge.EVENT_BUS.post(new DialogueStartedEvent(player, npcEntity, dialogueId));

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
     * 处理恢复对话请求（从商店或其他界面返回时调用）。
     */
    public void handleRestoreDialogue(ServerPlayer player) {
        DialogueSession session = getSession(player);
        if (session != null && !session.isEnded()) {
            String restoreNodeId = pollRestoreNodeId(player);
            if (restoreNodeId != null && !restoreNodeId.isEmpty() && !"__CURRENT__".equals(restoreNodeId)) {
                DialogueNode targetNode = session.getTree().getNode(restoreNodeId);
                if (targetNode != null) {
                    session.setCurrentNode(targetNode);
                } else {
                    LOGGER.warn("[Dialogue] Restore node '{}' not found for player {}", restoreNodeId, player.getName().getString());
                }
            }
            sendNodeToClient(session);
        } else {
            LOGGER.warn("[Dialogue] No active session for player {}", player.getName().getString());
        }
    }

    /**
     * 强制结束玩家的对话，清理关联实体的对话状态。
     */
    public void endDialogue(ServerPlayer player) {
        DialogueSession session = sessions.remove(player.getUUID());
        if (session != null) {
            String dialogueId = session.getTree().dialogueId();
            Entity npcEntity = null;
            
            if (session.getEntityId() != -1) {
                npcEntity = player.level().getEntity(session.getEntityId());
                if (npcEntity instanceof IDialogueNpc) {
                    DialogueNpcPatch patch = DialogueNpcPatch.get(npcEntity);
                    patch.clearConversing();
                }
            }

            if (!session.isEnded()) {
                session.end();
            }
            
            // 发布 Forge 事件（供附属模组监听）
            if (npcEntity != null) {
                MinecraftForge.EVENT_BUS.post(new DialogueEndedEvent(player, npcEntity, dialogueId));
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
     * 获取玩家的活跃会话。
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
        endDialogue(player);
    }

    // ═══════════════════════════════════════════════════════
    //  网络发送
    // ═══════════════════════════════════════════════════════

    private void sendNodeToClient(DialogueSession session) {
        ServerPlayer player = session.getPlayer();
        DialogueNode node = session.getCurrentNode();
        if (node == null) return;

        String speaker = node.speaker().isEmpty() ? session.getTree().defaultNpc() : node.speaker();

        net.minecraft.world.entity.Entity npc = (session.getEntityId() != -1)
                ? session.getPlayer().level().getEntity(session.getEntityId()) 
                : null;
        
        var cap = QuestCapabilityProvider.getOrNull(session.getPlayer());
        DialogueProgressStore progress = null;
        if (cap != null) {
            progress = cap.getDialogueProgress();
        }

        DialogueEvalContext ctx = DialogueEvalContext.of(
                session.getPlayer(),
                npc,
                session.getNamespace(),
                progress
        );
        
        String text = ConditionalTextEvaluator.evaluate(
                ctx,
                node.conditionalTexts(),
                node.text()
        );
        
        // 获取匹配到的 SayIf 音效
        SoundEvent matchedSaySound = ConditionalTextEvaluator.getMatchedSound(
                ctx, node.conditionalTexts()
        );

        text = session.processText(text);

        var visibleChoices = session.getVisibleChoices();
        String[] choiceTexts = new String[visibleChoices.size()];
        ResourceLocation[] choiceSounds = new ResourceLocation[visibleChoices.size()];
        
        for (int i = 0; i < visibleChoices.size(); i++) {
            choiceTexts[i] = session.processText(visibleChoices.get(i).text());
            var sound = visibleChoices.get(i).selectSound();
            if (sound != null) {
                choiceSounds[i] = ForgeRegistries.SOUND_EVENTS.getKey(sound);
            }
        }

        boolean isTerminal = node.isTerminal();
        boolean hasAutoNext = !node.hasChoices() && node.autoNextId() != null;

        var cooldownData = session.getChoiceCooldownRawData();

        ResourceLocation nodeSoundId = null;
        if (node.nodeEnterSound() != null) {
            nodeSoundId = ForgeRegistries.SOUND_EVENTS.getKey(node.nodeEnterSound());
        }

        ResourceLocation saySoundId = null;
        if (matchedSaySound != null) {
            saySoundId = ForgeRegistries.SOUND_EVENTS.getKey(matchedSaySound);
        }

        S2COpenDialoguePacket packet = new S2COpenDialoguePacket(
                session.getTree().dialogueId(),
                node.nodeId(),
                speaker,
                text,
                choiceTexts,
                isTerminal,
                hasAutoNext,
                node.delayMs(),
                session.getEntityId(),
                cooldownData.lastSelectTimes(),
                cooldownData.purchaseGameTimes(),
                cooldownData.purchaseDayTimes(),
                cooldownData.cooldownTypes(),
                cooldownData.cooldownValues(),
                cooldownData.resetTimeTicks(),
                choiceSounds,
                saySoundId
        );

        ArcQuestNetwork.sendToPlayer(player, packet);
    }

    private void sendClose(ServerPlayer player) {
        S2COpenDialoguePacket closePacket = S2COpenDialoguePacket.close();
        ArcQuestNetwork.sendToPlayer(player, closePacket);
    }

    /**
     * 设置玩家从商店退出后恢复的目标节点 ID。
     */
    public void setRestoreNodeId(ServerPlayer player, String restoreNodeId) {
        if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
            restoreNodeMap.put(player.getUUID(), restoreNodeId);
        } else {
            restoreNodeMap.remove(player.getUUID());
        }
    }

    /**
     * 获取玩家从商店退出后恢复的目标节点 ID，并清除缓存。
     */
    @Nullable
    public String pollRestoreNodeId(ServerPlayer player) {
        return restoreNodeMap.remove(player.getUUID());
    }
}