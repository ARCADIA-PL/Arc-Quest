package org.arcadia.arc_quest.client.compat.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.network.C2SJeiCatalogRequest;
import org.arcadia.arc_quest.integration.jei.network.JeiCatalogCodec;
import org.arcadia.arc_quest.integration.jei.network.JeiSnapshotAssembler;
import org.arcadia.arc_quest.integration.jei.network.S2CJeiCatalogChunk;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Optional-viewer-independent receiver. No external JEI type is reachable when JEI is absent. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class JeiCatalogClient {
    private static boolean enabled;
    private static Object connection;
    private static long nonce, revision, epoch = -1, requiredEpoch = -1;
    private static int heartbeat;
    private static JeiSnapshotAssembler assembler;
    private static JeiSnapshotAssembler.Snapshot pending;
    private static List<JeiCatalogEntry> entries = List.of();
    private static Map<String, JeiCatalogEntry> byId = Map.of();
    private static Runnable changed = () -> {};
    private static final ArrayDeque<Runnable> deferred = new ArrayDeque<>();
    private JeiCatalogClient() {}

    public static void activate(Runnable listener) { enabled = true; changed = listener; heartbeat = 0; }
    public static void deactivate() {
        if (connection == Minecraft.getInstance().getConnection() && connection != null && nonce > 0) {
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SJeiCatalogRequest(nonce, revision, epoch, false));
        }
        enabled = false; changed = () -> {}; reset();
    }
    public static List<JeiCatalogEntry> entries() { return entries; }
    public static JeiCatalogEntry find(String id) { return byId.get(id); }
    public static long revision() { return revision; }
    public static boolean isEnabled() { return enabled; }
    /** Screen changes must run after rendering, even when Minecraft.execute would run inline. */
    static void defer(Runnable action) { if (enabled && deferred.size() < 16) deferred.addLast(action); }

    /** Called as soon as a valid new content header arrives, before recompiled content can be navigated. */
    public static void invalidateContent(long contentEpoch) {
        if (contentEpoch <= requiredEpoch) return;
        requiredEpoch = contentEpoch;
        if (epoch < contentEpoch) replace(List.of());
        heartbeat = 0;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int queued = deferred.size();
        for (int i = 0; i < queued; i++) {
            Runnable action = deferred.pollFirst();
            if (action != null) action.run();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.player == null || minecraft.getConnection() == null) {
            if (connection != null) reset();
            return;
        }
        if (connection != minecraft.getConnection()) {
            reset(); connection = minecraft.getConnection();
            nonce = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
            assembler = new JeiSnapshotAssembler(nonce);
        }
        long appliedEpoch = ClientDatapackContentReceiver.INSTANCE.appliedEpoch();
        if (pending != null && pending.epoch() == appliedEpoch && appliedEpoch >= requiredEpoch) {
            try { apply(pending); }
            catch (RuntimeException exception) { reject(exception); }
        }
        if (appliedEpoch < 0 || appliedEpoch < requiredEpoch) return;
        if (heartbeat-- <= 0) {
            heartbeat = 100;
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SJeiCatalogRequest(nonce, revision, appliedEpoch, true));
        }
    }
    public static void accept(S2CJeiCatalogChunk packet) {
        if (!enabled || connection == null || connection != Minecraft.getInstance().getConnection() || assembler == null) return;
        try {
            assembler.accept(packet.nonce(), packet.revision(), packet.epoch(), packet.index(), packet.count(),
                    packet.totalBytes(), packet.digest(), packet.payload()).ifPresent(snapshot -> {
                if (snapshot.epoch() < requiredEpoch) return;
                long applied = ClientDatapackContentReceiver.INSTANCE.appliedEpoch();
                if (snapshot.epoch() == applied) apply(snapshot);
                else if (snapshot.epoch() > applied) pending = snapshot;
            });
        } catch (RuntimeException exception) {
            reject(exception);
        }
    }
    private static void reject(RuntimeException exception) {
        ArcQuestLog.error(ArcQuestLog.Category.DATA, "Rejected JEI catalog snapshot", exception);
        replace(List.of()); pending = null; revision = 0;
        assembler = new JeiSnapshotAssembler(nonce); heartbeat = 100;
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatableWithFallback(
                "arc_quest.jei.sync_failed", "ArcQ JEI data could not be read; retrying shortly."), false);
    }
    private static void apply(JeiSnapshotAssembler.Snapshot snapshot) {
        pending = null;
        var decoded = JeiCatalogCodec.decode(snapshot.bytes());
        epoch = snapshot.epoch(); requiredEpoch = epoch; revision = snapshot.revision();
        replace(decoded);
    }
    private static void replace(List<JeiCatalogEntry> replacement) {
        Map<String, JeiCatalogEntry> lookup = new LinkedHashMap<>();
        replacement.forEach(entry -> {
            var old = byId.get(entry.id());
            lookup.put(entry.id(), old != null && old.sameContent(entry) ? old : entry);
        });
        entries = List.copyOf(lookup.values()); byId = Map.copyOf(lookup); changed.run();
    }
    private static void reset() {
        connection = null; nonce = revision = 0; epoch = requiredEpoch = -1; heartbeat = 0;
        assembler = null; pending = null; deferred.clear(); replace(List.of());
    }
}
