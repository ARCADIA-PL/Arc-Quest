package org.arcadia.arc_quest.integration.jei.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.reload.ArcQuestReloadCoordinator;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProviders;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Only subscribed clients incur projection work. Rebuild once per second, send only changed content. */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class JeiCatalogService {
    private static final Map<UUID, Subscription> SUBSCRIBERS = new HashMap<>();
    private static final int LEASE_TICKS = 300;
    private JeiCatalogService() {}

    public static void subscribe(ServerPlayer player, C2SJeiCatalogRequest request) {
        if (player == null || player.getServer() == null || request.nonce() <= 0) return;
        var existing = SUBSCRIBERS.get(player.getUUID());
        if (!request.enabled()) {
            if (existing != null && existing.nonce == request.nonce()) SUBSCRIBERS.remove(player.getUUID());
            return;
        }
        if (request.revision() < 0 || request.contentEpoch() != ArcQuestReloadCoordinator.INSTANCE.getCommittedEpoch()) return;
        if (existing == null || existing.nonce != request.nonce()) {
            existing = new Subscription(request.nonce());
            SUBSCRIBERS.put(player.getUUID(), existing);
        }
        existing.lastRequestTick = player.getServer().getTickCount();
        existing.clientRevision = request.revision();
    }

    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        int tick = event.getServer().getTickCount();
        var iterator = SUBSCRIBERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var subscriber = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(subscriber.getKey());
            Subscription state = subscriber.getValue();
            if (player == null || tick - state.lastRequestTick > LEASE_TICKS) { iterator.remove(); continue; }
            try { update(player, state); }
            catch (RuntimeException exception) {
                ArcQuestLog.error(ArcQuestLog.Category.DATA, "Unable to build JEI catalog for {}", player.getUUID(), exception);
                // Explicitly revoke the previous view on failed projection/encoding.
                send(player, state, JeiCatalogCodec.encode(List.of(), player.registryAccess()), ArcQuestReloadCoordinator.INSTANCE.getCommittedEpoch());
            }
        }
    }
    private static void update(ServerPlayer player, Subscription state) {
        long epoch = ArcQuestReloadCoordinator.INSTANCE.getCommittedEpoch();
        byte[] bytes = JeiCatalogCodec.encode(JeiCatalogProviders.collect(player, ArcQuestPlayerManager.getOrCreate(player)), player.registryAccess());
        byte[] digest = JeiSnapshotAssembler.hash(bytes);
        if (epoch == state.epoch && Arrays.equals(digest, state.digest) && state.clientRevision == state.revision) return;
        send(player, state, bytes, epoch);
    }
    private static void send(ServerPlayer player, Subscription state, byte[] bytes, long epoch) {
        byte[] digest = JeiSnapshotAssembler.hash(bytes);
        boolean changed = epoch != state.epoch || !Arrays.equals(digest, state.digest);
        if (changed || state.revision == 0) state.revision++;
        state.epoch = epoch; state.digest = digest;
        int count = (bytes.length + JeiCatalogCodec.CHUNK_BYTES - 1) / JeiCatalogCodec.CHUNK_BYTES;
        for (int index = 0; index < count; index++) {
            int from = index * JeiCatalogCodec.CHUNK_BYTES;
            byte[] chunk = Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + JeiCatalogCodec.CHUNK_BYTES));
            PacketDistributor.sendToPlayer(player,
                    new S2CJeiCatalogChunk(state.nonce, state.revision, epoch, index, count, bytes.length, digest, chunk));
        }
        // A new heartbeat with an older revision requests retransmission.
        state.clientRevision = state.revision;
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { SUBSCRIBERS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void onStopped(ServerStoppedEvent event) { SUBSCRIBERS.clear(); }
    private static final class Subscription {
        private final long nonce;
        private int lastRequestTick;
        private long revision, clientRevision, epoch = -1;
        private byte[] digest;
        private Subscription(long nonce) { this.nonce = nonce; }
    }
}
