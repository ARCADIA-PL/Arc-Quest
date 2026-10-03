package org.arcadia.arc_quest.quest.logic;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.registry.*;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CollectionRunDefinitionsTest {
    private static final ResourceLocation LOGS = ResourceLocation.parse("minecraft:logs");
    private static final ResourceLocation OAK = ResourceLocation.parse("minecraft:oak_log");
    private static final ResourceLocation BIRCH = ResourceLocation.parse("minecraft:birch_log");
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void restoredHistoricalDemoUsesExactV1RulesInsteadOfCurrentV2() {
        var old = LegacyCollectionFieldDemos.field(LegacyCollectionFieldDemos.entries());
        var historical = runtime(old);
        historical.setObjectiveProgress("survey", 0, 2);
        historical = QuestRuntimeData.deserializeNBT(historical.serializeNBT());
        var current = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var store = new CollectionRunDefinitionStore();
        var pinned = CollectionRunDefinitions.resolveInStore(store, historical, current);
        assertEquals("collection-v1", historical.getFrozenDefinitionVersion());
        assertTrue(historical.hasFrozenDefinitionHash());
        assertEquals(2, historical.getObjectiveProgress("survey", 0));
        assertEquals(5, pinned.getPhase("survey").getObjectives().size());
        var zombie = pinned.getCollectionConfig().getEntry(CollectionFieldDemos.ZOMBIE);
        assertFalse(zombie.isUnifiedGameplay());
        assertEquals(5, zombie.getResearchObjectives().get(0).getRequiredCount());
        assertTrue(current.getCollectionConfig().getEntry(CollectionFieldDemos.ZOMBIE).getResearchObjectives().isEmpty());
        assertEquals(CollectionRewardPreviewVisibility.UNLOCKED_ONLY, zombie.getRewards().get(0).previewVisibility());
        var reloadedStore = CollectionRunDefinitionStore.load(store.save(new net.minecraft.nbt.CompoundTag()));
        var reloadedRun = QuestRuntimeData.deserializeNBT(historical.serializeNBT());
        assertEquals("anatomy", CollectionRunDefinitions.resolveInStore(reloadedStore, reloadedRun, current)
                .getCollectionConfig().getEntry(CollectionFieldDemos.ZOMBIE).getResearchObjectives().get(0).getObjectiveId());
    }

    @Test void newDemoCapturesCurrentVersionAndTagCandidatesOnceAcrossSaveCopyAndReload() {
        var current = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var run = runtime(current);
        var store = new CollectionRunDefinitionStore();
        CollectionRunDefinitions.captureInStore(store, current, run, tag -> List.of(OAK));
        assertEquals(CollectionDemoDefinitionFactories.CURRENT_VERSION, run.getFrozenDefinitionVersion());
        assertTrue(run.hasFrozenItemTag(LOGS));
        assertTrue(run.isItemTagSnapshotComplete());
        assertEquals(Set.of(OAK), run.getFrozenItemTagMembers(LOGS));
        CollectionRunDefinitions.freezeTags(current, run, tag -> List.of(BIRCH));
        assertTrue(CollectionRunDefinitions.matchesItemTag(run, LOGS, OAK));
        assertFalse(CollectionRunDefinitions.matchesItemTag(run, LOGS, BIRCH));
        var copy = run.copy();
        var restored = QuestRuntimeData.deserializeNBT(copy.serializeNBT());
        assertEquals(run.getFrozenDefinitionHash(), restored.getFrozenDefinitionHash());
        assertEquals(Set.of(OAK), restored.getFrozenItemTagMembers(LOGS));
        assertTrue(restored.isItemTagSnapshotComplete());
        assertThrows(IllegalStateException.class, () -> restored.freezeItemTag(LOGS, List.of(BIRCH)));
    }

    @Test void frozenEmptyTagCannotFallbackAndSecretServerFieldsDoNotEnterLegacyWire() {
        var quest = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var run = runtime(quest);
        CollectionRunDefinitions.captureInStore(new CollectionRunDefinitionStore(), quest, run, ignored -> List.of());
        assertTrue(run.hasFrozenItemTag(LOGS)); assertTrue(run.getFrozenItemTagMembers(LOGS).isEmpty());
        assertFalse(CollectionRunDefinitions.matchesItemTag(run, LOGS, OAK));
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            run.writeToNetwork(wire);
            var client = QuestRuntimeData.readFromNetwork(wire);
            assertFalse(client.hasFrozenDefinitionHash());
            assertFalse(client.hasFrozenItemTag(LOGS));
            assertFalse(client.isItemTagSnapshotComplete());
        } finally { wire.release(); }
    }

    @Test void unknownHistoricalCodeRunCannotBeReinterpretedAsLiveV2() {
        var quest = anonymousQuest("example:unknown_historical_collection");
        var old = QuestRuntimeData.deserializeNBT(runtime(quest).serializeNBT());
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class,
                () -> CollectionRunDefinitions.resolveInStore(new CollectionRunDefinitionStore(), old, quest));
        assertFalse(old.hasFrozenDefinitionHash());
    }

    @Test void knownHistoricalRulesRestoreWhenTheLiveQuestIsMissing() {
        var old = LegacyCollectionFieldDemos.field(LegacyCollectionFieldDemos.entries());
        var restored = QuestRuntimeData.deserializeNBT(runtime(old).serializeNBT());
        var pinned = CollectionRunDefinitions.resolveInStore(new CollectionRunDefinitionStore(), restored, null);
        assertNotNull(pinned);
        assertEquals(old.getId(), pinned.getId());
        assertFalse(pinned.getCollectionConfig().getEntry(CollectionFieldDemos.ZOMBIE).isUnifiedGameplay());
    }

    @Test void unknownModernSheetCannotUseAReplacementOrdinaryQuest() {
        var source = anonymousQuest("example:unknown_changed_to_ordinary");
        var run = runtime(source);
        CollectionSheetService.initialize(source, run, new CollectionRecordState());
        var historical = QuestRuntimeData.deserializeNBT(run.serializeNBT());
        var ordinary = QuestBuilder.create(source.getId())
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(OAK, 1).id("action"))).build();
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class,
                () -> CollectionRunDefinitions.resolveInStore(new CollectionRunDefinitionStore(), historical, ordinary));
        assertFalse(historical.hasFrozenDefinitionHash());
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class,
                () -> CollectionRunDefinitions.resolveInStore(new CollectionRunDefinitionStore(), historical, null));
    }

    @Test void failedHistoricalTagCaptureCannotPublishHashOrPartialCandidateSets() {
        var source = LegacyCollectionFieldDemos.field(LegacyCollectionFieldDemos.entries());
        var historical = QuestRuntimeData.deserializeNBT(runtime(source).serializeNBT());
        var store = new CollectionRunDefinitionStore();
        assertThrows(IllegalStateException.class, () -> CollectionRunDefinitions.resolveInStore(store, historical, source, tag -> {
            throw new IllegalStateException("Unavailable Tag registry");
        }));
        assertFalse(historical.hasFrozenDefinitionHash());
        assertTrue(historical.getFrozenItemTags().isEmpty());
        assertFalse(historical.isItemTagSnapshotComplete());
    }

    @Test void repeatedResolutionSkipsCompletedTagSnapshotEvenWhenAllCandidatesAreEmpty() {
        var current = CollectionFieldDemos.field(CollectionFieldDemos.entries());
        var run = runtime(current);
        var store = new CollectionRunDefinitionStore();
        AtomicInteger calls = new AtomicInteger();
        CollectionRunDefinitions.captureInStore(store, current, run, ignored -> {
            calls.incrementAndGet(); return List.of();
        });
        int captureCalls = calls.get();
        assertTrue(captureCalls > 0);
        assertTrue(run.isItemTagSnapshotComplete());
        assertTrue(run.getFrozenItemTagMembers(LOGS).isEmpty());
        var restored = QuestRuntimeData.deserializeNBT(run.copy().serializeNBT());
        assertTrue(restored.isItemTagSnapshotComplete());
        for (int event = 0; event < 1000; event++) {
            assertEquals(current.getId(), CollectionRunDefinitions.resolveInStore(store, restored, current, ignored -> {
                calls.incrementAndGet(); throw new AssertionError("An event revisited the accepted run's Tag resolver");
            }).getId());
        }
        assertEquals(captureCalls, calls.get());
        assertThrows(IllegalStateException.class, () -> restored.freezeItemTag(ResourceLocation.parse("minecraft:planks"), List.of(OAK)));

        // No item Tags at all still has an explicit, persistent complete snapshot.
        var noTags = anonymousQuest("example:frozen_snapshot_without_item_tags");
        var noTagRun = runtime(noTags);
        CollectionRunDefinitions.freezeTags(noTags, noTagRun, ignored -> { throw new AssertionError("No Tag should be resolved"); });
        assertTrue(noTagRun.getFrozenItemTags().isEmpty());
        assertTrue(QuestRuntimeData.deserializeNBT(noTagRun.copy().serializeNBT()).isItemTagSnapshotComplete());
    }

    @Test void legacyPhaseEntryRuntimeDoesNotRequireAModernDefinitionFactory() {
        var ordinary = QuestBuilder.create("example:legacy_phase_entry_without_sheet")
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(OAK, 1).id("action"))).build();
        var historical = new QuestRuntimeData(ordinary.getId().toString(), "survey", 1, 0, 0, 0);
        historical.setCollectionData(new CollectionRuntimeData());
        historical = QuestRuntimeData.deserializeNBT(historical.serializeNBT());
        assertSame(ordinary, CollectionRunDefinitions.resolveInStore(new CollectionRunDefinitionStore(), historical, ordinary));
        assertFalse(historical.hasFrozenDefinitionHash());
    }

    @Test void failedCaptureDoesNotPublishHashAndOrdinaryQuestsNeedNoFactory() {
        var unsupported = anonymousQuest("example:unsupported_new_collection");
        var run = runtime(unsupported);
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class,
                () -> CollectionRunDefinitions.captureInStore(new CollectionRunDefinitionStore(), unsupported, run, ignored -> List.of()));
        assertFalse(run.hasFrozenDefinitionHash());
        var ordinary = QuestBuilder.create("example:ordinary_no_freeze")
                .phase(PhaseBuilder.create("step").objective(ObjectiveBuilder.custom(OAK, 1).id("action"))).build();
        var ordinaryRun = new QuestRuntimeData(ordinary.getId().toString(), "step", 1, 0, 0, 0);
        assertDoesNotThrow(() -> CollectionRunDefinitions.captureInStore(new CollectionRunDefinitionStore(), ordinary, ordinaryRun, ignored -> List.of()));
        assertFalse(ordinaryRun.hasFrozenDefinitionHash());
    }

    private static QuestRuntimeData runtime(QuestDefinition quest) {
        var phase = quest.getInitialPhase();
        var runtime = new QuestRuntimeData(quest.getId().toString(), phase.getPhaseId(), phase.getObjectives().size(), 0, 0, 0);
        runtime.setCollectionData(new CollectionRuntimeData());
        return runtime;
    }
    private static QuestDefinition anonymousQuest(String id) {
        var entry = CollectionEntryBuilder.create("example:unversioned_entry").category("field").build();
        return QuestBuilder.create(id).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(OAK, 1).id("action"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("entry", entry.getEntryId()).objective("action"))))
                .build();
    }
}
