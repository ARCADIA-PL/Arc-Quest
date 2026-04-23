package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.gacha.runtime.GachaScreenOpener;

import java.util.function.Supplier;

/**
 * 客户端请求打开抽奖界面。
 * <p>
 * 服务端收到后检查权限，如果允许则发送 S2COpenGachaPacket。
 */
public class C2SOpenGachaPacket {
    
    private final String shopId;
    
    public C2SOpenGachaPacket(String shopId) {
        this.shopId = shopId;
    }
    
    public static void encode(C2SOpenGachaPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
    }
    
    public static C2SOpenGachaPacket decode(FriendlyByteBuf buf) {
        return new C2SOpenGachaPacket(buf.readUtf());
    }
    
    public static void handle(C2SOpenGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            
            // 1. 获取奖池定义
            GachaShopDefinition gachaShop = GachaRegistry.get(pkt.shopId);
            if (gachaShop == null) {
                Arc_quest.LOGGER.warn("[Gacha] Player {} tried to open non-existent shop: {}", 
                    player.getName().getString(), pkt.shopId);
                return;
            }
            
            // 2. 获取玩家能力数据
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            if (cap == null) {
                Arc_quest.LOGGER.warn("[Gacha] Player {} has no quest capability", 
                    player.getName().getString());
                return;
            }
            
            // 3. 【统一】使用 GachaScreenOpener 打开界面（自动处理重置和状态同步）
            GachaScreenOpener.openGachaScreen(player, gachaShop, cap);
        });
        
        ctx.get().setPacketHandled(true);
    }
}
