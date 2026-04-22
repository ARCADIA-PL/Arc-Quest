package org.com.arc_quest.trade.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.trade.gacha.GachaShopDefinition;
import org.com.arc_quest.trade.registry.TradeRegistry;

import java.util.function.Supplier;

/**
 * 服务端通知客户端打开抽奖界面。
 */
public class S2COpenGachaPacket {
    
    private final String shopId;
    private final int pityCounter;
    private final int totalDraws;
    
    public S2COpenGachaPacket(String shopId, int pityCounter, int totalDraws) {
        this.shopId = shopId;
        this.pityCounter = pityCounter;
        this.totalDraws = totalDraws;
    }
    
    public static void encode(S2COpenGachaPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeInt(pkt.pityCounter);
        buf.writeInt(pkt.totalDraws);
    }
    
    public static S2COpenGachaPacket decode(FriendlyByteBuf buf) {
        return new S2COpenGachaPacket(buf.readUtf(), buf.readInt(), buf.readInt());
    }
    
    public static void handle(S2COpenGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            // 验证商店存在
            var shopDef = TradeRegistry.get(pkt.shopId);
            if (shopDef == null) {
                return;
            }
            
            // 更新客户端缓存
            ClientTradeCache.INSTANCE.updateGachaSession(pkt.shopId, pkt.pityCounter, pkt.totalDraws);
            
            // TODO: 打开抽奖 HUD（由用户实现）
            // mc.setScreen(new GachaScreen(pkt.shopId));
        });
        ctx.get().setPacketHandled(true);
    }
}
