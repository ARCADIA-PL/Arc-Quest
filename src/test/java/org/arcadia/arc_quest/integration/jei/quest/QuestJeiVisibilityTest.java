package org.arcadia.arc_quest.integration.jei.quest;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestJeiVisibilityTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }
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

    @Test
    void modernSheetUsesReachedPhaseEvidenceAndSeparatelyGuardsHiddenObjectiveIdentity() {
        ResourceLocation id = ResourceLocation.parse("test:specimen");
        var entry = CollectionEntryBuilder.create(id).category("mobs")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER).build();
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(id, 2).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("specimen", id).objective("action"))).build();
        var quest = QuestBuilder.create("test:quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("mobs", "Mobs").entry(entry).build()).phase(phase).build();
        var runtime = new QuestRuntimeData("test:quest", "survey", 1, 0, 0, 0);
        runtime.setCollectionData(new CollectionRuntimeData());
        assertTrue(QuestJeiVisibility.canRevealPhase(phase, runtime));
        var records = new CollectionRecordState();
        assertFalse(QuestJeiVisibility.canRevealCollectionObjective(quest, phase, phase.getObjective("action"), records));
        records.discover(id);
        assertTrue(QuestJeiVisibility.canRevealCollectionObjective(quest, phase, phase.getObjective("action"), records));
    }

    @Test
    void actionSharedWithAPublicEntryKeepsItsJeiIngredientVisible() {
        ResourceLocation secret = ResourceLocation.parse("test:secret"), known = ResourceLocation.parse("test:public");
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(known, 2).id("shared"))
                .collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("secret", secret).objective("shared"))
                        .binding(EntryRequirementBuilder.create("public", known).objective("shared"))).build();
        var quest = QuestBuilder.create("test:quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("mobs", "Mobs")
                        .entry(CollectionEntryBuilder.create(secret).category("mobs").visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER))
                        .entry(CollectionEntryBuilder.create(known).category("mobs")).build()).phase(phase).build();
        assertTrue(QuestJeiVisibility.canRevealCollectionObjective(quest, phase, phase.getObjective("shared"), new CollectionRecordState()));
    }
}
