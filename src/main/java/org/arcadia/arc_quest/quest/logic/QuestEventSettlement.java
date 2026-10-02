package org.arcadia.arc_quest.quest.logic;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A real gameplay event updates all of its frozen recipients before advancing any phase.
 * Sessions are synchronous and disappear when the outer scope closes; no player is retained.
 */
public final class QuestEventSettlement {
    private static final Map<UUID, Batch> BATCHES = new HashMap<>();
    private static final Map<UUID, Integer> REWARD_DEPTH = new HashMap<>();

    private QuestEventSettlement() { }

    public static Scope begin(ServerPlayer player) {
        UUID id = player.getUUID();
        Batch batch = BATCHES.computeIfAbsent(id, ignored -> new Batch());
        batch.begin();
        return new Scope(id, batch);
    }

    /** Returns true when phase/Binding evaluation has been queued for this event's end. */
    public static boolean defer(ServerPlayer player, String questId) {
        Batch batch = BATCHES.get(player.getUUID());
        if (batch == null || !batch.updating()) return false;
        var data = ArcQuestPlayerManager.get(player);
        QuestRuntimeData runtime = data == null ? null : data.getActiveQuest(questId);
        if (runtime == null) return false;
        return batch.defer(runtime, () -> {
            // Reset/abandon/reaccept callbacks must never carry an earlier event into another run.
            var current = ArcQuestPlayerManager.get(player);
            var definition = CollectionRunAccess.resolve(player.server, runtime);
            if (current != null && definition != null && current.getActiveQuest(questId) == runtime) {
                QuestProgressHandler.refreshCollectionSheets(player, current, runtime, definition);
            }
        });
    }

    public static boolean isGrantingReward(ServerPlayer player) {
        return REWARD_DEPTH.getOrDefault(player.getUUID(), 0) > 0;
    }

    /** Rewards cannot reenter acquisition dispatch or reappear as acquisitions in the next tick diff. */
    public static void runReward(ServerPlayer player, Runnable grant) {
        try (var scope = beginReward(player)) { grant.run(); }
    }

    /** Keeps direct IReward.grant invocation sites intact for the public progress handler's Mixin contract. */
    public static RewardScope beginReward(ServerPlayer player) {
        UUID id = player.getUUID();
        var before = QuestEventManager.inventorySnapshot(player);
        REWARD_DEPTH.merge(id, 1, Integer::sum);
        return new RewardScope(player, before);
    }

    public static final class RewardScope implements AutoCloseable {
        private final ServerPlayer player;
        private final Map<ResourceLocation, Integer> before;
        private boolean closed;

        private RewardScope(ServerPlayer player, Map<ResourceLocation, Integer> before) {
            this.player = player;
            this.before = before;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            UUID id = player.getUUID();
            int depth = REWARD_DEPTH.getOrDefault(id, 1) - 1;
            if (depth == 0) REWARD_DEPTH.remove(id); else REWARD_DEPTH.put(id, depth);
            // Only the outer grant accounts its whole delta, so nested item rewards are not deducted twice.
            if (depth == 0) QuestEventManager.excludeRewardAcquisitions(player, before);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final UUID playerId;
        private final Batch batch;
        private boolean closed;

        private Scope(UUID playerId, Batch batch) { this.playerId = playerId; this.batch = batch; }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try { batch.end(); }
            finally { if (batch.idle()) BATCHES.remove(playerId, batch); }
        }
    }

    /** Pure scheduler used by the production scope and its ordering/reentrancy regression tests. */
    static final class Batch {
        private int depth;
        private boolean settling;
        private final Map<Object, Runnable> pending = new LinkedHashMap<>();

        void begin() { depth++; }
        boolean updating() { return depth > 0; }
        boolean idle() { return depth == 0 && !settling; }

        boolean defer(Object run, Runnable settle) {
            if (!updating()) return false;
            pending.putIfAbsent(Objects.requireNonNull(run), Objects.requireNonNull(settle));
            return true;
        }

        void end() {
            if (depth <= 0) throw new IllegalStateException("Unbalanced gameplay event scope");
            if (--depth > 0 || settling) return;
            settling = true;
            try {
                while (!pending.isEmpty()) {
                    List<Runnable> settlements = new ArrayList<>(pending.values());
                    pending.clear();
                    for (Runnable settle : settlements) settle.run();
                }
            } finally {
                settling = false;
                pending.clear();
            }
        }
    }
}
