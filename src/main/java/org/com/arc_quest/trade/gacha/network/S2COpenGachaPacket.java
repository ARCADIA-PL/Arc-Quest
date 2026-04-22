package org.com.arc_quest.trade.gacha.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.client.gui.gacha.GachaScreen;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.api.event.GachaEvents;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.network.ClientGachaCache;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 服务端通知客户端打开抽奖界面。
 */
public class S2COpenGachaPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final String shopId;
    private final int pityCounter;
    private final int totalDraws;
    
    // 【修复】对标交易系统：发送原始数据，不发送UI文本
    private final boolean canDraw;              // 是否可以抽奖（对标 canBuyConditions）
    private final int remainingDraws;           // 剩余可抽次数（-1=无限）
    
    private final long lastDrawRealTime;
    private final long lastDrawGameTime;
    private final long lastDrawDayTime;
    private final int cooldownType;
    private final long cooldownValue;
    private final int resetTimeTicks;
    
    public S2COpenGachaPacket(String shopId, int pityCounter, int totalDraws,
                               boolean canDraw, int remainingDraws,
                               long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                               int cooldownType, long cooldownValue, int resetTimeTicks) {
        this.shopId = shopId;
        this.pityCounter = pityCounter;
        this.totalDraws = totalDraws;
        this.canDraw = canDraw;
        this.remainingDraws = remainingDraws;
        this.lastDrawRealTime = lastDrawRealTime;
        this.lastDrawGameTime = lastDrawGameTime;
        this.lastDrawDayTime = lastDrawDayTime;
        this.cooldownType = cooldownType;
        this.cooldownValue = cooldownValue;
        this.resetTimeTicks = resetTimeTicks;
    }
    
    public static void encode(S2COpenGachaPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeInt(pkt.pityCounter);
        buf.writeInt(pkt.totalDraws);
        buf.writeBoolean(pkt.canDraw);
        buf.writeInt(pkt.remainingDraws);
        buf.writeLong(pkt.lastDrawRealTime);
        buf.writeLong(pkt.lastDrawGameTime);
        buf.writeLong(pkt.lastDrawDayTime);
        buf.writeInt(pkt.cooldownType);
        buf.writeLong(pkt.cooldownValue);
        buf.writeInt(pkt.resetTimeTicks);
    }
    
    public static S2COpenGachaPacket decode(FriendlyByteBuf buf) {
        return new S2COpenGachaPacket(
            buf.readUtf(),
            buf.readInt(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readInt(),
            buf.readLong(),
            buf.readLong(),
            buf.readLong(),
            buf.readInt(),
            buf.readLong(),
            buf.readInt()
        );
    }
    
    /**
     * 服务端打开抽奖界面（从命令或 NPC 调用）。
     */
    public static void handleServerOpen(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        // 触发打开事件
        var openEvent = new GachaEvents.OpenedEvent(player, shop.getShopId(), cap);
        MinecraftForge.EVENT_BUS.post(openEvent);
        
        // 获取当前保底计数和总抽奖次数
        int pityCounter = cap.getGachaPityCounter(shop.getShopId());
        int totalDraws = cap.getGachaDrawCount(shop.getShopId());
        
        // 【修复】服务端计算按钮状态（对标交易系统）
        boolean canDraw = true;
        int remainingDraws = -1; // -1 表示无限
        
        // 检查限购
        if (shop.hasLimit() && shop.getMaxDraws() > 0) {
            remainingDraws = Math.max(0, shop.getMaxDraws() - totalDraws);
            if (remainingDraws <= 0) {
                canDraw = false;
            }
        }
        
        // 检查冷却（只有在未达到限购时才检查）
        if (canDraw && shop.hasCooldown()) {
            ProgressKey drawKey = ProgressKey.ofTrade(shop.getShopId(), "draw");
            var progressEntry = cap.getDialogueProgress().getChoiceSelection(drawKey);
            
            long lastDrawRealTime = progressEntry != null ? progressEntry.realTime() : 0;
            long lastDrawGameTime = progressEntry != null ? progressEntry.gameTime() : 0;
            long lastDrawDayTime = progressEntry != null ? progressEntry.dayTime() : 0;
            
            // 使用 ClientCooldownHelper 的服务端等效逻辑判断冷却
            boolean onCooldown = ClientCooldownHelper.isOnCooldown(
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                shop.getCooldownType().ordinal(), shop.getCooldownValue(), shop.getResetTimeTicks()
            );
            
            if (onCooldown) {
                canDraw = false;
            }
        }
        
        // 获取冷却数据（对标商店系统）
        ProgressKey drawKey = ProgressKey.ofTrade(shop.getShopId(), "draw");
        var progressEntry = cap.getDialogueProgress().getChoiceSelection(drawKey);
        
        long lastDrawRealTime = progressEntry != null ? progressEntry.realTime() : 0;
        long lastDrawGameTime = progressEntry != null ? progressEntry.gameTime() : 0;
        long lastDrawDayTime = progressEntry != null ? progressEntry.dayTime() : 0;
        
        int cooldownType = shop.getCooldownType().ordinal();
        long cooldownValue = shop.getCooldownValue();
        int resetTimeTicks = shop.getResetTimeTicks();
        
        // 发送网络包给客户端
        ArcQuestNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new S2COpenGachaPacket(
                shop.getShopId(), pityCounter, totalDraws,
                canDraw, remainingDraws,
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                cooldownType, cooldownValue, resetTimeTicks
            )
        );
        
        LOGGER.info("[Gacha] Server opened gacha '{}' for player {} (pity={}, draws={}, canDraw={})", 
            shop.getShopId(), player.getName().getString(), pityCounter, totalDraws, canDraw);
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
            var cap = QuestCapabilityProvider.getOrNull(mc.player);
            if (cap != null) {
                // 注意：客户端事件中 player 为 null，仅服务端事件有完整 player 信息
                var openEvent = new GachaEvents.OpenedEvent(null, pkt.shopId, cap);
                MinecraftForge.EVENT_BUS.post(openEvent);
            }
            
            // 更新客户端缓存（含冷却数据，对标商店系统）
            ClientGachaCache.INSTANCE.updateSession(
                pkt.shopId, pkt.pityCounter, pkt.totalDraws, pkt.canDraw,
                pkt.lastDrawRealTime, pkt.lastDrawGameTime, pkt.lastDrawDayTime,
                pkt.cooldownType, pkt.cooldownValue, pkt.resetTimeTicks
            );
            
            // 打开抽奖界面
            mc.setScreen(new GachaScreen(pkt.shopId));
        });
        ctx.get().setPacketHandled(true);
    }
}
