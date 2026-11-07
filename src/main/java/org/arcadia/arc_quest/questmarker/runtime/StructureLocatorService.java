package org.arcadia.arc_quest.questmarker.runtime;

import com.google.common.collect.Iterators;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questmarker.api.StructureLocator;
import org.arcadia.arc_quest.questmarker.api.StructureLocator.*;
import org.arcadia.arc_quest.questmarker.runtime.StructureSearchCandidates.Candidate;
import org.arcadia.arc_quest.mixin.ServerChunkCacheInvoker;
import org.arcadia.arc_quest.mixin.StructureTemplateEntitiesAccessor;


import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Server-owned, bounded navigation search using the actual generated structure pieces. */
@Mod.EventBusSubscriber(modid = "arc_quest")
public final class StructureLocatorService implements StructureLocator {
    public static final StructureLocatorService INSTANCE = new StructureLocatorService();
    private static final Map<net.minecraft.resources.ResourceLocation, PlacementAdapter> ADAPTERS = new LinkedHashMap<>();
    private static final String ROOT = "ArcQuestStructureLocations";

    public static final int SEARCH_VERSION = 6;
    public static final int POSITION_VERSION = 2;
    private static final int CANDIDATES_PER_TICK = 16;
    private static final int PROBE_INTERVAL = 5;
    private static final int DEMAND_LEASE = 100;
    private static final int MAX_JOB_TICKS = 2400;
    private static final int MAX_PROBES_PER_JOB = 256;
    private record Key(UUID player, String dimension, String structure, Anchor anchor, String owner, int radius) {}
    private record ProbeKey(String dimension, String structure, Anchor anchor, long chunk) {}
    private static final Map<Key, Job> JOBS = new LinkedHashMap<>();
    // Shared verified starts avoid generating the same candidate again for another player.
    private static final Map<ProbeKey, Optional<Vec3>> VERIFIED = new LinkedHashMap<>(256, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ProbeKey, Optional<Vec3>> entry) {
            return size() > 4096;
        }
    };
    private static Probe probe;
    private static int nextProbeTick;
    private static long ticksMeasured, totalNanos, maxNanos, probesStarted, sharedHits;

    private static final class Job {
        final Key key;
        final ServerLevel world;
        final Holder<Structure> holder;
        final Iterator<Candidate> candidates;
        final int started;
        final BooleanSupplier demand;
        int requested, checked;
        Candidate current;

        Job(Key key, ServerPlayer player, ServerLevel world, Holder<Structure> holder,
                Iterator<Candidate> candidates, BooleanSupplier demand) {
            this.key = key;
            this.world = world;
            this.holder = holder;
            this.candidates = candidates;
            requested = started = world.getServer().getTickCount();
            this.demand = demand;
        }

        boolean demanded(ServerPlayer player, int now) {
            return player != null && player.serverLevel() == world && now - requested <= DEMAND_LEASE && demand.getAsBoolean();
        }
    }

    private static final class Probe {
        final ProbeKey key;
        final ServerLevel world;
        final Holder<Structure> holder;
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> chunk;

        Probe(Job job, Candidate candidate) {
            key = probeKey(job, candidate);
            world = job.world;
            holder = job.holder;
        }
    }

    private StructureLocatorService() {}

    public static String cacheKey(String dimension, String id, Anchor anchor) {
        return dimension + "/" + id + (anchor == null ? "" : anchor.cacheSuffix());
    }
    private static void requireThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Structure locator requires server thread");
    }
    private static CompoundTag cache(ServerPlayer player) { return player.getPersistentData().getCompound(ROOT); }
    @Override public void importLegacy(ServerPlayer player, ServerLevel world, ResourceLocation id, Anchor anchor, CompoundTag legacy) {
        requireThread(player); String key = cacheKey(world.dimension().location().toString(), id.toString(), anchor);
        var cache = cache(player); if (cache.contains(key) || legacy.isEmpty()) return;
        cache.put(key, legacy.copy()); player.getPersistentData().put(ROOT, cache);
    }
    @Override public Result query(ServerPlayer player, ServerLevel world, ResourceLocation structure, Anchor anchor,
                                  String owner, int radius, BooleanSupplier demand) {
        requireThread(player); Objects.requireNonNull(demand);
        if (owner == null || owner.isBlank() || owner.length() > 512 || radius < 1 || radius > 128) throw new IllegalArgumentException("Invalid locator request");
        String id = structure.toString();
        String dimension = world.dimension().location().toString();
        if (!demand.getAsBoolean()) { cancel(player,owner); return new Result(Status.CANCELLED, null, 0); }
        var cached = cache(player).getCompound(cacheKey(dimension, id, anchor));
        if (hasCurrentPosition(cached, anchor)) return new Result(Status.FOUND, new Vec3(cached.getDouble("x"), cached.getDouble("y"), cached.getDouble("z")), 0);
        // A verified destination may be displayed from another dimension, but
        // searching/generating there still requires the player to enter it.
        if (player.serverLevel() != world) { cancel(player,owner); return new Result(Status.CANCELLED, null, 0); }
        long cacheAge=world.getGameTime()-cached.getLong("attempt");
        if (cached.getInt("searchVersion") >= SEARCH_VERSION && cached.contains("attempt") && cached.getInt("radius") >= radius && cacheAge>=0 && cacheAge<600) return new Result(Status.NOT_FOUND, null, 0);
        Key key = new Key(player.getUUID(), dimension, id, anchor, owner, radius);
        var existing = JOBS.get(key);
        if (existing != null) { existing.requested = world.getServer().getTickCount(); return new Result(Status.PENDING, null, existing.checked); }
        if (JOBS.size() >= 128 || JOBS.keySet().stream().filter(k -> k.player.equals(player.getUUID())).count() >= 8) return new Result(Status.THROTTLED, null, 0);
        var holder = world.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)))
                .orElse(null);
        if (holder == null) return new Result(Status.NOT_FOUND, null, 0);
        var state = world.getChunkSource().getGeneratorState();
        var placements = state.getPlacementsForStructure(holder);
        var rings = new ArrayList<Candidate>();
        var preferred = new ArrayList<Candidate>();

        boolean legacyLocatePoint = anchor == null && cached.contains("x", Tag.TAG_ANY_NUMERIC)
                && cached.contains("z", Tag.TAG_ANY_NUMERIC) && cached.getInt("positionVersion") < POSITION_VERSION;
        var origin = legacyLocatePoint
                ? BlockPos.containing(cached.getDouble("x"), 0, cached.getDouble("z")) : player.blockPosition();
        for (var placement : placements) {
            if (legacyLocatePoint) {
                // The old cache is placement.getLocatePos(startChunk) + half a block.
                // Reverify that same start before searching near it, even if the player moved.
                var offset = placement.getLocatePos(new ChunkPos(0, 0));
                preferred.add(new Candidate(new ChunkPos(origin.subtract(offset)), placement));
            }
            if (placement instanceof ConcentricRingsStructurePlacement concentric) {
                var positions = state.getRingPositionsFor(concentric);
                if (positions != null)
                    for (var pos : positions) rings.add(new Candidate(pos, placement));
            } else if (ResourceLocation.parse("cataclysm_dimension:spawn_pos_placement")
                    .equals(BuiltInRegistries.STRUCTURE_PLACEMENT.getKey(placement.type()))) {
                // Official SpawnPosPlacement accepts exactly chunk (0, 0). Still verify its
                // real StructureStart asynchronously; a registered ID alone is no evidence.
                rings.add(new Candidate(new ChunkPos(0, 0), placement));
            }
        }
        JOBS.put(key, new Job(key, player, world, holder, Iterators.concat(preferred.iterator(), new StructureSearchCandidates(
                new ChunkPos(origin), state.getLevelSeed(), placements, rings,
                radius, StructureLocatorService::placementCandidates)), demand));
        return new Result(Status.PENDING, null, 0);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty() && probe == null) return;
        long start = System.nanoTime();
        try { step(); }
        finally {
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;
            maxNanos = Math.max(maxNanos, elapsed);
            ticksMeasured++;
        }
    }

    private static void step() {
        finishProbe();
        if (JOBS.isEmpty()) return;
        var iterator = JOBS.entrySet().iterator();
        var entry = iterator.next();
        var job = entry.getValue();
        iterator.remove();
        var player = job.world.getServer().getPlayerList().getPlayer(job.key.player);
        int now = job.world.getServer().getTickCount();
        if (!job.demanded(player, now)) return;
        if (now - job.started >= MAX_JOB_TICKS) {
            store(player, job, null);
            return;
        }
        long deadline = System.nanoTime() + 2_000_000L;
        for (int checked = 0; checked < CANDIDATES_PER_TICK && System.nanoTime() < deadline; checked++) {
            if (job.current == null) {
                if (!job.candidates.hasNext()) { store(player, job, null); return; }
                job.current = job.candidates.next();
            }
            var candidate = job.current;
            var key = probeKey(job, candidate);
            var result = VERIFIED.get(key);
            if (result != null) {
                sharedHits++;
                job.current = null;
                if (result.isPresent()) { store(player, job, result.get()); return; }
                continue;
            }
            var pos = candidate.chunk();
            if (!candidate.placement().isStructureChunk(job.world.getChunkSource().getGeneratorState(), pos.x, pos.z)) {
                job.current = null;
                continue;
            }
            // At most one chunk generation globally, even in multiplayer.
            if (probe != null || now < nextProbeTick) break;
            if (job.checked >= MAX_PROBES_PER_JOB) { store(player, job, null); return; }
            nextProbeTick = now + PROBE_INTERVAL;
            job.checked++;
            probesStarted++;
            probe = new Probe(job, candidate);
            var loaded = job.world.getChunkSource().getChunkNow(pos.x, pos.z);
            if (loaded != null) acceptChunk(loaded);
            else {
                // Public getChunkFuture calls managedBlock on the server thread. Invoke its
                // scheduling half only, retaining vanilla tickets, status and generation logic.
                probe.chunk = ((ServerChunkCacheInvoker) job.world.getChunkSource())
                        .arcq$scheduleChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true);
            }
            break;
        }
        JOBS.put(entry.getKey(), job);
    }

    private static ProbeKey probeKey(Job job, Candidate candidate) {
        return new ProbeKey(job.key.dimension, job.key.structure, job.key.anchor, candidate.chunk().toLong());
    }

    private static void finishProbe() {
        if (probe == null) return;
        try {
            if (probe.chunk != null && probe.chunk.isDone()) {
                var value = probe.chunk.getNow(null);
                probe.chunk = null;
                acceptChunk(value == null ? null : value.left().orElse(null));
            }
        } catch (CompletionException exception) {
            org.slf4j.LoggerFactory.getLogger("ArcQuestStructureLocator").warn("Navigation probe failed for {}", probe.key, exception);
            VERIFIED.put(probe.key, Optional.empty());
            probe = null;
        }
    }

    private static void acceptChunk(ChunkAccess access) {
        var start = access == null ? null : access.getStartForStructure(probe.holder.value());
        if (start == null || !start.isValid()) {
            VERIFIED.put(probe.key, Optional.empty());
            probe = null;
            return;
        }
        if (probe.key.anchor != null) {
            var target = probe.key.anchor instanceof BlockAnchor block
                    ? locateBlockAnchor(probe.world, start, block)
                    : locateEntityAnchor(probe.world, start, (EntityAnchor) probe.key.anchor);
            VERIFIED.put(probe.key, Optional.ofNullable(target)
                    .map(pos -> new Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5)));
            probe = null;
            return;
        }
        VERIFIED.put(probe.key, Optional.ofNullable(locateStructureCenter(start)));
        probe = null;
    }

    /** Piece bounds include their generated offset/rotation, without terrain-adaptation padding. */
    public static Vec3 locateStructureCenter(StructureStart start) {
        if (start == null || !start.isValid()) return null;
        return BoundingBox.encapsulatingBoxes(start.getPieces().stream()
                .map(StructurePiece::getBoundingBox).toList())
                .map(box -> new Vec3(((double) box.minX() + box.maxX() + 1) * .5,
                        ((double) box.minY() + box.maxY() + 1) * .5,
                        ((double) box.minZ() + box.maxZ() + 1) * .5))
                .orElse(null);
    }

    static boolean hasCurrentPosition(CompoundTag entry, Anchor anchor) {
        return Double.isFinite(entry.getDouble("x")) && Double.isFinite(entry.getDouble("y")) && Double.isFinite(entry.getDouble("z")) && entry.contains("x", Tag.TAG_ANY_NUMERIC) && entry.contains("y", Tag.TAG_ANY_NUMERIC)
                && entry.contains("z", Tag.TAG_ANY_NUMERIC)
                && (anchor == null ? entry.getInt("positionVersion") >= POSITION_VERSION
                        : entry.getInt("surfaceVersion") >= 1);
    }

    /** Resolve the authored block through the real piece origin/rotation; never load its chunk. */
    public static BlockPos locateBlockAnchor(ServerLevel world, StructureStart start, BlockAnchor anchor) {
        var blockId = ResourceLocation.parse(anchor.block());
        if (!ForgeRegistries.BLOCKS.containsKey(blockId)) return null;
        var block = ForgeRegistries.BLOCKS.getValue(blockId);
        var templateId = ResourceLocation.parse(anchor.template());
        var context = StructurePieceSerializationContext.fromLevel(world);
        for (var piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)) continue;
            var element = pool.createTag(context).getCompound("pool_element");
            if (!templateId.toString().equals(element.getString("location"))) continue;
            var template = world.getStructureManager().get(templateId).orElse(null);
            if (template == null) continue;
            var settings = new StructurePlaceSettings().setRotation(pool.getRotation());
            var blocks = template.filterBlocks(pool.getPosition(), settings, block);
            if (!blocks.isEmpty()) return blocks.get(0).pos().above();
        }
        return null;
    }

    /** Transform the authored NPC through its actual generated piece; no terrain-height fallback. */
    public static BlockPos locateEntityAnchor(ServerLevel world, StructureStart start, EntityAnchor anchor) {
        var context = StructurePieceSerializationContext.fromLevel(world);
        for (var piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)) continue;
            var element = pool.createTag(context).getCompound("pool_element");
            if (!anchor.template().equals(element.getString("location"))) continue;
            var template = world.getStructureManager().get(ResourceLocation.parse(anchor.template())).orElse(null);
            if (template == null) continue;
            for (var entry : ((StructureTemplateEntitiesAccessor) template).arcq$entities()) {
                if (!anchor.entity().equals(entry.nbt.getString("id"))) continue;
                var transformed = StructureTemplate.transform(entry.pos, Mirror.NONE, pool.getRotation(), BlockPos.ZERO)
                        .add(Vec3.atLowerCornerOf(pool.getPosition()));
                return BlockPos.containing(transformed).above(2);
            }
        }
        return null;
    }

    private static void store(ServerPlayer player, Job job, Vec3 pos) {
        var cache = cache(player);
        String key = cacheKey(job.key.dimension, job.key.structure, job.key.anchor);
        var value = cache.getCompound(key);
        value.putLong("attempt", job.world.getGameTime());
        value.putInt("searchVersion", SEARCH_VERSION);
        value.putInt("radius", job.key.radius);
        if (pos != null) {
            value.putDouble("x", pos.x);
            value.putDouble("z", pos.z);
            value.putDouble("y", pos.y);
            value.putInt("surfaceVersion", 1);
            value.putInt("positionVersion", POSITION_VERSION);
        } else {
            value.remove("x");
            value.remove("y");
            value.remove("z");
        }
        cache.put(key, value);
        player.getPersistentData().put(ROOT, cache);
    }

    @Override public Metrics metrics() {
        return new Metrics(JOBS.size(), ticksMeasured, ticksMeasured == 0 ? 0 : totalNanos / 1e6 / ticksMeasured,
                maxNanos / 1e6, probesStarted, sharedHits);
    }
    @Override public void cancel(ServerPlayer player, String owner) {
        requireThread(player); JOBS.keySet().removeIf(k -> k.player.equals(player.getUUID()) && k.owner.equals(owner));
    }
    @Override public void registerPlacementAdapter(ResourceLocation id, PlacementAdapter adapter) {
        if (ADAPTERS.putIfAbsent(id, Objects.requireNonNull(adapter)) != null) throw new IllegalArgumentException("Duplicate placement adapter " + id);
    }
    private static List<ChunkPos> placementCandidates(StructureSearchCandidates.Sample sample) {
        var result = new LinkedHashSet<ChunkPos>();
        result.add(sample.placement().getPotentialStructureChunk(sample.seed(), sample.x(), sample.z()));
        for (var adapter : ADAPTERS.values()) result.addAll(adapter.candidates(sample.placement(), sample.seed(), sample.x(), sample.z()));
        return List.copyOf(result);
    }
    @Override public Result queryTag(ServerPlayer player, ServerLevel world, net.minecraft.tags.TagKey<Structure> tag, String owner, int radius) {
        requireThread(player);
        var holders = world.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tag).orElse(null);
        if (holders == null) return new Result(Status.NOT_FOUND, null, 0);
        Result waiting = null, best = null;
        for (var holder : holders) {
            var id = holder.unwrapKey().orElseThrow().location();
            var result = query(player, world, id, null, owner, Math.min(128, Math.max(1, radius)), () -> true);
            if (result.found() && (best == null || result.position().distanceToSqr(player.position()) < best.position().distanceToSqr(player.position()))) best = result;
            if (result.status() == Status.PENDING || result.status() == Status.THROTTLED) waiting = result;
        }
        // Do not freeze the first tag member found as if it were the nearest of the entire tag.
        return waiting != null ? waiting : best != null ? best : new Result(Status.NOT_FOUND, null, 0);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        JOBS.keySet().removeIf(key -> key.player.equals(event.getEntity().getUUID()));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        JOBS.clear();
        VERIFIED.clear();
        probe = null;
        nextProbeTick = 0;
        ticksMeasured = totalNanos = maxNanos = probesStarted = sharedHits = 0;
    }
}
