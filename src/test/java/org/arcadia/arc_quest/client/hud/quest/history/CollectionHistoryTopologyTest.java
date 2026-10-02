package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CollectionHistoryTopologyTest {
    private static final ResourceLocation A = ResourceLocation.parse("example:a");
    private static final ResourceLocation B = ResourceLocation.parse("example:b");
    private static final ResourceLocation C = ResourceLocation.parse("example:c");

    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void oneRealPhaseShowsBindingChildrenWithTheAuthoritativeQuotaAndAnyGate() {
        var phase = PhaseBuilder.create("survey").displayName("Survey")
                .objective(ObjectiveBuilder.custom(A, 5).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().quota(1)
                        .binding(EntryRequirementBuilder.create("a", A).discovered().objective("action")
                                .requirementMode(CollectionRequirementMode.ANY))
                        .binding(EntryRequirementBuilder.create("b", B).discovered())
                        .binding(EntryRequirementBuilder.create("extra", C).discovered().optional())).build();
        var quest = quest(List.of(entry(A), entry(B), entry(C)), phase);
        var records = new CollectionRecordState(); records.discover(A); records.discover(C);
        var nodes = graph(quest, runtime(quest), records);
        assertEquals(4, nodes.size());
        var header = phase(nodes, "survey");
        assertTrue(header.active()); assertEquals("Survey", header.renderTitle().getString());
        assertEquals(1, header.completedCount()); assertEquals(1, header.targetCount());
        var a = binding(nodes, "survey", "a");
        assertSame(phase, a.phase()); assertEquals("Known example:a", a.renderTitle().getString());
        assertTrue(a.completed()); assertEquals(1, a.completedCount()); assertEquals(1, a.targetCount());
        assertTrue(binding(nodes, "survey", "extra").optional());
        assertEquals(1, quest.getPhaseIds().size());
    }

    @Test void parallelAndMergeEdgesFollowRealPhasesAndGroupsNeverOverlap() {
        var prepare = PhaseBuilder.create("prepare").objective(ObjectiveBuilder.custom(A, 1)).thenGoTo(List.of("wildlife", "materials"))
                .choice(Component.literal("Wildlife"), "chosen", "wildlife").build();
        var wildlife = PhaseBuilder.create("wildlife").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("same", A).discovered())
                .binding(EntryRequirementBuilder.create("other", B).discovered())).thenGoTo("report").build();
        var materials = PhaseBuilder.create("materials").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("same", A).discovered())).thenGoTo("report").build();
        var report = PhaseBuilder.create("report").objective(ObjectiveBuilder.custom(A, 1)).build();
        var quest = quest(List.of(entry(A), entry(B)), prepare, wildlife, materials, report);
        var run = runtime(quest); run.completePhase("prepare");
        run.activatePhase("wildlife", 0); run.activatePhase("materials", 0);
        var nodes = graph(quest, run, new CollectionRecordState());
        var edges = QuestHistoryGraphBuilder.connections(nodes);
        assertEquals(Set.of("prepare->wildlife", "prepare->materials", "wildlife->report", "materials->report"),
                edges.stream().filter(edge -> !edge.containment()).map(edge -> edge.sourceId() + "->" + edge.targetId()).collect(Collectors.toSet()));
        assertEquals(4, edges.stream().filter(edge -> !edge.containment()).count(), "Choice and transition targets are deduplicated");
        assertEquals(3, edges.stream().filter(QuestHistoryConnection::containment).count());
        assertNotEquals(binding(nodes, "wildlife", "same").id(), binding(nodes, "materials", "same").id());
        assertSame(wildlife, binding(nodes, "wildlife", "same").phase());
        assertFalse(phase(nodes, "report").reached());
        var sameLayer = nodes.stream().filter(node -> node.depth() == 1).toList();
        for (int i = 0; i < sameLayer.size(); i++) for (int j = i + 1; j < sameLayer.size(); j++)
            assertTrue(Math.abs(sameLayer.get(i).y() - sameLayer.get(j).y()) > QuestHistoryNodeRenderer.CARD_HEIGHT);
    }

    @Test void hiddenNamesIconsAndRequirementsAreNotExposedAndFutureProgressIsAnonymous() {
        var placeholder = CollectionEntryBuilder.create(B).category("records").displayName("Secret placeholder")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER).build();
        var hidden = CollectionEntryBuilder.create(C).category("records").displayName("Secret hidden")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build();
        var first = PhaseBuilder.create("first").collectionSheet(sheet()).thenGoTo("later").build();
        var later = PhaseBuilder.create("later").collectionSheet(sheet()).build();
        var quest = quest(List.of(entry(A), placeholder, hidden), first, later);
        var records = new CollectionRecordState(); records.discover(A);
        var nodes = graph(quest, runtime(quest), records);
        assertEquals(6, nodes.size(), "Fully hidden entries have no node");
        var unknown = binding(nodes, "first", "b");
        assertNull(unknown.entry()); assertNull(unknown.image()); assertFalse(unknown.reached());
        assertTrue(unknown.binding().requirements().isEmpty());
        assertKey(unknown.renderTitle(), "arc_quest.hud.history.collection_hidden");
        var future = binding(nodes, "later", "a");
        assertFalse(future.completed()); assertFalse(future.reached()); assertNull(future.entry());
        assertFalse(future.binding().revealed()); assertTrue(future.binding().requirements().isEmpty());
        assertEquals(0, future.completedCount()); assertEquals(0, future.targetCount());
        assertEquals(0, phase(nodes, "later").completedCount()); assertEquals(3, phase(nodes, "later").targetCount());
        assertKey(phase(nodes, "later").renderTitle(), "arc_quest.hud.history.collection_waiting");
    }

    @Test void missingRuntimeNeverPretendsThatTheQuestOrItsPermanentKnowledgeWasCompleted() {
        var quest = quest(List.of(entry(A)), PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("a", A).discovered())).build());
        var records = new CollectionRecordState(); records.discover(A);
        var nodes = graph(quest, null, records);
        assertEquals(2, nodes.size());
        assertTrue(nodes.stream().noneMatch(node -> node.completed() || node.active() || node.reached()));
        assertNull(binding(nodes, "survey", "a").entry());
        assertEquals(0, phase(nodes, "survey").completedCount());
    }

    @Test void actionAndPermanentKnowledgeKeepTheirSeparateCountsAndANewRunResetsActions() {
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(A, 5).id("action"))
                .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A)
                        .discovered().objective("action"))).build();
        var quest = quest(List.of(entry(A)), phase); var records = new CollectionRecordState(); records.discover(A);
        var first = runtime(quest); first.setObjectiveProgress("survey", 0, 2);
        var partial = binding(graph(quest, first, records), "survey", "a");
        assertEquals(1, partial.completedCount()); assertEquals(2, partial.targetCount());
        var action = partial.binding().requirements().stream().filter(row -> row.objective() != null).findFirst().orElseThrow();
        var permanent = partial.binding().requirements().stream().filter(row -> row.objective() == null).findFirst().orElseThrow();
        assertEquals(2, action.current()); assertEquals(5, action.target());
        assertEquals(1, permanent.current()); assertEquals(1, permanent.target());
        first.setObjectiveProgress("survey", 0, 5);
        assertTrue(binding(graph(quest, first, records), "survey", "a").completed());
        var next = binding(graph(quest, runtime(quest), records), "survey", "a");
        assertFalse(next.completed());
        assertEquals(0, next.binding().requirements().stream().filter(row -> row.objective() != null).findFirst().orElseThrow().current());
        assertTrue(next.binding().discovered());
    }

    @Test void archivedGateStaysFrozenWhenPermanentKnowledgeGrowsAndIsNotActive() {
        var phase = PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create().quota(1)
                .binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered())).build();
        var quest = quest(List.of(entry(A), entry(B)), phase);
        var records = new CollectionRecordState(); records.discover(A);
        for (var terminal : List.of(QuestState.COMPLETED, QuestState.FAILED)) {
            var run = runtime(quest); CollectionSheetService.initialize(quest, run, records);
            run.getCollectionData().markBindingComplete("survey", "a");
            if (terminal == QuestState.COMPLETED) run.completePhase("survey");
            run.setState(terminal); records.discover(B);
            var nodes = graph(quest, run, records);
            var header = phase(nodes, "survey");
            assertTrue(header.reached()); assertFalse(header.active());
            assertEquals(terminal == QuestState.COMPLETED, header.completed());
            assertEquals(1, header.completedCount()); assertEquals(1, header.targetCount());
            assertTrue(binding(nodes, "survey", "a").completed());
            var b = binding(nodes, "survey", "b");
            assertTrue(b.binding().discovered()); assertFalse(b.completed()); assertFalse(b.active());
            assertEquals(0, b.completedCount(), "Later knowledge cannot rewrite an archived gate");
            assertEquals(1, b.targetCount());
        }
    }

    @Test void removedFrozenBindingsAreNotReplacedByNewDefinitionRows() {
        var original = quest(List.of(entry(A), entry(B)), PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered())).build());
        var records = new CollectionRecordState(); var run = runtime(original);
        CollectionSheetService.initialize(original, run, records);
        var reloaded = quest(List.of(entry(C)), PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("c", C).discovered())).build());
        var nodes = graph(reloaded, run, records);
        assertEquals(1, nodes.size()); assertEquals(2, nodes.get(0).targetCount()); assertEquals(0, nodes.get(0).completedCount());
    }

    private static CollectionSheetBuilder sheet() {
        return CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("a", A).discovered())
                .binding(EntryRequirementBuilder.create("b", B).discovered())
                .binding(EntryRequirementBuilder.create("c", C).discovered());
    }
    private static CollectionEntryDefinition entry(ResourceLocation id) {
        return CollectionEntryBuilder.create(id).category("records").displayName("Known " + id).build();
    }
    private static QuestDefinition quest(List<CollectionEntryDefinition> entries, PhaseDefinition... phases) {
        var config = CollectionQuestConfigBuilder.create().category("records", "Records"); entries.forEach(config::entry);
        var builder = QuestBuilder.create("example:topology").category(QuestCategory.ADVENTURE)
                .mode(QuestMode.COLLECTION).collectionConfig(config.build());
        for (var phase : phases) builder.phase(phase);
        return builder.build();
    }
    private static QuestRuntimeData runtime(QuestDefinition quest) {
        var first = quest.getInitialPhaseId();
        return new QuestRuntimeData(quest.getId().toString(), first, quest.getPhase(first).getObjectives().size(), 0L, 0L, 0L);
    }
    private static List<QuestHistoryNodeData> graph(QuestDefinition quest, QuestRuntimeData run, CollectionRecordState records) {
        return QuestHistoryGraphBuilder.buildCollection(quest, run, id -> quest.getPhase(id).getDisplayName(),
                id -> CollectionProgressProjector.project(quest, quest.getPhase(id), run, records));
    }
    private static QuestHistoryNodeData phase(List<QuestHistoryNodeData> nodes, String id) {
        return nodes.stream().filter(node -> !node.isBinding() && node.id().equals(id)).findFirst().orElseThrow();
    }
    private static QuestHistoryNodeData binding(List<QuestHistoryNodeData> nodes, String phase, String id) {
        return nodes.stream().filter(node -> node.isBinding() && node.phaseId().equals(phase)
                && node.binding().bindingId().equals(id)).findFirst().orElseThrow();
    }
    private static void assertKey(Component label, String expected) {
        assertInstanceOf(TranslatableContents.class, label.getContents());
        assertEquals(expected, ((TranslatableContents) label.getContents()).getKey());
    }
}
