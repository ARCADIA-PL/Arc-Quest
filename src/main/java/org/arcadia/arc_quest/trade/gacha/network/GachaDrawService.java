package org.arcadia.arc_quest.trade.gacha.network;

import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.api.event.gacha.GachaEvents;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaEntryStateResolver;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaSession;
import org.arcadia.arc_quest.trade.network.RejectCodeDictionary;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.runtime.TradeTransactionCoordinator;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 服务端抽卡流程：占位、校验、选奖、扣费、发布待发奖励，最后通知客户端。 */
final class GachaDrawService {
    private GachaDrawService() { }

    static void execute(ServerPlayer player, String shopId) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            UUID token = PendingDrawManager.beginDraw(player);
            if (token == null) {
                sendFailure(player, shopId, RejectCodeDictionary.Code.PENDING_STORE_FAILED);
                return;
            }
            try {
                executeReserved(player, shopId, token);
            } catch (RuntimeException failure) {
                ArcQuestLog.error(ArcQuestLog.Category.GACHA,
                        "Draw failed: player={}, shop={}", player.getUUID(), shopId, failure);
                sendFailure(player, shopId, RejectCodeDictionary.Code.TRANSACTION_FAILED);
            } finally {
                PendingDrawManager.endDraw(player, token);
            }
        }
    }

    static void sendFailure(ServerPlayer player, String shopId, RejectCodeDictionary.Code code) {
        ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CDrawFailedPacket(shopId, GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(),
                        GachaRequestValidator.toErrorKey(code), List.of()));
    }

    private static void executeReserved(ServerPlayer player, String shopId, UUID token) {
        ArcQuestPlayer data = GachaRequestValidator.requireData(player, "draw", shopId);
        if (data == null) return;

        GachaShopDefinition gachaShop = GachaRequestValidator.requireShop(shopId, player, "draw");
        if (gachaShop == null) return;

        SyncObservability.trace("gacha", shopId, player.getName().getString(),
                SyncObservability.Stage.ACTION, "draw");

        // 扩展事件在请求占位之后执行，防止回调重入重复扣费。
        int basePityCounter = data.getGachaPityCounter(shopId);
        GachaEvents.PreDrawEvent preEvent = new GachaEvents.PreDrawEvent(player, shopId, data, basePityCounter);
        MinecraftForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCancelled()) {
            GachaRequestValidator.reject(
                    RejectCodeDictionary.Code.PRE_DRAW_CANCELLED,
                    "draw",
                    player,
                    shopId,
                    "pre draw event cancelled"
            );
            var reason = GachaEvents.DrawFailedEvent.FailReason.UNKNOWN;
            MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawFailedEvent(player, shopId, data, reason));

            ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CDrawFailedPacket(
                            shopId,
                            reason.name(),
                            GachaRequestValidator.toErrorKey(RejectCodeDictionary.Code.PRE_DRAW_CANCELLED),
                            List.of()
                    )
            );
            SyncObservability.trace("gacha", shopId, player.getName().getString(),
                    SyncObservability.Stage.RESULT, "draw_failed:" + reason.name());
            return;
        }
        int pityCounter = preEvent.getPityCounter();
        if (pityCounter < 0) throw new IllegalArgumentException("PreDrawEvent returned a negative pity counter");

        // 1) 先判定状态门禁（统一优先级：cooldown > limit > condition > afford）
        GachaSession preStateSession = new GachaSession(player, gachaShop, data);
        {
            preStateSession.checkAndResetDraws();
            if (!preStateSession.canDraw()) {
                GachaEvents.DrawFailedEvent.FailReason reason = mapFailReason(preStateSession.getFailReason());
                GachaRequestValidator.reject(
                        GachaRequestValidator.fromDrawFailedReasonName(reason.name()),
                        "draw",
                        player,
                        shopId,
                        "pre-state gate failed"
                );
                MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawFailedEvent(player, shopId, data, reason));

                ArcQuestNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CDrawFailedPacket(
                                shopId,
                                reason.name(),
                                GachaRequestValidator.toErrorKey(
                                        GachaRequestValidator.fromDrawFailedReasonName(reason.name())
                                ),
                                List.of()
                        )
                );
                SyncObservability.trace("gacha", shopId, player.getName().getString(),
                        SyncObservability.Stage.RESULT, "draw_failed:" + reason.name());
                return;
            }
        }

        // 在选奖之前检查支付能力；实际扣费在有效结果和数量确定之后执行。
        List<ITradeOffer> drawCosts = gachaShop.getDrawCosts();
        if (!drawCosts.isEmpty()) {
            for (ITradeOffer cost : drawCosts) {
                if (!cost.canAfford(player)) {
                    GachaRequestValidator.reject(
                            RejectCodeDictionary.Code.CANNOT_AFFORD,
                            "draw",
                            player,
                            shopId,
                            "draw cost cannot afford"
                    );
                    var reason = GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD;
                    MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawFailedEvent(player, shopId, data, reason));

                    ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new S2CDrawFailedPacket(
                                    shopId,
                                    reason.name(),
                                    GachaRequestValidator.toErrorKey(RejectCodeDictionary.Code.CANNOT_AFFORD),
                                    buildShortfallLines(drawCosts, player)
                            )
                    );
                    SyncObservability.trace("gacha", shopId, player.getName().getString(),
                            SyncObservability.Stage.RESULT, "draw_failed:" + reason.name());
                    return;
                }
            }
        }

        prepareDraw(player, data, gachaShop, shopId, pityCounter, token);
    }

    static void reportResolution(ServerPlayer player, String shopId, ArcQuestPlayer data, DrawResolution resolution) {
        // 奖励已进入待发队列，通知失败不会取消玩家的已付费结果。
        if (!resolution.succeeded()) {
            GachaEvents.DrawFailedEvent.FailReason reason =
                    GachaEvents.DrawFailedEvent.FailReason.valueOf(resolution.failedReasonName());
            GachaRequestValidator.reject(
                    GachaRequestValidator.fromDrawFailedReasonName(resolution.failedReasonName()),
                    "draw",
                    player,
                    shopId,
                    "resolution failed"
            );

            MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawFailedEvent(player, shopId, data, reason));

            ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CDrawFailedPacket(
                            shopId,
                            reason.name(),
                            GachaRequestValidator.toErrorKey(
                                    GachaRequestValidator.fromDrawFailedReasonName(resolution.failedReasonName())
                            ),
                            resolution.shortfallLines() != null ? resolution.shortfallLines() : List.of()
                    )
            );
            SyncObservability.trace("gacha", shopId, player.getName().getString(),
                    SyncObservability.Stage.RESULT, "draw_failed:" + reason.name());
            return;
        }

        MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawingEvent(
                player,
                shopId,
                data,
                resolution.preEventPityCounter()
        ));

        if (resolution.earlyTrigger()) {
            MinecraftForge.EVENT_BUS.post(new GachaEvents.PityEarlyTriggerEvent(
                    player,
                    shopId,
                    resolution.item(),
                    resolution.preEventPityCounter(),
                    resolution.pityThreshold(),
                    true
            ));
        }

        MinecraftForge.EVENT_BUS.post(new GachaEvents.PostDrawEvent(
                player,
                shopId,
                resolution.item(),
                resolution.pityTriggered(),
                resolution.newPityCounter(),
                data
        ));

        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CDrawResultPacket(
                        shopId,
                        resolution.item().getItemId(),
                        resolution.item().getRarity().getName(),
                        resolution.actualCount(),
                        resolution.pityTriggered(),
                        resolution.newPityCounter(),
                        resolution.canDraw(),
                        resolution.remainingDraws(),
                        resolution.lastDrawRealTime(),
                        resolution.lastDrawGameTime(),
                        resolution.lastDrawDayTime()
                )
        );

        SyncObservability.trace("gacha", shopId, player.getName().getString(),
                SyncObservability.Stage.RESULT, "draw_success");

        ArcQuestLog.info(ArcQuestLog.Category.GACHA, "Player {} drew {} x{} from {}",
                player.getName().getString(),
                resolution.item().getItemId(),
                resolution.actualCount(),
                shopId
        );
    }

    private static void prepareDraw(ServerPlayer player, ArcQuestPlayer data,
                                              GachaShopDefinition gachaShop, String shopId,
                                              int pityCounter, UUID token) {
        GachaSession session = new GachaSession(player, gachaShop, data);

        if (!session.canDraw()) {
            GachaEvents.DrawFailedEvent.FailReason reason = mapFailReason(session.getFailReason());

            // 仍保留门禁失败对应的缺口信息。
            List<CostShortfallLine> shortfallLines = List.of();
            if (reason == GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD) {
                List<ITradeOffer> costs = gachaShop.getDrawCosts();
                if (!costs.isEmpty()) {
                    shortfallLines = buildShortfallLines(costs, player);
                }
            }

            reportResolution(player, shopId, data, DrawResolution.failure(reason.name(), shortfallLines, pityCounter));
            return;
        }

        var drawResult = gachaShop.performDraw(player, data, pityCounter);
        if (drawResult.item() == null) {
            GachaRequestValidator.reject(
                    RejectCodeDictionary.Code.DRAW_RESULT_EMPTY,
                    "draw",
                    player,
                    shopId,
                    "draw result item is null"
            );
            reportResolution(player, shopId, data, DrawResolution.failure(GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(), List.of(), pityCounter));
            return;
        }

        int actualCount = drawResult.item().calculateActualCount();
        if (actualCount <= 0 || drawResult.item().getReward() == null) {
            throw new IllegalArgumentException("Draw result has no valid reward: " + drawResult.item().getItemId());
        }
        if (drawResult.item().getReward() instanceof ItemTradeOffer itemReward) {
            itemReward.createRewardStack(actualCount);
        }

        int newPityCounter;
        boolean isEarlyTrigger = false;
        int pityThreshold = 0;

        if (drawResult.pityTriggered()) {
            newPityCounter = 0;
        } else if (gachaShop.shouldResetPityOnEarlyTrigger()) {
            var pityConfig = gachaShop.getPityConfig();
            if (pityConfig != null) {
                pityThreshold = pityConfig.getPityThreshold();
                String drawnRarity = drawResult.item().getRarity().getName();
                String targetRarity = pityConfig.getGuaranteedRarity() != null
                        ? pityConfig.getGuaranteedRarity().getName()
                        : null;

                if (targetRarity != null && drawnRarity.equals(targetRarity)) {
                    isEarlyTrigger = true;
                    newPityCounter = 0;
                } else {
                    newPityCounter = pityCounter == Integer.MAX_VALUE ? Integer.MAX_VALUE : pityCounter + 1;
                }
            } else {
                newPityCounter = pityCounter == Integer.MAX_VALUE ? Integer.MAX_VALUE : pityCounter + 1;
            }
        } else {
            newPityCounter = pityCounter == Integer.MAX_VALUE ? Integer.MAX_VALUE : pityCounter + 1;
        }

        // 可失败的结果校验与时钟读取必须在扣费之前完成。
        var pending = PendingDrawManager.prepareDraw(shopId, drawResult.item(), actualCount,
                drawResult.pityTriggered(), newPityCounter);
        PreparedDraw prepared = new PreparedDraw(gachaShop, pending, pityCounter, data.getGachaPityCounter(shopId),
                isEarlyTrigger, pityThreshold);
        PendingDrawPersistence.prepare(player, token, prepared);
    }

    static DrawResolution commitPrepared(ServerPlayer player, UUID token, PreparedDraw prepared) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        GachaShopDefinition gachaShop = prepared.shop();
        var pending = prepared.pending();
        String shopId = pending.shopId;
        int pityCounter = prepared.pityCounter();
        int newPityCounter = pending.newPityCounter;
        int actualCount = pending.actualCount;
        GachaSession session = new GachaSession(player, gachaShop, data);
        // 写盘期间玩家可能改变背包、任务或商店；扣费前重新校验，不重新抽取结果。
        if (org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry.get(shopId) != gachaShop
                || !player.isAlive() || data.getGachaPityCounter(shopId) != prepared.expectedPityCounter() || !session.canDraw()) {
            return DrawResolution.failure(GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(), List.of(), pityCounter);
        }
        for (ITradeOffer cost : gachaShop.getDrawCosts()) {
            if (!cost.canAfford(player)) {
                return DrawResolution.failure(GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD.name(),
                        buildShortfallLines(gachaShop.getDrawCosts(), player), pityCounter);
            }
        }
        boolean recordCooldown = GachaEntryStateResolver.shouldRecordCooldown(data, shopId, gachaShop);
        var transaction = new TradeTransactionCoordinator()
                .execute(player, gachaShop.getDrawCosts(), List.of());
        if (!transaction.succeeded()) {
            if (transaction.rollbackSucceeded()) {
                ArcQuestLog.warn(ArcQuestLog.Category.GACHA,
                        "Draw payment rolled back: player={}, shop={}", player.getUUID(), shopId, transaction.failure());
                return DrawResolution.failure(GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(), List.of(), pityCounter);
            }
            PendingDrawManager.failDraw(player, token);
            throw new IllegalStateException("Gacha payment failed; rollbackSucceeded=" + transaction.rollbackSucceeded(),
                    transaction.failure());
        }

        boolean stored = PendingDrawManager.storeReservedDraw(player, token, pending);

        if (!stored) {
            PendingDrawManager.failDraw(player, token);
            ArcQuestLog.error(ArcQuestLog.Category.GACHA,
                    "Paid draw could not be published: player={}, shop={}, item={}, count={}",
                    player.getUUID(), shopId, pending.drawnItem.getItemId(), actualCount);
            GachaRequestValidator.reject(
                    RejectCodeDictionary.Code.PENDING_STORE_FAILED,
                    "draw",
                    player,
                    shopId,
                    "pending draw store failed"
            );
            throw new IllegalStateException("Paid draw could not be published: " + token);
        }

        if (recordCooldown) {
            GachaEntryStateResolver.recordCooldown(player, data, shopId, gachaShop);
        }

        session.incrementDrawCount();
        data.setGachaPityCounter(shopId, newPityCounter);
        ArcQuestPlayerManager.persistSnapshot(player, data);

        boolean canDraw = true;
        int remainingDraws = -1;
        long lastDrawRealTime = 0;
        long lastDrawGameTime = -1;
        long lastDrawDayTime = -1;

        if (gachaShop.hasLimit() && gachaShop.getMaxDraws() > 0) {
            int totalDraws = gachaShop.getTotalDraws(data);
            remainingDraws = Math.max(0, gachaShop.getMaxDraws() - totalDraws);
            if (remainingDraws <= 0) {
                canDraw = false;
            }
        }

        if (gachaShop.hasCooldown()) {
            var cooldownEntry = data.getGachaDataStore().getDrawCooldown(gachaShop.getShopId());
            lastDrawRealTime = cooldownEntry.realTime();
            lastDrawGameTime = cooldownEntry.gameTime();
            lastDrawDayTime = cooldownEntry.dayTime();

            if (canDraw) {
                boolean onCooldown = GachaEntryStateResolver.isOnCooldown(
                        player, data, gachaShop.getShopId(), gachaShop);
                if (onCooldown) {
                    canDraw = false;
                }
            }
        }

        return new DrawResolution(
                true,
                null,
                List.of(),
                pending.drawnItem,
                actualCount,
                pending.pityTriggered,
                pityCounter,
                newPityCounter,
                canDraw,
                remainingDraws,
                lastDrawRealTime,
                lastDrawGameTime,
                lastDrawDayTime,
                prepared.earlyTrigger(),
                prepared.pityThreshold()
        );
    }

    /**
     * 将 GachaSession 的失败原因映射到事件的失败原因。
     */
    private static GachaEvents.DrawFailedEvent.FailReason mapFailReason(GachaSession.DrawFailReason sessionReason) {
        return switch (sessionReason) {
            case NOT_VISIBLE -> GachaEvents.DrawFailedEvent.FailReason.NOT_VISIBLE;
            case MAX_DRAWS_REACHED -> GachaEvents.DrawFailedEvent.FailReason.MAX_DRAWS_REACHED;
            case ON_COOLDOWN -> GachaEvents.DrawFailedEvent.FailReason.ON_COOLDOWN;
            case CONDITION_NOT_MET -> GachaEvents.DrawFailedEvent.FailReason.CONDITION_NOT_MET;
            case CANNOT_AFFORD -> GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD;
            default -> GachaEvents.DrawFailedEvent.FailReason.UNKNOWN;
        };
    }

    private static List<CostShortfallLine> buildShortfallLines(List<ITradeOffer> costs, ServerPlayer player) {
        List<CostShortfallLine> lines = new ArrayList<>();
        for (ITradeOffer cost : costs) {
            lines.addAll(cost.buildShortfallLines(player));
        }
        return lines;
    }

    record PreparedDraw(GachaShopDefinition shop, PendingDrawManager.PendingDrawData pending,
                        int pityCounter, int expectedPityCounter, boolean earlyTrigger, int pityThreshold) { }

    record DrawResolution(
            boolean succeeded,
            @Nullable String failedReasonName,
            @Nullable List<CostShortfallLine> shortfallLines,
            @Nullable GachaItem item,
            int actualCount,
            boolean pityTriggered,
            int preEventPityCounter,
            int newPityCounter,
            boolean canDraw,
            int remainingDraws,
            long lastDrawRealTime,
            long lastDrawGameTime,
            long lastDrawDayTime,
            boolean earlyTrigger,
            int pityThreshold
    ) {
        static DrawResolution failure(String reason, List<CostShortfallLine> shortfalls, int pityCounter) {
            return new DrawResolution(false, reason, shortfalls, null, 0, false, pityCounter, pityCounter,
                    false, -1, 0, -1, -1, false, 0);
        }
    }
}
