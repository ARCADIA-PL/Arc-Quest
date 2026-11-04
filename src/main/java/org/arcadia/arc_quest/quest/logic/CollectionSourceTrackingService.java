package org.arcadia.arc_quest.quest.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.sync.CollectionContentDisclosure;
import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionSourceSetDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestTextContext;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2STrackCollectionSourcePacket;
import org.arcadia.arc_quest.quest.registry.CollectionSourceNavigationRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

/** One optional personal route; independent from incomplete task bindings. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class CollectionSourceTrackingService {
    public static final String MARKER_ID = "arc_quest:collection_source";
    private static final Map<ServerPlayer, Route> ROUTES = new WeakHashMap<>();

    private record Route(C2STrackCollectionSourcePacket request, long expiresAt) {}

    private CollectionSourceTrackingService() {}

    public static boolean request(ServerPlayer player, C2STrackCollectionSourcePacket request) {
        if (!PlayerSessionEpochManager.matches(player, request.playerEpoch())) return false;
        if (request.clear()) {
            clear(player);
            return true;
        }
        var entry = authorize(player, request);
        if (entry == null || CollectionSourceNavigationRegistry.provider(entry) == null)
            return false;
        clear(player);
        ROUTES.put(player, new Route(request, player.server.getTickCount() + 1200L));
        player.displayClientMessage(
                Component.translatable("arc_quest.collection.route.searching"), true);
        update(player, ROUTES.get(player));
        return true;
    }

    @Nullable
    public static CollectionEntryDefinition authorize(
            ServerPlayer player, C2STrackCollectionSourcePacket request) {
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return null;
        var run = data.getActiveQuest(request.questId());
        if (run == null
                || !run.hasCollectionData()
                || !run.getCollectionData().getRunId().equals(request.runId()))
            run = data.getCollectionArchives().get(request.questId(), request.runId());
        if (run == null
                || !run.hasCollectionData()
                || !run.getCollectionData().getRunId().equals(request.runId())) return null;
        var definition = CollectionRunAccess.resolve(player.server, run);
        var phase = definition == null ? null : definition.getPhase(request.phaseId());
        if (phase == null
                || !phase.hasCollectionSheet()
                || !(run.isPhaseActive(request.phaseId())
                        || run.isPhaseCompleted(request.phaseId())
                        || run.isPhasePendingManualAdvance(request.phaseId()))) return null;
        var row =
                CollectionProgressProjector.project(
                                definition, phase, run, data.getCollectionRecords())
                        .binding(request.bindingId());
        if (row == null || !row.visible() || !row.revealed()) return null;
        var selected = definition.getCollectionConfig().getEntry(row.entryId());
        if (request.relationId().isEmpty()) return selected;
        var relationId = ResourceLocation.tryParse(request.relationId());
        if (relationId == null) return null;
        for (var sources : presentationConfig(definition).getSourceSets())
            for (var relation : sources.relations()) {
                if (!relation.relationId().equals(relationId)
                        || !relation.targetEntryId().equals(selected.getEntryId())) continue;
                var presentationEntry =
                        presentationConfig(definition).getEntry(selected.getEntryId());
                if (presentationEntry == null
                        || !relationDisclosed(
                                relation, presentationEntry, data.getCollectionRecords())) continue;
                return authorizedEntry(player, data, relation.sourceEntryId());
            }
        return null;
    }

    /**
     * The same live presentation update accepted by recipient synchronization; gameplay remains
     * pinned.
     */
    private static CollectionQuestConfig presentationConfig(QuestDefinition pinned) {
        var previous = pinned.getCollectionConfig();
        var current = QuestRegistry.getServerDefinition(pinned.getId());
        if (current != null
                && current.getCollectionConfig() != null
                && previous.getAtlasId() != null
                && Objects.equals(
                        previous.getAtlasId(), current.getCollectionConfig().getAtlasId()))
            return current.getCollectionConfig();
        return previous;
    }

    static boolean relationDisclosed(
            CollectionSourceSetDefinition.Relation relation,
            CollectionEntryDefinition target,
            CollectionRecordState records) {
        if (!CollectionContentDisclosure.entryRevealed(target, records)) return false;
        return relation.variantIds().isEmpty()
                || target.getPresentation().variants().stream()
                        .anyMatch(
                                variant ->
                                        relation.variantIds().contains(variant.variantId())
                                                && CollectionContentDisclosure.variantRevealed(
                                                        target, variant, records));
    }

    @Nullable
    private static CollectionEntryDefinition authorizedEntry(
            ServerPlayer player, ArcQuestPlayer data, ResourceLocation entryId) {
        var runs = new ArrayList<>(data.getAllActiveQuests().values());
        for (var id : data.getCompletedQuests())
            runs.addAll(data.getCollectionArchives().allRuns(id));
        for (var id : data.getFailedQuests()) runs.addAll(data.getCollectionArchives().allRuns(id));
        for (QuestRuntimeData run : runs) {
            if (!run.hasCollectionData()) continue;
            var definition = CollectionRunAccess.resolve(player.server, run);
            if (definition == null
                    || definition.getCollectionConfig() == null
                    || definition.getCollectionConfig().getEntry(entryId) == null) continue;
            for (var phase : definition.getAllPhases()) {
                if (!phase.hasCollectionSheet()
                        || !(run.isPhaseActive(phase.getPhaseId())
                                || run.isPhaseCompleted(phase.getPhaseId())
                                || run.isPhasePendingManualAdvance(phase.getPhaseId()))) continue;
                var rows =
                        CollectionProgressProjector.project(
                                definition, phase, run, data.getCollectionRecords());
                for (var row : rows.bindings())
                    if (row.entryId().equals(entryId) && row.visible() && row.revealed())
                        return definition.getCollectionConfig().getEntry(entryId);
            }
        }
        return null;
    }

    private static void update(ServerPlayer player, Route route) {
        if (route == null) return;
        var entry = authorize(player, route.request());
        var provider = entry == null ? null : CollectionSourceNavigationRegistry.provider(entry);
        if (provider == null
                || !PlayerSessionEpochManager.matches(player, route.request().playerEpoch())) {
            clear(player);
            return;
        }
        try {
            var destination =
                    provider.resolve(new CollectionSourceNavigationRegistry.Context(player, entry));
            if (destination == null) {
                if (player.server.getTickCount() > route.expiresAt()) {
                    clear(player);
                    player.displayClientMessage(
                            Component.translatable("arc_quest.collection.route.unavailable"), true);
                }
                return;
            }
            var pos = destination.position();
            var label = entry.getDisplayQuestText().resolve(player, QuestTextContext.empty());
            var marker =
                    new QuestMarkerData.Builder(MARKER_ID, pos.x, pos.y, pos.z, label.getString())
                            .dimension(destination.dimension().toString())
                            .showDistance(true)
                            .allowOffscreenArrow(true)
                            .persistent(false)
                            .color(0xFFB9A1E8)
                            .build();
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return;
            var old = data.getAllMarkers().get(MARKER_ID);
            if (!marker.equals(old)) {
                data.upsertMarker(marker);
                ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);
            }
        } catch (RuntimeException failure) {
            clear(player);
            player.displayClientMessage(
                    Component.translatable("arc_quest.collection.route.unavailable"), true);
        }
    }

    public static void clear(ServerPlayer player) {
        ROUTES.remove(player);
        var data = ArcQuestPlayerManager.get(player);
        if (data != null && data.getAllMarkers().containsKey(MARKER_ID)) {
            data.removeMarker(MARKER_ID);
            ArcQuestNetwork.syncMarkerDeltaRemove(player, MARKER_ID);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0) update(player, ROUTES.get(player));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ROUTES.clear();
    }
}
