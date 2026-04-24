package org.com.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.quest.network.SyncObservability;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：请求交易权威状态同步（不打开界面）。
 */
public class C2SRequestTradeSyncPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String shopId;
    private final C2SRequestTradePacket.ScreenType screenType;

    public C2SRequestTradeSyncPacket(String shopId, C2SRequestTradePacket.ScreenType screenType) {
        this.shopId = shopId;
        this.screenType = screenType != null ? screenType : C2SRequestTradePacket.ScreenType.FULL;
    }

    public static void encode(C2SRequestTradeSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeEnum(pkt.screenType);
    }

    public static C2SRequestTradeSyncPacket decode(FriendlyByteBuf buf) {
        String shopId = buf.readUtf();
        C2SRequestTradePacket.ScreenType screenType = buf.readEnum(C2SRequestTradePacket.ScreenType.class);
        return new C2SRequestTradeSyncPacket(shopId, screenType);
    }

    public static void handle(C2SRequestTradeSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = TradeRequestValidator.requirePlayer(ctx.get().getSender(), "trade_sync", pkt.shopId, LOGGER);
            if (player == null) return;

            SyncObservability.recordRequest("trade", pkt.shopId, player.getName().getString());
            SyncObservability.trace("trade", pkt.shopId, player.getName().getString(), SyncObservability.Stage.SYNC_REQUEST, "manual_sync");

            TradeShopDefinition shop = TradeRequestValidator.requireShop(pkt.shopId, player, "trade_sync", LOGGER);
            if (shop == null) {
                return;
            }

            C2SRequestTradePacket.syncState(player, shop, pkt.screenType);
        });
        ctx.get().setPacketHandled(true);
    }
}