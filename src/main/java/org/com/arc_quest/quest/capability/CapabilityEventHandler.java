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
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.slf4j.Logger;

/**
 * 处理 Capability 的注册、附着、死亡克隆和登录同步。
 *
 * <p>分别挂在 MOD 总线（注册）和 FORGE 总线（附着/克隆/登录）上。
 */
public final class CapabilityEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation CAP_ID = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "quest_data");

    private CapabilityEventHandler() {
    }

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
         * 2. 验证并修复任务数据（处理代码修改后的不兼容）
         * 3. 全量同步到客户端
         */
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.getCapability(QuestCapabilityProvider.QUEST_CAP).ifPresent(cap -> {
                    // 验证并修复任务数据
                    validateAndFixQuestData(serverPlayer, cap);
                    
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
         * 验证并修复玩家的任务数据，确保与当前注册的定义一致。
         * <p>
         * 处理场景：
         * - 任务定义被修改（阶段增删、目标变更）
         * - 旧存档中的任务数据与新定义不匹配
         */
        private static void validateAndFixQuestData(ServerPlayer player, IQuestCapability cap) {
            var activeQuests = cap.getAllActiveQuests();
            if (activeQuests.isEmpty()) return;

            boolean needsSync = false;

            for (var entry : activeQuests.entrySet()) {
                String questId = entry.getKey();
                QuestRuntimeData data = entry.getValue();
                ResourceLocation rl = ResourceLocation.tryParse(questId);
                
                if (rl == null) continue;
                
                QuestDefinition def = QuestRegistry.get(rl);
                if (def == null) {
                    // 任务定义已被移除，标记为失败
                    LOGGER.warn("[ArcQuest] Quest '{}' no longer exists in registry. Marking as failed for player: {}",
                            questId, player.getName().getString());
                    data.setState(QuestState.FAILED);
                    needsSync = true;
                    continue;
                }

                // 检查当前阶段是否存在
                String currentPhase = data.getCurrentPhaseId();
                if (!def.getPhaseIds().contains(currentPhase)) {
                    // 阶段不存在，重置到第一个阶段
                    String firstPhase = def.getPhaseIds().iterator().next();
                    LOGGER.warn("[ArcQuest] Phase '{}' not found in quest '{}'. Resetting to phase '{}' for player: {}",
                            currentPhase, questId, firstPhase, player.getName().getString());
                    data.setCurrentPhaseId(firstPhase);
                    data.resetObjectives(def.getPhase(firstPhase).getObjectives().size());
                    needsSync = true;
                }
            }

            if (needsSync) {
                LOGGER.info("[ArcQuest] Fixed quest data inconsistencies for player: {}", player.getName().getString());
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