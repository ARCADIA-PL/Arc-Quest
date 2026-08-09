package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public sealed interface MarkableObject permits MarkableObject.Pos, MarkableObject.DimensionPos, MarkableObject.BlockPosition, MarkableObject.EntityByUuid, MarkableObject.EntityByTypeNearest, MarkableObject.EntityByNpcId, MarkableObject.StructureNearest, MarkableObject.CustomResolver {

    record Pos(int x, int y, int z) implements MarkableObject {
    }

    record DimensionPos(ResourceKey<Level> dimension, int x, int y, int z) implements MarkableObject {
        public DimensionPos {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    record BlockPosition(BlockPos pos) implements MarkableObject {
        public BlockPosition {
            Objects.requireNonNull(pos, "pos");
        }
    }

    record EntityByUuid(UUID uuid) implements MarkableObject {
        public EntityByUuid {
            Objects.requireNonNull(uuid, "uuid");
        }
    }

    record EntityByTypeNearest(EntityType<?> type, int searchRadius) implements MarkableObject {
        public EntityByTypeNearest {
            Objects.requireNonNull(type, "type");
            if (searchRadius <= 0) throw new IllegalArgumentException("searchRadius must be > 0");
        }
    }

    record EntityByNpcId(String npcId, int searchRadius) implements MarkableObject {
        public EntityByNpcId {
            if (npcId == null || npcId.isBlank()) throw new IllegalArgumentException("npcId cannot be blank");
            if (searchRadius <= 0) throw new IllegalArgumentException("searchRadius must be > 0");
        }
    }

    /**
     * Locates the nearest structure and optionally replaces its located Y coordinate.
     * The two-argument constructor preserves the original located Y behavior.
     */
    record StructureNearest(TagKey<Structure> structureTag, int searchRadius,
                            Integer y, boolean useSurfaceY) implements MarkableObject {
        public StructureNearest(TagKey<Structure> structureTag, int searchRadius) {
            this(structureTag, searchRadius, null, false);
        }

        public StructureNearest(TagKey<Structure> structureTag, int searchRadius, int y) {
            this(structureTag, searchRadius, y, false);
        }

        public StructureNearest(TagKey<Structure> structureTag, int searchRadius, boolean useSurfaceY) {
            this(structureTag, searchRadius, null, useSurfaceY);
        }

        public StructureNearest {
            Objects.requireNonNull(structureTag, "structureTag");
            if (searchRadius <= 0) throw new IllegalArgumentException("searchRadius must be > 0");
            if (y != null && useSurfaceY) {
                throw new IllegalArgumentException("y and useSurfaceY cannot both be set");
            }
        }

        public static StructureNearest of(ResourceLocation structureId, int searchRadius) {
            return new StructureNearest(TagKey.create(Registries.STRUCTURE, structureId), searchRadius);
        }

        public static StructureNearest of(ResourceLocation structureId, int searchRadius, int y) {
            return new StructureNearest(TagKey.create(Registries.STRUCTURE, structureId), searchRadius, y);
        }

        public static StructureNearest of(ResourceLocation structureId, int searchRadius, boolean useSurfaceY) {
            return new StructureNearest(TagKey.create(Registries.STRUCTURE, structureId), searchRadius, useSurfaceY);
        }

        public static StructureNearest atSurface(ResourceLocation structureId, int searchRadius) {
            return of(structureId, searchRadius, true);
        }
    }

    record CustomResolver(String resolverId, Map<String, String> args) implements MarkableObject {
        public CustomResolver {
            if (resolverId == null || resolverId.isBlank())
                throw new IllegalArgumentException("resolverId cannot be blank");
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }
}
