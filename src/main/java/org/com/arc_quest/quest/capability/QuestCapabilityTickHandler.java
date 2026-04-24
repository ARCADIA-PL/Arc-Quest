package org.com.arc_quest.quest.capability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.network.QuestSyncCoordinator;

/**
 * 玩家Tick事件处理器：以固定节流频率执行“变更检测 -> 持久化快照 -> 网络同步”。
 * <p>
 * 注意：这里的“持久化”是写入 Player PersistentData 的运行时快照，
 * 不等同于立即磁盘落盘。
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestCapabilityTickHandler {

    private static int tickCounter = 0;

    private QuestCapabilityTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.player;

        tickCounter++;
        if (tickCounter % 20 != 0) return;

        persistAndSyncIfChanged(player);
    }

    /**
     * 统一语义入口：有变更才执行“快照持久化 + 客户端同步 + 清脏”。
     */
    private static void persistAndSyncIfChanged(ServerPlayer player) {
        player.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap ->
                QuestSyncCoordinator.persistAndSyncIfChanged(player, cap)
        );
    }
}
