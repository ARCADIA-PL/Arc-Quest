package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端抽卡请求的稳定协议入口，业务处理在服务端主线程执行。 */
public class C2SDrawGachaPacket {
    private final String shopId;

    public C2SDrawGachaPacket(String shopId) { this.shopId = shopId; }

    public static void encode(C2SDrawGachaPacket pkt, FriendlyByteBuf buf) { buf.writeUtf(pkt.shopId); }

    public static C2SDrawGachaPacket decode(FriendlyByteBuf buf) {
        return new C2SDrawGachaPacket(buf.readUtf());
    }

    public static void handle(C2SDrawGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = GachaRequestValidator.requirePlayer(context.getSender(), "draw", pkt.shopId);
            if (player != null) GachaDrawService.execute(player, pkt.shopId);
        });
        context.setPacketHandled(true);
    }
}
