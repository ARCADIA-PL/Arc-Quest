package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.condition.ConditionBridge;
import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurrentRunPhaseConditionTest {
    private static final ResourceLocation ID = ResourceLocation.parse("example:parallel");

    @Test void completionBelongsToTheCurrentRunAndNeverToHistoricalFlagsOrArchives() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var first = new QuestRuntimeData(ID.toString(), "mobs", 1, 0, 0, 0);
        data.addActiveQuest(first);
        data.setFlag("mobs_done");
        assertFalse(ICondition.phaseCompleteCurrentRun(data, ID, "mobs"));
        first.completePhase("mobs");
        assertTrue(ICondition.phaseCompleteCurrentRun(data, ID, "mobs"));
        data.removeActiveQuest(ID.toString());
        assertFalse(ICondition.phaseCompleteCurrentRun(data, ID, "mobs"));
        data.addActiveQuest(new QuestRuntimeData(ID.toString(), "mobs", 1, 1, 2, 3));
        assertFalse(ICondition.phaseCompleteCurrentRun(data, ID, "mobs"));
        assertTrue(data.hasFlag("mobs_done"));
    }

    @Test void compositionRetainsRuntimeDependencySoParallelJoinsDoNotCacheFalse() {
        var condition = ICondition.phaseCompleteCurrentRun(ID, "mobs");
        assertTrue(condition.dependsOnCurrentRun());
        assertTrue(condition.and(ICondition.always()).dependsOnCurrentRun());
        assertTrue(ICondition.always().or(condition).dependsOnCurrentRun());
        assertTrue(condition.negate().dependsOnCurrentRun());
        assertFalse(condition.testClient(Set.of(ID), Set.of("mobs_done"), Map.of()));
    }

    @Test void jsonBridgeSupportsTheExplicitCurrentRunConditionAndItsHistoricalAlias() {
        for (String key : Set.of("arc_quest:quest_phase_completed_current_run", "arc_quest:quest_phase_completed")) {
            var spec = new ConditionSpec(); spec.condition = key; spec.questId = ID.toString(); spec.phaseId = "mobs";
            var condition = ConditionBridge.toQuestCondition(spec);
            assertNotNull(condition);
            assertTrue(condition.dependsOnCurrentRun());
            assertFalse(condition.testClient(Set.of(ID), Set.of(), Map.of()));
        }
    }
}
