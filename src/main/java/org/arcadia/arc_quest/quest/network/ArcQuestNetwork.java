package org.arcadia.arc_quest.quest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentChunkPacket;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentStartPacket;
import org.arcadia.arc_quest.data.sync.network.C2SRequestDatapackContentPacket;
import org.arcadia.arc_quest.data.sync.network.C2SDatapackContentReadyPacket;
import org.arcadia.arc_quest.guide.network.C2SMarkGuideSeenPacket;
import org.arcadia.arc_quest.guide.network.C2SUpdateGuideProgressPacket;
import org.arcadia.arc_quest.guide.network.S2COpenGuidePacket;
import org.arcadia.arc_quest.guide.network.S2CSyncGuideStatePacket;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.codec.MarkerNetworkCodec;
import org.arcadia.arc_quest.quest.editor.network.C2SCloseQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.C2SSaveQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2COpenQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;
import org.arcadia.arc_quest.trade.gacha.network.*;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.network.C2SRequestTradeSyncPacket;
import org.arcadia.arc_quest.trade.network.S2COpenTradePacket;
import org.arcadia.arc_quest.trade.network.S2CSyncTradeStatePacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Arc Quest 网络通信中心。
 * 使用 Forge SimpleChannel 进行 S2C / C2S 数据包注册与发送。
 */
public final class ArcQuestNetwork {

    private static final String PROTOCOL_VERSION = "12";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static final Map<UUID, Long> MARKER_EPOCH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MARKER_REVISION = new ConcurrentHashMap<>();
    private static final AtomicLong MARKER_EPOCH_SEQUENCE = new AtomicLong(System.currentTimeMillis());
    private static int packetId = 0;

    private ArcQuestNetwork() {
    }

    /**
     * 在 Mod 构造器（FMLCommonSetupEvent）中调用。
     */
    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                S2CDatapackContentStartPacket.class,
                S2CDatapackContentStartPacket::encode,
                S2CDatapackContentStartPacket::decode,
                S2CDatapackContentStartPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                packetId++,
                S2CDatapackContentChunkPacket.class,
                S2CDatapackContentChunkPacket::encode,
                S2CDatapackContentChunkPacket::decode,
                S2CDatapackContentChunkPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestDatapackContentPacket.class,
                C2SRequestDatapackContentPacket::encode,
                C2SRequestDatapackContentPacket::decode,
                C2SRequestDatapackContentPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                packetId++,
                C2SDatapackContentReadyPacket.class,
                C2SDatapackContentReadyPacket::encode,
                C2SDatapackContentReadyPacket::decode,
                C2SDatapackContentReadyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                packetId++,
                S2CDatapackReloadEpochPacket.class,
                S2CDatapackReloadEpochPacket::encode,
                S2CDatapackReloadEpochPacket::decode,
                S2CDatapackReloadEpochPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // ─── S2C：全量同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncFullDataPacket.class,
                S2CSyncFullDataPacket::encode,
                S2CSyncFullDataPacket::decode,
                S2CSyncFullDataPacket::handle
        );

        // ─── S2C：单任务状态同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncQuestStatePacket.class,
                S2CSyncQuestStatePacket::encode,
                S2CSyncQuestStatePacket::decode,
                ArcQuestClientPacketBridge::handleQuestState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // ─── S2C：增量进度同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CDeltaProgressPacket.class,
                S2CDeltaProgressPacket::encode,
                S2CDeltaProgressPacket::decode,
                S2CDeltaProgressPacket::handle
        );

        // ─── S2C：Flags / Variables 同步 ───
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncFlagsVarsPacket.class,
                S2CSyncFlagsVarsPacket::encode,
                S2CSyncFlagsVarsPacket::decode,
                S2CSyncFlagsVarsPacket::handle
        );

        // ─── C2S：玩家请求（接受/放弃/选择）───
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestQuestActionPacket.class,
                C2SRequestQuestActionPacket::encode,
                C2SRequestQuestActionPacket::decode,
                C2SRequestQuestActionPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SRequestQuestResyncPacket.class,
                C2SRequestQuestResyncPacket::encode,
                C2SRequestQuestResyncPacket::decode,
                C2SRequestQuestResyncPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SSubmitOfferPacket.class,
                C2SSubmitOfferPacket::encode,
                C2SSubmitOfferPacket::decode,
                C2SSubmitOfferPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SClaimCollectionRewardPacket.class,
                C2SClaimCollectionRewardPacket::encode,
                C2SClaimCollectionRewardPacket::decode,
                C2SClaimCollectionRewardPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                S2COfferSubmitResultPacket.class,
                S2COfferSubmitResultPacket::encode,
                S2COfferSubmitResultPacket::decode,
                ArcQuestClientPacketBridge::handleOfferResult,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // ─── S2C：任务动作结果（标准拒绝码）───
        CHANNEL.registerMessage(
                packetId++,
                S2CQuestActionResultPacket.class,
                S2CQuestActionResultPacket::encode,
                S2CQuestActionResultPacket::decode,
                ArcQuestClientPacketBridge::handleQuestActionResult,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // ─── S2C：打开对话界面 ───
        CHANNEL.registerMessage(
                packetId++,
                S2COpenDialoguePacket.class,
                S2COpenDialoguePacket::encode,
                S2COpenDialoguePacket::decode,
                ArcQuestClientPacketBridge::handleDialogue,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                S2CDialogueTranscriptDeltaPacket.class,
                S2CDialogueTranscriptDeltaPacket::encode,
                S2CDialogueTranscriptDeltaPacket::decode,
                S2CDialogueTranscriptDeltaPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                S2CDialogueTranscriptSnapshotPacket.class,
                S2CDialogueTranscriptSnapshotPacket::encode,
                S2CDialogueTranscriptSnapshotPacket::decode,
                S2CDialogueTranscriptSnapshotPacket::handle
        );

        // ─── C2S：对话选择 ───
        CHANNEL.registerMessage(
                packetId++,
                C2SDialogueChoicePacket.class,
                C2SDialogueChoicePacket::encode,
                C2SDialogueChoicePacket::decode,
                C2SDialogueChoicePacket::handle
        );

        // --- S2C: Trade window ---
        CHANNEL.registerMessage(
                packetId++,
                S2COpenTradePacket.class,
                S2COpenTradePacket::encode,
                S2COpenTradePacket::decode,
                ArcQuestClientPacketBridge::handleOpenTrade,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // --- S2C: Trade state sync ---
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncTradeStatePacket.class,
                S2CSyncTradeStatePacket::encode,
                S2CSyncTradeStatePacket::decode,
                ArcQuestClientPacketBridge::handleTradeState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // --- C2S: Trade request ---
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestTradePacket.class,
                C2SRequestTradePacket::encode,
                C2SRequestTradePacket::decode,
                C2SRequestTradePacket::handle
        );

        // --- C2S: Trade state sync request ---
        CHANNEL.registerMessage(
                packetId++,
                C2SRequestTradeSyncPacket.class,
                C2SRequestTradeSyncPacket::encode,
                C2SRequestTradeSyncPacket::decode,
                C2SRequestTradeSyncPacket::handle
        );

        // --- C2S: Gacha control (open/sync) ---
        CHANNEL.registerMessage(
                packetId++,
                C2SGachaControlPacket.class,
                C2SGachaControlPacket::encode,
                C2SGachaControlPacket::decode,
                C2SGachaControlPacket::handle
        );

        // --- S2C: Gacha state (open/sync) ---
        CHANNEL.registerMessage(
                packetId++,
                S2CGachaStatePacket.class,
                S2CGachaStatePacket::encode,
                S2CGachaStatePacket::decode,
                ArcQuestClientPacketBridge::handleGachaState,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // --- C2S: Gacha draw request ---
        CHANNEL.registerMessage(
                packetId++,
                C2SDrawGachaPacket.class,
                C2SDrawGachaPacket::encode,
                C2SDrawGachaPacket::decode,
                C2SDrawGachaPacket::handle
        );

        // --- C2S: Confirm gacha draw (grant reward after animation) ---
        CHANNEL.registerMessage(
                packetId++,
                C2SConfirmDrawPacket.class,
                C2SConfirmDrawPacket::encode,
                C2SConfirmDrawPacket::decode,
                C2SConfirmDrawPacket::handle
        );

        // --- S2C: Gacha draw result ---
        CHANNEL.registerMessage(
                packetId++,
                S2CDrawResultPacket.class,
                S2CDrawResultPacket::encode,
                S2CDrawResultPacket::decode,
                ArcQuestClientPacketBridge::handleDrawResult,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // --- S2C: Gacha draw failed ---
        CHANNEL.registerMessage(
                packetId++,
                S2CDrawFailedPacket.class,
                S2CDrawFailedPacket::encode,
                S2CDrawFailedPacket::decode,
                ArcQuestClientPacketBridge::handleDrawFailed,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // --- S2C: Sync quest markers ---
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncMarkersPacket.class,
                S2CSyncMarkersPacket::encode,
                S2CSyncMarkersPacket::decode,
                ArcQuestClientPacketBridge::handleMarkers,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SRequestMarkerResyncPacket.class,
                C2SRequestMarkerResyncPacket::encode,
                C2SRequestMarkerResyncPacket::decode,
                C2SRequestMarkerResyncPacket::handle
        );

        // --- S2C: Open guide ---
        CHANNEL.registerMessage(
                packetId++,
                S2COpenGuidePacket.class,
                S2COpenGuidePacket::encode,
                S2COpenGuidePacket::decode,
                S2COpenGuidePacket::handle
        );

        // --- S2C: Sync guide state ---
        CHANNEL.registerMessage(
                packetId++,
                S2CSyncGuideStatePacket.class,
                S2CSyncGuideStatePacket::encode,
                S2CSyncGuideStatePacket::decode,
                S2CSyncGuideStatePacket::handle
        );

        // --- C2S: Mark guide seen ---
        CHANNEL.registerMessage(
                packetId++,
                C2SMarkGuideSeenPacket.class,
                C2SMarkGuideSeenPacket::encode,
                C2SMarkGuideSeenPacket::decode,
                C2SMarkGuideSeenPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SUpdateGuideProgressPacket.class,
                C2SUpdateGuideProgressPacket::encode,
                C2SUpdateGuideProgressPacket::decode,
                C2SUpdateGuideProgressPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SSetTrackedQuestPacket.class,
                C2SSetTrackedQuestPacket::encode,
                C2SSetTrackedQuestPacket::decode,
                C2SSetTrackedQuestPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SSetTrackedPhaseFocusPacket.class,
                C2SSetTrackedPhaseFocusPacket::encode,
                C2SSetTrackedPhaseFocusPacket::decode,
                C2SSetTrackedPhaseFocusPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                S2CSyncTrackedQuestPacket.class,
                S2CSyncTrackedQuestPacket::encode,
                S2CSyncTrackedQuestPacket::decode,
                S2CSyncTrackedQuestPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                S2CSyncTrackedPhaseFocusPacket.class,
                S2CSyncTrackedPhaseFocusPacket::encode,
                S2CSyncTrackedPhaseFocusPacket::decode,
                S2CSyncTrackedPhaseFocusPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                C2SMarkPhaseStoryReadPacket.class,
                C2SMarkPhaseStoryReadPacket::encode,
                C2SMarkPhaseStoryReadPacket::decode,
                C2SMarkPhaseStoryReadPacket::handle
        );
        CHANNEL.registerMessage(packetId++, S2COpenQuestEditorPacket.class, S2COpenQuestEditorPacket::encode,
                S2COpenQuestEditorPacket::decode, ArcQuestClientPacketBridge::handleOpenEditor,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, C2SSaveQuestEditorPacket.class, C2SSaveQuestEditorPacket::encode,
                C2SSaveQuestEditorPacket::decode, C2SSaveQuestEditorPacket::handle);
        CHANNEL.registerMessage(packetId++, S2CQuestEditorResultPacket.class, S2CQuestEditorResultPacket::encode,
                S2CQuestEditorResultPacket::decode, ArcQuestClientPacketBridge::handleEditorResult,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(packetId++, C2SCloseQuestEditorPacket.class, C2SCloseQuestEditorPacket::encode,
                C2SCloseQuestEditorPacket::decode, C2SCloseQuestEditorPacket::handle);
    }

    // ═══════════════════════════════════════════════════════
    // 服务端便捷发送方法
    // ═══════════════════════════════════════════════════════

    /**
     * 全量同步（登录/重生/维度切换）
     */
    public static void syncFullData(ServerPlayer player, ArcQuestPlayer data) {
        resetMarkerStream(player);
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFullDataPacket(data, envelope.playerSessionEpoch(), envelope.newRevision()));
        syncMarkers(player, data);
    }

    public static void broadcastDatapackReloadEpoch(long epoch) {
        tryBroadcastDatapackReloadEpoch(epoch);
    }

    public static boolean tryBroadcastDatapackReloadEpoch(long epoch) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;

        S2CDatapackReloadEpochPacket packet = new S2CDatapackReloadEpochPacket(epoch);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
        return true;
    }

    public static void sendDatapackReloadEpoch(ServerPlayer player, long epoch) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CDatapackReloadEpochPacket(epoch));
    }

    public static void sendQuestEditorOpen(ServerPlayer player, S2COpenQuestEditorPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendQuestEditorResult(ServerPlayer player, S2CQuestEditorResultPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendQuestEditorSave(C2SSaveQuestEditorPacket packet) { CHANNEL.sendToServer(packet); }
    public static void sendQuestEditorClose(C2SCloseQuestEditorPacket packet) { CHANNEL.sendToServer(packet); }

    /**
     * 单任务状态同步
     */
    public static void syncQuestState(ServerPlayer player, QuestRuntimeData data) {
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncQuestStatePacket(data, envelope.playerSessionEpoch(),
                        envelope.baseRevision(), envelope.newRevision()));

        pushSyncForActiveUIs(player, null, "quest_state_sync");
    }

    /**
     * 增量进度同步（小包）
     */
    public static void syncDeltaProgress(ServerPlayer player,
                                         String questId,
                                         String phaseId,
                                         int objectiveIndex,
                                         int newProgress) {
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        String objectiveId = resolveObjectiveId(player, questId, phaseId, objectiveIndex);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CDeltaProgressPacket(questId, phaseId, objectiveId, objectiveIndex, newProgress,
                        envelope.playerSessionEpoch(), envelope.baseRevision(), envelope.newRevision()));

        pushSyncForActiveUIs(player, null, "delta_progress_sync");
    }

    public static void syncDeltaProgress(ServerPlayer player,
                                         String questId,
                                         int objectiveIndex,
                                         int newProgress) {
        syncDeltaProgress(player, questId, "", objectiveIndex, newProgress);
    }

    /**
     * Flags / Variables 同步
     */
    public static void syncFlagsAndVars(ServerPlayer player, ArcQuestPlayer data) {
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncFlagsVarsPacket(data, envelope.playerSessionEpoch(),
                        envelope.baseRevision(), envelope.newRevision()));

        pushSyncForActiveUIs(player, data, "flags_vars_sync");
    }

    public static void syncTrackedQuest(ServerPlayer player, ArcQuestPlayer data) {
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncTrackedQuestPacket(
                data.getQuestTrackingSnapshot(), data.getLastQuestTrackingChangeReason(),
                envelope.playerSessionEpoch(), envelope.baseRevision(), envelope.newRevision()));
        syncTrackedPhaseFocus(player, data);
    }

    public static void syncTrackedPhaseFocus(ServerPlayer player, ArcQuestPlayer data) {
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncTrackedPhaseFocusPacket(
                data.getTrackedQuestId(), data.getTrackedPhaseId(),
                envelope.playerSessionEpoch(), envelope.baseRevision(), envelope.newRevision()));
    }

    private static String resolveObjectiveId(ServerPlayer player, String questId,
                                             String phaseId, int objectiveIndex) {
        ResourceLocation questKey = ResourceLocation.tryParse(questId);
        QuestDefinition definition = questKey != null ? QuestRegistry.get(questKey) : null;
        if (definition == null) return "";
        String resolvedPhaseId = phaseId;
        if (resolvedPhaseId == null || resolvedPhaseId.isBlank()) {
            ArcQuestPlayer playerData = ArcQuestPlayerManager.get(player);
            QuestRuntimeData runtimeData = playerData != null ? playerData.getActiveQuest(questId) : null;
            resolvedPhaseId = runtimeData != null ? runtimeData.getCurrentPhaseId() : "";
        }
        PhaseDefinition phase = definition.getPhase(resolvedPhaseId);
        if (phase == null || objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) return "";
        return phase.getObjectives().get(objectiveIndex).getObjectiveId();
    }

    /**
     * Quest 同步后统一触发 Trade + Gacha 活跃界面 push-first。
     */
    private static void pushSyncForActiveUIs(ServerPlayer player,
                                             @Nullable ArcQuestPlayer data,
                                             String reason) {
        SyncObservability.trace("quest", "active_ui", player.getName().getString(), SyncObservability.Stage.ACTION, reason);
        C2SRequestTradePacket.pushSyncForActiveShop(player, reason);

        ArcQuestPlayer resolved = (data != null) ? data : ArcQuestPlayerManager.get(player);
        if (resolved != null) {
            GachaScreenOpener.pushSync(player, resolved, reason);
        }
    }

    // ═══════════════════════════════════════════════════════
    // 客户端便捷发送方法
    // ═══════════════════════════════════════════════════════

    public static void sendQuestAction(C2SRequestQuestActionPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTrackedQuestUpdate(@Nullable String questId) {
        CHANNEL.sendToServer(new C2SSetTrackedQuestPacket(questId));
    }

    public static void sendTrackedQuestUpdate(@Nullable String questId, @Nullable String phaseId) {
        if (questId != null && phaseId != null && !phaseId.isBlank()) {
            sendTrackedPhaseFocusUpdate(questId, phaseId);
        } else {
            sendTrackedQuestUpdate(questId);
        }
    }

    public static void sendTrackedPhaseFocusUpdate(String questId, String phaseId) {
        CHANNEL.sendToServer(new C2SSetTrackedPhaseFocusPacket(questId, phaseId));
    }

    public static void markPhaseStoryRead(String questId, String phaseId) {
        CHANNEL.sendToServer(new C2SMarkPhaseStoryReadPacket(questId, phaseId));
    }

    public static void sendSubmitOffer(C2SSubmitOfferPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendClaimCollectionReward(C2SClaimCollectionRewardPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendDialogueChoice(C2SDialogueChoicePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTradeRequest(C2SRequestTradePacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendTradeSyncRequest(C2SRequestTradeSyncPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendGachaControl(C2SGachaControlPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendDrawGacha(C2SDrawGachaPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendConfirmDraw(C2SConfirmDrawPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendMarkGuideSeen(C2SMarkGuideSeenPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendGuideProgress(C2SUpdateGuideProgressPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    // ═══════════════════════════════════════════════════════
    // 服务端定向发送（S2C）
    // ═══════════════════════════════════════════════════════

    public static void sendToPlayer(ServerPlayer player, S2COpenDialoguePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendTradePacket(ServerPlayer player, S2COpenTradePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendTradeStatePacket(ServerPlayer player, S2CSyncTradeStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendGachaStatePacket(ServerPlayer player, S2CGachaStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendGuideOpenPacket(ServerPlayer player, S2COpenGuidePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendDrawResultPacket(ServerPlayer player, S2CDrawResultPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendDrawFailedPacket(ServerPlayer player, S2CDrawFailedPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendTranscriptDeltaPacket(ServerPlayer player, S2CDialogueTranscriptDeltaPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendTranscriptSnapshotPacket(ServerPlayer player, S2CDialogueTranscriptSnapshotPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void syncMarkers(ServerPlayer player, ArcQuestPlayer data) {
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);

        List<S2CSyncMarkersPacket.MarkerEntry> entries = data.getAllMarkers().values().stream()
                .map(ArcQuestNetwork::toMarkerEntry)
                .toList();

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                S2CSyncMarkersPacket.snapshot(epoch, revision, entries));
    }

    public static void syncMarkerDeltaClear(ServerPlayer player) {
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                S2CSyncMarkersPacket.deltaClear(epoch, revision));
    }

    public static void syncMarkerDeltaUpsert(ServerPlayer player, QuestMarkerData marker) {
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                S2CSyncMarkersPacket.deltaAdd(epoch, revision, List.of(toMarkerEntry(marker))));
    }

    public static void syncMarkerDeltaRemove(ServerPlayer player, String markerId) {
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                S2CSyncMarkersPacket.deltaRemove(epoch, revision, markerId));
    }

    public static void requestMarkerResync() {
        CHANNEL.sendToServer(new C2SRequestMarkerResyncPacket());
    }

    public static void bumpMarkerEpoch(ServerPlayer player) {
        resetMarkerStream(player);
    }

    private static long nextMarkerRevision(ServerPlayer player) {
        return MARKER_REVISION.merge(player.getUUID(), 1L, Long::sum);
    }

    private static long currentMarkerEpoch(ServerPlayer player) {
        return MARKER_EPOCH.computeIfAbsent(player.getUUID(), ignored -> MARKER_EPOCH_SEQUENCE.incrementAndGet());
    }

    private static void resetMarkerStream(ServerPlayer player) {
        MARKER_EPOCH.put(player.getUUID(), MARKER_EPOCH_SEQUENCE.incrementAndGet());
        MARKER_REVISION.put(player.getUUID(), 0L);
    }

    public static void clearPlayerMarkerState(UUID uuid) {
        MARKER_EPOCH.remove(uuid);
        MARKER_REVISION.remove(uuid);
        C2SRequestMarkerResyncPacket.clearPlayer(uuid);
    }

    private static S2CSyncMarkersPacket.MarkerEntry toMarkerEntry(QuestMarkerData m) {
        return MarkerNetworkCodec.toEntry(m);
    }
}
