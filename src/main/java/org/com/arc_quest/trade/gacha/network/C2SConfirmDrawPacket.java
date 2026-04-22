package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.Arc_quest;

import java.util.function.Supplier;

/**
 * 客户端确认抽奖结果并请求发放奖励。
 * <p>
 * 在抽奖动画第二阶段结束时由客户端发送，
 * 服务端收到后从 PendingDrawManager 中取出暂存的结果并发放奖励。
 */
public class C2SConfirmDrawPacket {

    private final String shopId;

    public C2SConfirmDrawPacket(String shopId) {
        this.shopId = shopId;
    }

    public static void encode(C2SConfirmDrawPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
    }

    public static C2SConfirmDrawPacket decode(FriendlyByteBuf buf) {
        return new C2SConfirmDrawPacket(buf.readUtf());
    }

    public static void handle(C2SConfirmDrawPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // 从管理器中取出暂存的抽奖结果并发放
            boolean success = PendingDrawManager.confirmAndGrant(player);

            if (!success) {
                Arc_quest.LOGGER.warn("[Gacha] Player {} tried to confirm draw but no pending data found or expired",
                        player.getName().getString());
            } else {
                Arc_quest.LOGGER.info("[Gacha] Confirmed and granted draw reward for player {}",
                        player.getName().getString());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
