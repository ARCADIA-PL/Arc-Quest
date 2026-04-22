package org.com.arc_quest.trade.gacha.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.gui.gacha.GachaScreen;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.network.ClientGachaCache;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 服务端发送抽奖结果给客户端。
 * <p>
 * 【权威设计】此包包含：
 * 1. 抽奖结果数据
 * 2. 服务端计算后的最新状态（canDraw, remainingDraws）
 * 3. 动画触发标志（由服务端决定是否需要播放动画）
 */
public class S2CDrawResultPacket {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final String shopId;
    private final String drawnItemId;
    private final String rarityName;
    private final int actualCount;
    private final boolean pityTriggered;
    private final int newPityCounter;
    
    // 【新增】服务端计算的最新状态
    private final boolean canDraw;              // 是否仍可抽奖
    private final int remainingDraws;           // 剩余次数（-1=无限）
    
    // 【新增】冷却信息（用于客户端HUD显示）
    private final long lastDrawRealTime;
    private final long lastDrawGameTime;
    private final long lastDrawDayTime;
    
    public S2CDrawResultPacket(String shopId, String drawnItemId, String rarityName,
                                int actualCount, boolean pityTriggered, int newPityCounter,
                                boolean canDraw, int remainingDraws,
                                long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime) {
        this.shopId = shopId;
        this.drawnItemId = drawnItemId;
        this.rarityName = rarityName;
        this.actualCount = actualCount;
        this.pityTriggered = pityTriggered;
        this.newPityCounter = newPityCounter;
        this.canDraw = canDraw;
        this.remainingDraws = remainingDraws;
        this.lastDrawRealTime = lastDrawRealTime;
        this.lastDrawGameTime = lastDrawGameTime;
        this.lastDrawDayTime = lastDrawDayTime;
    }
    
    public static void encode(S2CDrawResultPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeUtf(pkt.drawnItemId);
        buf.writeUtf(pkt.rarityName);
        buf.writeInt(pkt.actualCount);
        buf.writeBoolean(pkt.pityTriggered);
        buf.writeInt(pkt.newPityCounter);
        // 【新增】同步服务端计算的状态
        buf.writeBoolean(pkt.canDraw);
        buf.writeInt(pkt.remainingDraws);
        buf.writeLong(pkt.lastDrawRealTime);
        buf.writeLong(pkt.lastDrawGameTime);
        buf.writeLong(pkt.lastDrawDayTime);
    }
    
    public static S2CDrawResultPacket decode(FriendlyByteBuf buf) {
        return new S2CDrawResultPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readInt(),
            // 【新增】读取服务端状态
            buf.readBoolean(),
            buf.readInt(),
            buf.readLong(),
            buf.readLong(),
            buf.readLong()
        );
    }
    
    public static void handle(S2CDrawResultPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            
            // 【权威】1. 记录抽奖结果到 ClientGachaCache
            ClientGachaCache.INSTANCE.recordDrawResult(
                pkt.shopId,
                pkt.drawnItemId,
                pkt.rarityName,
                pkt.actualCount,
                pkt.pityTriggered,
                pkt.newPityCounter
            );
            
            // 【权威】2. 同步服务端计算的最新状态（canDraw, remainingDraws）
            // 【修复】从 GachaRegistry 获取冷却配置，而不是硬编码为0
            GachaShopDefinition gachaShop = GachaRegistry.get(pkt.shopId);
            int cooldownType = gachaShop != null ? gachaShop.getCooldownType().ordinal() : 0;
            long cooldownValue = gachaShop != null ? gachaShop.getCooldownValue() : 0;
            int resetTimeTicks = gachaShop != null ? gachaShop.getResetTimeTicks() : 0;
            
            ClientGachaCache.INSTANCE.updateSession(
                pkt.shopId,
                pkt.newPityCounter,
                ClientGachaCache.INSTANCE.getTotalDraws(pkt.shopId) + 1, // totalDraws+1
                pkt.lastDrawRealTime,
                pkt.lastDrawGameTime,
                pkt.lastDrawDayTime,
                cooldownType,
                cooldownValue,
                resetTimeTicks
            );
            
            // 【权威】3. 如果当前正在显示该奖池的界面，直接触发滚动动画
            if (mc.screen instanceof GachaScreen gachaScreen) {
                if (gachaScreen.getShopId().equals(pkt.shopId)) {
                    gachaScreen.triggerRollingAnimation(pkt.drawnItemId, pkt.rarityName, pkt.actualCount, pkt.pityTriggered);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
