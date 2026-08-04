package org.arcadia.arc_quest.quest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.guide.network.C2SMarkGuideSeenPacket;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Arc Quest 缂傚倸鍊搁崯顖炲垂閸︻厼鍨濋柛顐ｆ礃閻掔粯鎱ㄥ鍡椾簵缂佽妫楅埥澶愬箼閸愩劌绠圭紓浣规そ缁犳牗淇?
 * 濠电偠鎻紞鈧繛澶嬫礋瀵?NeoForge PayloadRegistrar / CustomPacketPayload 闂佸搫顦弲婊呯矙閺嶎厹鈧?S2C / C2S 闂備浇妗ㄩ懗鑸垫櫠濡も偓閻ｅ灚绗熼埀顒€鐣烽悩璇插窛妞ゆ棁濮ゅ▓姗€姊洪崨濠傚濠靛倹姊荤划顓炵暆閸曨偆鐓戦梺纭呮彧閼靛綊宕戦幘瀛樺缁剧増锚娴?
 */
public final class ArcQuestNetwork {

    private static final Map<UUID, Long> MARKER_EPOCH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MARKER_REVISION = new ConcurrentHashMap<>();
    private static final AtomicLong MARKER_EPOCH_SEQUENCE = new AtomicLong(System.currentTimeMillis());

    private ArcQuestNetwork() {
    }

    /**
     * 闂?Mod 闂備礁鎼鍛偓姘嵆閸┾偓妞ゆ帒鍊稿瓭濠电偛鎳忕敮锟犲蓟瀹€鈧禒锕傚箚瑜忓Σ鎼佹⒑?{@code modEventBus.addListener(ArcQuestNetwork::register)}闂?
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Arc_Quest.MOD_ID).versioned("8");

        // 闂備礁鍟块崢婊堝磻閹剧粯鐓冮柛蹇擃槸娴滈箖姊洪崘鎻掑辅闁?S2C闂備焦瀵х粙鎴濐焽缁屾槮y to client闂備焦瀵х粙鎴λ囬锕€缁╅柕蹇嬪€曢悡姗€鏌嶈閸撶喖宕洪悙鍝勭劦?
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientPayloadHandlers(registrar);
        } else {
            registerClientPayloadCodecs(registrar);
        }

        // 闂備礁鍟块崢婊堝磻閹剧粯鐓冮柛蹇擃槸娴滈箖姊洪崘鎻掑辅闁?C2S闂備焦瀵х粙鎴濐焽缁屾槮y to server闂備焦瀵х粙鎴λ囬锕€缁╅柕蹇嬪€曢悡姗€鏌嶈閸撶喖宕洪悙鍝勭劦?
        registrar.playToServer(C2SRequestQuestActionPacket.TYPE, C2SRequestQuestActionPacket.STREAM_CODEC, C2SRequestQuestActionPacket::handle);
        registrar.playToServer(C2SRequestQuestResyncPacket.TYPE, C2SRequestQuestResyncPacket.STREAM_CODEC, C2SRequestQuestResyncPacket::handle);
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
        registrar.playToServer(C2SUpdateGuideProgressPacket.TYPE, C2SUpdateGuideProgressPacket.STREAM_CODEC, C2SUpdateGuideProgressPacket::handle);
        registrar.playToServer(C2SSetTrackedQuestPacket.TYPE, C2SSetTrackedQuestPacket.STREAM_CODEC, C2SSetTrackedQuestPacket::handle);
        registrar.playToServer(C2SMarkPhaseStoryReadPacket.TYPE, C2SMarkPhaseStoryReadPacket.STREAM_CODEC, C2SMarkPhaseStoryReadPacket::handle);
        registrar.playToServer(C2SCloseQuestEditorPacket.TYPE, C2SCloseQuestEditorPacket.STREAM_CODEC, C2SCloseQuestEditorPacket::handle);
        registrar.playToServer(C2SSaveQuestEditorPacket.TYPE, C2SSaveQuestEditorPacket.STREAM_CODEC, C2SSaveQuestEditorPacket::handle);
    }

    private static void registerClientPayloadHandlers(PayloadRegistrar registrar) {
        registrar.playToClient(S2CDatapackReloadEpochPacket.TYPE, S2CDatapackReloadEpochPacket.STREAM_CODEC, S2CDatapackReloadEpochPacket::handle);
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
        registrar.playToClient(S2COpenQuestEditorPacket.TYPE, S2COpenQuestEditorPacket.STREAM_CODEC, S2COpenQuestEditorPacket::handle);
        registrar.playToClient(S2CQuestEditorResultPacket.TYPE, S2CQuestEditorResultPacket.STREAM_CODEC, S2CQuestEditorResultPacket::handle);
    }

    private static void registerClientPayloadCodecs(PayloadRegistrar registrar) {
        registrar.playToClient(S2CDatapackReloadEpochPacket.TYPE, S2CDatapackReloadEpochPacket.STREAM_CODEC, (packet, context) -> {});
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
        registrar.playToClient(S2COpenQuestEditorPacket.TYPE, S2COpenQuestEditorPacket.STREAM_CODEC, (packet, context) -> {});
        registrar.playToClient(S2CQuestEditorResultPacket.TYPE, S2CQuestEditorResultPacket.STREAM_CODEC, (packet, context) -> {});
    }

    // 闂備礁纾崕銈夊礉韫囨稑鐤鹃柡灞诲劚閻撴盯鏌熼懜顒€濡芥繛鍛矒閺屽秹鎮滃Ο鍝勵潊闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春?
    // 闂備礁鎼悧鍡欑矓鐎涙ɑ鍙忛柣鏃囨閸楁碍銇勯弽銊ф噮闁宠甯￠弻鐔兼偡閺夋寧些閻熸粍婢橀崐鍧楀蓟閸涱喗濯撮悷娆忓閸橀潧鈹?
    // 闂備礁纾崕銈夊礉韫囨稑鐤鹃柡灞诲劚閻撴盯鏌熼懜顒€濡芥繛鍛矒閺屽秹鎮滃Ο鍝勵潊闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春?

    /**
     * 闂備胶顭堢换鍫ュ礉閹达箑闂繛宸簻鐟欙箓鎮橀悙浣冩闁告柨瀚伴弻銊モ槈濡厧鈪卞┑鐐叉噷閸庢挳濡?闂傚倷鐒﹁ぐ鍐矓閸洖鏋?缂傚倸鍊烽悞锕€顭囧▎鎴斿亾鐟欏嫬鈻曠€规洘宀稿畷鍫曞Ω閵夈儳鍝庨梻?
     */
    public static void sendQuestEditorOpen(ServerPlayer player, S2COpenQuestEditorPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendQuestEditorResult(ServerPlayer player, S2CQuestEditorResultPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendQuestEditorSave(C2SSaveQuestEditorPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendQuestEditorClose(C2SCloseQuestEditorPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void syncFullData(ServerPlayer player, ArcQuestPlayer data) {
        resetMarkerStream(player);
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);

        PacketDistributor.sendToPlayer(player,
                new S2CSyncFullDataPacket(data, envelope.playerSessionEpoch(), envelope.newRevision()));
        syncMarkers(player, data);
    }

    /**
     * 闂備礁鎲￠〃鍡椕洪幋锕€绠查柕蹇嬪€曠粈澶愭煥濞戞ê顏╃粭鎴︽⒑鐠団€冲幐缂佲偓娓氣偓楠炲啴骞橀幇浣规〃?
     */
    public static void syncQuestState(ServerPlayer player, QuestRuntimeData data) {
        QuestSyncRevisionManager.Envelope envelope = QuestSyncRevisionManager.next(player);
        PacketDistributor.sendToPlayer(player,
                new S2CSyncQuestStatePacket(data, envelope.playerSessionEpoch(),
                        envelope.baseRevision(), envelope.newRevision()));

        pushSyncForActiveUIs(player, null, "quest_state_sync");
    }

    /**
     * 濠电姭鎷冮崨顓濇闂侀潻绲块崕銈咁焽鐠囨祴妲堟俊顖欒閸炴椽姊洪崨濠呭闁哄牜鍓熼、妤呮倷閻戞ɑ娅栭梺鍓插亝缁诲嫬袙閸儲鐓曢柡宓嫬鐝旂紓?
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
     * Flags / Variables 闂備礁鎲￠懝楣冨嫉椤掑嫷鏁?
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
     * Quest 闂備礁鎲￠懝楣冨嫉椤掑嫷鏁嗛柣鎰惈鐟欙箓骞栨潏鍓ф偧闁糕晝濮撮埥澶愬箻椤栨矮澹曢梺鑽ゅ枑閻熻京绮婚幋锝冧汗?Trade + Gacha 婵犵數鍋涘璺何ｉ幒鏃傜煔妞ゆ帒瀚崑鈺冣偓鍏夊亾闁告洦鍓氶妴?push-first闂?
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

    // 闂備礁纾崕銈夊礉韫囨稑鐤鹃柡灞诲劚閻撴盯鏌熼懜顒€濡芥繛鍛矒閺屽秹鎮滃Ο鍝勵潊闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春?
    // 闂佽楠哥粻宥夊垂濞差亜鏄ユ繛鎴炴皑閸楁碍銇勯弽銊ф噮闁宠甯￠弻鐔兼偡閺夋寧些閻熸粍婢橀崐鍧楀蓟閸涱喗濯撮悷娆忓閸橀潧鈹?
    // 闂備礁纾崕銈夊礉韫囨稑鐤鹃柡灞诲劚閻撴盯鏌熼懜顒€濡芥繛鍛矒閺屽秹鎮滃Ο鍝勵潊闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春?

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

    public static void sendGuideProgress(C2SUpdateGuideProgressPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendTrackedQuestUpdate(@Nullable String questId) {
        PacketDistributor.sendToServer(new C2SSetTrackedQuestPacket(questId));
    }

    public static void markPhaseStoryRead(String questId, String phaseId) {
        PacketDistributor.sendToServer(new C2SMarkPhaseStoryReadPacket(questId, phaseId));
    }

    // 闂備礁纾崕銈夊礉韫囨稑鐤鹃柡灞诲劚閻撴盯鏌熼懜顒€濡芥繛鍛矒閺屽秹鎮滃Ο鍝勵潊闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春?
    // 闂備礁鎼悧鍡欑矓鐎涙ɑ鍙忛柣鏃囨閸楁碍銇勯弽銊︾殤闁哄棔鍗抽弻娑橆潩椤撶偟浠撮悷婊勬緲閸婂潡寮婚崨顔剧懝濠电姴瀚ˇ銖?C闂?
    // 闂備礁纾崕銈夊礉韫囨稑鐤鹃柡灞诲劚閻撴盯鏌熼懜顒€濡芥繛鍛矒閺屽秹鎮滃Ο鍝勵潊闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春閿熺姴绠涢柕濠忛檮閻濇娊姊哄畷鍥у妺闁告柨绻樺鏌ュ蓟閵夈儳鍘搁梺纭呭焽閸斿秴鈻嶅鍫熺厓闁绘垶锚婵偓闂佸搫鎳忛悡锟犲春?

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

    public static void requestMarkerResync() {
        PacketDistributor.sendToServer(new C2SRequestMarkerResyncPacket());
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
        return MARKER_EPOCH.computeIfAbsent(player.getUUID(), k -> MARKER_EPOCH_SEQUENCE.incrementAndGet());
    }

    private static void resetMarkerStream(ServerPlayer player) {
        MARKER_EPOCH.put(player.getUUID(), MARKER_EPOCH_SEQUENCE.incrementAndGet());
        MARKER_REVISION.put(player.getUUID(), 0L);
    }

    public static void clearPlayerMarkerState(UUID uuid) {
        MARKER_EPOCH.remove(uuid);
        MARKER_REVISION.remove(uuid);
    }

    private static S2CSyncMarkersPacket.MarkerEntry toMarkerEntry(QuestMarkerData m) {
        return MarkerNetworkCodec.toEntry(m);
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
        PacketDistributor.sendToPlayer(player, new S2CDatapackReloadEpochPacket(epoch));
    }
}
