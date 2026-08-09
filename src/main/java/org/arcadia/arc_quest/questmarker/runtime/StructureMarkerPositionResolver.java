package org.arcadia.arc_quest.questmarker.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;

/** Applies optional vertical placement rules to located structure markers. */
public final class StructureMarkerPositionResolver {

    private StructureMarkerPositionResolver() {
    }

    public static BlockPos adjustY(ServerLevel level, BlockPos located,
                                   MarkableObject.StructureNearest target) {
        if (located == null) return null;
        if (target.y() != null) return located.atY(target.y());
        if (!target.useSurfaceY()) return located;
        return findSurface(level, located.getX(), located.getZ(), located);
    }

    public static BlockPos atSurface(ServerLevel level, BlockPos located) {
        if (located == null) return null;
        return findSurface(level, located.getX(), located.getZ(), located);
    }

    private static BlockPos findSurface(ServerLevel level, int x, int z, BlockPos fallback) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(
                x, level.getMaxBuildHeight() - 1, z);
        int minY = level.getMinBuildHeight();
        for (int y = cursor.getY(); y >= minY; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            MapColor color = state.getMapColor(level, cursor);
            if (state.getLightBlock(level, cursor) >= 15
                    || color == MapColor.WATER
                    || color == MapColor.ICE) {
                return cursor.above().immutable();
            }
        }
        return fallback;
    }
}
