package org.arcadia.arc_quest.questmarker.runtime;
import java.util.*;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StructureSearchCandidatesTest {
    @Test
    void navigationDoesNotBuildDistantCandidatesBeforeTheyAreNeeded() {
        int[] samples = {0};
        var placement = new RandomSpreadStructurePlacement(Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, 7211184,
                Optional.empty(), 24, 20, RandomSpreadType.LINEAR) {
            @Override public ChunkPos getPotentialStructureChunk(long seed, int x, int z) {
                samples[0]++;
                return super.getPotentialStructureChunk(seed, x, z);
            }
        };
        var candidates = new StructureSearchCandidates(new ChunkPos(-1, 0), 7L,
                List.of(placement), List.of(), 64, sample -> List.of(sample.placement().getPotentialStructureChunk(sample.seed(), sample.x(), sample.z())));
        assertEquals(0, samples[0]);
        candidates.next();
        assertEquals(1, samples[0], "Only the origin region is needed for the first candidate");
        var unique = new java.util.HashSet<ChunkPos>();
        for (int i = 0; i < 8; i++) assertTrue(unique.add(candidates.next().chunk()));
        assertEquals(9, samples[0], "The next perimeter has exactly eight regions");
    }
}
