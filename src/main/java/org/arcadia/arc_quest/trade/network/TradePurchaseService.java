package org.arcadia.arc_quest.trade.network;

import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.api.event.trade.*;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.sync.BoundedProcessedRequestStore;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.runtime.TradeSession;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket.ScreenType;

import java.util.*;

/** 购买请求去重、扩展事件与结果通知；物品事务由 TradeSession 执行。 */
final class TradePurchaseService {
    private static final BoundedProcessedRequestStore<TradeCommandResult> PROCESSED_PURCHASES =
            new BoundedProcessedRequestStore<>(256);

    private static final Set<UUID> DISPATCHING_PLAYERS = new HashSet<>();

    private TradePurchaseService() { }

    static void handlePurchase(ServerPlayer player, TradeShopDefinition shop,
                                       String entryId, ScreenType clientScreenType,
                                       UUID requestId, long playerSessionEpoch) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            if (!player.server.isSameThread()) throw new IllegalStateException("Purchases require the server thread");
            if (!DISPATCHING_PLAYERS.add(player.getUUID())) {
                ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        S2COpenTradePacket.tradeFail(shop.getShopId(), entryId, S2COpenTradePacket.FailReason.GENERIC,
                                TradeRequestValidator.toErrorKey(RejectCodeDictionary.Code.TRANSACTION_FAILED)));
                return;
            }
            try {
                dispatchPurchase(player, shop, entryId, clientScreenType, requestId, playerSessionEpoch);
            } finally {
                DISPATCHING_PLAYERS.remove(player.getUUID());
            }
        }
    }

    private static void dispatchPurchase(ServerPlayer player, TradeShopDefinition shop,
                                         String entryId, ScreenType clientScreenType,
                                         UUID requestId, long playerSessionEpoch) {
        SyncObservability.trace("trade", shop.getShopId(), player.getName().getString(),
                SyncObservability.Stage.ACTION, "purchase:" + entryId);

        long effectiveEpoch = playerSessionEpoch > 0L
                ? playerSessionEpoch
                : PlayerSessionEpochManager.getOrCreate(player);
        BoundedProcessedRequestStore.ProcessedResult<TradeCommandResult> processed = PROCESSED_PURCHASES.process(
                new PlayerSessionRef(player.getUUID(), effectiveEpoch),
                requestId,
                new PurchaseKey(shop.getShopId(), entryId),
                () -> executePurchase(player, shop, entryId));
        TradeCommandResult commandResult = processed.result();
        TradeSession.TradeResult result = commandResult.tradeResult();

        try {
            S2COpenTradePacket.FailReason reason = commandResult.failReason();
            String errorKey = result.errorKey();

            S2COpenTradePacket response = result.succeeded()
                    ? S2COpenTradePacket.tradeSuccess(shop.getShopId(), entryId)
                    : S2COpenTradePacket.tradeFail(shop.getShopId(), entryId, reason, errorKey, result.shortfallLines());

            ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    response);

            SyncObservability.trace("trade", shop.getShopId(), player.getName().getString(),
                    SyncObservability.Stage.RESULT,
                    result.succeeded() ? "purchase_success" : "purchase_failed:" + reason.name());

            // 发包失败后的重试仍可发布尚未尝试的事件；回调自身失败或重入不能重复发布。
            boolean publishEvents = !commandResult.eventsAttempted;
            commandResult.eventsAttempted = true;
            if (publishEvents && result.succeeded()) {
                MinecraftForge.EVENT_BUS.post(new TradePurchasedSuccessEvent(player, shop.getShopId(), entryId));
            } else if (publishEvents) {
                // 转换失败原因
                TradePurchaseFailedEvent.FailureReason failureReason = switch (reason) {
                    case COOLDOWN -> TradePurchaseFailedEvent.FailureReason.ON_COOLDOWN;
                    case LIMIT_REACHED -> TradePurchaseFailedEvent.FailureReason.MAX_PURCHASES_REACHED;
                    case CONDITION_FAIL -> TradePurchaseFailedEvent.FailureReason.CONDITION_NOT_MET;
                    case CANNOT_AFFORD -> TradePurchaseFailedEvent.FailureReason.INSUFFICIENT_FUNDS;
                    default -> TradePurchaseFailedEvent.FailureReason.UNKNOWN;
                };
                MinecraftForge.EVENT_BUS.post(new TradePurchaseFailedEvent(
                        player, shop.getShopId(), entryId, failureReason));
                MinecraftForge.EVENT_BUS.post(new TradePurchaseRejectedEvent(
                        player, shop.getShopId(), entryId, errorKey));
            }

            if (clientScreenType != ScreenType.NONE) {
                TradeScreenService.touchActiveTradeContext(player, shop.getShopId(), clientScreenType);
            }

            TradeScreenService.refreshTradeData(player, shop, clientScreenType, "purchase_result");
        } catch (RuntimeException notificationFailure) {
            // 购买结果已提交，通知失败不能再向客户端宣称扣费/发奖回滚。
            ArcQuestLog.error(ArcQuestLog.Category.TRADE,
                    "Purchase notification failed: player={}, shop={}, entry={}, request={}, succeeded={}",
                    player.getUUID(), shop.getShopId(), entryId, requestId, result.succeeded(), notificationFailure);
        }
    }

    private static TradeCommandResult executePurchase(ServerPlayer player, TradeShopDefinition shop, String entryId) {
        TradeEntry entry = shop.getEntry(entryId);
        if (entry != null) {
            TradePurchaseAttemptEvent event = new TradePurchaseAttemptEvent(player, shop, entry);
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                ArcQuestLog.debug(ArcQuestLog.Category.TRADE,
                        "Trade purchase cancelled by event. player={}, shop={}, entry={}, reason={}",
                        player.getUUID(), shop.getShopId(), entryId, event.getCancellationReason());
                return new TradeCommandResult(
                        TradeSession.TradeResult.fail(RejectCodeDictionary.errorKey(
                                RejectCodeDictionary.Domain.TRADE,
                                RejectCodeDictionary.Code.UNKNOWN)),
                        S2COpenTradePacket.FailReason.GENERIC);
            }
        }
        TradeSession.TradeResult result = new TradeSession(player, shop).executeTrade(entryId);
        S2COpenTradePacket.FailReason reason = S2COpenTradePacket.FailReason.GENERIC;
        RejectCodeDictionary.Code mappedCode = RejectCodeDictionary.fromTradeErrorKey(result.errorKey());
        switch (mappedCode) {
            case SESSION_ON_COOLDOWN -> reason = S2COpenTradePacket.FailReason.COOLDOWN;
            case SESSION_MAX_DRAWS_REACHED -> reason = S2COpenTradePacket.FailReason.LIMIT_REACHED;
            case SESSION_NOT_VISIBLE, SESSION_CONDITION_NOT_MET ->
                    reason = S2COpenTradePacket.FailReason.CONDITION_FAIL;
            case CANNOT_AFFORD -> reason = S2COpenTradePacket.FailReason.CANNOT_AFFORD;
            default -> reason = S2COpenTradePacket.FailReason.GENERIC;
        }
        return new TradeCommandResult(result, reason);
    }

    static void clearPlayer(UUID playerId) { PROCESSED_PURCHASES.clearPlayer(playerId); }

    static void clearAll() { PROCESSED_PURCHASES.clear(); }

    private record PurchaseKey(String shopId, String entryId) { }

    private static final class TradeCommandResult {
        private final TradeSession.TradeResult tradeResult;
        private final S2COpenTradePacket.FailReason failReason;
        private boolean eventsAttempted;

        private TradeCommandResult(TradeSession.TradeResult tradeResult, S2COpenTradePacket.FailReason failReason) {
            this.tradeResult = tradeResult;
            this.failReason = failReason;
        }

        TradeSession.TradeResult tradeResult() { return tradeResult; }
        S2COpenTradePacket.FailReason failReason() { return failReason; }
    }
}
