package org.arcadia.arc_quest.quest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.guide.network.C2SMarkGuideSeenPacket;
import org.arcadia.arc_quest.guide.network.S2COpenGuidePacket;
import org.arcadia.arc_quest.guide.network.S2CSyncGuideStatePacket;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.trade.gacha.network.*;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.network.C2SRequestTradeSyncPacket;
import org.arcadia.arc_quest.trade.network.S2COpenTradePacket;
import org.arcadia.arc_quest.trade.network.S2CSyncTradeStatePacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arc Quest 网络通信中心。
 * 使用 NeoForge PayloadRegistrar / CustomPacketPayload 进行 S2C / C2S 数据包注册与发送。
 */
public final class ArcQuestNetwork {

    private static final Map<UUID, Long> MARKER_EPOCH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MARKER_REVISION = new ConcurrentHashMap<>();

    private ArcQuestNetwork() {
    }

    /**
     * 在 Mod 构造器里挂到 {@code modEventBus.addListener(ArcQuestNetwork::register)}。
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Arc_Quest.MOD_ID).versioned("5");

        // ─── S2C（play to client）───
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientPayloadHandlers(registrar);
        } else {
            registerClientPayloadCodecs(registrar);
        }

        // ─── C2S（play to server）───
        registrar.playToServer(C2SRequestQuestActionPacket.TYPE, C2SRequestQuestActionPacket.STREAM_CODEC, C2SRequestQuestActionPacket::handle);
        registrar.playToServer(C2SRequestQuestResyncPacket.TYPE, C2SRequestQuestResyncPacket.STREAM_CODEC, C2SRequestQuestResyncPacket::handle);
        registrar.playToServer(C2SSubmitOfferPacket.TYPE, C2SSubmitOfferPacket.STREAM_CODEC, C2SSubmitOfferPacket::handle);
        registrar.playToServer(C2SClaimCollectionRewardPacket.TYPE, C2SClaimCollectionRewardPacket.STREAM_CODEC, C2SClaimCollectionRewardPacket::handle);
        registrar.playToServer(C2SGachaControlPacket.TYPE, C2SGachaControlPacket.STREAM_CODEC, C2SGachaControlPacket::handle);
        registrar.playToServer(C2SDrawGachaPacket.TYPE, C2SDrawGachaPacket.STREAM_CODEC, C2SDrawGachaPacket::handle);
        registrar.playToServer(C2SConfirmDrawPacket.TYPE, C2SConfirmDrawPacket.STREAM_CODEC, C2SConfirmDrawPacket::handle);
        registrar.playToServer(C2SRequestTradePacket.TYPE, C2SRequestTradePacket.STREAM_CODEC, C2SRequestTradePacket::handle);
        registrar.playToServer(C2SRequestTradeSyncPacket.TYPE, C2SRequestTradeSyncPacket.STREAM_CODEC, C2SRequestTradeSyncPacket::handle);
        registrar.playToServer(C2SDialogueChoicePacket.TYPE, C2SDialogueChoicePacket.STREAM_CODEC, C2SDialogueChoicePacket::handle);
        registrar.playToServer(C2SMarkGuideSeenPacket.TYPE, C2SMarkGuideSeenPacket.STREAM_CODEC, C2SMarkGuideSeenPacket::handle);
        registrar.playToServer(C2SSetTrackedQuestPacket.TYPE, C2SSetTrackedQuestPacket.STREAM_CODEC, C2SSetTrackedQuestPacket::handle);
    }

    private static void registerClientPayloadHandlers(PayloadRegistrar registrar) {
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
    }

    private static void registerClientPayloadCodecs(PayloadRegistrar registrar) {
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

        PacketDistributor.sendToPlayer(player,
                new S2CSyncFullDataPacket(data, envelope.playerSessionEpoch(), envelope.newRevision()));
        syncMarkers(player, data);
    }

    /**
     * 单任务状态同步
     */
    public static void syncQuestState(ServerPlayer player, QuestRuntimeData data) {
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        PacketDistributor.sendToPlayer(player,
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
        PacketDistributor.sendToPlayer(player,
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
        PacketDistributor.sendToPlayer(player,
                new S2CSyncFlagsVarsPacket(data, envelope.playerSessionEpoch(),
                        envelope.baseRevision(), envelope.newRevision()));

        pushSyncForActiveUIs(player, data, "flags_vars_sync");
    }

    public static void syncTrackedQuest(ServerPlayer player, ArcQuestPlayer data) {
        PacketDistributor.sendToPlayer(player, new S2CSyncTrackedQuestPacket(data.getTrackedQuestId()));
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

    public static void sendTrackedQuestUpdate(@Nullable String questId) {
        PacketDistributor.sendToServer(new C2SSetTrackedQuestPacket(questId));
    }

    // ═══════════════════════════════════════════════════════
    // 服务端定向发送（S2C）
    // ═══════════════════════════════════════════════════════

    public static void sendToPlayer(ServerPlayer player, S2COpenDialoguePacket packet) {
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

    public static void bumpMarkerEpoch(ServerPlayer player) {
        resetMarkerStream(player);
    }

    private static long nextMarkerRevision(ServerPlayer player) {
        return MARKER_REVISION.merge(player.getUUID(), 1L, Long::sum);
    }

    private static long currentMarkerEpoch(ServerPlayer player) {
        return MARKER_EPOCH.computeIfAbsent(player.getUUID(), k -> System.currentTimeMillis());
    }

    private static void resetMarkerStream(ServerPlayer player) {
        MARKER_EPOCH.put(player.getUUID(), System.currentTimeMillis());
        MARKER_REVISION.put(player.getUUID(), 0L);
    }

    public static void clearPlayerMarkerState(UUID uuid) {
        MARKER_EPOCH.remove(uuid);
        MARKER_REVISION.remove(uuid);
    }

    private static S2CSyncMarkersPacket.MarkerEntry toMarkerEntry(QuestMarkerData m) {
        return new S2CSyncMarkersPacket.MarkerEntry(
                m.getId(),
                m.getType().name(),
                m.getWorldX(),
                m.getWorldY(),
                m.getWorldZ(),
                m.getLabel(),
                m.getDimension(),
                m.getQuestId(),
                m.getPhaseId(),
                m.getObjectiveIndex(),
                m.getFollowEntityId(),
                m.getFollowEntityUuid(),
                m.getFollowEntityGuid(),
                m.getAttachPoint().name(),
                m.getColorARGB(),
                m.getState().name(),
                m.isShowDistance(),
                m.isAllowOffscreenArrow()
        );
    }
}
