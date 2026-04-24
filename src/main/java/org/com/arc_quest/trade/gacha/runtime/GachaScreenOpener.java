package org.com.arc_quest.trade.gacha.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.api.event.GachaEvents;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.CostShortfallLine;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.network.S2CGachaStatePacket;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GachaScreenOpener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Map<String, Integer>> LAST_GACHA_SYNC_FINGERPRINTS = new ConcurrentHashMap<>();

    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        openGachaScreen(player, shop, cap, null);
    }

    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop,
                                       IQuestCapability cap, String restoreNodeId) {
        if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
            DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
            if (manager.isInDialogue(player)) {
                manager.setRestoreNodeId(player, restoreNodeId);
            }
        }

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

        markGachaSynced(player, shop.getShopId(), snapshot);
        LOGGER.debug("[Gacha] Sent S2CGachaStatePacket(OPEN) to {}", player.getName().getString());
    }

    public static void syncGachaState(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        GachaSnapshot snapshot = resolveSnapshot(player, shop, cap, true);

        if (!shouldSendGachaSync(player, shop.getShopId(), snapshot)) {
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

        LOGGER.debug("[Gacha] Sent S2CGachaStatePacket(SYNC) to {}", player.getName().getString());
    }

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
        Map<String, Integer> map = LAST_GACHA_SYNC_FINGERPRINTS.computeIfAbsent(player.getUUID(), __ -> new ConcurrentHashMap<>());
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
    ) {}
}