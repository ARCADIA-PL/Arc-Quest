package org.com.arc_quest.trade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.gacha.GachaEvents;
import org.com.arc_quest.trade.gacha.GachaItem;
import org.com.arc_quest.trade.gacha.GachaShopDefinition;
import org.com.arc_quest.trade.registry.GachaRegistry;

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
            GachaShopDefinition gachaShop = GachaRegistry.get(pkt.shopId);
            if (gachaShop == null) {
                Arc_quest.LOGGER.warn("[Gacha] Shop not found: {}", pkt.shopId);
                return;
            }
            
            // 检查是否可以抽奖（条件 + 冷却）
            long[] times = TimeSanitizer.getAllTimes(player);
            long nowRealTime = times[0];
            long nowGameTime = times[1];
            long nowDayTime = times[2];
            
            // 获取当前保底计数
            ProgressKey pityKey = ProgressKey.ofTrade(pkt.shopId, "pity_counter");
            
            // 使用 synchronized 确保「检查-执行-更新」的原子性
            synchronized (cap) {
                if (!gachaShop.canDraw(cap, nowRealTime, nowGameTime, nowDayTime)) {
                    Arc_quest.LOGGER.debug("[Gacha] Draw condition/cooldown not met for player: {}", player.getName().getString());
                    
                    // 触发失败事件，细分原因
                    GachaEvents.DrawFailedEvent.FailReason reason;
                    if (gachaShop.getDrawCondition() != null) {
                        var completedQuests = cap.getCompletedQuestLocations();
                        if (!gachaShop.getDrawCondition().test(null, completedQuests, cap.getAllFlags(), cap.getAllVariables())) {
                            reason = GachaEvents.DrawFailedEvent.FailReason.CONDITION_NOT_MET;
                        } else {
                            reason = GachaEvents.DrawFailedEvent.FailReason.ON_COOLDOWN;
                        }
                    } else {
                        reason = GachaEvents.DrawFailedEvent.FailReason.ON_COOLDOWN;
                    }
                    
                    var failedEvent = new GachaEvents.DrawFailedEvent(player, pkt.shopId, cap, reason);
                    MinecraftForge.EVENT_BUS.post(failedEvent);
                    return;
                }
                
                int pityCounter = (int) cap.getDialogueProgress().getEntry(pityKey).realTime();
                
                // 第一步：尝试重置过期次数（对标商店系统的 checkAndResetPurchases）
                gachaShop.checkAndResetDraws(cap, nowRealTime, nowGameTime, nowDayTime);
                
                // 第二步：重新检查是否可以抽奖（重置后可能改变状态）
                if (!gachaShop.canDraw(cap, nowRealTime, nowGameTime, nowDayTime)) {
                    Arc_quest.LOGGER.debug("[Gacha] Draw condition/cooldown not met after reset for player: {}", player.getName().getString());
                    
                    // 触发失败事件（可能是限购已满）
                    int totalDraws = gachaShop.getTotalDraws(cap);
                    GachaEvents.DrawFailedEvent.FailReason reason = 
                        (gachaShop.getMaxDraws() > 0 && totalDraws >= gachaShop.getMaxDraws()) 
                        ? GachaEvents.DrawFailedEvent.FailReason.MAX_DRAWS_REACHED
                        : GachaEvents.DrawFailedEvent.FailReason.ON_COOLDOWN;
                    
                    var failedEvent = new GachaEvents.DrawFailedEvent(player, pkt.shopId, cap, reason);
                    MinecraftForge.EVENT_BUS.post(failedEvent);
                    return;
                }
                
                // 第三步：触发抽奖前事件
                var preEvent = new GachaEvents.PreDrawEvent(player, pkt.shopId, cap, pityCounter);
                MinecraftForge.EVENT_BUS.post(preEvent);
                
                if (preEvent.isCancelled()) {
                    Arc_quest.LOGGER.debug("[Gacha] Draw cancelled by event for player: {}", player.getName().getString());
                    return;
                }
                
                pityCounter = preEvent.getPityCounter();
                
                // 触发抽奖执行中事件（通知 HUD 播放动画）
                var drawingEvent = new GachaEvents.DrawingEvent(player, pkt.shopId, cap, pityCounter);
                MinecraftForge.EVENT_BUS.post(drawingEvent);
                
                // 执行抽奖
                var drawResult = gachaShop.performDraw(player, cap, pityCounter);
                if (drawResult.item() == null) {
                    Arc_quest.LOGGER.warn("[Gacha] Failed to draw item for player: {}", player.getName().getString());
                    return;
                }
                
                // 计算实际数量
                int actualCount = drawResult.item().calculateActualCount();
                
                // 发放奖励（根据 ITradeOffer 类型处理）
                var reward = drawResult.item().getReward();
                if (reward != null) {
                    reward.execute(player);
                }
                
                // 更新保底计数
                int newPityCounter = drawResult.pityTriggered() ? 0 : pityCounter + 1;
                
                // 记录抽奖冷却
                ProgressKey drawKey = ProgressKey.ofTrade(pkt.shopId, "draw");
                cap.getDialogueProgress().recordChoiceSelection(drawKey, nowRealTime, nowGameTime, nowDayTime);
                
                // 增加抽奖次数计数（对标商店系统的 incrementTradePurchase）
                gachaShop.incrementDrawCount(cap, nowRealTime, nowGameTime, nowDayTime);
                
                // 记录保底计数
                cap.getDialogueProgress().recordChoiceSelection(pityKey, newPityCounter, nowGameTime, nowDayTime);
                
                // 触发抽奖后事件
                var postEvent = new GachaEvents.PostDrawEvent(
                    player,
                    pkt.shopId,
                    drawResult.item(),
                    drawResult.pityTriggered(),
                    newPityCounter,
                    cap
                );
                MinecraftForge.EVENT_BUS.post(postEvent);
                
                // 发送结果给客户端
                ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CDrawResultPacket(
                        pkt.shopId,
                        drawResult.item().getItemId(),
                        drawResult.item().getRarity().getName(),
                        actualCount,
                        drawResult.pityTriggered(),
                        newPityCounter
                    )
                );
                
                Arc_quest.LOGGER.info("[Gacha] Player {} drew {} x{} from {}", 
                    player.getName().getString(),
                    drawResult.item().getItemId(),
                    actualCount,
                    pkt.shopId
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
