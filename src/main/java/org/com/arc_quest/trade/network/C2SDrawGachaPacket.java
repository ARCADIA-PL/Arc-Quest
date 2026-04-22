package org.com.arc_quest.trade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.GachaEvents;
import org.com.arc_quest.trade.gacha.GachaItem;
import org.com.arc_quest.trade.gacha.GachaShopDefinition;
import org.com.arc_quest.trade.registry.TradeRegistry;

import java.util.function.Supplier;

/**
 * 客户端请求执行抽奖。
 */
public class C2SDrawGachaPacket {
    
    private final String shopId;
    
    public C2SDrawGachaPacket(String shopId) {
        this.shopId = shopId;
    }
    
    public static void encode(C2SDrawGachaPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
    }
    
    public static C2SDrawGachaPacket decode(FriendlyByteBuf buf) {
        return new C2SDrawGachaPacket(buf.readUtf());
    }
    
    public static void handle(C2SDrawGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            
            IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
            if (cap == null) return;
            
            // 获取抽奖商店定义
            var shopDef = TradeRegistry.get(pkt.shopId);
            if (!(shopDef instanceof GachaShopDefinition gachaShop)) {
                Arc_quest.LOGGER.warn("[Gacha] Invalid shop type: {}", pkt.shopId);
                return;
            }
            
            // 检查是否可以抽奖（条件 + 冷却）
            long nowRealTime = System.currentTimeMillis();
            long nowGameTime = player.level().getGameTime();
            long nowDayTime = player.level().dayTime();
            
            if (!gachaShop.canDraw(cap, nowRealTime, nowGameTime, nowDayTime)) {
                Arc_quest.LOGGER.debug("[Gacha] Draw condition/cooldown not met for player: {}", player.getName().getString());
                return;
            }
            
            // 获取当前保底计数
            ProgressKey pityKey = ProgressKey.ofTrade(pkt.shopId, "pity_counter");
            int pityCounter = (int) cap.getDialogueProgress().getEntry(pityKey).realTime();
            
            // 触发抽奖前事件
            var preEvent = new GachaEvents.PreDrawEvent(pkt.shopId, cap, pityCounter);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(preEvent);
            
            if (preEvent.isCancelled()) {
                Arc_quest.LOGGER.debug("[Gacha] Draw cancelled by event for player: {}", player.getName().getString());
                return;
            }
            
            pityCounter = preEvent.getPityCounter();
            
            // 执行抽奖
            var drawResult = gachaShop.performDraw(cap, pityCounter);
            if (drawResult.item() == null) {
                Arc_quest.LOGGER.warn("[Gacha] Failed to draw item for player: {}", player.getName().getString());
                return;
            }
            
            // 计算实际数量
            int actualCount = drawResult.item().calculateActualCount();
            
            // TODO: 发放奖励（需要根据 ITradeOffer 类型处理）
            
            // 更新保底计数
            int newPityCounter = drawResult.pityTriggered() ? 0 : pityCounter + 1;
            
            // 记录抽奖冷却
            ProgressKey drawKey = ProgressKey.ofTrade(pkt.shopId, "draw");
            cap.getDialogueProgress().recordChoiceSelection(drawKey, nowRealTime, nowGameTime, nowDayTime);
            
            // 记录保底计数
            cap.getDialogueProgress().recordChoiceSelection(pityKey, newPityCounter, nowGameTime, nowDayTime);
            
            // 触发抽奖后事件
            var postEvent = new GachaEvents.PostDrawEvent(
                pkt.shopId,
                drawResult.item(),
                drawResult.pityTriggered(),
                newPityCounter,
                cap
            );
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(postEvent);
            
            // 发送结果给客户端
            Arc_quest.NETWORK.sendToPlayer(new S2CDrawResultPacket(
                pkt.shopId,
                drawResult.item().getItemId(),
                drawResult.item().getRarity().getName(),
                actualCount,
                drawResult.pityTriggered(),
                newPityCounter
            ), player);
            
            Arc_quest.LOGGER.info("[Gacha] Player {} drew {} x{} from {}", 
                player.getName().getString(),
                drawResult.item().getItemId(),
                actualCount,
                pkt.shopId
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
