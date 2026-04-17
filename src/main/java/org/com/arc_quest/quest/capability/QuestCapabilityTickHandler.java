package org.com.arc_quest.quest.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.slf4j.Logger;

/**
 * 玩家Tick事件处理器，脏标记防抖与批量保存。
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestCapabilityTickHandler {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int tickCounter = 0;
    
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        
        ServerPlayer player = (ServerPlayer) event.player;
        

        tickCounter++;
        if (tickCounter % 20 != 0) return;
        

        player.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap -> {
            if (cap instanceof QuestCapabilityImpl impl) {

                if (impl.isDirty()) {

                    impl.clearDirty();
                    
                    LOGGER.debug("[QuestSave] Player {} data saved (dirty flag cleared)", 
                            player.getGameProfile().getName());
                }
            }
        });
    }
}
