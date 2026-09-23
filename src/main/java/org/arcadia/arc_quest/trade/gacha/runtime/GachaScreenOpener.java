package org.arcadia.arc_quest.trade.gacha.runtime;

import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.api.event.gacha.GachaEvents;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.state.ExpiringStateStore;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.network.S2CGachaStatePacket;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.arcadia.arc_quest.sync.BoundedSyncStateCache;

public final class GachaScreenOpener {
    private static final BoundedSyncStateCache<ShopKey, GachaSnapshot> LAST_SENT = new BoundedSyncStateCache<>(4096);

    // 活跃上下文：用于 quest 事件触发时只推“当前打开的 gacha shop”
    private static final ExpiringStateStore<UUID, ActiveGachaContext> ACTIVE_GACHA_CONTEXTS =
            CoreProcessors.get().createExpiringStateStore();
    private static final long ACTIVE_GACHA_CONTEXT_TTL_MS = 20_000L;

    private GachaScreenOpener() {
    }

    // ════════════════════════════════════════
    // 对外入口
    // ════════════════════════════════════════

    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop, ArcQuestPlayer data) {
        openGachaScreen(player, shop, data, null);
    }

    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop,
                                       ArcQuestPlayer data, @Nullable String restoreNodeId) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            requireServerThread(player);
            if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
                DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
                if (manager.isInDialogue(player)) {
                    manager.setRestoreNodeId(player, restoreNodeId);
                }
            }

            touchActiveContext(player, shop.getShopId());

            GachaSnapshot snapshot = resolveSnapshot(player, shop, data, true);

            MinecraftForge.EVENT_BUS.post(new GachaEvents.OpenedEvent(player, shop.getShopId(), data));

            LAST_SENT.send(new ShopKey(player.getUUID(), shop.getShopId()), snapshot, true, () -> {
                ArcQuestNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        S2CGachaStatePacket.open(
                                shop.getShopId(),
                                snapshot.pityCounter(),
                                snapshot.totalDraws(),
                                snapshot.canDraw(),
                                snapshot.remainingDraws(),
                                snapshot.lastDrawRealTime(),
                                snapshot.lastDrawGameTime(),
                                snapshot.lastDrawDayTime(),
                                snapshot.cooldownType(),
                                snapshot.cooldownValue(),
                                snapshot.resetTimeTicks(),
                                snapshot.shortfallLines(),
                                data.getGachaDrawHistory(shop.getShopId())
                        )
                );
            });

            SyncObservability.trace("gacha", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.OPEN, "open_gacha_screen");

            ArcQuestLog.debug(ArcQuestLog.Category.GACHA, "OPEN push: player={}, shop={}", player.getName().getString(), shop.getShopId());
            SyncObservability.recordSent("gacha", shop.getShopId(), player.getName().getString(), true);
        }
    }

    /**
     * 兼容旧调用：手动按 shop 同步一次（允许去重）。
     */
    public static void syncGachaState(ServerPlayer player, GachaShopDefinition shop, ArcQuestPlayer data) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            requireServerThread(player);
            touchActiveContext(player, shop.getShopId());
            syncShopState(player, shop, data, "manual_sync", true);
        }
    }

    /**
     * 统一 push 入口（推荐）：基于活跃上下文决定是否推送。
     */
    public static void pushSyncForActiveShop(ServerPlayer player, @Nullable ArcQuestPlayer data, String reason) {
        pushSync(player, data, reason);
    }

    /**
     * 新统一入口别名（更语义化）。
     */
    public static void pushSync(ServerPlayer player, @Nullable ArcQuestPlayer data, String reason) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            requireServerThread(player);
            if (data == null) return;

            long now = CoreProcessors.get().time().realTimeMillis();
            ExpiringStateStore.TakeResult<ActiveGachaContext> activeContext =
                    ACTIVE_GACHA_CONTEXTS.get(player.getUUID(), now);
            if (!activeContext.active()) return;
            ActiveGachaContext context = activeContext.value();

            GachaShopDefinition shop = GachaRegistry.get(context.shopId());
            if (shop == null) {
                ACTIVE_GACHA_CONTEXTS.remove(player.getUUID());
                return;
            }

            ArcQuestLog.debug(ArcQuestLog.Category.GACHA, "trigger: player={}, shop={}, reason={}",
                    player.getName().getString(), context.shopId(), reason);

            touchActiveContext(player, context.shopId());
            syncShopState(player, shop, data, reason, true);
        }
    }

    public static void closeActiveShopContext(ServerPlayer player) {
        if (player != null) {
            ACTIVE_GACHA_CONTEXTS.remove(player.getUUID());
        }
    }

    // ════════════════════════════════════════
    // 内部统一同步链路
    // ════════════════════════════════════════

    private static void syncShopState(ServerPlayer player, GachaShopDefinition shop,
                                      ArcQuestPlayer data, String reason, boolean dedupe) {
        GachaSnapshot snapshot = resolveSnapshot(player, shop, data, true);

        boolean sent = LAST_SENT.send(new ShopKey(player.getUUID(), shop.getShopId()), snapshot, !dedupe, () -> {
            ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    S2CGachaStatePacket.sync(
                            shop.getShopId(),
                            snapshot.pityCounter(),
                            snapshot.totalDraws(),
                            snapshot.canDraw(),
                            snapshot.remainingDraws(),
                            snapshot.lastDrawRealTime(),
                            snapshot.lastDrawGameTime(),
                            snapshot.lastDrawDayTime(),
                            snapshot.cooldownType(),
                            snapshot.cooldownValue(),
                            snapshot.resetTimeTicks(),
                            snapshot.shortfallLines()
                    )
            );
        });
        if (!sent) {
            SyncObservability.recordDropped("gacha", shop.getShopId(), player.getName().getString(), false);
            SyncObservability.trace("gacha", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.SYNC_DROPPED, reason);
            MinecraftForge.EVENT_BUS.post(new GachaEvents.StateSyncedEvent(
                    player,
                    shop.getShopId(),
                    reason,
                    GachaEvents.StateSyncedEvent.SyncResult.DROPPED
            ));
            ArcQuestLog.debug(ArcQuestLog.Category.GACHA, "SYNC dropped: unchanged state: player={}, shop={}, reason={}",
                    player.getName().getString(), shop.getShopId(), reason);
            return;
        }

        SyncObservability.trace("gacha", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.SYNC_SENT, reason);

        ArcQuestLog.debug(ArcQuestLog.Category.GACHA, "SYNC push: player={}, shop={}, reason={}",
                player.getName().getString(), shop.getShopId(), reason);
        SyncObservability.recordSent("gacha", shop.getShopId(), player.getName().getString(), true);
        MinecraftForge.EVENT_BUS.post(new GachaEvents.StateSyncedEvent(
                player,
                shop.getShopId(),
                reason,
                GachaEvents.StateSyncedEvent.SyncResult.SENT
        ));
    }

    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Gacha screens require the server thread");
    }

    private static void touchActiveContext(ServerPlayer player, String shopId) {
        if (player == null || shopId == null || shopId.isEmpty()) return;
        long now = CoreProcessors.get().time().realTimeMillis();
        ACTIVE_GACHA_CONTEXTS.put(player.getUUID(),
                new ActiveGachaContext(shopId), now + ACTIVE_GACHA_CONTEXT_TTL_MS);
    }

    // ════════════════════════════════════════
    // 状态快照
    // ════════════════════════════════════════

    private static GachaSnapshot resolveSnapshot(ServerPlayer player, GachaShopDefinition shop,
                                                 ArcQuestPlayer data, boolean applyReset) {
        GachaSession session = new GachaSession(player, shop, data);

        if (applyReset) {
            int before = session.getDrawCount();
            session.checkAndResetDraws();
            int after = session.getDrawCount();
            if (before != after) {
                ArcQuestLog.info(ArcQuestLog.Category.GACHA, "Draw count reset: shop={}, before={}, after={}", shop.getShopId(), before, after);
            }
        }

        boolean canDrawByRule = session.canDraw();
        int remainingDraws = session.getRemainingDraws();

        List<CostShortfallLine> shortfalls = List.of();
        boolean canAfford = true;

        if (canDrawByRule) {
            List<ITradeOffer> costs = shop.getDrawCosts();
            if (!costs.isEmpty()) {
                for (ITradeOffer cost : costs) {
                    if (!cost.canAfford(player)) {
                        canAfford = false;
                        break;
                    }
                }
                if (!canAfford) {
                    shortfalls = buildShortfallLines(costs, player);
                }
            }
        }

        boolean canDraw = canDrawByRule && canAfford;

        int pityCounter = data.getGachaPityCounter(shop.getShopId());
        int totalDraws = data.getGachaDrawCount(shop.getShopId());
        var cooldownEntry = data.getGachaDataStore().getDrawCooldown(shop.getShopId());

        return new GachaSnapshot(
                pityCounter,
                totalDraws,
                canDraw,
                remainingDraws,
                cooldownEntry.realTime(),
                cooldownEntry.gameTime(),
                cooldownEntry.dayTime(),
                shop.getCooldownType().ordinal(),
                shop.getCooldownValue(),
                shop.getResetTimeTicks(),
                shortfalls
        );
    }

    public static void clearPlayer(UUID playerId) {
        ACTIVE_GACHA_CONTEXTS.remove(playerId);
        LAST_SENT.removeIf(key -> key.playerId().equals(playerId));
    }

    public static void clearAll() {
        ACTIVE_GACHA_CONTEXTS.clear();
        LAST_SENT.clear();
    }

    private record ShopKey(UUID playerId, String shopId) { }

    private static List<CostShortfallLine> buildShortfallLines(List<ITradeOffer> costs, ServerPlayer player) {
        List<CostShortfallLine> lines = new ArrayList<>();
        for (ITradeOffer cost : costs) {
            lines.addAll(cost.buildShortfallLines(player));
        }
        return lines;
    }

    private record ActiveGachaContext(String shopId) {
    }

    private record GachaSnapshot(
            int pityCounter,
            int totalDraws,
            boolean canDraw,
            int remainingDraws,
            long lastDrawRealTime,
            long lastDrawGameTime,
            long lastDrawDayTime,
            int cooldownType,
            long cooldownValue,
            int resetTimeTicks,
            List<CostShortfallLine> shortfallLines
    ) {
        private GachaSnapshot {
            shortfallLines = shortfallLines.stream().map(line -> new CostShortfallLine(
                    line.label().copy(), line.required(), line.owned(), line.missing())).toList();
        }
    }
}
