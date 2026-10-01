package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/** 客户端抽卡请求的稳定协议入口，业务处理在服务端主线程执行。 */
public class C2SDrawGachaPacket implements CustomPacketPayload {
    public static final Type<C2SDrawGachaPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "draw_gacha"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDrawGachaPacket> STREAM_CODEC = StreamCodec.ofMember(C2SDrawGachaPacket::encode, C2SDrawGachaPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private final String shopId;

    public C2SDrawGachaPacket(String shopId) { this.shopId = shopId; }

    public static void encode(C2SDrawGachaPacket pkt, FriendlyByteBuf buf) { buf.writeUtf(pkt.shopId); }

    public static C2SDrawGachaPacket decode(FriendlyByteBuf buf) {
        return new C2SDrawGachaPacket(buf.readUtf());
    }

    public static void handle(C2SDrawGachaPacket pkt, IPayloadContext ctx) {
        IPayloadContext context = ctx;
        context.enqueueWork(() -> {
            ServerPlayer player = GachaRequestValidator.requirePlayer((context.player() instanceof net.minecraft.server.level.ServerPlayer sender ? sender : null), "draw", pkt.shopId);
            if (player != null) GachaDrawService.execute(player, pkt.shopId);
        });

    }
}
