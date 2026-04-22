package org.com.arc_quest.trade.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.GachaEvents;
import org.com.arc_quest.trade.registry.GachaRegistry;

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
            var shopDef = GachaRegistry.get(pkt.shopId);
            if (shopDef == null) {
                return;
            }
            
            // 获取玩家能力并触发打开事件
            var cap = mc.player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
            if (cap != null) {
                // 注意：客户端事件中 player 为 null，仅服务端事件有完整 player 信息
                var openEvent = new GachaEvents.OpenedEvent(null, pkt.shopId, cap);
                MinecraftForge.EVENT_BUS.post(openEvent);
            }
            
            // 更新客户端缓存
            ClientTradeCache.INSTANCE.updateGachaSession(pkt.shopId, pkt.pityCounter, pkt.totalDraws);
            
            // TODO: 打开抽奖 HUD（由用户实现）
            // mc.setScreen(new GachaScreen(pkt.shopId));
        });
        ctx.get().setPacketHandled(true);
    }
}
