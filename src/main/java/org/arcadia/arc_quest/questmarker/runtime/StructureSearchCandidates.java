package org.arcadia.arc_quest.questmarker.runtime;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.*;

import java.util.*;

/** Builds only the next region perimeter, rather than every square inside all 65 rings. */
final class StructureSearchCandidates implements Iterator<StructureSearchCandidates.Candidate> {
    record Candidate(ChunkPos chunk, StructurePlacement placement) {}
    private final ChunkPos origin;
    private final long seed;
    private final List<StructurePlacement> placements;
    private final List<Candidate> rings;
    private final java.util.function.Function<Sample, List<ChunkPos>> adapter;
    private final int maxRings;
    record Sample(RandomSpreadStructurePlacement placement, long seed, int x, int z) {}
    private final Set<String> seen = new HashSet<>();
    private final ArrayDeque<Candidate> ready = new ArrayDeque<>();
    private int ring;

    StructureSearchCandidates(ChunkPos origin, long seed, List<StructurePlacement> placements,
            List<Candidate> rings, int maxRings, java.util.function.Function<Sample, List<ChunkPos>> adapter) {
        this.origin = origin;
        this.seed = seed;
        this.placements = List.copyOf(placements);
        this.rings = List.copyOf(rings);
        this.maxRings = maxRings; this.adapter = adapter;
    }

    @Override public boolean hasNext() {
        while (ready.isEmpty() && ring <= maxRings) fillRing();
        return !ready.isEmpty();
    }

    @Override public Candidate next() {
        if (!hasNext()) throw new NoSuchElementException();
        return ready.removeFirst();
    }

    private void fillRing() {
        var batch = new ArrayList<Candidate>();
        if (ring == 0) batch.addAll(rings);
        for (var placement : placements) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) continue;
            if (ring == 0) add(batch, spread, 0, 0);
            else {
                for (int dx = -ring; dx <= ring; dx++) {
                    add(batch, spread, dx, -ring);
                    add(batch, spread, dx, ring);
                }
                for (int dz = -ring + 1; dz < ring; dz++) {
                    add(batch, spread, -ring, dz);
                    add(batch, spread, ring, dz);
                }
            }
        }
        batch.sort(Comparator.comparingDouble(c -> c.chunk().getMiddleBlockPosition(0)
                .distSqr(origin.getMiddleBlockPosition(0))));
        ready.addAll(batch);
        ring++;
    }

    private void add(List<Candidate> batch, RandomSpreadStructurePlacement spread, int dx, int dz) {
        int x = origin.x + dx * spread.spacing(), z = origin.z + dz * spread.spacing();
        var positions = adapter.apply(new Sample(spread, seed, x, z));
        for (var pos : positions) {
            // Different placements may legitimately choose the same chunk.
            if (seen.add(placements.indexOf(spread) + "/" + pos.toLong()))
                batch.add(new Candidate(pos, spread));
        }
    }
}
