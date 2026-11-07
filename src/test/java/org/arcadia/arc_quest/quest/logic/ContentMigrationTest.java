package org.arcadia.arc_quest.quest.logic;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.migration.*;
import org.arcadia.arc_quest.quest.api.migration.QuestLifecycleService;
import org.arcadia.arc_quest.quest.api.migration.ContentMigrationRegistry.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ContentMigrationTest {
    static final String OLD = "a".repeat(64), NEW = "b".repeat(64);
    static final ResourceLocation ID = ResourceLocation.parse("test:migration");
    static final ContentMigrationRegistry API = ContentMigrationRegistry.INSTANCE;
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void previewIsDetachedAndCommitIsIdempotentAcrossSaveReload() {
        var data = new ArcQuestPlayer(UUID.randomUUID()); var run = run(); data.addActiveQuest(run);
        data.getCollectionRecords().discover(ResourceLocation.parse("test:old"));
        var facts = data.getCollectionRecords().serializeNBT();
        var original = run.serializeNBT();
        var plan = API.preview(definition(), run, Scope.ACTIVE).orElseThrow();
        assertEquals(original, run.serializeNBT()); assertEquals(OLD, run.getFrozenDefinitionHash());
        assertEquals(2, plan.previewCopy().getCollectionData().getFrozenBindingIds("sheet").size());
        assertTrue(API.commit(data, plan)); assertFalse(API.commit(data, plan));
        var reloaded = new ArcQuestPlayer(data.getOwnerUuid()); reloaded.deserializeNBT(data.serializeNBT());
        assertEquals(facts, reloaded.getCollectionRecords().serializeNBT());
        assertEquals(NEW, reloaded.getActiveQuest(run.getQuestId()).getFrozenDefinitionHash());
        assertFalse(API.commit(reloaded, plan));
        assertEquals(original, reloaded.getRuntimeManagement().receipt(ID + "/" + plan.runId()).getCompound("Before"));
    }
    @Test void stalePlanCannotEraseNewProgress() {
        var data = new ArcQuestPlayer(UUID.randomUUID()); var run = run(); data.addActiveQuest(run);
        var plan = API.preview(definition(), run, Scope.ACTIVE).orElseThrow();
        run.getCollectionData().markBindingComplete("sheet", "old");
        assertThrows(IllegalStateException.class, () -> API.commit(data, plan));
        assertEquals(OLD, run.getFrozenDefinitionHash()); assertFalse(data.getRuntimeManagement().contains(ID + "/" + plan.runId()));
    }
    @Test void failedEditDoesNotPartiallyMutateOriginal() {
        var run = run(); var before = run.serializeNBT();
        var spec = new Definition(ID, target().getId(), Set.of(OLD), target(), NEW, "v2", Set.of(Scope.ACTIVE),
                editor -> { editor.sheet("sheet", QuestRuntimeEditor.SheetPolicy.EXTEND_ACTIVE); throw new IllegalStateException("injected"); });
        assertThrows(IllegalStateException.class, () -> API.preview(spec, run, Scope.ACTIVE));
        assertEquals(before, run.serializeNBT());
    }
    @Test void unknownHashAndWrongInstanceScopeAreRefused() {
        var tag = run().serializeNBT(); tag.putString(QuestRuntimeData.FROZEN_DEFINITION_HASH_KEY, "c".repeat(64));
        var run = QuestRuntimeData.deserializeNBT(tag);
        assertTrue(API.preview(definition(), run, Scope.ACTIVE).isEmpty());
        var restricted = new Definition(ID, target().getId(), Set.of(OLD), target(), NEW, "v2", Set.of(Scope.ACTIVE), editor -> {});
        assertTrue(API.preview(restricted, run(), Scope.ARCHIVE).isEmpty());
    }
    @Test void terminalArchiveKeepsItsTargetStateRunAndEarnedReceipt() {
        var data = new ArcQuestPlayer(UUID.randomUUID()); var run = run();
        run.getCollectionData().unlockEntryReward("sheet", "old", "earned"); run.setState(QuestState.COMPLETED);
        data.getCollectionArchives().capture(run);
        var plan = API.preview(definition(), run, Scope.ARCHIVE).orElseThrow(); assertTrue(API.commit(data, plan));
        var updated = data.getCollectionArchives().get(run.getQuestId());
        assertEquals(QuestState.COMPLETED, updated.getState()); assertEquals(1, updated.getCollectionData().getFrozenSheetTarget("sheet", 99));
        assertEquals(List.of("old"), updated.getCollectionData().getFrozenBindingIds("sheet"));
        assertTrue(updated.getCollectionData().isEntryRewardUnlocked("sheet", "old", "earned"));
        assertNull(data.getActiveQuest(run.getQuestId()));
    }
    @Test void explicitRecoveryRefusesPostMigrationChangesAndRestoresUntouchedRunOnce() {
        var data = new ArcQuestPlayer(UUID.randomUUID()); var run = run(); data.addActiveQuest(run);
        var plan = API.preview(definition(), run, Scope.ACTIVE).orElseThrow(); API.commit(data, plan);
        var original = data.getActiveQuest(run.getQuestId()).copy();
        data.getActiveQuest(run.getQuestId()).getCollectionData().markBindingComplete("sheet", "old");
        assertEquals(RestoreResult.CHANGED_SINCE_MIGRATION, API.restore(data, ID, run.getQuestId(), plan.runId()));
        data.addActiveQuest(original);
        assertEquals(RestoreResult.RESTORED, API.restore(data, ID, run.getQuestId(), plan.runId()));
        assertEquals(OLD, data.getActiveQuest(run.getQuestId()).getFrozenDefinitionHash());
        assertEquals(RestoreResult.ALREADY_RESTORED, API.restore(data, ID, run.getQuestId(), plan.runId()));
        assertFalse(API.commit(data, plan), "An explicit rollback must not auto-reapply on next login");
    }
    @Test void earlierUnpaidArchiveDoesNotBecomeLatestWhenMigrated() {
        var data = new ArcQuestPlayer(UUID.randomUUID()); var old = run();
        old.getCollectionData().unlockEntryReward("sheet", "old", "earned");
        data.getCollectionArchives().capture(old); var latest = run(); data.getCollectionArchives().capture(latest);
        var plan = API.preview(definition(), old, Scope.ARCHIVE).orElseThrow(); assertTrue(API.commit(data, plan));
        assertEquals(latest.getCollectionData().getRunId(), data.getCollectionArchives().get(old.getQuestId()).getCollectionData().getRunId());
        assertEquals(NEW, data.getCollectionArchives().get(old.getQuestId(), plan.runId()).getFrozenDefinitionHash());
    }
    @Test void terminalAggregateTakesPrecedenceOverStaleActiveArchive() {
        var data = new ArcQuestPlayer(UUID.randomUUID()); var run = run(); data.addActiveQuest(run);
        data.markCompleted(run.getQuestId());
        assertEquals(QuestLifecycleService.Status.COMPLETED, QuestLifecycleService.INSTANCE.status(data, run.getQuestId()));
        var second = new ArcQuestPlayer(UUID.randomUUID()); second.getCollectionArchives().capture(run);
        assertEquals(QuestLifecycleService.Status.PAUSED, QuestLifecycleService.INSTANCE.status(second, run.getQuestId()));
    }
    @Test void ordinaryPausedMigrationPersistsTheNewRunAndItsReceiptTogether() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var run = new QuestRuntimeData("test:paused", "before", 1, 10, 20, 30);
        run.setFrozenDefinition(OLD, "v1");
        data.getRuntimeManagement().pauseRun(run.getQuestId(), run, "reader closed");
        var target = QuestBuilder.create("test:paused").phase(PhaseBuilder.create("after")
                .objective(ObjectiveBuilder.custom(ResourceLocation.parse("test:action"), 1).id("action"))).build();
        var spec = new Definition(ID, target.getId(), Set.of(OLD), target, NEW, "v2", Set.of(Scope.PAUSED),
                editor -> editor.mapPhase("before", "after"));
        var plan = API.preview(spec, run, Scope.PAUSED).orElseThrow();
        assertTrue(API.commit(data, plan));
        var saved = new ArcQuestPlayer(data.getOwnerUuid()); saved.deserializeNBT(data.serializeNBT());
        var migrated = saved.getRuntimeManagement().pausedRun(run.getQuestId());
        assertNotNull(migrated); assertEquals(NEW, migrated.getFrozenDefinitionHash());
        assertTrue(migrated.isPhaseActive("after")); assertFalse(migrated.isPhaseActive("before"));
        assertNull(saved.getActiveQuest(run.getQuestId()));
        assertEquals("reader closed", saved.getRuntimeManagement().pauseReason(run.getQuestId()));
        assertEquals(QuestLifecycleService.Status.PAUSED, QuestLifecycleService.INSTANCE.status(saved, run.getQuestId()));
        assertFalse(API.commit(saved, plan));
        assertEquals(RestoreResult.RESTORED, API.restore(saved, ID, run.getQuestId(), plan.runId()));
        assertEquals(run.serializeNBT(), saved.getRuntimeManagement().pausedRun(run.getQuestId()).serializeNBT());
        assertEquals("reader closed", saved.getRuntimeManagement().pauseReason(run.getQuestId()));
        assertEquals(RestoreResult.ALREADY_RESTORED, API.restore(saved, ID, run.getQuestId(), plan.runId()));
        assertFalse(API.commit(saved, plan));
    }
    @Test void stalePausedPlanDoesNotRecordAReceiptOrReplaceNewerState() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var run = new QuestRuntimeData("test:paused_stale", "before", 1, 10, 20, 30);
        run.setFrozenDefinition(OLD, "v1");
        data.getRuntimeManagement().pauseRun(run.getQuestId(), run, "old reason");
        var target = QuestBuilder.create("test:paused_stale").phase(PhaseBuilder.create("after")
                .objective(ObjectiveBuilder.custom(ResourceLocation.parse("test:action"), 1).id("action"))).build();
        var spec = new Definition(ID, target.getId(), Set.of(OLD), target, NEW, "v2", Set.of(Scope.PAUSED),
                editor -> editor.mapPhase("before", "after"));
        var plan = API.preview(spec, run, Scope.PAUSED).orElseThrow();
        run.setObjectiveProgress("before", 0, 1);
        data.getRuntimeManagement().pauseRun(run.getQuestId(), run, "new reason");
        var before = data.serializeNBT();
        assertThrows(IllegalStateException.class, () -> API.commit(data, plan));
        assertEquals(before, data.serializeNBT());
        assertFalse(data.getRuntimeManagement().contains(ID + "/" + plan.runId()));
    }
    private static Definition definition() {
        return new Definition(ID, target().getId(), Set.of(OLD), target(), NEW, "v2", Set.of(Scope.ACTIVE, Scope.ARCHIVE),
                editor -> editor.sheet("sheet", QuestRuntimeEditor.SheetPolicy.EXTEND_ACTIVE));
    }
    private static QuestDefinition target() {
        return QuestBuilder.create("test:book").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("objects", "Objects")
                        .entry(CollectionEntryBuilder.create("test:old").category("objects").build())
                        .entry(CollectionEntryBuilder.create("test:new").category("objects").build()).build())
                .phase(PhaseBuilder.create("sheet").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("old", "test:old").discovered())
                        .binding(EntryRequirementBuilder.create("new", "test:new").discovered()))).build();
    }
    private static QuestRuntimeData run() {
        var run = new QuestRuntimeData("test:book", "sheet", 0, 10, 20, 30);
        var collection = new CollectionRuntimeData(); collection.initializeSheet("sheet", List.of("old"), 1, Set.of());
        run.setCollectionData(collection); run.setFrozenDefinition(OLD, "v1"); return run;
    }
}
