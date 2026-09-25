package org.arcadia.arc_quest.trade.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.sync.BoundedSyncStateCache;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.runtime.TradeUpdateStore;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** 复用商店打开/状态刷新时机比较更新，不扫描未打开的商店。 */
final class TradeUpdateTracker {
    private static final BoundedSyncStateCache<Key, Map<String, S2CTradeUpdatesPacket.Entry>> LAST_SENT = new BoundedSyncStateCache<>(4096);
    private static final Set<String> WARNED = new LinkedHashSet<>();
    private record Key(UUID player, String shop, long epoch) { }

    static void update(ServerPlayer player, TradeShopDefinition shop, boolean[] visibility, int[] purchases, boolean force) {
        var data = ArcQuestPlayerManager.get(player);
        if (data == null || !TradeUpdateStore.validId(shop.getShopId()) || shop.getAllEntries().size() > TradeUpdateStore.MAX_ENTRIES) return;
        Map<String, TradeUpdateStore.Snapshot> snapshots = new LinkedHashMap<>();
        QuestConditionContext context = new QuestConditionContext(player, data.getCompletedQuestLocations(), data.getAllFlags(), data.getAllVariables());
        int i = 0;
        try {
            for (var entry : shop.getAllEntries()) {
                boolean unlocked = entry.getCanBuyCondition() == null || CoreProcessors.get().conditions().evaluateSafely(
                        entry.getCanBuyCondition(), context, false, null, "trade update entry=" + entry.getEntryId());
                snapshots.put(entry.getEntryId(), new TradeUpdateStore.Snapshot(
                        fingerprint(entry.getCosts(), player), fingerprint(entry.getRewards(), player), visibility[i], unlocked,
                        entry.hasLimit() && purchases[i] >= entry.getMaxPurchases()));
                i++;
            }
        } catch (RuntimeException exception) {
            // 附属签名失败只禁用这次提示比较，不阻断交易或覆盖之前的观察基线。
            if (WARNED.add(shop.getShopId())) {
                ArcQuestLog.warn(ArcQuestLog.Category.TRADE, "Cannot compare trade updates: shop={}, player={}", shop.getShopId(), player.getUUID(), exception);
                if (WARNED.size() > 256) WARNED.remove(WARNED.iterator().next());
            }
            return;
        }
        data.getTradeDataStore().getUpdates().observe(shop.getShopId(), snapshots);
        send(player, shop, force);
    }

    static void send(ServerPlayer player, TradeShopDefinition shop, boolean force) {
        Map<String, S2CTradeUpdatesPacket.Entry> updates = new LinkedHashMap<>();
        var store = ArcQuestPlayerManager.get(player).getTradeDataStore().getUpdates();
        store.pending(shop.getShopId()).forEach((id, notice) -> {
            var entry = shop.getEntry(id);
            String category = entry == null || entry.getCategory() == null ? "" : entry.getCategory().getId();
            if (entry != null && category.length() <= 256) updates.put(id, new S2CTradeUpdatesPacket.Entry(
                    category, notice.reasons(), notice.revision()));
        });
        long epoch = PlayerSessionEpochManager.getOrCreate(player);
        LAST_SENT.send(new Key(player.getUUID(), shop.getShopId(), epoch), updates, force,
                () -> ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CTradeUpdatesPacket(shop.getShopId(), epoch, updates)));
    }

    private static String fingerprint(List<ITradeOffer> offers, ServerPlayer player) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ITradeOffer offer : offers) {
                byte[] value = offer.getUpdateSignature(player).getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());
                digest.update(value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static void clearPlayer(UUID player) { LAST_SENT.removeIf(key -> key.player().equals(player)); }
    static void clearAll() { LAST_SENT.clear(); WARNED.clear(); }
}
