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
import org.com.arc_quest.trade.gacha.network.S2COpenGachaPacket;
import org.com.arc_quest.trade.gacha.network.S2CSyncGachaStatePacket;
import org.slf4j.Logger;

import java.util.List;

/**
 * 抽奖界面打开管理器。
 * 统一管理服务端打开抽奖界面的逻辑，确保 C2S 和 S2C 路径行为一致。
 */
public class GachaScreenOpener {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 服务端打开抽奖界面（统一入口）。
     */
    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        openGachaScreen(player, shop, cap, null);
    }

    /**
     * 从对话中打开抽奖界面，并记录对话恢复目标节点。
     */
    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop,
                                       IQuestCapability cap, String restoreNodeId) {
        if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
            DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
            if (manager.isInDialogue(player)) {
                manager.setRestoreNodeId(player, restoreNodeId);
            }
        }

        GachaSnapshot snapshot = resolveSnapshot(player, shop, cap, true);

        var openEvent = new GachaEvents.OpenedEvent(player, shop.getShopId(), cap);
        MinecraftForge.EVENT_BUS.post(openEvent);

        var drawHistory = cap.getGachaDrawHistory(shop.getShopId());

        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2COpenGachaPacket(
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
                        drawHistory
                )
        );

        LOGGER.debug("[Gacha] Sent S2COpenGachaPacket to player {}", player.getName().getString());
    }

    /**
     * 仅同步当前抽奖界面的权威状态，不打开界面。
     */
    public static void syncGachaState(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        GachaSnapshot snapshot = resolveSnapshot(player, shop, cap, true);

        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncGachaStatePacket(
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

        LOGGER.debug("[Gacha] Sent S2CSyncGachaStatePacket to player {}", player.getName().getString());
    }

    private static GachaSnapshot resolveSnapshot(ServerPlayer player, GachaShopDefinition shop,
                                                 IQuestCapability cap, boolean applyReset) {
        GachaSession session = new GachaSession(player, shop, cap);

        if (applyReset) {
            int drawCountBeforeReset = session.getDrawCount();
            session.checkAndResetDraws();
            int drawCountAfterReset = session.getDrawCount();

            if (drawCountBeforeReset != drawCountAfterReset) {
                LOGGER.info("[Gacha-Open] Draw count reset on open/sync: shop={}, before={}, after={}",
                        shop.getShopId(), drawCountBeforeReset, drawCountAfterReset);
            }
        }

        boolean canDrawByRule = session.canDraw();
        int remainingDraws = session.getRemainingDraws();
        List<CostShortfallLine> shortfallLines = List.of();

        boolean canAfford = true;
        if (canDrawByRule) {
            ITradeOffer drawCost = shop.getDrawCost();
            if (drawCost != null && !drawCost.canAfford(player)) {
                canAfford = false;
                shortfallLines = drawCost.buildShortfallLines(player);
            }
        }

        boolean canDraw = canDrawByRule && canAfford;

        int pityCounter = cap.getGachaPityCounter(shop.getShopId());
        int totalDraws = cap.getGachaDrawCount(shop.getShopId());

        var cooldownEntry = cap.getGachaDataStore().getDrawCooldown(shop.getShopId());

        long lastDrawRealTime = cooldownEntry.realTime();
        long lastDrawGameTime = cooldownEntry.gameTime();
        long lastDrawDayTime = cooldownEntry.dayTime();

        int cooldownType = shop.getCooldownType().ordinal();
        long cooldownValue = shop.getCooldownValue();
        int resetTimeTicks = shop.getResetTimeTicks();

        LOGGER.info("[Gacha-Snapshot] shop={}, canDraw={}, remaining={}, totalDraws={}, pityCounter={}",
                shop.getShopId(), canDraw, remainingDraws, totalDraws, pityCounter);

        return new GachaSnapshot(
                pityCounter,
                totalDraws,
                canDraw,
                remainingDraws,
                lastDrawRealTime,
                lastDrawGameTime,
                lastDrawDayTime,
                cooldownType,
                cooldownValue,
                resetTimeTicks,
                shortfallLines
        );
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