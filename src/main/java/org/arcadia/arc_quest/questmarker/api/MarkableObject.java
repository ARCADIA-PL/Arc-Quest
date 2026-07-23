package org.arcadia.arc_quest.questmarker.api;

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

public sealed interface MarkableObject permits MarkableObject.Pos, MarkableObject.DimensionPos, MarkableObject.BlockPos, MarkableObject.EntityByUuid, MarkableObject.EntityByTypeNearest, MarkableObject.EntityByNpcId, MarkableObject.StructureNearest, MarkableObject.CustomResolver {

    record Pos(int x, int y, int z) implements MarkableObject {
    }

    record DimensionPos(ResourceKey<Level> dimension, int x, int y, int z) implements MarkableObject {
        public DimensionPos {
            Objects.requireNonNull(dimension, "dimension");
        }
    }

    record BlockPos(net.minecraft.core.BlockPos pos) implements MarkableObject {
        public BlockPos {
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

    record StructureNearest(TagKey<Structure> structureTag, int searchRadius) implements MarkableObject {
        public StructureNearest {
            Objects.requireNonNull(structureTag, "structureTag");
            if (searchRadius <= 0) throw new IllegalArgumentException("searchRadius must be > 0");
        }

        public static StructureNearest of(ResourceLocation structureId, int searchRadius) {
            return new StructureNearest(TagKey.create(Registries.STRUCTURE, structureId), searchRadius);
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
