package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.ResolvedMarkTarget;

import java.util.Comparator;

public final class ExampleMarkerContent {
    public static final String RESOLVER_ID = "example_arcq_addon:nearest_story_anchor";

    private ExampleMarkerContent() {
    }

    public static void register(ArcQuestRegistrationEvent.Quest event) {
        ArcQuestAPI.registerMarkTargetResolver(RESOLVER_ID, (player, level, target) -> {
            int radius = parseRadius(target.args().get("radius"));
            ArmorStand nearest = level.getEntitiesOfClass(
                            ArmorStand.class,
                            player.getBoundingBox().inflate(radius),
                            ArmorStand::isAlive)
                    .stream()
                    .min(Comparator.comparingDouble(player::distanceToSqr))
                    .orElse(null);
            if (nearest == null) {
                return null;
            }
            ResourceLocation dimension = level.dimension().location();
            return new ResolvedMarkTarget(
                    nearest.getX(),
                    nearest.getY(),
                    nearest.getZ(),
                    dimension.toString(),
                    nearest.getId(),
                    nearest.getUUID().toString(),
                    "",
                    QuestMarkerData.EntityAttachPoint.HEAD
            );
        });
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
}
