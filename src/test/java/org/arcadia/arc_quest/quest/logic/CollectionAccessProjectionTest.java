package org.arcadia.arc_quest.quest.logic;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.collection.CollectionViews.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionAccessProjectionTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void categoryHidingGatesBindingsEvenAfterDiscovery() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var entry = entry("test:hidden_category");
        var quest = quest("test:category", entry, List.of(ICondition.flagSet("visited")));
        data.addActiveQuest(run(quest));
        data.getCollectionRecords().discover(entry.getEntryId());
        assertTrue(view(data, quest).bindings().isEmpty());
        data.setFlag("visited");
        assertEquals(1, view(data, quest).bindings().size());
        data.removeFlag("visited");
        assertTrue(view(data, quest).bindings().isEmpty(), "A new query must revoke a cached grant");
    }
    @Test void publicDimEntryRemainsAuthorizedWithoutFabricatingDiscovery() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var entry = entry("test:dim").withPresentation(new CollectionEntryPresentation(null, List.of(), "", List.of(), List.of(), List.of(), true));
        var quest = quest("test:dim_book", entry, List.of());
        data.addActiveQuest(run(quest));
        var result = view(data, quest).bindings().get(0).view();
        assertFalse(result.discovered()); assertFalse(result.obtainable());
        assertFalse(data.getCollectionRecords().isDiscovered(entry.getEntryId()));
    }
    @Test void exactArchiveRunResolvesWhileAnotherRunIsActive() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var quest = quest("test:runs", entry("test:old_entry"), List.of());
        var old = run(quest); data.getCollectionArchives().capture(old);
        var current = run(quest); data.addActiveQuest(current);
        var view = view(data, quest);
        assertEquals(2, view.bindings().size());
        var ref = new EntryRef(new RunRef(quest.getId().toString(), old.getCollectionData().getRunId()), "sheet", "binding");
        assertSame(data.getCollectionArchives().get(quest.getId().toString()), view.resolve(ref, "").runtime());
        assertNull(view.resolve(new EntryRef(new RunRef(quest.getId().toString(), "unknown"), "sheet", "binding"), ""));
    }
    @Test void archivedCopyCannotOverrideTheSameActiveRun() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var quest = quest("test:dedup", entry("test:dedup_entry"), List.of());
        var run = run(quest); data.addActiveQuest(run); data.getCollectionArchives().capture(run);
        var view = view(data, quest);
        assertEquals(1, view.bindings().size()); assertSame(run, view.bindings().get(0).runtime());
    }
    @Test void detailsRejectUndisclosedVariantAndSourceRequiresBothEnds() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var target = entry("test:target").withPresentation(new CollectionEntryPresentation(null,
                List.of(new CollectionVariantDefinition("private", QuestText.literal("Private"),
                        CollectionSubjectSpec.item(ResourceLocation.parse("minecraft:paper")), "", CollectionContentReveal.DISCOVERED)), "private", List.of()));
        var source = CollectionEntryBuilder.create("test:source").category("objects")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build();
        var sourceQuest = quest("test:source_book", source, List.of());
        var relation = new CollectionSourceSetDefinition(1, ResourceLocation.parse("test:set"), List.of(
                new CollectionSourceSetDefinition.Relation(ResourceLocation.parse("test:relation"), source.getEntryId(), target.getEntryId(), List.of("private"), "loot", QuestText.literal("route"), 0)));
        var targetQuest = QuestBuilder.create("test:target_book").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("objects", "Objects").entry(target).build().withSourceSets(List.of(relation)))
                .phase(PhaseBuilder.create("sheet").collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("binding", target.getEntryId()).discovered()))).build();
        data.addActiveQuest(run(targetQuest)); data.addActiveQuest(run(sourceQuest));
        var definitions = Map.of(targetQuest.getId().toString(), targetQuest, sourceQuest.getId().toString(), sourceQuest);
        var view = new CollectionAccessProjection(null, data, r -> definitions.get(r.getQuestId()));
        var ref = view.bindings().get(0).view().ref();
        assertNull(view.resolve(ref, "private")); assertTrue(view.relations().isEmpty());
        data.getCollectionRecords().discover(target.getEntryId());
        view = new CollectionAccessProjection(null, data, r -> definitions.get(r.getQuestId()));
        assertNotNull(view.resolve(ref, "private")); assertTrue(view.relations().isEmpty());
        data.getCollectionRecords().discover(source.getEntryId());
        view = new CollectionAccessProjection(null, data, r -> definitions.get(r.getQuestId()));
        assertEquals(1, view.relations().size()); assertTrue(view.relations().get(0).matches("private"));
        assertFalse(view.relations().get(0).matches(""));
        data.removeActiveQuest(sourceQuest.getId().toString());
        assertTrue(new CollectionAccessProjection(null, data, r -> definitions.get(r.getQuestId())).relations().isEmpty());
    }
    @Test void inactivePhaseNeverGrantsAnEntry() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var quest = quest("test:inactive", entry("test:inactive_entry"), List.of());
        var run = new QuestRuntimeData(quest.getId().toString(), "other", 0, 0, 0, 0);
        var collection = new CollectionRuntimeData(); collection.initializeSheet("sheet", List.of("binding"), 1, Set.of());
        run.setCollectionData(collection); data.addActiveQuest(run);
        assertTrue(view(data, quest).bindings().isEmpty());
    }
    private static CollectionEntryDefinition entry(String id) { return CollectionEntryBuilder.create(id).category("objects").build(); }
    private static QuestDefinition quest(String id, CollectionEntryDefinition entry, List<ICondition> conditions) {
        return QuestBuilder.create(id).mode(QuestMode.COLLECTION).collectionConfig(CollectionQuestConfigBuilder.create()
                .category(new CollectionCategoryDefinition("objects", QuestText.literal("Objects"), null, 0, List.of(), List.of(), conditions)).entry(entry).build())
                .phase(PhaseBuilder.create("sheet").collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("binding", entry.getEntryId()).discovered()))).build();
    }
    private static QuestRuntimeData run(QuestDefinition quest) {
        var runtime = new QuestRuntimeData(quest.getId().toString(), "sheet", 0, 0, 0, 0);
        var collection = new CollectionRuntimeData(); collection.initializeSheet("sheet", List.of("binding"), 1, Set.of());
        runtime.setCollectionData(collection); return runtime;
    }
    private static CollectionAccessProjection view(ArcQuestPlayer data, QuestDefinition definition) {
        return new CollectionAccessProjection(null, data, run -> definition);
    }
}
