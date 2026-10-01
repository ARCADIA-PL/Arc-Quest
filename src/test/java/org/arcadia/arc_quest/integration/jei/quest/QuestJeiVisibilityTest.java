package org.arcadia.arc_quest.integration.jei.quest;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestJeiVisibilityTest {
    @Test
    void revealsReachedParallelPhasesButNotFutureOrAlternativeBranches() {
        var first = PhaseBuilder.create("first").objective(ObjectiveBuilder.nullObjective()).build();
        var second = PhaseBuilder.create("second").objective(ObjectiveBuilder.nullObjective()).build();
        var unseen = PhaseBuilder.create("secret").objective(ObjectiveBuilder.nullObjective()).build();
        var runtime = new QuestRuntimeData("test:quest", "first", 0, 0, 0, 0);
        runtime.activatePhase("second", 0);
        assertTrue(QuestJeiVisibility.canRevealPhase(first, runtime));
        assertTrue(QuestJeiVisibility.canRevealPhase(second, runtime));
        assertFalse(QuestJeiVisibility.canRevealPhase(unseen, runtime));
        runtime.completePhase("first");
        assertTrue(QuestJeiVisibility.canRevealPhase(first, runtime));
        assertFalse(QuestJeiVisibility.canRevealPhase(first, null));
    }

    @Test
    void placeholderCardsDoNotRevealTheRealItemUntilDiscovered() {
        for (HiddenPresentationMode mode : List.of(HiddenPresentationMode.PLACEHOLDER,
                HiddenPresentationMode.SILHOUETTE, HiddenPresentationMode.NAME_MASKED)) {
            var phase = PhaseBuilder.create("entry").collectionEntryConfig(new CollectionEntryConfig("category",
                    VisibilityMode.VISIBLE_BY_DEFAULT, mode, List.of(), CountingMode.BINARY, 1, false, false,
                    0, EntryRewardGrantMode.AUTO, List.of(), 0, false)).objective(ObjectiveBuilder.nullObjective()).build();
            var runtime = new QuestRuntimeData("test:quest", "entry", 0, 0, 0, 0);
            var collection = new CollectionRuntimeData();
            runtime.setCollectionData(collection);
            collection.markVisible("entry");
            assertFalse(QuestJeiVisibility.canRevealPhase(phase, runtime), mode.name());
            collection.markDiscovered("entry");
            assertTrue(QuestJeiVisibility.canRevealPhase(phase, runtime), mode.name());
        }
    }
}
