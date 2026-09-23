package org.arcadia.arc_quest.trade.network;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.api.event.trade.*;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.sync.BoundedProcessedRequestStore;
import org.arcadia.arc_quest.sync.RequestIdempotencyStore;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;

import java.util.*;
import java.util.function.Supplier;

/** 客户端交易协议与附属 API 的稳定入口；服务端执行和界面同步分别委托内部服务。 */
public class C2SRequestTradePacket {
    private final Action action;
    private final String shopId;
    private final String entryId;
    private final ScreenType currentScreenType;
    private final UUID requestId;
    private final long playerSessionEpoch;

    public C2SRequestTradePacket(Action action, String shopId, String entryId) {
        this(action, shopId, entryId, ScreenType.NONE);
    }

    public C2SRequestTradePacket(Action action, String shopId, String entryId, ScreenType currentScreenType) {
        this(action, shopId, entryId, currentScreenType, UUID.randomUUID(), 0L);
    }

    public C2SRequestTradePacket(Action action, String shopId, String entryId, ScreenType currentScreenType,
                                 UUID requestId, long playerSessionEpoch) {
        this.action = action;
        this.shopId = shopId;
        this.entryId = entryId != null ? entryId : "";
        this.currentScreenType = currentScreenType != null ? currentScreenType : ScreenType.NONE;
        this.requestId = requestId != null ? requestId : RequestIdempotencyStore.LEGACY_REQUEST_ID;
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
    }

    public static void syncState(ServerPlayer player, TradeShopDefinition shop, ScreenType clientScreenType) {
        TradeScreenService.syncState(player, shop, clientScreenType);
    }

    public static void pushSyncForActiveShop(ServerPlayer player, String reason) {
        TradeScreenService.pushSyncForActiveShop(player, reason);
    }

    public static C2SRequestTradePacket openFull(String shopId) {
        return new C2SRequestTradePacket(Action.OPEN_FULL, shopId, null);
    }

    public static C2SRequestTradePacket openSimple(String shopId) {
        return new C2SRequestTradePacket(Action.OPEN_SIMPLE, shopId, null);
    }

    public static C2SRequestTradePacket purchase(String shopId, String entryId) {
        return new C2SRequestTradePacket(Action.PURCHASE, shopId, entryId);
    }

    public static C2SRequestTradePacket purchaseWithScreenType(String shopId, String entryId, ScreenType screenType) {
        return new C2SRequestTradePacket(Action.PURCHASE, shopId, entryId, screenType);
    }

    public static C2SRequestTradePacket purchaseWithScreenType(String shopId, String entryId, ScreenType screenType,
                                                                UUID requestId, long playerSessionEpoch) {
        return new C2SRequestTradePacket(Action.PURCHASE, shopId, entryId, screenType, requestId, playerSessionEpoch);
    }

    public static C2SRequestTradePacket refresh(String shopId, ScreenType screenType) {
        return new C2SRequestTradePacket(
                screenType == ScreenType.SIMPLE ? Action.OPEN_SIMPLE : Action.OPEN_FULL,
                shopId, null, screenType);
    }

    public static C2SRequestTradePacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String shopId = buf.readUtf();
        String entryId = buf.readUtf();
        ScreenType screenType = buf.readEnum(ScreenType.class);
        UUID requestId = buf.readUUID();
        long playerSessionEpoch = buf.readLong();
        return new C2SRequestTradePacket(action, shopId, entryId, screenType, requestId, playerSessionEpoch);
    }

    public static void handle(C2SRequestTradePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = TradeRequestValidator.requirePlayer(context.getSender(), "trade_request", pkt.shopId, null);
            if (player == null) return;

            TradeShopDefinition shop = TradeRequestValidator.requireShop(pkt.shopId, player, "trade_request", null);
            if (shop == null) {
                sendGuardTradeFail(player, pkt, RejectCodeDictionary.Code.SHOP_NOT_FOUND);
                return;
            }

            ArcQuestPlayer data = TradeRequestValidator.requireData(player, "trade_request", pkt.shopId, null);
            if (data == null) {
                sendGuardTradeFail(player, pkt, RejectCodeDictionary.Code.DATA_MISSING);
                return;
            }

            if (!PlayerSessionEpochManager.matches(player, pkt.playerSessionEpoch)) {
                sendGuardTradeFail(player, pkt, RejectCodeDictionary.Code.SESSION_EPOCH_MISMATCH);
                return;
            }

            try {
                switch (pkt.action) {
                    case OPEN_FULL -> TradeScreenService.open(player, shop, false);
                    case OPEN_SIMPLE -> TradeScreenService.open(player, shop, true);
                    case PURCHASE -> TradePurchaseService.handlePurchase(player, shop, pkt.entryId, pkt.currentScreenType,
                            pkt.requestId, pkt.playerSessionEpoch);
                }
            } catch (BoundedProcessedRequestStore.RequestRejectedException rejected) {
                ArcQuestLog.debug(ArcQuestLog.Category.TRADE,
                        "Purchase request rejected: player={}, request={}, reason={}",
                        player.getUUID(), pkt.requestId, rejected.reason());
                sendExecutionFailure(player, pkt);
            } catch (RuntimeException failure) {
                ArcQuestLog.error(ArcQuestLog.Category.TRADE,
                        "Trade request failed: player={}, shop={}, entry={}, request={}",
                        player.getUUID(), pkt.shopId, pkt.entryId, pkt.requestId, failure);
                sendExecutionFailure(player, pkt);
            }
        });
        context.setPacketHandled(true);
    }

    private static void sendExecutionFailure(ServerPlayer player, C2SRequestTradePacket packet) {
        // 执行异常或重入拒绝不再次触发业务事件，避免失败监听器递归发起同一操作。
        ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                S2COpenTradePacket.tradeFail(packet.shopId, packet.entryId, S2COpenTradePacket.FailReason.GENERIC,
                        TradeRequestValidator.toErrorKey(RejectCodeDictionary.Code.TRANSACTION_FAILED)));
    }

    // ── 序列化 ──

    private static void sendGuardTradeFail(ServerPlayer player,
                                           C2SRequestTradePacket pkt,
                                           RejectCodeDictionary.Code code) {
        String errorKey = TradeRequestValidator.toErrorKey(code);
        ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                S2COpenTradePacket.tradeFail(
                        pkt.shopId,
                        pkt.entryId,
                        S2COpenTradePacket.FailReason.GENERIC,
                        errorKey
                )
        );
        if (pkt.entryId == null || pkt.entryId.isEmpty()) {
            MinecraftForge.EVENT_BUS.post(new TradeOpenRejectedEvent(player, pkt.shopId, errorKey));
        } else {
            MinecraftForge.EVENT_BUS.post(new TradePurchaseRejectedEvent(player, pkt.shopId, pkt.entryId, errorKey));
        }
    }

    /**
     * 服务端打开处理器，可由 DialogueAction 或网络包处理流程调用。
     */
    public static void handleServerOpen(ServerPlayer player, TradeShopDefinition shop, boolean simple) {
        TradeScreenService.open(player, shop, simple);
    }

    /**
     * 从对话中打开商店，确保不中断当前的对话会话。
     */
    public static void handleServerOpenFromDialogue(ServerPlayer player, TradeShopDefinition shop, boolean simple, String restoreNodeId) {
        TradeScreenService.open(player, shop, simple);

        DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
        if (manager.isInDialogue(player)) {
            manager.setRestoreNodeId(player, restoreNodeId);
        }
    }

    public static void clearPlayer(UUID playerId) {
        TradeScreenService.clearPlayer(playerId);
        TradePurchaseService.clearPlayer(playerId);
    }

    /** 恢复准备阶段仅作废界面缓存；恢复失败时仍需保留已执行购买的去重结果。 */
    public static void invalidateScreenState(UUID playerId) {
        TradeScreenService.clearPlayer(playerId);
    }

    public static void clearAll() {
        TradeScreenService.clearAll();
        TradePurchaseService.clearAll();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(shopId);
        buf.writeUtf(entryId);
        buf.writeEnum(currentScreenType);
        buf.writeUUID(requestId);
        buf.writeLong(playerSessionEpoch);
    }

    public Action getAction() {
        return action;
    }

    public String getShopId() {
        return shopId;
    }

    public String getEntryId() {
        return entryId;
    }

    public ScreenType getCurrentScreenType() {
        return currentScreenType;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public enum Action {
        OPEN_FULL,
        OPEN_SIMPLE,
        PURCHASE
    }

    /**
     * 客户端当前打开的界面类型（用于刷新时保持类型一致）
     */
    public enum ScreenType {
        NONE,
        SIMPLE,
        FULL
    }

}
