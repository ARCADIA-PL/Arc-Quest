package org.com.arc_quest.trade.gacha.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 服务端发送抽奖失败通知给客户端（冷却/限购/条件不满足）。
 */
public class S2CDrawFailedPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final String shopId;
    private final String failReason; // "COOLDOWN", "MAX_DRAWS_REACHED", "CONDITION_NOT_MET"
    
    public S2CDrawFailedPacket(String shopId, String failReason) {
        this.shopId = shopId;
        this.failReason = failReason;
    }
    
    public static void encode(S2CDrawFailedPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeUtf(pkt.failReason);
    }
    
    public static S2CDrawFailedPacket decode(FriendlyByteBuf buf) {
        return new S2CDrawFailedPacket(
            buf.readUtf(),
            buf.readUtf()
        );
    }
    
    public static void handle(S2CDrawFailedPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            // 记录失败结果，触发 UI 状态切换
            ClientGachaCache.INSTANCE.recordDrawFailure(pkt.shopId, pkt.failReason);
        });
        ctx.get().setPacketHandled(true);
    }
}
