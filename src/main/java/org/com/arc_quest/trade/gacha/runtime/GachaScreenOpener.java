package org.com.arc_quest.trade.gacha.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.api.event.GachaEvents;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.network.S2COpenGachaPacket;
import org.slf4j.Logger;

/**
 * 抽奖界面打开管理器。
 * 统一管理服务端打开抽奖界面的逻辑，确保 C2S 和 S2C 路径行为一致。
 */
public class GachaScreenOpener {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 服务端打开抽奖界面（统一入口）。
     * 
     * @param player 玩家
     * @param shop 抽奖商店定义
     * @param cap 玩家能力数据
     */
    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop, IQuestCapability cap) {
        openGachaScreen(player, shop, cap, null);
    }

    /**
     * 从对话中打开抽奖界面，并记录对话恢复目标节点。
     */
    public static void openGachaScreen(ServerPlayer player, GachaShopDefinition shop,
                                       IQuestCapability cap, String restoreNodeId) {
        // 记录对话恢复节点（如果当前确实处于对话中）
        if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
            DialogueSessionManager manager = DialogueSessionManager.INSTANCE;
            if (manager.isInDialogue(player)) {
                manager.setRestoreNodeId(player, restoreNodeId);
            }
        }

        // 【关键】创建会话并执行重置逻辑
        GachaSession session = new GachaSession(player, shop, cap);
        
        int drawCountBeforeReset = session.getDrawCount();
        session.checkAndResetDraws(); // 先重置过期的计数和冷却
        int drawCountAfterReset = session.getDrawCount();
        
        if (drawCountBeforeReset != drawCountAfterReset) {
            LOGGER.info("[Gacha-Open] Draw count reset on open: shop={}, before={}, after={}", 
                shop.getShopId(), drawCountBeforeReset, drawCountAfterReset);
        }
        
        // 触发打开事件
        var openEvent = new GachaEvents.OpenedEvent(player, shop.getShopId(), cap);
        MinecraftForge.EVENT_BUS.post(openEvent);
        
        // 【修复】使用统一的 canDraw() 方法计算按钮状态
        boolean canDraw = session.canDraw();
        int remainingDraws = session.getRemainingDraws();
        
        // 【新增】额外检查成本是否充足（对标商店系统）
        if (canDraw) {
            ITradeOffer drawCost = shop.getDrawCost();
            if (drawCost != null && !drawCost.canAfford(player)) {
                canDraw = false;
            }
        }
        
        // 获取当前保底计数和总抽奖次数（使用重置后的值）
        int pityCounter = cap.getGachaPityCounter(shop.getShopId());
        int totalDraws = cap.getGachaDrawCount(shop.getShopId());
        
        LOGGER.info("[Gacha-Open] State synced: shop={}, canDraw={}, remaining={}, totalDraws={}, pityCounter={}",
            shop.getShopId(), canDraw, remainingDraws, totalDraws, pityCounter);
        
        // 获取冷却数据（统一从 GachaDataStore 读取，避免旧进度存储残留）
        var cooldownEntry = cap.getGachaDataStore().getDrawCooldown(shop.getShopId());

        long lastDrawRealTime = cooldownEntry.realTime();
        long lastDrawGameTime = cooldownEntry.gameTime();
        long lastDrawDayTime = cooldownEntry.dayTime();
        
        int cooldownType = shop.getCooldownType().ordinal();
        long cooldownValue = shop.getCooldownValue();
        int resetTimeTicks = shop.getResetTimeTicks();
        
        // 【新增】获取抽奖历史记录
        var drawHistory = cap.getGachaDrawHistory(shop.getShopId());
        
        // 发送网络包给客户端
        ArcQuestNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new S2COpenGachaPacket(
                shop.getShopId(), pityCounter, totalDraws,
                canDraw, remainingDraws,
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                cooldownType, cooldownValue, resetTimeTicks,
                drawHistory
            )
        );
        
        LOGGER.debug("[Gacha] Sent S2COpenGachaPacket to player {}", player.getName().getString());
    }
}
