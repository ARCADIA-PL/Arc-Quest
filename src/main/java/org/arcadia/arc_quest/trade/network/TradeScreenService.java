package org.arcadia.arc_quest.trade.network;

import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.api.event.trade.*;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.state.ExpiringStateStore;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.sync.BoundedSyncStateCache;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.trade.runtime.TradeEntryStateResolver;
import org.arcadia.arc_quest.trade.runtime.TradeSession;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket.ScreenType;

import java.util.*;

/** 服务端交易界面的打开、状态快照和活跃上下文。 */
final class TradeScreenService {
    private TradeScreenService() { }

    private static final BoundedSyncStateCache<ShopKey, TradeSnapshot> LAST_SENT = new BoundedSyncStateCache<>(4096);
    private static final ExpiringStateStore<UUID, ActiveTradeContext> ACTIVE_TRADE_CONTEXTS =
            CoreProcessors.get().createExpiringStateStore();
    private static final long ACTIVE_TRADE_CONTEXT_TTL_MS = 20000L;

    public static void syncState(ServerPlayer player, TradeShopDefinition shop, ScreenType clientScreenType) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            requireServerThread(player);
            touchActiveTradeContext(player, shop.getShopId(), clientScreenType);
            refreshTradeData(player, shop, clientScreenType, "manual_sync");
        }
    }

    public static void pushSyncForActiveShop(ServerPlayer player, String reason) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            requireServerThread(player);
            long now = CoreProcessors.get().time().realTimeMillis();
            ExpiringStateStore.TakeResult<ActiveTradeContext> activeContext =
                    ACTIVE_TRADE_CONTEXTS.get(player.getUUID(), now);
            if (!activeContext.active()) return;
            ActiveTradeContext context = activeContext.value();

            TradeShopDefinition shop = TradeRegistry.get(context.shopId());
            if (shop == null) {
                ACTIVE_TRADE_CONTEXTS.remove(player.getUUID());
                return;
            }

            ArcQuestLog.debug(ArcQuestLog.Category.TRADE, "Active shop sync push: player={}, shop={}, screenType={}, reason={}",
                    player.getName().getString(), context.shopId(), context.screenType(), reason);

            refreshTradeData(player, shop, context.screenType(), reason);
            touchActiveTradeContext(player, context.shopId(), context.screenType());
        }
    }

    static void open(ServerPlayer player, TradeShopDefinition shop, boolean simple) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            requireServerThread(player);
            TradeSession session = new TradeSession(player, shop);

            TradeSnapshot snap = buildTradeSnapshot(player, shop, session);

            // 获取商店音效 ID
            String openSoundId = shop.getOpenSound() != null ?
                    ResourceLocation.fromNamespaceAndPath(
                            Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getOpenSound())).getNamespace(),
                            Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getOpenSound())).getPath()
                    ).toString() : "";
            String closeSoundId = shop.getCloseSound() != null ?
                    ResourceLocation.fromNamespaceAndPath(
                            Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getCloseSound())).getNamespace(),
                            Objects.requireNonNull(ForgeRegistries.SOUND_EVENTS.getKey(shop.getCloseSound())).getPath()
                    ).toString() : "";

            S2COpenTradePacket response = simple
                    ? S2COpenTradePacket.openSimple(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                    snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                    snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                    snap.visibility(), snap.canBuyConditions(),
                    openSoundId, closeSoundId, PlayerSessionEpochManager.getOrCreate(player))
                    : S2COpenTradePacket.openFull(shop.getShopId(), snap.purchases(), snap.maxPurchases(),
                    snap.lastPurchaseTimes(), snap.purchaseGameTimes(), snap.purchaseDayTimes(),
                    snap.cooldownTypes(), snap.cooldownValues(), snap.resetTimeTicks(),
                    snap.visibility(), snap.canBuyConditions(),
                    openSoundId, closeSoundId, PlayerSessionEpochManager.getOrCreate(player));

            LAST_SENT.send(key(player, shop.getShopId()), snap, true,
                    () -> ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response));
            touchActiveTradeContext(player, shop.getShopId(), simple ? ScreenType.SIMPLE : ScreenType.FULL);

            SyncObservability.trace("trade", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.OPEN,
                    simple ? "open_simple" : "open_full");

            // 发布 Forge 事件（供附属模组监听）
            // 注意：商店打开时没有 NPC 上下文，npc 参数为 null
            MinecraftForge.EVENT_BUS.post(new TradeOpenedEvent(player, shop.getShopId(), null));
        }
    }

    static void clearPlayer(UUID playerId) {
        LAST_SENT.removeIf(key -> key.playerId().equals(playerId));
        ACTIVE_TRADE_CONTEXTS.remove(playerId);
    }

    static void clearAll() {
        LAST_SENT.clear();
        ACTIVE_TRADE_CONTEXTS.clear();
    }

    static void refreshTradeData(ServerPlayer player, TradeShopDefinition shop, ScreenType clientScreenType, String reason) {
        TradeSession session = new TradeSession(player, shop);
        TradeSnapshot snap = buildTradeSnapshot(player, shop, session);

        boolean sent = LAST_SENT.send(key(player, shop.getShopId()), snap, false, () -> {
            S2CSyncTradeStatePacket refreshPkt = new S2CSyncTradeStatePacket(
                    shop.getShopId(),
                    snap.purchases(),
                    snap.maxPurchases(),
                    snap.lastPurchaseTimes(),
                    snap.purchaseGameTimes(),
                    snap.purchaseDayTimes(),
                    snap.cooldownTypes(),
                    snap.cooldownValues(),
                    snap.resetTimeTicks(),
                    snap.visibility(),
                    snap.canBuyConditions(),
                    PlayerSessionEpochManager.getOrCreate(player)
            );

            ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    refreshPkt
            );
        });
        if (!sent) {
            SyncObservability.recordDropped("trade", shop.getShopId(), player.getName().getString(), false);
            SyncObservability.trace("trade", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.SYNC_DROPPED, reason);
            MinecraftForge.EVENT_BUS.post(new TradeStateSyncedEvent(player, shop.getShopId(), reason, TradeStateSyncedEvent.SyncResult.DROPPED));
            return;
        }

        SyncObservability.recordSent("trade", shop.getShopId(), player.getName().getString(), true);
        SyncObservability.trace("trade", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.SYNC_SENT, reason);
        MinecraftForge.EVENT_BUS.post(new TradeStateSyncedEvent(player, shop.getShopId(), reason, TradeStateSyncedEvent.SyncResult.SENT));
    }

    /**
     * 为商店所有商品构建网络传输快照数据，供 handleOpen 和 refreshTradeData 共用。
     */
    private static TradeSnapshot buildTradeSnapshot(ServerPlayer player,
                                                    TradeShopDefinition shop,
                                                    TradeSession session) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        List<TradeEntry> allEntries = new ArrayList<>(shop.getAllEntries());
        int count = allEntries.size();

        int[] purchases = new int[count];
        int[] maxPurchases = new int[count];
        long[] lastPurchaseTimes = new long[count];
        long[] purchaseGameTimes = new long[count];
        long[] purchaseDayTimes = new long[count];
        int[] cooldownTypes = new int[count];
        long[] cooldownValues = new long[count];
        int[] resetTimeTicks = new int[count];
        boolean[] visibility = new boolean[count];
        boolean[] canBuyConditions = new boolean[count];

        for (int i = 0; i < count; i++) {
            TradeEntry entry = allEntries.get(i);
            if (entry.hasLimit() || entry.hasCooldown()) {
                TradeEntryStateResolver.resetIfNeeded(player, data, shop.getShopId(), entry);
            }
            purchases[i] = session.getPurchaseCount(entry.getEntryId());
            maxPurchases[i] = entry.getMaxPurchases();

            if (entry.hasCooldown()) {
                var cooldownRecord = data.getTradeDataStore().getCooldown(shop.getShopId(), entry.getEntryId());
                lastPurchaseTimes[i] = cooldownRecord.realTime();
                purchaseGameTimes[i] = cooldownRecord.gameTime();
                purchaseDayTimes[i] = cooldownRecord.dayTime();
                cooldownTypes[i] = entry.getCooldownType().ordinal();
                cooldownValues[i] = entry.getCooldownValue();
                resetTimeTicks[i] = entry.getResetTimeTicks();
            }

            visibility[i] = session.isEntryVisible(entry);
            canBuyConditions[i] = session.canPurchase(entry);
        }

        return new TradeSnapshot(purchases, maxPurchases, lastPurchaseTimes, purchaseGameTimes,
                purchaseDayTimes, cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
    }

    static void touchActiveTradeContext(ServerPlayer player, String shopId, ScreenType screenType) {
        if (player == null || shopId == null || shopId.isEmpty()) {
            return;
        }
        ScreenType effectiveType = screenType != null ? screenType : ScreenType.FULL;
        if (effectiveType == ScreenType.NONE) {
            effectiveType = ScreenType.FULL;
        }
        long now = CoreProcessors.get().time().realTimeMillis();
        ACTIVE_TRADE_CONTEXTS.put(player.getUUID(),
                new ActiveTradeContext(shopId, effectiveType), now + ACTIVE_TRADE_CONTEXT_TTL_MS);
    }

    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Trade screens require the server thread");
    }

    private static ShopKey key(ServerPlayer player, String shopId) {
        return new ShopKey(player.getUUID(), shopId, PlayerSessionEpochManager.getOrCreate(player));
    }

    private record ShopKey(UUID playerId, String shopId, long epoch) { }

    private static int buildTradeFingerprint(TradeSnapshot snap) {
        int h = 1;
        h = 31 * h + Arrays.hashCode(snap.purchases());
        h = 31 * h + Arrays.hashCode(snap.maxPurchases());
        h = 31 * h + Arrays.hashCode(snap.lastPurchaseTimes());
        h = 31 * h + Arrays.hashCode(snap.purchaseGameTimes());
        h = 31 * h + Arrays.hashCode(snap.purchaseDayTimes());
        h = 31 * h + Arrays.hashCode(snap.cooldownTypes());
        h = 31 * h + Arrays.hashCode(snap.cooldownValues());
        h = 31 * h + Arrays.hashCode(snap.resetTimeTicks());
        h = 31 * h + Arrays.hashCode(snap.visibility());
        h = 31 * h + Arrays.hashCode(snap.canBuyConditions());
        return h;
    }

    private record TradeSnapshot(
            int[] purchases,
            int[] maxPurchases,
            long[] lastPurchaseTimes,
            long[] purchaseGameTimes,
            long[] purchaseDayTimes,
            int[] cooldownTypes,
            long[] cooldownValues,
            int[] resetTimeTicks,
            boolean[] visibility,
            boolean[] canBuyConditions
    ) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TradeSnapshot that)) return false;
            return Arrays.equals(purchases, that.purchases)
                    && Arrays.equals(maxPurchases, that.maxPurchases)
                    && Arrays.equals(lastPurchaseTimes, that.lastPurchaseTimes)
                    && Arrays.equals(purchaseGameTimes, that.purchaseGameTimes)
                    && Arrays.equals(purchaseDayTimes, that.purchaseDayTimes)
                    && Arrays.equals(cooldownTypes, that.cooldownTypes)
                    && Arrays.equals(cooldownValues, that.cooldownValues)
                    && Arrays.equals(resetTimeTicks, that.resetTimeTicks)
                    && Arrays.equals(visibility, that.visibility)
                    && Arrays.equals(canBuyConditions, that.canBuyConditions);
        }

        @Override
        public int hashCode() { return buildTradeFingerprint(this); }
    }

    private static record ActiveTradeContext(String shopId, ScreenType screenType) {
    }

}
