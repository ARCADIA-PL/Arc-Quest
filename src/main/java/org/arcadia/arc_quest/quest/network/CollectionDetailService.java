package org.arcadia.arc_quest.quest.network;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.CollectionDetailDocument;
import org.arcadia.arc_quest.quest.logic.CollectionRunAccess;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.CollectionDetailProviderRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import java.util.Map;
import java.util.WeakHashMap;

/** Authorizes the frozen binding and its public identity before invoking any external provider. */
public final class CollectionDetailService {
    private CollectionDetailService() {}
    private record Window(long tick, int requests) {}
    private static final Map<ServerPlayer, Window> WINDOWS = new WeakHashMap<>();
    public static boolean allowRequest(ServerPlayer player) {
        long tick = player.server.getTickCount();
        var previous = WINDOWS.get(player);
        if (previous == null || tick - previous.tick() >= 20 || tick < previous.tick()) {
            WINDOWS.put(player, new Window(tick, 1)); return true;
        }
        if (previous.requests() >= 12) return false;
        WINDOWS.put(player, new Window(previous.tick(), previous.requests() + 1)); return true;
    }
    public static CollectionDetailDocument resolve(ServerPlayer player, C2SCollectionDetailRequest request) {
        if (player == null || request.playerEpoch() <= 0
                || !PlayerSessionEpochManager.matches(player, request.playerEpoch())) return CollectionDetailDocument.EMPTY;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return CollectionDetailDocument.EMPTY;
        var runtime = data.getActiveQuest(request.questId());
        if (runtime == null) runtime = data.getCollectionArchives().get(request.questId());
        if (runtime == null || runtime.getCollectionData() == null
                || !runtime.getCollectionData().getRunId().equals(request.runId())) return CollectionDetailDocument.EMPTY;
        var quest = CollectionRunAccess.resolve(player.server, runtime);
        var phase = quest == null ? null : quest.getPhase(request.phaseId());
        if (phase == null || !phase.hasCollectionSheet() || !(runtime.isPhaseActive(request.phaseId())
                || runtime.isPhaseCompleted(request.phaseId()) || runtime.isPhasePendingManualAdvance(request.phaseId())))
            return CollectionDetailDocument.EMPTY;
        var sheet = CollectionProgressProjector.project(quest, phase, runtime, data.getCollectionRecords());
        var row = sheet.binding(request.bindingId());
        if (row == null || !row.visible() || !row.revealed()) return CollectionDetailDocument.EMPTY;
        var entry = quest.getCollectionConfig().getEntry(row.entryId());
        try {
            var document = CollectionDetailProviderRegistry.describe(new CollectionDetailProviderRegistry.Context(
                    player, runtime, request.phaseId(), request.bindingId(), entry));
            // Validate provider data before handing it to the asynchronous network encoder.
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), player.registryAccess());
            try { S2CCollectionDetailPacket.encode(new S2CCollectionDetailPacket(request, document), buffer); }
            finally { buffer.release(); }
            return document;
        } catch (RuntimeException failure) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_NETWORK, "Supplementary details unavailable for {}: {}", row.entryId(), failure.toString());
            return new CollectionDetailDocument(net.minecraft.network.chat.Component.translatable("arc_quest.collection.detail.unavailable"), java.util.List.of());
        }
    }
}
