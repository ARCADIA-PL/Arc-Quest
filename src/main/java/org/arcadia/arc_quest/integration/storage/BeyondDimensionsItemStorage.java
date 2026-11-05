package org.arcadia.arc_quest.integration.storage;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.inventory.PlayerItemAccess;

/** Loaded only when Beyond Dimensions is installed; supports the 0.7.26 and 0.8.9 server APIs. */
public final class BeyondDimensionsItemStorage {
    private static final Map<UnifiedStorage, CachedContents> CACHE = new WeakHashMap<>();
    private static final boolean HAS_RESTORE = hasRestore();
    private BeyondDimensionsItemStorage() {}

    public static void register() {
        PlayerItemAccess.register(ResourceLocation.parse("arc_quest:beyonddimensions"), player -> {
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (!authorized(net, player)) return null;
            return new View(player.server, player, net);
        });
        PlayerItemAccess.registerRecovery((server, identity) -> {
            if (!identity.startsWith("beyonddimensions:")) return null;
            int id;
            try { id = Integer.parseInt(identity.substring("beyonddimensions:".length())); }
            catch (NumberFormatException malformed) { return null; }
            if (id < 0) return null;
            var net = server.overworld().getDataStorage().get(DimensionsNet::load, "BDNet_" + id);
            return net == null ? null : new View(server, null, net);
        });
    }

    private static boolean authorized(DimensionsNet net, ServerPlayer player) {
        return player != null && net != null && !net.deleted && (net.isOwner(player) || net.isManager(player)
                || net.getPlayers().contains(player.getUUID()));
    }

    private static final class CachedContents {
        boolean dirty = true;
        List<PlayerItemAccess.StackAmount> items = List.of();
    }

    private record View(MinecraftServer server, ServerPlayer player, DimensionsNet net) implements PlayerItemAccess.DurableStorage {
        @Override public boolean committed(String receipt, UUID owner, List<PlayerItemAccess.StackAmount> cost) {
            return BeyondDimensionsPayments.committed(server, net, receipt, owner, cost);
        }
        @Override public void commit(String receipt, UUID owner, List<PlayerItemAccess.StackAmount> cost) {
            if (!authorized(net, player) || !owner.equals(player.getUUID()) || DimensionsNet.getPrimaryNetFromPlayer(player) != net)
                throw new IllegalStateException("施工材料网络或成员权限已变化，请重新提交。");
            BeyondDimensionsPayments.commit(server, net, receipt, owner, cost);
        }
        @Override public void cancel(String receipt, UUID owner, List<PlayerItemAccess.StackAmount> cost) {
            BeyondDimensionsPayments.cancel(server, net, receipt, owner, cost);
        }
        @Override public void finish(String receipt, UUID owner, List<PlayerItemAccess.StackAmount> cost) {
            BeyondDimensionsPayments.finish(server, net, receipt, owner, cost);
        }
        @Override public String identity() { return "beyonddimensions:" + net.getId(); }
        @Override public List<PlayerItemAccess.StackAmount> contents() {
            if (!authorized(net, player)) return List.of();
            UnifiedStorage storage = net.getUnifiedStorage();
            CachedContents cache = CACHE.computeIfAbsent(storage, source -> {
                var created = new CachedContents();
                source.subscribeAnyWeak(created, changed -> changed.dirty = true);
                // Ordinary insert/extract suppresses "any" and publishes only a delta.
                source.subscribeDeltaWeak(created, (changed, key, amount, inserted) -> changed.dirty = true);
                return created;
            });
            if (cache.dirty) {
                List<PlayerItemAccess.StackAmount> items = new ArrayList<>();
                for (var entry : storage.getStorage()) if (entry.amount() > 0 && entry.key() instanceof ItemStackKey item)
                    items.add(new PlayerItemAccess.StackAmount(item.copyStack(), entry.amount()));
                cache.items = List.copyOf(items);
                cache.dirty = false;
            }
            return cache.items;
        }

        @Override public PlayerItemAccess.Removal remove(Predicate<ItemStack> matcher, int requested) {
            if (!authorized(net, player) || DimensionsNet.getPrimaryNetFromPlayer(player) != net)
                throw new IllegalStateException("Dimensional network membership changed");
            UnifiedStorage storage = net.getUnifiedStorage();
            Map<IStackKey<?>, Long> before = amounts(storage);
            List<PlayerItemAccess.StackAmount> removed = new ArrayList<>();
            try {
                int left = requested;
                for (var entry : List.copyOf(storage.getStorage())) {
                    if (left <= 0) break;
                    if (!(entry.key() instanceof ItemStackKey item) || !matcher.test(item.copyStack())) continue;
                    int take = (int) Math.min(left, Math.max(0, entry.amount()));
                    if (take == 0) continue;
                    var debit = storage.extract(item, take, false, false);
                    if (debit.amount() > 0) {
                        if (debit.amount() > take || !item.equals(debit.key()))
                            throw new IllegalStateException("Unexpected dimensional extraction result");
                        removed.add(new PlayerItemAccess.StackAmount(item.copyStack(), debit.amount()));
                        left -= (int) debit.amount();
                    }
                }
            } catch (RuntimeException failure) {
                try { restoreDelta(storage, difference(before, amounts(storage))); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
            Map<IStackKey<?>, Long> refund = difference(before, amounts(storage));
            return new PlayerItemAccess.Removal() {
                private boolean rolledBack;
                @Override public List<PlayerItemAccess.StackAmount> removed() { return List.copyOf(removed); }
                @Override public void rollback() {
                    if (rolledBack) return;
                    restoreDelta(storage, refund);
                    rolledBack = true;
                }
            };
        }
    }

    static Map<IStackKey<?>, Long> amounts(UnifiedStorage storage) {
        Map<IStackKey<?>, Long> result = new LinkedHashMap<>();
        for (var entry : storage.getStorage()) result.put(entry.key(), entry.amount());
        return result;
    }

    static Map<IStackKey<?>, Long> difference(Map<IStackKey<?>, Long> before, Map<IStackKey<?>, Long> after) {
        Set<IStackKey<?>> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        Map<IStackKey<?>, Long> result = new LinkedHashMap<>();
        for (var key : keys) {
            long delta = before.getOrDefault(key, 0L) - after.getOrDefault(key, 0L);
            if (delta != 0) result.put(key, delta);
        }
        return result;
    }

    /** Compensation only: reverse this payment's resource deltas, including extraction-rule costs. */
    static void restoreDelta(UnifiedStorage storage, Map<IStackKey<?>, Long> refund) {
        Map<IStackKey<?>, Long> targets = new LinkedHashMap<>();
        for (var entry : refund.entrySet()) {
            long current = storage.getStackByKey(entry.getKey()).amount();
            long restored = Math.addExact(current, entry.getValue());
            if (restored < 0) throw new IllegalStateException("Dimensional refund conflicts with another mutation");
            targets.put(entry.getKey(), restored);
        }
        for (var entry : targets.entrySet()) {
            long current = storage.getStackByKey(entry.getKey()).amount();
            if (entry.getValue() >= current) restore(storage, entry.getKey(), entry.getValue() - current);
            else {
                // restore deliberately bypasses capacity: refunds must survive a full/reduced network.
                storage.setAmountByKey(entry.getKey(), 0);
                restore(storage, entry.getKey(), entry.getValue());
            }
        }
        storage.onChange();
    }

    private static boolean hasRestore() {
        try {
            UnifiedStorage.class.getMethod("restore", IStackKey.class, long.class);
            return true;
        } catch (NoSuchMethodException legacyVersion) {
            return false;
        }
    }

    private static void restore(UnifiedStorage storage, IStackKey<?> key, long amount) {
        if (amount == 0) return;
        if (HAS_RESTORE) {
            storage.restore(key, amount);
            return;
        }
        // 0.7.26 has no restore API. Its public setter maintains indexes and notifications,
        // but clamps to slotCapacity. Temporarily widen that bound for this refund only;
        // finally restores the exact configured limit even if a listener throws.
        long target = Math.addExact(storage.getStackByKey(key).amount(), amount);
        long capacity = storage.slotCapacity;
        int slots = storage.slotMaxSize;
        try {
            storage.slotCapacity = Math.max(capacity, target);
            storage.slotMaxSize = Math.max(slots, Math.addExact(storage.getStorage().size(), 1));
            if (storage.setAmountByKey(key, target) != target)
                throw new IllegalStateException("Legacy dimensional refund was incomplete");
        } finally {
            storage.slotCapacity = capacity;
            storage.slotMaxSize = slots;
            storage.onChange();
        }
    }
}
