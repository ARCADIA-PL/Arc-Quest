package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.dialogue.util.TimeSanitizer;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.api.event.GachaEvents;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.gacha.runtime.GachaEntryStateResolver;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.runtime.GachaSession;
import org.com.arc_quest.trade.offer.ItemTradeOffer;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;

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
            
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            if (cap == null) return;
            
            // 获取抽奖商店定义
            GachaShopDefinition gachaShop = GachaRegistry.get(pkt.shopId);
            if (gachaShop == null) {
                Arc_quest.LOGGER.warn("[Gacha] Shop not found: {}", pkt.shopId);
                return;
            }
            
            // 创建 GachaSession（对标 TradeSession）
            GachaSession session = new GachaSession(player, gachaShop, cap);
            
            // 使用 synchronized 确保「检查-执行-更新」的原子性
            synchronized (cap) {
                // 第一步：尝试重置过期次数和冷却（对标商店系统的 checkAndResetPurchases）
                session.checkAndResetDraws();
                
                // 第二步：综合判断是否可以抽奖
                if (!session.canDraw()) {
                    // 触发失败事件，细分原因
                    GachaEvents.DrawFailedEvent.FailReason reason = mapFailReason(session.getFailReason());
                    var failedEvent = new GachaEvents.DrawFailedEvent(player, pkt.shopId, cap, reason);
                    MinecraftForge.EVENT_BUS.post(failedEvent);
                    
                    // 发送失败通知给客户端
                    ArcQuestNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CDrawFailedPacket(pkt.shopId, reason.name())
                    );
                    return;
                }
                
                // 获取当前保底计数
                int pityCounter = cap.getGachaPityCounter(pkt.shopId);
                
                // 第三步：触发抽奖前事件
                var preEvent = new GachaEvents.PreDrawEvent(player, pkt.shopId, cap, pityCounter);
                MinecraftForge.EVENT_BUS.post(preEvent);
                
                if (preEvent.isCancelled()) {
                    return;
                }
                
                pityCounter = preEvent.getPityCounter();
                
                // 【修复】第四步：验证并扣除抽奖成本（对标商店系统）
                ITradeOffer drawCost = gachaShop.getDrawCost();
                if (drawCost != null) {
                    // 检查是否能支付成本
                    if (!drawCost.canAfford(player)) {
                        Arc_quest.LOGGER.warn("[Gacha] Player {} cannot afford draw cost for shop: {}", 
                            player.getName().getString(), pkt.shopId);
                        
                        // 触发失败事件
                        var failedEvent = new GachaEvents.DrawFailedEvent(
                            player, pkt.shopId, cap, GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD
                        );
                        MinecraftForge.EVENT_BUS.post(failedEvent);
                        
                        // 发送失败通知给客户端
                        ArcQuestNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new S2CDrawFailedPacket(pkt.shopId, GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD.name())
                        );
                        return;
                    }
                    
                    // 扣除成本
                    drawCost.execute(player);
                }
                
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
                
                // 发放奖励（使用动态计算的数量）
                var reward = drawResult.item().getReward();
                if (reward != null && reward instanceof ItemTradeOffer itemReward) {
                    // 对于物品奖励，使用实际计算的数量创建 ItemStack
                    ItemStack rewardStack = new ItemStack(itemReward.getItem(), actualCount);
                    if (!player.getInventory().add(rewardStack)) {
                        player.drop(rewardStack, false);
                    }
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
                } else if (reward != null) {
                    // 其他类型的奖励（命令、效果等），使用原有逻辑
                    reward.execute(player);
                }
                
                // 更新保底计数
                int newPityCounter;
                boolean isEarlyTrigger = false;  // 标记是否提前触发保底
                
                if (drawResult.pityTriggered()) {
                    // 触发保底，重置为0
                    newPityCounter = 0;
                } else if (gachaShop.shouldResetPityOnEarlyTrigger()) {
                    // 【新增】检查是否抽中保底指定的物品/品质
                    var pityConfig = gachaShop.getPityConfig();
                    if (pityConfig != null) {
                        // 检查是否抽中保底目标物品或品质
                        String drawnRarity = drawResult.item().getRarity().getName();
                        String targetRarity = pityConfig.getGuaranteedRarity() != null 
                            ? pityConfig.getGuaranteedRarity().getName() : null;
                        
                        // 如果抽中的稀有度 >= 保底稀有度，视为提前触发
                        if (targetRarity != null && drawnRarity.equals(targetRarity)) {
                            isEarlyTrigger = true;
                            newPityCounter = 0;  // 重置保底
                        } else {
                            newPityCounter = pityCounter + 1;
                        }
                    } else {
                        newPityCounter = pityCounter + 1;
                    }
                } else {
                    // 不重置，继续累加
                    newPityCounter = pityCounter + 1;
                }
                
                // 【新增】触发保底提前触发事件
                if (isEarlyTrigger) {
                    var pityConfig = gachaShop.getPityConfig();
                    int pityThreshold = pityConfig != null ? pityConfig.getPityThreshold() : 0;
                    
                    var earlyTriggerEvent = new GachaEvents.PityEarlyTriggerEvent(
                        player,
                        pkt.shopId,
                        drawResult.item(),
                        pityCounter,      // 触发时的保底计数
                        pityThreshold,    // 保底阈值
                        true              // 会重置保底进度
                    );
                    MinecraftForge.EVENT_BUS.post(earlyTriggerEvent);
                }
                
                // 记录抽奖冷却（通过 GachaEntryStateResolver）
                if (GachaEntryStateResolver.shouldRecordCooldown(cap, pkt.shopId, gachaShop)) {
                    GachaEntryStateResolver.recordCooldown(player, cap, pkt.shopId, gachaShop);
                }
                
                // 增加抽奖次数计数（使用 session.incrementDrawCount()）
                session.incrementDrawCount();
                
                // 更新保底计数
                cap.setGachaPityCounter(pkt.shopId, newPityCounter);
                
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
                
                // 【权威】服务端计算抽奖后的最新状态（对标交易系统）
                var refreshedCap = QuestCapabilityProvider.getOrNull(player);
                boolean canDraw = true;
                int remainingDraws = -1; // -1 表示无限
                long lastDrawRealTime = 0;
                long lastDrawGameTime = -1;
                long lastDrawDayTime = -1;
                
                if (refreshedCap != null) {
                    // 检查限购
                    if (gachaShop.hasLimit() && gachaShop.getMaxDraws() > 0) {
                        int totalDraws = gachaShop.getTotalDraws(refreshedCap);
                        remainingDraws = Math.max(0, gachaShop.getMaxDraws() - totalDraws);
                        if (remainingDraws <= 0) {
                            canDraw = false;
                        }
                    }
                    
                    // 检查冷却（只有在未达到限购时才检查）
                    if (canDraw && gachaShop.hasCooldown()) {
                        var progress = refreshedCap.getDialogueProgress();
                        var entry = progress.getEntry(ProgressKey.ofTrade(gachaShop.getShopId(), "draw"));
                        
                        lastDrawRealTime = entry != null ? entry.realTime() : 0;
                        lastDrawGameTime = entry != null ? entry.gameTime() : -1;
                        lastDrawDayTime = entry != null ? entry.dayTime() : -1;
                        
                        boolean onCooldown = ClientCooldownHelper.isOnCooldown(
                            lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                            gachaShop.getCooldownType().ordinal(), gachaShop.getCooldownValue(), gachaShop.getResetTimeTicks()
                        );
                        
                        if (onCooldown) {
                            canDraw = false;
                        }
                    } else if (gachaShop.hasCooldown()) {
                        // 即使不检查冷却，也要获取冷却数据用于同步
                        var progress = refreshedCap.getDialogueProgress();
                        var entry = progress.getEntry(ProgressKey.ofTrade(gachaShop.getShopId(), "draw"));
                        lastDrawRealTime = entry != null ? entry.realTime() : 0;
                        lastDrawGameTime = entry != null ? entry.gameTime() : -1;
                        lastDrawDayTime = entry != null ? entry.dayTime() : -1;
                    }
                }
                
                // 【修复】发送抽奖结果包到客户端
                // 【权威】包含服务端计算的最新状态（canDraw, remainingDraws）
                ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CDrawResultPacket(
                        pkt.shopId,
                        drawResult.item().getItemId(),
                        drawResult.item().getRarity().getName(),
                        actualCount,
                        drawResult.pityTriggered(),
                        newPityCounter,
                        // 【权威】服务端计算的最新状态
                        canDraw,
                        remainingDraws,
                        lastDrawRealTime,
                        lastDrawGameTime,
                        lastDrawDayTime
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
    
    /**
     * 将 GachaSession 的失败原因映射到事件的失败原因。
     */
    private static GachaEvents.DrawFailedEvent.FailReason mapFailReason(GachaSession.DrawFailReason sessionReason) {
        return switch (sessionReason) {
            case NOT_VISIBLE -> GachaEvents.DrawFailedEvent.FailReason.NOT_VISIBLE;
            case MAX_DRAWS_REACHED -> GachaEvents.DrawFailedEvent.FailReason.MAX_DRAWS_REACHED;
            case ON_COOLDOWN -> GachaEvents.DrawFailedEvent.FailReason.ON_COOLDOWN;
            case CONDITION_NOT_MET -> GachaEvents.DrawFailedEvent.FailReason.CONDITION_NOT_MET;
            case CANNOT_AFFORD -> GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD;  // 【新增】
            default -> GachaEvents.DrawFailedEvent.FailReason.UNKNOWN;
        };
    }
}
