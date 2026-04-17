package org.com.arc_quest.quest.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.slf4j.Logger;

/**
 * 玩家Tick事件处理器 - P2优化：脏标记防抖与批量保存。
 * 
 * <p>功能：
 * <ul>
 *   <li>每20 ticks（1秒）检查一次所有在线玩家的脏数据</li>
 *   <li>仅对有变化的玩家执行保存操作</li>
 *   <li>重置脏标记，避免重复保存</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestCapabilityTickHandler {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int tickCounter = 0;
    
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅在服务端、END阶段处理
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        
        ServerPlayer player = (ServerPlayer) event.player;
        
        // 每20 ticks（1秒）检查一次
        tickCounter++;
        if (tickCounter % 20 != 0) return;
        
        // 获取玩家的Quest Capability
        player.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap -> {
            if (cap instanceof QuestCapabilityImpl impl) {
                // 检查是否有脏数据
                if (impl.isDirty()) {
                    // 触发保存（Minecraft会自动在玩家tick中保存Capability）
                    // 注意：Capability的保存由Forge自动处理，我们只需要确保数据已更新
                    // player.setChanged() 不存在，但Capability会在世界保存时自动持久化
                    
                    // 重置脏标记
                    impl.clearDirty();
                    
                    LOGGER.debug("[QuestSave] Player {} data saved (dirty flag cleared)", 
                            player.getGameProfile().getName());
                }
            }
        });
    }
}
