package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.gacha.runtime.GachaSession;
import org.com.arc_quest.trade.api.ITradeOffer;

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
            
            // 3. 创建会话并同步状态（不检查是否可以打开）
            GachaSession session = new GachaSession(player, gachaShop, cap);
            session.checkAndResetDraws(); // 先重置过期的计数和冷却
            
            // 4. 【修复】服务端计算按钮状态（对标交易系统）
            boolean canDraw = true;
            int remainingDraws = -1; // -1 表示无限
            
            // 检查限购
            if (gachaShop.hasLimit() && gachaShop.getMaxDraws() > 0) {
                int totalDraws = gachaShop.getTotalDraws(cap);
                remainingDraws = Math.max(0, gachaShop.getMaxDraws() - totalDraws);
                if (remainingDraws <= 0) {
                    canDraw = false;
                }
            }
            
            // 检查冷却（只有在未达到限购时才检查）
            if (canDraw && gachaShop.hasCooldown()) {
                var progress = cap.getDialogueProgress();
                var key = ProgressKey.ofTrade(pkt.shopId, "draw");
                var entry = progress.getChoiceSelection(key);
                
                long lastDrawRealTime = entry.exists() ? entry.realTime() : 0;
                long lastDrawGameTime = entry.exists() ? entry.gameTime() : 0;
                long lastDrawDayTime = entry.exists() ? entry.dayTime() : 0;
                
                boolean onCooldown = ClientCooldownHelper.isOnCooldown(
                    lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                    gachaShop.getCooldownType().ordinal(), gachaShop.getCooldownValue(), gachaShop.getResetTimeTicks()
                );
                
                if (onCooldown) {
                    canDraw = false;
                }
            }
            
            // 【新增】检查成本是否充足（对标商店系统）
            if (canDraw) {
                ITradeOffer drawCost = gachaShop.getDrawCost();
                if (drawCost != null && !drawCost.canAfford(player)) {
                    canDraw = false;
                }
            }
            
            // 5. 无条件发送打开界面的数据包，包含服务端计算好的状态
            // 注意：这里不增加抽奖次数，只在真正抽奖时增加
            long lastDrawRealTime = 0;
            long lastDrawGameTime = -1;
            long lastDrawDayTime = -1;
            
            // 从 DialogueProgressStore 获取最后抽奖时间
            var progress = cap.getDialogueProgress();
            var key = ProgressKey.ofTrade(pkt.shopId, "draw");
            var entry = progress.getChoiceSelection(key);
            if (entry.exists()) {
                lastDrawRealTime = entry.realTime();
                lastDrawGameTime = entry.gameTime();
                lastDrawDayTime = entry.dayTime();
            }
            
            ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2COpenGachaPacket(
                    pkt.shopId,
                    gachaShop.getPityConfig() != null ? cap.getGachaPityCounter(pkt.shopId) : 0,
                    gachaShop.getTotalDraws(cap),
                    canDraw,
                    remainingDraws,
                    lastDrawRealTime,
                    lastDrawGameTime,
                    lastDrawDayTime,
                    gachaShop.getCooldownType().ordinal(),
                    gachaShop.getCooldownValue(),
                    gachaShop.getResetTimeTicks()
                )
            );
            
            Arc_quest.LOGGER.info("[Gacha] Player {} opened gacha '{}' (canDraw={}, remaining={})", 
                player.getName().getString(), pkt.shopId, canDraw, remainingDraws);
        });
        
        ctx.get().setPacketHandled(true);
    }
}
