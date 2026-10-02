package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JournalSelectionResolverTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:collection_selection");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void anAuthorizedModernCollectionReplacementAtTheSameEpochPreservesTheArchiveContext() {
        var oldEntry = entry(modern(), QuestState.ACTIVE);
        var replacement = entry(modern(), QuestState.ACTIVE);
        assertNotSame(oldEntry.def(), replacement.def());
        var result = JournalSelectionResolver.resolve(List.of(replacement), oldEntry, null, true, true);
        assertEquals(0, result.selectedIndex());
        assertFalse(result.contextChanged());
    }

    @Test void aDifferentContentEpochOrExplicitSelectionResetInvalidatesTheContext() {
        var oldEntry = entry(modern(), QuestState.ACTIVE);
        var replacement = entry(modern(), QuestState.ACTIVE);
        assertTrue(JournalSelectionResolver.resolve(List.of(replacement), oldEntry, null, true, false).contextChanged());
        assertTrue(JournalSelectionResolver.resolve(List.of(replacement), oldEntry, ID.toString(), false, true).contextChanged());
    }

    @Test void ordinaryAndLegacyCollectionDefinitionChangesStillResetAtTheSameEpoch() {
        for (QuestDefinition oldDefinition : List.of(ordinary(), legacy())) {
            QuestDefinition replacement = oldDefinition.isCollectionQuest() ? legacy() : ordinary();
            assertTrue(JournalSelectionResolver.resolve(List.of(entry(replacement, QuestState.ACTIVE)),
                    entry(oldDefinition, QuestState.ACTIVE), null, true, true).contextChanged());
        }
    }

    @Test void changedModeStateRemovedQuestOrMissingDefinitionNeverRetainAnArchive() {
        var oldEntry = entry(modern(), QuestState.ACTIVE);
        assertTrue(JournalSelectionResolver.resolve(List.of(entry(ordinary(), QuestState.ACTIVE)), oldEntry, null, true, true).contextChanged());
        assertTrue(JournalSelectionResolver.resolve(List.of(entry(modern(), QuestState.COMPLETED)), oldEntry, null, true, true).contextChanged());
        assertTrue(JournalSelectionResolver.resolve(List.of(new JournalTypes.QuestListEntry(ID.toString(), Component.literal("missing"),
                QuestState.ACTIVE, null)), oldEntry, null, true, true).contextChanged());
        var removed = JournalSelectionResolver.resolve(List.of(), oldEntry, null, true, true);
        assertEquals(-1, removed.selectedIndex()); assertTrue(removed.contextChanged());
    }

    @Test void reorderRetainsTheQuestIdentityAndSwitchingToAnotherQuestResets() {
        var oldEntry = entry(modern(), QuestState.ACTIVE);
        var other = new JournalTypes.QuestListEntry("test:other", Component.literal("Other"), QuestState.ACTIVE, ordinary());
        var replacement = entry(modern(), QuestState.ACTIVE);
        var reordered = JournalSelectionResolver.resolve(List.of(other, replacement), oldEntry, null, true, true);
        assertEquals(1, reordered.selectedIndex()); assertFalse(reordered.contextChanged());
        var disappeared = JournalSelectionResolver.resolve(List.of(other), oldEntry, null, true, true);
        assertEquals(0, disappeared.selectedIndex()); assertTrue(disappeared.contextChanged());
    }

    private static JournalTypes.QuestListEntry entry(QuestDefinition definition, QuestState state) {
        return new JournalTypes.QuestListEntry(ID.toString(), Component.literal("Collection"), state, definition);
    }

    private static QuestDefinition modern() {
        var subject = ResourceLocation.parse("test:subject");
        var config = CollectionQuestConfigBuilder.create()
                .category(category())
                .entry(CollectionEntryBuilder.create(subject).category("field")).build();
        return QuestBuilder.create(ID).category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION).collectionConfig(config)
                .phase(PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("subject", subject).discovered()))).build();
    }

    private static QuestDefinition ordinary() {
        return QuestBuilder.create(ID).category(QuestCategory.ADVENTURE)
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(ResourceLocation.parse("test:subject"), 1))).build();
    }

    private static QuestDefinition legacy() {
        var config = CollectionQuestConfigBuilder.create().category(category()).build();
        return QuestBuilder.create(ID).category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION).collectionConfig(config)
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(ResourceLocation.parse("test:subject"), 1))
                        .collectionEntryConfig(new CollectionEntryConfig("field", null, null, List.of(), null, 1,
                                false, false, 0, null, List.of(), 0, true))).build();
    }

    private static CollectionCategoryDefinition category() {
        return new CollectionCategoryDefinition("field", QuestText.literal("Field"), null, 0, List.of(), List.of(), List.of());
    }
}
