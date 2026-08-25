package org.arcadia.arc_quest.quest.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.api.QuestTimeLimitType;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerReconciliationService;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 相关处理说明。 */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class QuestDataTickHandler {

    private static final Map<UUID, Object2LongOpenHashMap<String>> markerRefreshClocks = new HashMap<>();
    private static final Map<UUID, Object2ByteOpenHashMap<String>> markerStateCache = new HashMap<>();

    private QuestDataTickHandler() {
    }

    public static void clearMarkerRuntimeState(UUID playerId) {
        QuestMarkerRuntimeManager.clearPlayer(playerId);
    }

    public static void clearMarkerRuntimeState() {
        QuestMarkerRuntimeManager.clearAll();
    }

    public static void rebuildDynamicMarkers(ServerPlayer player) {
        clearMarkerRuntimeState(player.getUUID());
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data != null) QuestMarkerReconciliationService.reconcileContinuousQuestMarkers(player, data, true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (player.tickCount % 20 != 0) return;

        checkQuestTimeouts(player);
        expireTriggeredMarkers(player);
        refreshDynamicMarkers(player);
        persistAndSyncIfChanged(player);
    }

    private static void checkQuestTimeouts(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;

        var now = CoreProcessors.get().time().capture(player);
        long nowRealMs = now.realTime();
        long nowDayTime = now.dayTime();

        List<String> timedOutQuestIds = new ArrayList<>();
        for (QuestRuntimeData qdata : data.getAllActiveQuests().values()) {
            if (qdata.getState() != QuestState.ACTIVE) continue;

            QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(qdata.getQuestId()));
            if (def == null || !def.hasTimeLimit()) continue;

            QuestTimeLimitType type = def.getTimeLimitType();
            long limit = def.getTimeLimitValue();
            boolean timeout = false;

            if (type == QuestTimeLimitType.REAL_SECONDS) {
                long acceptedRealMs = qdata.getAcceptedAtRealMs();
                if (acceptedRealMs > 0L) {
                    timeout = (nowRealMs - acceptedRealMs) >= (limit * 1000L);
                }
            } else if (type == QuestTimeLimitType.GAME_DAY_TIME) {
                long acceptedDay = qdata.getAcceptedAtDayTime() % 24000L;
                long elapsed = (nowDayTime - acceptedDay + 24000L) % 24000L;
                timeout = elapsed >= limit;
            }

            if (timeout) {
                timedOutQuestIds.add(qdata.getQuestId());
            }
        }
        // 遍历结束后再标记失败，以便处理器安全移除活动条目并发出
        // 任务专用清理和同步监听器使用的标准失败事件。
        timedOutQuestIds.forEach(questId -> QuestProgressHandler.failQuest(player, questId));
    }

    private static void refreshDynamicMarkers(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestMarkerReconciliationService.reconcileContinuousQuestMarkers(player, data, false);
    }

    private static void expireTriggeredMarkers(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        for (String markerId : QuestMarkerRuntimeManager.expire(player, data)) {
            ArcQuestNetwork.syncMarkerDeltaRemove(player, markerId);
        }
    }
    private static void persistAndSyncIfChanged(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data != null) {
            QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
        }
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel serverLevel)) return;
        for (ServerPlayer player : serverLevel.players()) {
            persistAndSyncIfChanged(player);
        }
    }
}
