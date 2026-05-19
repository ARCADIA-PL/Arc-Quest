package org.arcadia.arc_quest.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.trade.gacha.network.PendingDrawManager;
import org.slf4j.Logger;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArcQuestPlayerLifecycleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestPlayerLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(sp);
        validateAndFixQuestData(sp, data);
        QuestProgressHandler.rebuildTrackingIndex(sp, data);
        ArcQuestNetwork.syncFullData(sp, data);
        PendingDrawManager.compensateAndGrant(sp);
        LOGGER.debug("[ArcQuest] Login sync complete for: {}", sp.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer to)) return;
        if (!(event.getOriginal() instanceof ServerPlayer from)) return;
        ArcQuestPlayerManager.clone(from, to);
        LOGGER.debug("[ArcQuest] Quest data cloned for player: {}", to.getName().getString());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.get(sp);
        if (data == null) return;
        QuestProgressHandler.rebuildTrackingIndex(sp, data);
        ArcQuestNetwork.syncFullData(sp, data);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.get(sp);
        if (data == null) return;
        QuestProgressHandler.rebuildTrackingIndex(sp, data);
        ArcQuestNetwork.syncFullData(sp, data);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.get(sp);
        if (data != null) {
            ArcQuestPlayerManager.persistSnapshot(sp, data);
        }
        ArcQuestPlayerManager.unload(sp.getUUID());
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel))
            return;
        for (ServerPlayer player : serverLevel.players()) {
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data != null && data.isDirty()) {
                QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
            }
        }
    }

    private static void validateAndFixQuestData(ServerPlayer player, ArcQuestPlayer data) {
        var activeQuests = data.getAllActiveQuests();
        if (activeQuests.isEmpty()) return;

        boolean needsSync = false;

        for (var entry : activeQuests.entrySet()) {
            String questId = entry.getKey();
            QuestRuntimeData qdata = entry.getValue();
            ResourceLocation rl = ResourceLocation.tryParse(questId);

            if (rl == null) continue;

            QuestDefinition def = QuestRegistry.get(rl);
            if (def == null) {
                LOGGER.warn("[ArcQuest] Quest '{}' no longer exists in registry. Marking as failed for player: {}",
                        questId, player.getName().getString());
                qdata.setState(QuestState.FAILED);
                needsSync = true;
                continue;
            }

            var activeIds = new ArrayList<>(qdata.getActivePhaseIds());
            for (String phaseId : activeIds) {
                if (!def.getPhaseIds().contains(phaseId)) {
                    LOGGER.warn("[ArcQuest] Phase '{}' not found in quest '{}'. Removing for player: {}",
                            phaseId, questId, player.getName().getString());
                    qdata.completePhase(phaseId);
                    needsSync = true;
                }
            }

            if (qdata.getState() == QuestState.ACTIVE && qdata.getActivePhaseIds().isEmpty()) {
                PhaseDefinition init = def.getInitialPhase();
                if (init != null) {
                    qdata.activatePhase(init.getPhaseId(), init.getObjectives().size());
                    needsSync = true;
                }
            }
        }

        if (needsSync) {
            LOGGER.info("[ArcQuest] Fixed quest data inconsistencies for player: {}", player.getName().getString());
        }
    }
}
