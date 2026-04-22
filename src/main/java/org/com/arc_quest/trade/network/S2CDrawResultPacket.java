package org.com.arc_quest.trade.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端发送抽奖结果给客户端。
 */
public class S2CDrawResultPacket {
    
    private final String shopId;
    private final String drawnItemId;
    private final String rarityName;
    private final int actualCount;
    private final boolean pityTriggered;
    private final int newPityCounter;
    
    public S2CDrawResultPacket(String shopId, String drawnItemId, String rarityName,
                                int actualCount, boolean pityTriggered, int newPityCounter) {
        this.shopId = shopId;
        this.drawnItemId = drawnItemId;
        this.rarityName = rarityName;
        this.actualCount = actualCount;
        this.pityTriggered = pityTriggered;
        this.newPityCounter = newPityCounter;
    }
    
    public static void encode(S2CDrawResultPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeUtf(pkt.drawnItemId);
        buf.writeUtf(pkt.rarityName);
        buf.writeInt(pkt.actualCount);
        buf.writeBoolean(pkt.pityTriggered);
        buf.writeInt(pkt.newPityCounter);
    }
    
    public static S2CDrawResultPacket decode(FriendlyByteBuf buf) {
        return new S2CDrawResultPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readInt()
        );
    }
    
    public static void handle(S2CDrawResultPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            // 更新客户端缓存
            ClientTradeCache.INSTANCE.updateGachaSession(pkt.shopId, pkt.newPityCounter, 
                                                         ClientTradeCache.INSTANCE.getGachaSession(pkt.shopId) != null ?
                                                         ClientTradeCache.INSTANCE.getGachaSession(pkt.shopId).getTotalDraws() + 1 : 1);
            
            // TODO: 显示抽奖结果 HUD（由用户实现）
            // 可以播放音效、显示动画等
            
        });
        ctx.get().setPacketHandled(true);
    }
}
