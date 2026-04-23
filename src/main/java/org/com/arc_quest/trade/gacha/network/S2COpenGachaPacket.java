package org.com.arc_quest.trade.gacha.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.api.event.GachaEvents;
import org.com.arc_quest.client.gui.gacha.GachaScreen;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    
    // 【新增】抽奖历史记录（最多50条）
    private final List<IQuestCapability.GachaDrawRecord> drawHistory;
    
    public S2COpenGachaPacket(String shopId, int pityCounter, int totalDraws,
                               boolean canDraw, int remainingDraws,
                               long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                               int cooldownType, long cooldownValue, int resetTimeTicks,
                               List<IQuestCapability.GachaDrawRecord> drawHistory) {
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
        this.drawHistory = drawHistory != null ? drawHistory : Collections.emptyList();
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
        
        // 序列化历史记录
        buf.writeInt(pkt.drawHistory.size());
        for (IQuestCapability.GachaDrawRecord record : pkt.drawHistory) {
            buf.writeUtf(record.itemId());
            buf.writeUtf(record.rarityName());
            buf.writeInt(record.actualCount());
            buf.writeBoolean(record.pityTriggered());
            buf.writeLong(record.drawTime());
        }
    }
    
    public static S2COpenGachaPacket decode(FriendlyByteBuf buf) {
        String shopId = buf.readUtf();
        int pityCounter = buf.readInt();
        int totalDraws = buf.readInt();
        boolean canDraw = buf.readBoolean();
        int remainingDraws = buf.readInt();
        long lastDrawRealTime = buf.readLong();
        long lastDrawGameTime = buf.readLong();
        long lastDrawDayTime = buf.readLong();
        int cooldownType = buf.readInt();
        long cooldownValue = buf.readLong();
        int resetTimeTicks = buf.readInt();
        
        // 反序列化历史记录
        int historySize = buf.readInt();
        List<IQuestCapability.GachaDrawRecord> history = new ArrayList<>();
        for (int i = 0; i < historySize; i++) {
            IQuestCapability.GachaDrawRecord record = new IQuestCapability.GachaDrawRecord(
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readLong()
            );
            history.add(record);
        }
        
        return new S2COpenGachaPacket(
            shopId, pityCounter, totalDraws, canDraw, remainingDraws,
            lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
            cooldownType, cooldownValue, resetTimeTicks,
            history
        );
    }
    
    /**
     * 服务端打开抽奖界面（从命令或 NPC 调用）。
     */
    public static void handleServerOpen(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        // 【统一】使用 GachaScreenOpener 打开界面（自动处理重置和状态同步）
        GachaScreenOpener.openGachaScreen(player, shop, cap);
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
            
            // 【新增】更新客户端缓存并同步完整历史记录（一次性替换）
            List<ClientGachaCache.DrawRecord> historyRecords = new ArrayList<>();
            for (var record : pkt.drawHistory) {
                historyRecords.add(new ClientGachaCache.DrawRecord(
                    record.itemId(),
                    record.rarityName(),
                    record.actualCount(),
                    record.pityTriggered(),
                    record.drawTime()
                ));
            }
            
            ClientGachaCache.INSTANCE.updateSessionWithHistory(
                pkt.shopId, pkt.pityCounter, pkt.totalDraws, pkt.canDraw,
                pkt.lastDrawRealTime, pkt.lastDrawGameTime, pkt.lastDrawDayTime,
                pkt.cooldownType, pkt.cooldownValue, pkt.resetTimeTicks,
                historyRecords
            );
            
            // 打开抽奖界面
            mc.setScreen(new GachaScreen(pkt.shopId));
        });
        ctx.get().setPacketHandled(true);
    }
}
