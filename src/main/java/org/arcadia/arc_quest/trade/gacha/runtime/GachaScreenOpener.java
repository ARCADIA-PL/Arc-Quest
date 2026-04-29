package org.arcadia.arc_quest.trade.gacha.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.api.event.GachaEvents;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.network.S2CGachaStatePacket;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GachaScreenOpener {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 去重：按 player+shop 缓存最近一次同步指纹
    private static final Map<UUID, Map<String, Integer>> LAST_GACHA_SYNC_FINGERPRINTS = new ConcurrentHashMap<>();

    // 活跃上下文：用于 quest 事件触发时只推“当前打开的 gacha shop”
    private static final Map<UUID, ActiveGachaContext> ACTIVE_GACHA_CONTEXTS = new ConcurrentHashMap<>();
    private static final long ACTIVE_GACHA_CONTEXT_TTL_MS = 20_000L;

    private GachaScreenOpener() {
    }

    // ════════════════════════════════════════
    // 对外入口
    // ════════════════════════════════════════

    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        openGachaScreen(player, shop, cap, null);
    }

    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop,
                                       IQuestCapability cap, @Nullable String restoreNodeId) {
        if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
            DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
            if (manager.isInDialogue(player)) {
                manager.setRestoreNodeId(player, restoreNodeId);
            }
        }

        touchActiveContext(player, shop.getShopId());

        GachaSnapshot snapshot = resolveSnapshot(player, shop, cap, true);

        MinecraftForge.EVENT_BUS.post(new GachaEvents.OpenedEvent(player, shop.getShopId(), cap));

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
                        cap.getGachaDrawHistory(shop.getShopId())
                )
        );

        SyncObservability.trace("gacha", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.OPEN, "open_gacha_screen");

        markGachaSynced(player, shop.getShopId(), snapshot);
        LOGGER.debug("[Gacha] OPEN push: player={}, shop={}", player.getName().getString(), shop.getShopId());
        SyncObservability.recordSent("gacha", shop.getShopId(), player.getName().getString(), true);
    }

    /**
     * 兼容旧调用：手动按 shop 同步一次（允许去重）。
     */
    public static void syncGachaState(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        touchActiveContext(player, shop.getShopId());
        syncShopState(player, shop, cap, "manual_sync", true);
    }

    /**
     * 统一 push 入口（推荐）：基于活跃上下文决定是否推送。
     */
    public static void pushSyncForActiveShop(ServerPlayer player, @Nullable IQuestCapability cap, String reason) {
        pushSync(player, cap, reason);
    }

    /**
     * 新统一入口别名（更语义化）。
     */
    public static void pushSync(ServerPlayer player, @Nullable IQuestCapability cap, String reason) {
        if (cap == null) return;

        ActiveGachaContext context = ACTIVE_GACHA_CONTEXTS.get(player.getUUID());
        if (context == null) return;

        if (isContextExpired(context)) {
            ACTIVE_GACHA_CONTEXTS.remove(player.getUUID());
            return;
        }

        GachaShopDefinition shop = GachaRegistry.get(context.shopId());
        if (shop == null) {
            ACTIVE_GACHA_CONTEXTS.remove(player.getUUID());
            return;
        }

        LOGGER.debug("[Gacha-Push] trigger: player={}, shop={}, reason={}",
                player.getName().getString(), context.shopId(), reason);

        touchActiveContext(player, context.shopId());
        syncShopState(player, shop, cap, reason, true);
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
                                      IQuestCapability cap, String reason, boolean dedupe) {
        GachaSnapshot snapshot = resolveSnapshot(player, shop, cap, true);

        if (dedupe && !shouldSendGachaSync(player, shop.getShopId(), snapshot)) {
            SyncObservability.recordDropped("gacha", shop.getShopId(), player.getName().getString(), false);
            SyncObservability.trace("gacha", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.SYNC_DROPPED, reason);
            LOGGER.debug("[Gacha] SYNC dropped by fingerprint: player={}, shop={}, reason={}",
                    player.getName().getString(), shop.getShopId(), reason);
            return;
        }

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

        SyncObservability.trace("gacha", shop.getShopId(), player.getName().getString(), SyncObservability.Stage.SYNC_SENT, reason);

        LOGGER.debug("[Gacha] SYNC push: player={}, shop={}, reason={}",
                player.getName().getString(), shop.getShopId(), reason);
        SyncObservability.recordSent("gacha", shop.getShopId(), player.getName().getString(), true);
    }

    private static void touchActiveContext(ServerPlayer player, String shopId) {
        if (player == null || shopId == null || shopId.isEmpty()) return;
        ACTIVE_GACHA_CONTEXTS.put(player.getUUID(), new ActiveGachaContext(shopId, System.currentTimeMillis()));
    }

    private static boolean isContextExpired(ActiveGachaContext context) {
        return System.currentTimeMillis() - context.lastSeenMs() > ACTIVE_GACHA_CONTEXT_TTL_MS;
    }

    // ════════════════════════════════════════
    // 快照/指纹
    // ════════════════════════════════════════

    private static GachaSnapshot resolveSnapshot(ServerPlayer player, GachaShopDefinition shop,
                                                 IQuestCapability cap, boolean applyReset) {
        GachaSession session = new GachaSession(player, shop, cap);

        if (applyReset) {
            int before = session.getDrawCount();
            session.checkAndResetDraws();
            int after = session.getDrawCount();
            if (before != after) {
                LOGGER.info("[Gacha] Draw count reset: shop={}, before={}, after={}", shop.getShopId(), before, after);
            }
        }

        boolean canDrawByRule = session.canDraw();
        int remainingDraws = session.getRemainingDraws();

        List<CostShortfallLine> shortfalls = List.of();
        boolean canAfford = true;

        if (canDrawByRule) {
            ITradeOffer drawCost = shop.getDrawCost();
            if (drawCost != null && !drawCost.canAfford(player)) {
                canAfford = false;
                shortfalls = drawCost.buildShortfallLines(player);
            }
        }

        boolean canDraw = canDrawByRule && canAfford;

        int pityCounter = cap.getGachaPityCounter(shop.getShopId());
        int totalDraws = cap.getGachaDrawCount(shop.getShopId());
        var cooldownEntry = cap.getGachaDataStore().getDrawCooldown(shop.getShopId());

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

    private static boolean shouldSendGachaSync(ServerPlayer player, String shopId, GachaSnapshot snapshot) {
        int fp = buildGachaFingerprint(snapshot);
        Map<String, Integer> map = LAST_GACHA_SYNC_FINGERPRINTS.computeIfAbsent(
                player.getUUID(), __ -> new ConcurrentHashMap<>()
        );
        Integer old = map.get(shopId);
        if (old != null && old == fp) return false;
        map.put(shopId, fp);
        return true;
    }

    private static void markGachaSynced(ServerPlayer player, String shopId, GachaSnapshot snapshot) {
        int fp = buildGachaFingerprint(snapshot);
        LAST_GACHA_SYNC_FINGERPRINTS
                .computeIfAbsent(player.getUUID(), __ -> new ConcurrentHashMap<>())
                .put(shopId, fp);
    }

    private static int buildGachaFingerprint(GachaSnapshot s) {
        int h = 1;
        h = 31 * h + s.pityCounter();
        h = 31 * h + s.totalDraws();
        h = 31 * h + (s.canDraw() ? 1 : 0);
        h = 31 * h + s.remainingDraws();
        h = 31 * h + Long.hashCode(s.lastDrawRealTime());
        h = 31 * h + Long.hashCode(s.lastDrawGameTime());
        h = 31 * h + Long.hashCode(s.lastDrawDayTime());
        h = 31 * h + s.cooldownType();
        h = 31 * h + Long.hashCode(s.cooldownValue());
        h = 31 * h + s.resetTimeTicks();
        h = 31 * h + buildShortfallFingerprint(s.shortfallLines());
        return h;
    }

    private static int buildShortfallFingerprint(List<CostShortfallLine> lines) {
        int h = 1;
        if (lines == null || lines.isEmpty()) return h;
        for (CostShortfallLine line : lines) {
            h = 31 * h + line.label().getString().hashCode();
            h = 31 * h + line.required();
            h = 31 * h + line.owned();
            h = 31 * h + line.missing();
        }
        return h;
    }

    private record ActiveGachaContext(String shopId, long lastSeenMs) {
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
    }
}