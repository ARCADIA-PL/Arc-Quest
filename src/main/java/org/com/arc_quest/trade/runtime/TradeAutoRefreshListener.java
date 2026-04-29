package org.com.arc_quest.trade.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.com.arc_quest.client.hud.shop.SimpleTradePanel;
import org.com.arc_quest.client.hud.shop.TradeScreen;
import org.com.arc_quest.quest.event.QuestChangeEvent;
import org.com.arc_quest.quest.event.QuestEventBus;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.slf4j.Logger;

/**
 * 交易界面自动刷新监听器。
 * <p>
 * 当玩家完成任务或设置 flag 时，如果当前打开了交易界面，
 * 则自动请求服务端刷新交易数据，确保 UI 状态与服务器同步。
 * <p>
 * <b>原理说明</b>：
 * <ul>
 *   <li>监听 {@link QuestChangeEvent} 事件（任务完成、flag 变化等）</li>
 *   <li>检查当前是否打开交易界面</li>
 *   <li>发送刷新请求到服务端，重新计算 canBuyConditions</li>
 *   <li>服务端返回最新状态，客户端更新 UI</li>
 * </ul>
 * <p>
 * <b>性能优化</b>：
 * <ul>
 *   <li>仅在交易界面打开时触发</li>
 *   <li>使用防抖机制（1秒内最多刷新1次）</li>
 *   <li>避免频繁的网络通信</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, value = Dist.CLIENT)
public class TradeAutoRefreshListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    /** 上次刷新时间戳（毫秒），用于防抖 */
    private static long lastRefreshTime = 0;
    
    /** 防抖间隔（毫秒） */
    private static final long REFRESH_DEBOUNCE_MS = 1000;

    @SubscribeEvent
    public static void onClientInit(FMLClientSetupEvent event) {
        QuestEventBus.subscribe(TradeAutoRefreshListener::onQuestEvent);
    }

    /**
     * 处理任务事件，触发交易界面自动刷新
     */
    private static void onQuestEvent(QuestChangeEvent event) {
        Minecraft mc = Minecraft.getInstance();
        
        if (mc.player == null || mc.level == null) {
            return;
        }
        
        if (!(mc.screen instanceof AbstractTradeScreen tradeScreen)) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshTime < REFRESH_DEBOUNCE_MS) {
            LOGGER.debug("[Trade-AutoRefresh] Skipped refresh due to debounce ({}ms since last)", 
                    currentTime - lastRefreshTime);
            return;
        }
        
        String shopId = tradeScreen.getShopId();
        if (shopId == null || shopId.isEmpty()) {
            return;
        }
        
        boolean shouldRefresh = false;
        String reason = "";
        
        switch (event.getType()) {
            case QUEST_COMPLETED -> {
                shouldRefresh = true;
                reason = "quest completed: " + event.getQuestId();
            }
            case QUEST_ACCEPTED -> {
                shouldRefresh = true;
                reason = "quest accepted: " + event.getQuestId();
            }
            case OBJECTIVE_COMPLETED -> {
                shouldRefresh = true;
                reason = "objective completed in quest: " + event.getQuestId();
            }
            default -> {
            }
        }
        
        if (!shouldRefresh) {
            return;
        }
        
        C2SRequestTradePacket.ScreenType screenType = determineScreenType(mc.screen);
        
        LOGGER.info("[Trade-AutoRefresh] Triggering auto-refresh for shop={}, reason={}", shopId, reason);
        ArcQuestNetwork.sendTradeRequest(C2SRequestTradePacket.refresh(shopId, screenType));
        
        lastRefreshTime = currentTime;
    }

    /**
     * 判断当前界面类型
     */
    private static C2SRequestTradePacket.ScreenType determineScreenType(Screen screen) {
        if (screen instanceof SimpleTradePanel) {
            return C2SRequestTradePacket.ScreenType.SIMPLE;
        } else if (screen instanceof TradeScreen) {
            return C2SRequestTradePacket.ScreenType.FULL;
        }
        return C2SRequestTradePacket.ScreenType.NONE;
    }
}
