package org.arcadia.arc_quest.quest.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.api.QuestTimeLimitType;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家Tick事件处理器：以固定节流频率执行“变更检测 -> 持久化快照 -> 网络同步”。
 */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestCapabilityTickHandler {

    private static final Object2LongOpenHashMap<String> markerRefreshClock = new Object2LongOpenHashMap<>();
    private static final Map<UUID, Object2ByteOpenHashMap<String>> markerStateCache = new HashMap<>();
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

        checkQuestTimeouts(player);
        refreshDynamicMarkers(player);
        syncIfChanged(player);
    }

    private static void checkQuestTimeouts(ServerPlayer player) {
        {
            ArcQuestPlayer playerData = ArcQuestPlayerManager.get(player);
            if (playerData != null) {
            long nowRealMs = System.currentTimeMillis();
            long nowDayTime = player.level().getDayTime() % 24000L;

            for (QuestRuntimeData data : playerData.getAllActiveQuests().values()) {
                if (data.getState() != QuestState.ACTIVE) continue;

                QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(data.getQuestId()));
                if (def == null || !def.hasTimeLimit()) continue;

                QuestTimeLimitType type = def.getTimeLimitType();
                long limit = def.getTimeLimitValue();
                boolean timeout = false;

                if (type == QuestTimeLimitType.REAL_SECONDS) {
                    long acceptedRealMs = data.getAcceptedAtRealMs();
                    if (acceptedRealMs > 0L) {
                        timeout = (nowRealMs - acceptedRealMs) >= (limit * 1000L);
                    }
                } else if (type == QuestTimeLimitType.GAME_DAY_TIME) {
                    long acceptedDay = data.getAcceptedAtDayTime() % 24000L;
                    long elapsed = (nowDayTime - acceptedDay + 24000L) % 24000L;
                    timeout = elapsed >= limit;
                }

                if (timeout) {
                    data.setState(QuestState.FAILED);
                    playerData.markFailed(data.getQuestId());
                    playerData.removeActiveQuest(data.getQuestId());
                }
            }
            }
        }
    }

    private static void refreshDynamicMarkers(ServerPlayer player) {
        {
            ArcQuestPlayer playerData = ArcQuestPlayerManager.get(player);
            if (playerData != null) {
            ServerLevel level = player.serverLevel();
            UUID pid = player.getUUID();
            Object2ByteOpenHashMap<String> states = markerStateCache.computeIfAbsent(
                    pid, k -> new Object2ByteOpenHashMap<>());

            for (Map.Entry<String, QuestRuntimeData> e : playerData.getAllActiveQuests().entrySet()) {
                String questId = e.getKey();
                QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
                if (def == null) continue;

                for (MarkSpec spec : def.getRelatedMarks()) {
                    String markerId = "aq:auto:" + questId + ":" + spec.id();
                    refreshMarker(markerId, questId, spec, player, cap, level, states, null, -1);
                }

                for (String phaseId : e.getValue().getActivePhaseIds()) {
                    var phase = def.getPhase(phaseId);
                    if (phase == null) continue;

                    for (MarkSpec spec : phase.getRelatedMarks()) {
                        String markerId = "aq:auto:" + questId + ":" + phaseId + ":phase:" + spec.id();
                        refreshMarker(markerId, questId, spec, player, cap, level, states, phaseId, -1);
                    }

                    int[] progress = e.getValue().getAllProgress(phaseId);
                    var objectives = phase.getObjectives();
                    for (int i = 0; i < objectives.size(); i++) {
                        var obj = objectives.get(i);
                        int p = i < progress.length ? progress[i] : 0;
                        int required = obj.resolveRequiredCount(player);
                        if (p >= required) continue;

                        for (MarkSpec spec : obj.getRelatedMarks()) {
                            String markerId = "aq:auto:" + questId + ":" + phaseId + ":obj" + i + ":" + spec.id();
                            refreshMarker(markerId, questId, spec, player, cap, level, states, phaseId, i);
                        }
                    }
                }
            }
            }
        }
    }

    private static void refreshMarker(String markerId, String questId, MarkSpec spec,
                                       ServerPlayer player, ArcQuestPlayer playerData, ServerLevel level,
                                       Object2ByteOpenHashMap<String> states,
                                       String phaseId, int objIndex) {
        if (!shouldRefresh(markerId, spec.refreshTicks(), player.tickCount)) return;

        byte newState = (byte) (spec.activateWhen().test(player, cap)
                && !spec.deactivateWhen().test(player, cap) ? 1 : 0);
        byte oldState = states.getByte(markerId);
        if (oldState == newState && oldState != 0) return;

        states.put(markerId, newState);
        if (newState == 0) {
            playerData.removeMarker(markerId);
            return;
        }

        QuestMarkerData marker = resolveMarker(markerId, questId, spec, player, level);
        if (marker == null) return;

        if (phaseId != null) {
            marker = new QuestMarkerData.Builder(marker.getId(), marker.getWorldX(), marker.getWorldY(), marker.getWorldZ(), marker.getLabel())
                    .dimension(marker.getDimension())
                    .bindQuest(questId)
                    .bindPhase(phaseId)
                    .followEntity(marker.getFollowEntityId(), marker.getFollowEntityUuid(), marker.getFollowEntityGuid(), marker.getAttachPoint())
                    .type(marker.getType())
                    .state(marker.getState())
                    .color(marker.getColorARGB())
                    .showDistance(marker.isShowDistance())
                    .allowOffscreenArrow(marker.isAllowOffscreenArrow())
                    .build();
            if (objIndex >= 0) {
                marker = new QuestMarkerData.Builder(marker.getId(), marker.getWorldX(), marker.getWorldY(), marker.getWorldZ(), marker.getLabel())
                        .dimension(marker.getDimension())
                        .bindQuest(questId)
                        .bindPhase(phaseId)
                        .bindObjective(objIndex)
                        .followEntity(marker.getFollowEntityId(), marker.getFollowEntityUuid(), marker.getFollowEntityGuid(), marker.getAttachPoint())
                        .type(marker.getType())
                        .state(marker.getState())
                        .color(marker.getColorARGB())
                        .showDistance(marker.isShowDistance())
                        .allowOffscreenArrow(marker.isAllowOffscreenArrow())
                        .build();
            }
        }
        playerData.upsertMarker(marker);
    }

    private static QuestMarkerData resolveMarker(String markerId,
                                                 String questId,
                                                 MarkSpec spec,
                                                 ServerPlayer player,
                                                 ServerLevel level) {
        MarkableObject target = spec.target();

        QuestMarkerData marker = null;

        if (target instanceof MarkableObject.Pos p) {
            marker = new QuestMarkerData.Builder(markerId, p.x(), p.y(), p.z(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(questId)
                    .type(spec.markerType())
                    .build();
        } else if (target instanceof MarkableObject.DimensionPos dp) {
            if (!level.dimension().equals(dp.dimension())) return null;
            marker = new QuestMarkerData.Builder(markerId, dp.x(), dp.y(), dp.z(), spec.id())
                    .dimension(dp.dimension().location().toString())
                    .bindQuest(questId)
                    .type(spec.markerType())
                    .build();
        } else if (target instanceof MarkableObject.EntityByUuid byUuid) {
            Entity ent = level.getEntity(byUuid.uuid());
            if (ent == null) return null;
            marker = new QuestMarkerData.Builder(markerId, ent.getX(), ent.getY(), ent.getZ(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(questId)
                    .followEntity(ent.getId(), byUuid.uuid().toString(), "", QuestMarkerData.EntityAttachPoint.HEAD)
                    .type(spec.markerType())
                    .build();
        } else if (target instanceof MarkableObject.EntityByNpcId byNpc) {
            Entity nearestNpc = level.getEntities(player,
                            player.getBoundingBox().inflate(byNpc.searchRadius()),
                            e -> e.getPersistentData().contains("ArcQuestNpcId")
                                    && byNpc.npcId().equals(e.getPersistentData().getString("ArcQuestNpcId")))
                    .stream()
                    .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                    .orElse(null);
            if (nearestNpc == null) return null;
            UUID uuid = nearestNpc.getUUID();
            marker = new QuestMarkerData.Builder(markerId, nearestNpc.getX(), nearestNpc.getY(), nearestNpc.getZ(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(questId)
                    .followEntity(nearestNpc.getId(), uuid.toString(), byNpc.npcId(), QuestMarkerData.EntityAttachPoint.HEAD)
                    .type(spec.markerType())
                    .build();
        } else if (target instanceof MarkableObject.EntityByTypeNearest byType) {
            Entity nearest = level.getEntities(player,
                            player.getBoundingBox().inflate(byType.searchRadius()),
                            e -> e.getType() == byType.type())
                    .stream()
                    .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                    .orElse(null);
            if (nearest == null) return null;
            UUID uuid = nearest.getUUID();
            marker = new QuestMarkerData.Builder(markerId, nearest.getX(), nearest.getY(), nearest.getZ(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(questId)
                    .followEntity(nearest.getId(), uuid.toString(), "", QuestMarkerData.EntityAttachPoint.HEAD)
                    .type(spec.markerType())
                    .build();
        } else if (target instanceof MarkableObject.StructureNearest byStructure) {
            BlockPos pos = level.findNearestMapStructure(byStructure.structureTag(), player.blockPosition(), byStructure.searchRadius(), false);
            if (pos == null) return null;
            marker = new QuestMarkerData.Builder(markerId, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(questId)
                    .type(spec.markerType())
                    .build();
        }

        if (marker == null) return null;
        if (spec.maxDistance() > 0 && marker.distanceTo(player.getX(), player.getY(), player.getZ()) > spec.maxDistance()) {
            return null;
        }
        return marker;
    }

    private static boolean shouldRefresh(String markerId, int refreshTicks, int playerTick) {
        int period = Math.max(1, refreshTicks);
        long last = markerRefreshClock.getLong(markerId);
        if (!markerRefreshClock.containsKey(markerId) || (playerTick - last) >= period) {
            markerRefreshClock.put(markerId, playerTick);
            return true;
        }
        return false;
    }

    /**
     * tick 中仅做网络同步，不做持久化（持久化交由 worldSave / playerLogout）。
     */
    private static void syncIfChanged(ServerPlayer player) {
        {
            ArcQuestPlayer playerData = ArcQuestPlayerManager.get(player);
            if (playerData != null) {
            if (cap instanceof QuestCapabilityImpl impl) {
                QuestSyncCoordinator.syncIfChanged(player, playerData);
            }
            }
        }
    }

    /**
     * 有变更时才执行"快照持久化 + 客户端同步 + 清脏"。
     */
    private static void persistAndSyncIfChanged(ServerPlayer player) {
        {
            ArcQuestPlayer playerData = ArcQuestPlayerManager.get(player);
            if (playerData != null) {
            if (cap instanceof QuestCapabilityImpl impl) {
                QuestSyncCoordinator.persistAndSyncIfChanged(player, playerData);
            }
            }
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
