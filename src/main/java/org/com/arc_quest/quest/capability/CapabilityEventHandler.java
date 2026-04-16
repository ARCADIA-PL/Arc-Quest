package org.com.arc_quest.quest.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.slf4j.Logger;

/**
 * 处理 Capability 的注册、附着、死亡克隆和登录同步。
 *
 * <p>分别挂在 MOD 总线（注册）和 FORGE 总线（附着/克隆/登录）上。
 */
public final class CapabilityEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation CAP_ID = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "quest_data");

    private CapabilityEventHandler() {}

    // ═══════════════════════════════════════════════════════
    //  MOD 总线 — 注册 Capability 类型
    // ═══════════════════════════════════════════════════════

    @Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {

        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(IQuestCapability.class);
            LOGGER.info("[ArcQuest] IQuestCapability registered.");
        }
    }

    // ═══════════════════════════════════════════════════════
    //  FORGE 总线 — 附着 / 克隆 / 登录同步
    // ═══════════════════════════════════════════════════════

    @Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusEvents {

        /**
         * 将 Capability 附着到每个 Player 实体。
         */
        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                if (!event.getObject().getCapability(QuestCapabilityProvider.QUEST_CAP).isPresent()) {
                    event.addCapability(CAP_ID, new QuestCapabilityProvider());
                }
            }
        }

        /**
         * 死亡后克隆数据（含从末地返回）。
         * <p>
         * Forge 的 {@code PlayerEvent.Clone} 在玩家重生时触发。
         * {@code event.isWasDeath()} 为 true 时表示真正的死亡重生。
         */
        @SubscribeEvent
        public static void onPlayerClone(PlayerEvent.Clone event) {
            // 不论是死亡还是末地传送，都复制任务数据
            event.getOriginal().reviveCaps(); // 1.20.1 需要先恢复旧 caps
            event.getOriginal().getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(oldCap -> {
                event.getEntity().getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(newCap -> {
                    newCap.copyFrom(oldCap);
                    LOGGER.debug("[ArcQuest] Quest data cloned for player: {}",
                            event.getEntity().getName().getString());
                });
            });
            event.getOriginal().invalidateCaps(); // 恢复失效状态
        }

        /**
         * 玩家登录时：
         * 1. 重建 ObjectiveTracker 索引
         * 2. 全量同步到客户端
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap -> {
                    // 重建追踪索引
                    QuestProgressHandler.rebuildTrackingIndex(serverPlayer, cap);

                    // 全量同步到客户端
                    ArcQuestNetwork.syncFullData(serverPlayer, cap);

                    LOGGER.debug("[ArcQuest] Login sync complete for: {}",
                            serverPlayer.getGameProfile().getName());
                });
            }
        }

        /**
         * 重生后同步（死亡重生 / 末地返回）。
         */
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap -> {
                    QuestProgressHandler.rebuildTrackingIndex(serverPlayer, cap);
                    ArcQuestNetwork.syncFullData(serverPlayer, cap);
                });
            }
        }

        /**
         * 维度变更后重新同步（保险措施）。
         */
        @SubscribeEvent
        public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap -> {
                    ArcQuestNetwork.syncFullData(serverPlayer, cap);
                });
            }
        }
    }
}