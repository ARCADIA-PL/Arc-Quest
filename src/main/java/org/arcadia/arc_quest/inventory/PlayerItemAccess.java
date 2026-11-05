package org.arcadia.arc_quest.inventory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.BiFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-authoritative holdings and reversible payments. The cursor is observed but never spent. */
public final class PlayerItemAccess {
    private static final Map<ResourceLocation, Function<ServerPlayer, ExternalStorage>> PROVIDERS = new LinkedHashMap<>();
    private static final List<BiFunction<MinecraftServer, String, DurableStorage>> RECOVERY = new ArrayList<>();

    private PlayerItemAccess() {}

    public record StackAmount(ItemStack template, long amount) {
        public StackAmount {
            template = template.copy();
            template.setCount(1);
            if (amount < 0) throw new IllegalArgumentException("Negative storage amount");
        }
        @Override public ItemStack template() { return template.copy(); }
        public ItemStack observedStack() {
            ItemStack result = template.copy();
            result.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return result;
        }
    }

    /** Addons supply owned storage only; no client packet can register or select a storage. */
    public interface ExternalStorage {
        String identity();
        List<StackAmount> contents();
        Removal remove(Predicate<ItemStack> matcher, int requested);
    }

    /** Receipt and resource changes must share the storage's atomic durable record. */
    public interface DurableStorage extends ExternalStorage {
        boolean committed(String receipt, UUID owner, List<StackAmount> cost);
        void commit(String receipt, UUID owner, List<StackAmount> cost);
        void cancel(String receipt, UUID owner, List<StackAmount> cost);
        void finish(String receipt, UUID owner, List<StackAmount> cost);
    }

    public static void registerRecovery(BiFunction<MinecraftServer, String, DurableStorage> resolver) {
        RECOVERY.add(Objects.requireNonNull(resolver));
    }

    public static List<DurableStorage> durableSources(ServerPlayer player) {
        return external(player).stream().filter(DurableStorage.class::isInstance).map(DurableStorage.class::cast).toList();
    }

    /** Recovery resolves the recorded source, never the payer's newly selected primary network. */
    public static DurableStorage recoverStorage(MinecraftServer server, String identity) {
        for (var resolver : RECOVERY) {
            var storage = resolver.apply(server, identity);
            if (storage != null) return storage;
        }
        throw new IllegalStateException("施工材料来源暂不可用，请恢复原存储后重试：" + identity);
    }

    public interface Removal {
        List<StackAmount> removed();
        void rollback();
        default int amount() {
            long total = 0;
            for (var entry : removed()) total = Math.min(Integer.MAX_VALUE, total + entry.amount());
            return (int) total;
        }
    }

    public static void register(ResourceLocation id, Function<ServerPlayer, ExternalStorage> provider) {
        if (PROVIDERS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(provider)) != null)
            throw new IllegalStateException("Duplicate player item storage: " + id);
    }

    private static List<ExternalStorage> external(ServerPlayer player) {
        List<ExternalStorage> sources = new ArrayList<>();
        for (var provider : PROVIDERS.values()) {
            ExternalStorage source = provider.apply(player);
            if (source != null) sources.add(source);
        }
        return sources;
    }

    /** Changes when membership/primary storage changes, rather than when its contents change. */
    public static List<String> externalIdentity(ServerPlayer player) {
        return external(player).stream().map(ExternalStorage::identity).toList();
    }

    public static List<StackAmount> snapshot(ServerPlayer player, boolean includeCursor) {
        List<StackAmount> stacks = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty()) stacks.add(new StackAmount(stack, stack.getCount()));
        }
        if (includeCursor && !player.containerMenu.getCarried().isEmpty()) {
            var carried = player.containerMenu.getCarried();
            stacks.add(new StackAmount(carried, carried.getCount()));
        }
        for (var source : external(player)) stacks.addAll(source.contents());
        return List.copyOf(stacks);
    }

    public static Map<ResourceLocation, Integer> counts(ServerPlayer player, boolean includeCursor) {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        for (var entry : snapshot(player, includeCursor)) {
            var id = ForgeRegistries.ITEMS.getKey(entry.template().getItem());
            if (id != null) result.compute(id, (ignored, previous) -> (int) Math.min(Integer.MAX_VALUE,
                    (long) (previous == null ? 0 : previous) + Math.min(Integer.MAX_VALUE, entry.amount())));
        }
        return result;
    }

    public static int count(ServerPlayer player, Predicate<ItemStack> matcher) {
        long result = 0;
        for (var entry : snapshot(player, false)) if (matcher.test(entry.template())) {
            result += Math.min(Integer.MAX_VALUE, entry.amount());
            if (result >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) result;
    }

    /** A partial quest submission is explicit; ordinary costs must either remove all or remove nothing. */
    public static Removal remove(ServerPlayer player, Predicate<ItemStack> matcher, int requested, boolean partial) {
        if (requested < 1) throw new IllegalArgumentException("Payment must be positive");
        var inventory = player.getInventory();
        List<ItemStack> before = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) before.add(inventory.getItem(slot).copy());
        List<StackAmount> removed = new ArrayList<>();
        List<Removal> externalRemovals = new ArrayList<>();
        Removal receipt = new Removal() {
            private boolean rolledBack;
            @Override public List<StackAmount> removed() { return List.copyOf(removed); }
            @Override public void rollback() {
                if (rolledBack) return;
                RuntimeException failure = null;
                for (int i = externalRemovals.size() - 1; i >= 0; i--) {
                    try { externalRemovals.get(i).rollback(); }
                    catch (RuntimeException problem) { if (failure == null) failure = problem; else failure.addSuppressed(problem); }
                }
                for (int slot = 0; slot < before.size(); slot++) inventory.setItem(slot, before.get(slot).copy());
                inventory.setChanged();
                if (failure != null) throw failure;
                rolledBack = true;
            }
        };
        try {
            int left = requested;
            for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
                var stack = inventory.getItem(slot);
                if (stack.isEmpty() || !matcher.test(stack)) continue;
                int take = Math.min(stack.getCount(), left);
                removed.add(new StackAmount(stack, take));
                stack.shrink(take);
                left -= take;
            }
            if (left > 0) for (var source : external(player)) {
                var debit = source.remove(matcher, left);
                externalRemovals.add(debit);
                if (debit.amount() > left) throw new IllegalStateException("Storage removed too many items");
                removed.addAll(debit.removed());
                left -= debit.amount();
                if (left == 0) break;
            }
            if (left > 0 && !partial) throw new IllegalStateException("Insufficient items when committing payment");
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
            return receipt;
        } catch (RuntimeException failure) {
            try { receipt.rollback(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
}
