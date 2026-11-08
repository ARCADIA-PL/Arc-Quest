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

    private record Route(C2STrackCollectionSourcePacket request, long expiresAt,
                         CollectionEntryDefinition entry, CollectionSourceNavigationRegistry.Destination destination) {}

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
        ROUTES.put(player, new Route(request, player.server.getTickCount() + 1200L, entry, null));
        try {
            CollectionSourceNavigationRegistry.provider(entry).begin(new CollectionSourceNavigationRegistry.Context(player, entry));
        } catch (RuntimeException failure) {
            clear(player);
            player.displayClientMessage(Component.translatable("arc_quest.collection.route.unavailable"), true);
            return false;
        }
        player.displayClientMessage(
                Component.translatable("arc_quest.collection.route.searching"), true);
        update(player, ROUTES.get(player));
        return true;
    }

    @Nullable
    public static CollectionEntryDefinition authorize(
            ServerPlayer player, C2STrackCollectionSourcePacket request) {
        var view = CollectionViewService.project(player);
        var selected = view == null ? null : view.resolve(new org.arcadia.arc_quest.quest.api.collection.CollectionViews.EntryRef(
                new org.arcadia.arc_quest.quest.api.collection.CollectionViews.RunRef(request.questId(), request.runId()),
                request.phaseId(), request.bindingId()), "");
        if (selected == null) return null;
        if (request.relationId().isEmpty()) return selected.entry();
        var relationId = ResourceLocation.tryParse(request.relationId());
        if (relationId == null) return null;
        return view.relations().stream().filter(r -> r.target().view().ref().equals(selected.view().ref())
                && r.definition().relationId().equals(relationId))
                .map(r -> r.source().entry()).findFirst().orElse(null);
    }

    /** Compatibility helper for existing extensions; all production paths use the unified view. */
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
                    route.destination() != null ? route.destination()
                            : provider.resolve(new CollectionSourceNavigationRegistry.Context(player, entry));
            if (destination == null) {
                if (player.server.getTickCount() > route.expiresAt()) {
                    clear(player);
                    player.displayClientMessage(
                            Component.translatable("arc_quest.collection.route.unavailable"), true);
                }
                return;
            }
            if (route.destination() == null)
                ROUTES.put(player, new Route(route.request(), route.expiresAt(), entry, destination));
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
        var route = ROUTES.remove(player);
        if (route != null) {
            var provider = CollectionSourceNavigationRegistry.provider(route.entry());
            if (provider != null) try {
                provider.cancel(new CollectionSourceNavigationRegistry.Context(player, route.entry()));
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(CollectionSourceTrackingService.class)
                        .warn("Failed to cancel collection route for {}", route.entry().getEntryId(), failure);
            }
        }
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
                && player.tickCount % 20 == 0) {
            var route = ROUTES.get(player);
            if (route != null && (route.destination() == null || player.tickCount % 100 == 0)) update(player, route);
        }
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
