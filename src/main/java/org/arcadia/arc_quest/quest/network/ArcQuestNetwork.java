package org.arcadia.arc_quest.quest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.sync.network.C2SDatapackContentReadyPacket;
import org.arcadia.arc_quest.data.sync.network.C2SRequestDatapackContentPacket;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentChunkPacket;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentStartPacket;
import org.arcadia.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.guide.network.C2SMarkGuideSeenPacket;
import org.arcadia.arc_quest.guide.network.C2SMarkAllGuidesSeenPacket;
import org.arcadia.arc_quest.guide.network.C2SUpdateGuideProgressPacket;
import org.arcadia.arc_quest.guide.network.S2COpenGuidePacket;
import org.arcadia.arc_quest.guide.network.S2CSyncGuideStatePacket;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.editor.network.C2SCloseQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.C2SSaveQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2COpenQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.codec.MarkerNetworkCodec;
import org.arcadia.arc_quest.trade.gacha.network.*;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.network.C2SRequestTradeSyncPacket;
import org.arcadia.arc_quest.trade.network.S2COpenTradePacket;
import org.arcadia.arc_quest.trade.network.S2CSyncTradeStatePacket;

import org.arcadia.arc_quest.integration.jei.network.JeiCatalogNetwork;
import org.arcadia.arc_quest.trade.network.C2SReadTradeUpdatePacket;
import org.arcadia.arc_quest.trade.network.S2CTradeUpdatesPacket;
import org.arcadia.arc_quest.trade.network.S2CTestTradeShopPacket;
import org.arcadia.arc_quest.quest.logic.ObjectiveRequiredCounts;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 相关处理说明。 */
public final class ArcQuestNetwork {

    private static final Map<UUID, Long> MARKER_EPOCH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MARKER_REVISION = new ConcurrentHashMap<>();
    private static final AtomicLong MARKER_EPOCH_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    private ArcQuestNetwork() {
    }

    /** 相关处理说明。 */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Arc_Quest.MOD_ID).versioned("18");
        JeiCatalogNetwork.register(registrar);
        registrar.playToServer(C2SReadTradeUpdatePacket.TYPE, C2SReadTradeUpdatePacket.STREAM_CODEC, C2SReadTradeUpdatePacket::handle);
        registrar.playToServer(C2SMarkCollectionSeenPacket.TYPE, C2SMarkCollectionSeenPacket.STREAM_CODEC, C2SMarkCollectionSeenPacket::handle);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientPayloadHandlers(registrar);
        } else {
            registerClientPayloadCodecs(registrar);
        }

        registrar.playToServer(C2SRequestQuestActionPacket.TYPE, C2SRequestQuestActionPacket.STREAM_CODEC, C2SRequestQuestActionPacket::handle);
        registrar.playToServer(C2SRequestQuestResyncPacket.TYPE, C2SRequestQuestResyncPacket.STREAM_CODEC, C2SRequestQuestResyncPacket::handle);
        registrar.playToServer(C2SRequestDatapackContentPacket.TYPE, C2SRequestDatapackContentPacket.STREAM_CODEC, C2SRequestDatapackContentPacket::handle);
        registrar.playToServer(C2SDatapackContentReadyPacket.TYPE, C2SDatapackContentReadyPacket.STREAM_CODEC, C2SDatapackContentReadyPacket::handle);
        registrar.playToServer(C2SRequestMarkerResyncPacket.TYPE, C2SRequestMarkerResyncPacket.STREAM_CODEC, C2SRequestMarkerResyncPacket::handle);
        registrar.playToServer(C2SSubmitOfferPacket.TYPE, C2SSubmitOfferPacket.STREAM_CODEC, C2SSubmitOfferPacket::handle);
        registrar.playToServer(C2SClaimCollectionRewardPacket.TYPE, C2SClaimCollectionRewardPacket.STREAM_CODEC, C2SClaimCollectionRewardPacket::handle);
        registrar.playToServer(C2SGachaControlPacket.TYPE, C2SGachaControlPacket.STREAM_CODEC, C2SGachaControlPacket::handle);
        registrar.playToServer(C2SDrawGachaPacket.TYPE, C2SDrawGachaPacket.STREAM_CODEC, C2SDrawGachaPacket::handle);
        registrar.playToServer(C2SConfirmDrawPacket.TYPE, C2SConfirmDrawPacket.STREAM_CODEC, C2SConfirmDrawPacket::handle);
        registrar.playToServer(C2SRequestTradePacket.TYPE, C2SRequestTradePacket.STREAM_CODEC, C2SRequestTradePacket::handle);
        registrar.playToServer(C2SRequestTradeSyncPacket.TYPE, C2SRequestTradeSyncPacket.STREAM_CODEC, C2SRequestTradeSyncPacket::handle);
        registrar.playToServer(C2SDialogueChoicePacket.TYPE, C2SDialogueChoicePacket.STREAM_CODEC, C2SDialogueChoicePacket::handle);
        registrar.playToServer(C2SMarkGuideSeenPacket.TYPE, C2SMarkGuideSeenPacket.STREAM_CODEC, C2SMarkGuideSeenPacket::handle);
        registrar.playToServer(C2SMarkAllGuidesSeenPacket.TYPE, C2SMarkAllGuidesSeenPacket.STREAM_CODEC, C2SMarkAllGuidesSeenPacket::handle);
        registrar.playToServer(C2SUpdateGuideProgressPacket.TYPE, C2SUpdateGuideProgressPacket.STREAM_CODEC, C2SUpdateGuideProgressPacket::handle);
        registrar.playToServer(C2SSetTrackedQuestPacket.TYPE, C2SSetTrackedQuestPacket.STREAM_CODEC, C2SSetTrackedQuestPacket::handle);
        registrar.playToServer(C2SSetTrackedPhaseFocusPacket.TYPE, C2SSetTrackedPhaseFocusPacket.STREAM_CODEC, C2SSetTrackedPhaseFocusPacket::handle);
        registrar.playToServer(C2SMarkPhaseStoryReadPacket.TYPE, C2SMarkPhaseStoryReadPacket.STREAM_CODEC, C2SMarkPhaseStoryReadPacket::handle);
        registrar.playToServer(C2SCloseQuestEditorPacket.TYPE, C2SCloseQuestEditorPacket.STREAM_CODEC, C2SCloseQuestEditorPacket::handle);
        registrar.playToServer(C2SSaveQuestEditorPacket.TYPE, C2SSaveQuestEditorPacket.STREAM_CODEC, C2SSaveQuestEditorPacket::handle);
    }

    private static void registerClientPayloadHandlers(PayloadRegistrar registrar) {
        registrar.playToClient(S2CCollectionRecordsPacket.TYPE, S2CCollectionRecordsPacket.STREAM_CODEC, S2CCollectionRecordsPacket::handle);
        registrar.playToClient(S2CTestTradeShopPacket.TYPE, S2CTestTradeShopPacket.STREAM_CODEC, S2CTestTradeShopPacket::handle);
        registrar.playToClient(S2CTradeUpdatesPacket.TYPE, S2CTradeUpdatesPacket.STREAM_CODEC, S2CTradeUpdatesPacket::handle);
        registrar.playToClient(S2CDatapackReloadEpochPacket.TYPE, S2CDatapackReloadEpochPacket.STREAM_CODEC, S2CDatapackReloadEpochPacket::handle);
        registrar.playToClient(S2CDatapackContentStartPacket.TYPE, S2CDatapackContentStartPacket.STREAM_CODEC, S2CDatapackContentStartPacket::handle);
        registrar.playToClient(S2CDatapackContentChunkPacket.TYPE, S2CDatapackContentChunkPacket.STREAM_CODEC, S2CDatapackContentChunkPacket::handle);
        registrar.playToClient(S2CSyncFullDataPacket.TYPE, S2CSyncFullDataPacket.STREAM_CODEC, S2CSyncFullDataPacket::handle);
        registrar.playToClient(S2CSyncQuestStatePacket.TYPE, S2CSyncQuestStatePacket.STREAM_CODEC, S2CSyncQuestStatePacket::handle);
        registrar.playToClient(S2CDeltaProgressPacket.TYPE, S2CDeltaProgressPacket.STREAM_CODEC, S2CDeltaProgressPacket::handle);
        registrar.playToClient(S2CSyncFlagsVarsPacket.TYPE, S2CSyncFlagsVarsPacket.STREAM_CODEC, S2CSyncFlagsVarsPacket::handle);
        registrar.playToClient(S2CSyncMarkersPacket.TYPE, S2CSyncMarkersPacket.STREAM_CODEC, S2CSyncMarkersPacket::handle);
        registrar.playToClient(S2CQuestActionResultPacket.TYPE, S2CQuestActionResultPacket.STREAM_CODEC, S2CQuestActionResultPacket::handle);
        registrar.playToClient(S2COfferSubmitResultPacket.TYPE, S2COfferSubmitResultPacket.STREAM_CODEC, S2COfferSubmitResultPacket::handle);
        registrar.playToClient(S2CGachaStatePacket.TYPE, S2CGachaStatePacket.STREAM_CODEC, S2CGachaStatePacket::handle);
        registrar.playToClient(S2CDrawResultPacket.TYPE, S2CDrawResultPacket.STREAM_CODEC, S2CDrawResultPacket::handle);
        registrar.playToClient(S2CDrawFailedPacket.TYPE, S2CDrawFailedPacket.STREAM_CODEC, S2CDrawFailedPacket::handle);
        registrar.playToClient(S2COpenTradePacket.TYPE, S2COpenTradePacket.STREAM_CODEC, S2COpenTradePacket::handle);
        registrar.playToClient(S2CSyncTradeStatePacket.TYPE, S2CSyncTradeStatePacket.STREAM_CODEC, S2CSyncTradeStatePacket::handle);
        registrar.playToClient(S2COpenDialoguePacket.TYPE, S2COpenDialoguePacket.STREAM_CODEC, S2COpenDialoguePacket::handle);
        registrar.playToClient(S2CDialogueTranscriptSnapshotPacket.TYPE, S2CDialogueTranscriptSnapshotPacket.STREAM_CODEC, S2CDialogueTranscriptSnapshotPacket::handle);
        registrar.playToClient(S2CDialogueTranscriptDeltaPacket.TYPE, S2CDialogueTranscriptDeltaPacket.STREAM_CODEC, S2CDialogueTranscriptDeltaPacket::handle);
        registrar.playToClient(S2COpenGuidePacket.TYPE, S2COpenGuidePacket.STREAM_CODEC, S2COpenGuidePacket::handle);
        registrar.playToClient(S2CSyncGuideStatePacket.TYPE, S2CSyncGuideStatePacket.STREAM_CODEC, S2CSyncGuideStatePacket::handle);
        registrar.playToClient(S2CSyncTrackedQuestPacket.TYPE, S2CSyncTrackedQuestPacket.STREAM_CODEC, S2CSyncTrackedQuestPacket::handle);
        registrar.playToClient(S2CSyncTrackedPhaseFocusPacket.TYPE, S2CSyncTrackedPhaseFocusPacket.STREAM_CODEC, S2CSyncTrackedPhaseFocusPacket::handle);
        registrar.playToClient(S2COpenQuestEditorPacket.TYPE, S2COpenQuestEditorPacket.STREAM_CODEC, S2COpenQuestEditorPacket::handle);
        registrar.playToClient(S2CQuestEditorResultPacket.TYPE, S2CQuestEditorResultPacket.STREAM_CODEC, S2CQuestEditorResultPacket::handle);
    }

    private static void registerClientPayloadCodecs(PayloadRegistrar registrar) {
        registrar.playToClient(S2CCollectionRecordsPacket.TYPE, S2CCollectionRecordsPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CTestTradeShopPacket.TYPE, S2CTestTradeShopPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CTradeUpdatesPacket.TYPE, S2CTradeUpdatesPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDatapackReloadEpochPacket.TYPE, S2CDatapackReloadEpochPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDatapackContentStartPacket.TYPE, S2CDatapackContentStartPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDatapackContentChunkPacket.TYPE, S2CDatapackContentChunkPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncFullDataPacket.TYPE, S2CSyncFullDataPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncQuestStatePacket.TYPE, S2CSyncQuestStatePacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDeltaProgressPacket.TYPE, S2CDeltaProgressPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncFlagsVarsPacket.TYPE, S2CSyncFlagsVarsPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncMarkersPacket.TYPE, S2CSyncMarkersPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CQuestActionResultPacket.TYPE, S2CQuestActionResultPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2COfferSubmitResultPacket.TYPE, S2COfferSubmitResultPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CGachaStatePacket.TYPE, S2CGachaStatePacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDrawResultPacket.TYPE, S2CDrawResultPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDrawFailedPacket.TYPE, S2CDrawFailedPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2COpenTradePacket.TYPE, S2COpenTradePacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncTradeStatePacket.TYPE, S2CSyncTradeStatePacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2COpenDialoguePacket.TYPE, S2COpenDialoguePacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDialogueTranscriptSnapshotPacket.TYPE, S2CDialogueTranscriptSnapshotPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CDialogueTranscriptDeltaPacket.TYPE, S2CDialogueTranscriptDeltaPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2COpenGuidePacket.TYPE, S2COpenGuidePacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncGuideStatePacket.TYPE, S2CSyncGuideStatePacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncTrackedQuestPacket.TYPE, S2CSyncTrackedQuestPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CSyncTrackedPhaseFocusPacket.TYPE, S2CSyncTrackedPhaseFocusPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2COpenQuestEditorPacket.TYPE, S2COpenQuestEditorPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CQuestEditorResultPacket.TYPE, S2CQuestEditorResultPacket.STREAM_CODEC, (packet, context) -> {});
    }


    public static void syncFullData(ServerPlayer player, ArcQuestPlayer data) {
        ObjectiveRequiredCounts.refreshAll(player, data);
        if (player.connection == null) return;
        resetMarkerStream(player);
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);

        PacketDistributor.sendToPlayer(player,
                new S2CSyncFullDataPacket(data, envelope.playerSessionEpoch(), envelope.newRevision()));
        org.arcadia.arc_quest.data.sync.DatapackContentSyncService.syncIfChanged(player);
        syncMarkers(player, data);
    }

    public static void syncCollectionRecords(ServerPlayer player, java.util.Set<ResourceLocation> ids) {
        if (ids.isEmpty() || player.connection == null) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        PacketDistributor.sendToPlayer(player, new S2CCollectionRecordsPacket(
                org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.sanitizeRecordSnapshot(data,
                        data.getCollectionRecords().serializeEntries(ids)), envelope.playerSessionEpoch(),
                envelope.baseRevision(), envelope.newRevision()));
        org.arcadia.arc_quest.data.sync.DatapackContentSyncService.syncIfChanged(player, ids);
        pushSyncForActiveUIs(player, data, "collection_records_sync");
    }

    public static void broadcastDatapackReloadEpoch(long epoch) {
        tryBroadcastDatapackReloadEpoch(epoch);
    }

    public static boolean tryBroadcastDatapackReloadEpoch(long epoch) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;

        S2CDatapackReloadEpochPacket packet = new S2CDatapackReloadEpochPacket(epoch);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            PacketDistributor.sendToPlayer(player, packet);
        }
        return true;
    }

    public static void sendDatapackReloadEpoch(ServerPlayer player, long epoch) {
        PacketDistributor.sendToPlayer(player,
                new S2CDatapackReloadEpochPacket(epoch));
    }

    public static void sendQuestEditorOpen(ServerPlayer player, S2COpenQuestEditorPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendQuestEditorResult(ServerPlayer player, S2CQuestEditorResultPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendQuestEditorSave(C2SSaveQuestEditorPacket packet) { PacketDistributor.sendToServer(packet); }
    public static void sendQuestEditorClose(C2SCloseQuestEditorPacket packet) { PacketDistributor.sendToServer(packet); }

    /**
     * 单任务状态同步
     */
    public static void syncQuestState(ServerPlayer player, QuestRuntimeData data) {
        ObjectiveRequiredCounts.refresh(player, ArcQuestPlayerManager.getOrCreate(player), data);
        sendQuestState(player, data);
    }

    private static void sendQuestState(ServerPlayer player, QuestRuntimeData data) {
        if (player.connection == null) return;
        QuestDefinition definition = QuestRegistry.getServerDefinition(ResourceLocation.tryParse(data.getQuestId()));
        if (definition != null && definition.hasCollectionSheets() && definition.getCollectionConfig() != null) {
            java.util.Set<ResourceLocation> knownEntries = new java.util.HashSet<>();
            definition.getCollectionConfig().getEntries().forEach(entry -> knownEntries.add(entry.getEntryId()));
            syncCollectionRecords(player, knownEntries);
        }
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        PacketDistributor.sendToPlayer(player,
                new S2CSyncQuestStatePacket(data, envelope.playerSessionEpoch(),
                        envelope.baseRevision(), envelope.newRevision()));
        org.arcadia.arc_quest.data.sync.DatapackContentSyncService.syncIfChanged(player);

        pushSyncForActiveUIs(player, null, "quest_state_sync");
    }

    /** Dynamic thresholds can change without a progress event (levels, variables, addon state). */
    public static void syncRequiredCounts(ServerPlayer player, ArcQuestPlayer data) {
        for (QuestRuntimeData runtime : List.copyOf(data.getAllActiveQuests().values())) {
            if (ObjectiveRequiredCounts.refresh(player, data, runtime)) sendQuestState(player, runtime);
        }
    }

    /**
     * 增量进度同步（小包）
     */
    public static void syncDeltaProgress(ServerPlayer player,
                                         String questId,
                                         String phaseId,
                                         int objectiveIndex,
                                         int newProgress) {
        if (player.connection == null) return;
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        String objectiveId = resolveObjectiveId(player, questId, phaseId, objectiveIndex);
        int required = effectiveRequiredCount(player, questId, phaseId, objectiveIndex);
        PacketDistributor.sendToPlayer(player,
                new S2CDeltaProgressPacket(questId, phaseId, objectiveId, objectiveIndex, newProgress, required,
                        envelope.playerSessionEpoch(), envelope.baseRevision(), envelope.newRevision()));

        pushSyncForActiveUIs(player, null, "delta_progress_sync");
    }

    private static int effectiveRequiredCount(ServerPlayer player, String questId, String phaseId, int index) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        QuestRuntimeData runtime = data.getActiveQuest(questId);
        if (runtime == null) return 0;
        String phase = phaseId == null || phaseId.isBlank() ? runtime.getCurrentPhaseId() : phaseId;
        if (runtime.hasRequiredCount(phase, index)) return runtime.getRequiredCount(phase, index, 1);
        ResourceLocation id = ResourceLocation.tryParse(questId);
        QuestDefinition definition = id == null ? null : QuestRegistry.get(id);
        PhaseDefinition phaseDefinition = definition == null ? null : definition.getPhase(phase);
        if (phaseDefinition == null || index < 0 || index >= phaseDefinition.getObjectives().size()) return 0;
        int required = QuestProgressHandler.resolveRequiredCount(player, phaseDefinition.getObjectives().get(index), data);
        runtime.setRequiredCount(phase, index, required);
        return required;
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
        if (player.connection == null) return;
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        PacketDistributor.sendToPlayer(player,
                new S2CSyncFlagsVarsPacket(data, envelope.playerSessionEpoch(),
                        envelope.baseRevision(), envelope.newRevision()));
        org.arcadia.arc_quest.data.sync.DatapackContentSyncService.syncIfChanged(player);

        pushSyncForActiveUIs(player, data, "flags_vars_sync");
        syncRequiredCounts(player, data);
    }

    public static void syncTrackedQuest(ServerPlayer player, ArcQuestPlayer data) {
        if (player.connection == null) return;
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        PacketDistributor.sendToPlayer(player, new S2CSyncTrackedQuestPacket(
                data.getQuestTrackingSnapshot(), data.getLastQuestTrackingChangeReason(),
                envelope.playerSessionEpoch(), envelope.baseRevision(), envelope.newRevision()));
        syncTrackedPhaseFocus(player, data);
    }

    public static void syncTrackedPhaseFocus(ServerPlayer player, ArcQuestPlayer data) {
        if (player.connection == null) return;
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        PacketDistributor.sendToPlayer(player, new S2CSyncTrackedPhaseFocusPacket(
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
        PacketDistributor.sendToServer(packet);
    }

    public static void sendTrackedQuestUpdate(@Nullable String questId) {
        PacketDistributor.sendToServer(new C2SSetTrackedQuestPacket(questId));
    }

    public static void sendTrackedQuestUpdate(@Nullable String questId, @Nullable String phaseId) {
        if (questId != null && phaseId != null && !phaseId.isBlank()) {
            sendTrackedPhaseFocusUpdate(questId, phaseId);
        } else {
            sendTrackedQuestUpdate(questId);
        }
    }

    public static void sendTrackedPhaseFocusUpdate(String questId, String phaseId) {
        PacketDistributor.sendToServer(new C2SSetTrackedPhaseFocusPacket(questId, phaseId));
    }

    public static void markPhaseStoryRead(String questId, String phaseId) {
        PacketDistributor.sendToServer(new C2SMarkPhaseStoryReadPacket(questId, phaseId));
    }

    public static void sendSubmitOffer(C2SSubmitOfferPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendClaimCollectionReward(C2SClaimCollectionRewardPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendDialogueChoice(C2SDialogueChoicePacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendTradeRequest(C2SRequestTradePacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendTradeSyncRequest(C2SRequestTradeSyncPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendGachaControl(C2SGachaControlPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendDrawGacha(C2SDrawGachaPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendConfirmDraw(C2SConfirmDrawPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendMarkGuideSeen(C2SMarkGuideSeenPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendMarkAllGuidesSeen(C2SMarkAllGuidesSeenPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendGuideProgress(C2SUpdateGuideProgressPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    // ═══════════════════════════════════════════════════════
    // 服务端定向发送（S2C）
    // ═══════════════════════════════════════════════════════

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendTradePacket(ServerPlayer player, S2COpenTradePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendTradeStatePacket(ServerPlayer player, S2CSyncTradeStatePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendGachaStatePacket(ServerPlayer player, S2CGachaStatePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendGuideOpenPacket(ServerPlayer player, S2COpenGuidePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendDrawResultPacket(ServerPlayer player, S2CDrawResultPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendDrawFailedPacket(ServerPlayer player, S2CDrawFailedPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendTranscriptDeltaPacket(ServerPlayer player, S2CDialogueTranscriptDeltaPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendTranscriptSnapshotPacket(ServerPlayer player, S2CDialogueTranscriptSnapshotPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void syncMarkers(ServerPlayer player, ArcQuestPlayer data) {
        if (player.connection == null) return;
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);

        List<S2CSyncMarkersPacket.MarkerEntry> entries = data.getAllMarkers().values().stream()
                .map(ArcQuestNetwork::toMarkerEntry)
                .toList();

        PacketDistributor.sendToPlayer(player,
                S2CSyncMarkersPacket.snapshot(epoch, revision, entries));
    }

    public static void syncMarkerDeltaClear(ServerPlayer player) {
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);
        PacketDistributor.sendToPlayer(player,
                S2CSyncMarkersPacket.deltaClear(epoch, revision));
    }

    public static void syncMarkerDeltaUpsert(ServerPlayer player, QuestMarkerData marker) {
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);
        PacketDistributor.sendToPlayer(player,
                S2CSyncMarkersPacket.deltaAdd(epoch, revision, List.of(toMarkerEntry(marker))));
    }

    public static void syncMarkerDeltaRemove(ServerPlayer player, String markerId) {
        long epoch = currentMarkerEpoch(player);
        long revision = nextMarkerRevision(player);
        PacketDistributor.sendToPlayer(player,
                S2CSyncMarkersPacket.deltaRemove(epoch, revision, markerId));
    }

    public static void requestMarkerResync() {
        PacketDistributor.sendToServer(new C2SRequestMarkerResyncPacket());
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
