package org.arcadia.arc_quest.questmarker.api;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.Vec3;

/** Server-thread locator. Polling renews a 100-tick demand lease; it never waits for generation.
 * A soft scan budget bounds scheduling, not the cost of an individual vanilla generation task.
 * Cached coordinates are server-only vanilla player data; search state is transient.
 */
public interface StructureLocator {
    int SEARCH_VERSION = 6, POSITION_VERSION = 2;
    enum Status { PENDING, FOUND, NOT_FOUND, CANCELLED, THROTTLED }
    sealed interface Anchor permits BlockAnchor, EntityAnchor { String template(); String cacheSuffix(); }
    record BlockAnchor(String template, String block) implements Anchor {
        public BlockAnchor { ResourceLocation.parse(template); ResourceLocation.parse(block); }
        public String cacheSuffix() { return "/block/" + template + "/" + block; }
    }
    record EntityAnchor(String template, String entity) implements Anchor {
        public EntityAnchor { ResourceLocation.parse(template); ResourceLocation.parse(entity); }
        public String cacheSuffix() { return "/entity/" + template + "/" + entity; }
    }
    record Result(Status status, Vec3 position, int probes) {
        public Result { if (status == Status.FOUND && (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))) throw new IllegalArgumentException("Invalid location"); }
        public boolean found() { return status == Status.FOUND; }
    }
    record Metrics(int queued, long ticks, double averageMillis, double maxMillis, long probes, long sharedHits) {}
    @FunctionalInterface interface PlacementAdapter {
        List<ChunkPos> candidates(RandomSpreadStructurePlacement placement, long seed, int x, int z);
    }
    Result query(ServerPlayer player, ServerLevel world, ResourceLocation structure, Anchor anchor,
                 String owner, int radius, BooleanSupplier demanded);
    Result queryTag(ServerPlayer player, ServerLevel world, TagKey<Structure> tag, String owner, int radius);
    void cancel(ServerPlayer player, String owner);
    /** Import a historical coordinate once. Outdated positions become a preferred start to reverify. */
    void importLegacy(ServerPlayer player, ServerLevel world, ResourceLocation structure, Anchor anchor, CompoundTag legacy);
    void registerPlacementAdapter(ResourceLocation id, PlacementAdapter adapter);
    Metrics metrics();
}
