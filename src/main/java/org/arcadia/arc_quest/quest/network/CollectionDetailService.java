package org.arcadia.arc_quest.quest.network;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.CollectionDetailDocument;
import org.arcadia.arc_quest.quest.logic.CollectionViewService;
import org.arcadia.arc_quest.quest.api.collection.CollectionViews.EntryRef;
import org.arcadia.arc_quest.quest.api.collection.CollectionViews.RunRef;
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
        var view = CollectionViewService.project(player);
        var row = view == null ? null : view.resolve(new EntryRef(new RunRef(request.questId(), request.runId()),
                request.phaseId(), request.bindingId()), request.variant());
        if (row == null) return CollectionDetailDocument.EMPTY;
        try {
            var document = CollectionDetailProviderRegistry.describe(new CollectionDetailProviderRegistry.Context(
                    player, row.runtime(), request.phaseId(), request.bindingId(), row.entry(), request.variant()));
            // Validate provider data before handing it to the asynchronous network encoder.
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try { S2CCollectionDetailPacket.encode(new S2CCollectionDetailPacket(request, document), buffer); }
            finally { buffer.release(); }
            return document;
        } catch (RuntimeException failure) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_NETWORK, "Supplementary details unavailable for {}: {}", row.entry().getEntryId(), failure.toString());
            return new CollectionDetailDocument(net.minecraft.network.chat.Component.translatable("arc_quest.collection.detail.unavailable"), java.util.List.of());
        }
    }
}
