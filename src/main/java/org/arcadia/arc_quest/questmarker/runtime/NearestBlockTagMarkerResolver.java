package org.arcadia.arc_quest.questmarker.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.ResolvedMarkTarget;

import java.util.Map;

final class NearestBlockTagMarkerResolver {

    private static final int DEFAULT_HORIZONTAL_RADIUS = 48;
    private static final int MAX_HORIZONTAL_RADIUS = 64;
    private static final int DEFAULT_VERTICAL_RADIUS = 16;
    private static final int MAX_VERTICAL_RADIUS = 32;

    private NearestBlockTagMarkerResolver() {
    }

    static ResolvedMarkTarget resolve(ServerPlayer player,
                                      ServerLevel level,
                                      MarkableObject.CustomResolver target) {
        Map<String, String> args = target.args();
        ResourceLocation tagId = ResourceLocation.tryParse(args.getOrDefault("tag", ""));
        if (tagId == null) return null;

        int horizontalRadius = boundedInt(args, "horizontal_radius",
                DEFAULT_HORIZONTAL_RADIUS, 1, MAX_HORIZONTAL_RADIUS);
        int verticalRadius = boundedInt(args, "vertical_radius",
                DEFAULT_VERTICAL_RADIUS, 1, MAX_VERTICAL_RADIUS);
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, tagId);

        return BlockPos.findClosestMatch(player.blockPosition(), horizontalRadius, verticalRadius,
                        pos -> level.hasChunkAt(pos) && level.getBlockState(pos).is(blockTag))
                .map(pos -> ResolvedMarkTarget.position(
                        pos.getX() + 0.5,
                        pos.getY() + 1.0,
                        pos.getZ() + 0.5,
                        level.dimension().location().toString()))
                .orElse(null);
    }

    private static int boundedInt(Map<String, String> args, String key,
                                  int fallback, int minimum, int maximum) {
        try {
            return Mth.clamp(Integer.parseInt(args.getOrDefault(key, Integer.toString(fallback))),
                    minimum, maximum);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
