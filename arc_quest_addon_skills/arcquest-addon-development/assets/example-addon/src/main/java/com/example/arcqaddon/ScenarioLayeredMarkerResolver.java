package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.ResolvedMarkTarget;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class ScenarioLayeredMarkerResolver {
    public static final String RESOLVER_ID = "example_arcq_addon:story_actor";
    private static final String ACTOR_KEY = "ExampleArcQActorId";
    private static final int MAX_CACHE_ENTRIES = 1024;
    private static final Map<CacheKey, CachedTarget> LAST_KNOWN_TARGETS =
            new ConcurrentHashMap<>();

    private ScenarioLayeredMarkerResolver() {
    }

    public static void register(ArcQuestRegistrationEvent.Quest event) {
        ArcQuestAPI.registerMarkTargetResolver(RESOLVER_ID, (player, level, target) -> {
            String actorId = target.args().get("actor_id");
            if (actorId == null || actorId.isBlank() || actorId.length() > 64) {
                return null;
            }
            int radius = parseRadius(target.args().get("radius"));
            CacheKey key = new CacheKey(player.getUUID(), actorId);
            ArmorStand actor = level.getEntitiesOfClass(
                            ArmorStand.class,
                            player.getBoundingBox().inflate(radius),
                            entity -> entity.isAlive()
                                    && actorId.equals(entity.getPersistentData().getString(ACTOR_KEY)))
                    .stream()
                    .min(Comparator.comparingDouble(player::distanceToSqr))
                    .orElse(null);
            if (actor != null) {
                CachedTarget cached = new CachedTarget(
                        actor.getX(), actor.getY(), actor.getZ(), level.dimension().location());
                remember(key, cached);
                return new ResolvedMarkTarget(
                        cached.x(), cached.y(), cached.z(), cached.dimension().toString(),
                        actor.getId(), actor.getUUID().toString(), actorId,
                        QuestMarkerData.EntityAttachPoint.HEAD);
            }
            CachedTarget cached = LAST_KNOWN_TARGETS.get(key);
            return cached == null
                    ? null
                    : ResolvedMarkTarget.position(
                            cached.x(), cached.y(), cached.z(), cached.dimension().toString());
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        LAST_KNOWN_TARGETS.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LAST_KNOWN_TARGETS.clear();
    }

    private static void remember(CacheKey key, CachedTarget target) {
        if (LAST_KNOWN_TARGETS.size() >= MAX_CACHE_ENTRIES
                && !LAST_KNOWN_TARGETS.containsKey(key)) {
            LAST_KNOWN_TARGETS.clear();
        }
        LAST_KNOWN_TARGETS.put(key, target);
    }

    private static int parseRadius(String value) {
        if (value == null || value.isBlank()) {
            return 64;
        }
        try {
            return Math.max(8, Math.min(128, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return 64;
        }
    }

    private record CacheKey(UUID playerId, String actorId) {
    }

    private record CachedTarget(double x, double y, double z, ResourceLocation dimension) {
    }
}
