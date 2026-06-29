package org.arcadia.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.slf4j.Logger;

/**
 * 客户端 -> 服务端：请求交易权威状态同步（不打开界面）。
 */
public class C2SRequestTradeSyncPacket implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<C2SRequestTradeSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "request_trade_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestTradeSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SRequestTradeSyncPacket::encode, C2SRequestTradeSyncPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestTradeSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.player() instanceof ServerPlayer sp ? sp : null;
            ServerPlayer player = TradeRequestValidator.requirePlayer(sender, "trade_sync", pkt.shopId, LOGGER);
            if (player == null) return;

            SyncObservability.recordRequest("trade", pkt.shopId, player.getName().getString());
            SyncObservability.trace("trade", pkt.shopId, player.getName().getString(), SyncObservability.Stage.SYNC_REQUEST, "manual_sync");

            TradeShopDefinition shop = TradeRequestValidator.requireShop(pkt.shopId, player, "trade_sync", LOGGER);
            if (shop == null) {
                return;
            }

            C2SRequestTradePacket.syncState(player, shop, pkt.screenType);
        });
    }
}