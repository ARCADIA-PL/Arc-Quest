package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionCategoryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestCompletionPolicy;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrackerStyleRendererTest {
    private static final String QUEST = "test:tracker_styles";
    private static final String FIRST = "test:first";
    private static final String SECOND = "test:second";

    @BeforeAll
    static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test
    void snapshotsKeepSourceIndicesDynamicCountsHiddenRowsAndOptionalLabels() {
        var definition = QuestBuilder.create(QUEST).phase(PhaseBuilder.create(FIRST)
                .objective(goal(1)).objective(goal(20).hidden())
                .objective(ObjectiveBuilder.nullObjective().display("Instruction"))
                .objective(goal(5).optional()).objective(goal(1))).build();
        var runtime = runtime(definition);
        runtime.setObjectiveProgress(FIRST, 0, 1);
        runtime.setRequiredCount(FIRST, 4, 16);
        runtime.setObjectiveProgress(FIRST, 4, 8);

        var snapshot = snapshot(new TrackerStyleRenderer(), definition, runtime, 1000);

        assertEquals(List.of(2, 3, 4), snapshot.details().stream()
                .map(row -> row.objective().index()).toList());
        assertEquals(new TrackerStyleModel.Summary(1, 3, 0.5), snapshot.focus().summary());
        assertFalse(snapshot.details().get(0).objective().trackable());
        assertTrue(snapshot.details().get(1).text().getSiblings().stream().anyMatch(component ->
                component.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("arc_quest.hud.tracker_style.optional")));
        assertEquals("8/16", snapshot.details().get(2).value());
        assertEquals(16, snapshot.details().get(2).objective().required());
        assertNull(snapshot.focus().status());
    }

    @Test
    void incompleteBooleanUsesCheckboxAndNotFractionalProgress() {
        var entry = new ObjectiveEntry(ObjectiveType.TALK, ResourceLocation.parse("test:npc"), 2,
                Component.literal("Speak"), false, false, Map.of());
        var definition = QuestBuilder.create(QUEST).phase(PhaseBuilder.create(FIRST).objective(entry)).build();
        var runtime = runtime(definition);
        runtime.setObjectiveProgress(FIRST, 0, 1);
        var snapshot = snapshot(new TrackerStyleRenderer(), definition, runtime, 1000);
        assertEquals(0, snapshot.focus().summary().progress());
        assertEquals("", snapshot.details().get(0).value());
    }

    @Test
    void explicitCollectionProgressAndRewardsRemainVisibleWithoutDisclosingEntries() {
        var category = new CollectionCategoryDefinition("category", QuestText.literal("Category"), null,
                0, List.of(), List.of(), List.of());
        var config = new CollectionQuestConfig(List.of(category), List.of(), List.of(), null, null, false, true, false);
        var entry = new CollectionEntryConfig("category", null, null, List.of(), null, 5,
                false, false, 0, null, List.of(), 0, true);
        var definition = QuestBuilder.create(QUEST).mode(QuestMode.COLLECTION).collectionConfig(config)
                .phase(PhaseBuilder.create(FIRST).collectionEntryConfig(entry).objective(goal(5)))
                .phase(PhaseBuilder.create(SECOND).displayName("Undiscovered secret").objective(goal(2))).build();
        var runtime = runtime(definition);
        var progress = new ObjectiveEntry(ObjectiveType.CUSTOM, ResourceLocation.parse("arc_quest:collection_tracker/collection_progress"),
                100, Component.literal("Collection"), false, false, Map.of("tracker_progress", "25"));
        var rewards = new ObjectiveEntry(ObjectiveType.CUSTOM, ResourceLocation.parse("arc_quest:collection_tracker/claimable_rewards"),
                2, Component.literal("Rewards"), false, false, Map.of("tracker_progress", "2"));
        var snapshot = new TrackerStyleRenderer().snapshot(runtime, definition, "arc_quest:collection_tracker_summary",
                List.of(progress, rewards), List.of(FIRST, SECOND), 1000);
        assertEquals(1, snapshot.lanes().size());
        assertEquals(0, snapshot.otherPhases());
        assertEquals(new TrackerStyleModel.Summary(0, 1, 0.25), snapshot.focus().summary());
        assertEquals(List.of("25/100", "2"), snapshot.details().stream().map(TrackerStyleRenderer.Detail::value).toList());
        assertFalse(snapshot.details().get(1).objective().trackable());
        assertFalse(snapshot.details().get(1).objective().complete());
    }

    @Test
    void malformedExplicitProgressFallsBackToAuthoritativeRuntimeValue() {
        var definition = QuestBuilder.create(QUEST).phase(PhaseBuilder.create(FIRST)
                .objective(goal(10).extra("tracker_progress", "not_a_number"))).build();
        var runtime = runtime(definition);
        runtime.setObjectiveProgress(FIRST, 0, 4);
        var snapshot = snapshot(new TrackerStyleRenderer(), definition, runtime, 1000);
        assertEquals("4/10", snapshot.details().get(0).value());
    }

    @Test
    void hiddenNullInstructionCannotClaimThatObjectivesAreReady() {
        var definition = QuestBuilder.create(QUEST).phase(PhaseBuilder.create(FIRST)
                .objective(ObjectiveBuilder.nullObjective().hidden())).build();
        var snapshot = snapshot(new TrackerStyleRenderer(), definition, runtime(definition), 1000);
        assertEquals(0, snapshot.focus().summary().total());
        assertTrue(snapshot.details().isEmpty());
        assertEquals("arc_quest.hud.tracker_style.empty", key(snapshot.emptyMessage()));
    }

    @Test
    void phaseStatusUsesManualAndCompletionStateRatherThanInferringFromVisibleRows() {
        var definition = QuestBuilder.create(QUEST).phase(PhaseBuilder.create(FIRST)
                .objective(goal(1)).objective(goal(10).hidden())).build();
        var runtime = runtime(definition);
        runtime.setObjectiveProgress(FIRST, 0, 1);
        var renderer = new TrackerStyleRenderer();
        var visibleReady = snapshot(renderer, definition, runtime, 1000);
        assertEquals(1, visibleReady.focus().summary().progress());
        assertNull(visibleReady.focus().status());
        assertEquals("arc_quest.hud.tracker_style.empty", key(visibleReady.emptyMessage()));

        runtime.markPhasePendingManualAdvance(FIRST);
        var pending = snapshot(renderer, definition, runtime, 1200);
        assertEquals("arc_quest.hud.toast.phase_pending_confirm", key(pending.focus().status()));
        runtime.completePhase(FIRST);
        var completed = snapshot(renderer, definition, runtime, 1400);
        assertEquals("arc_quest.hud.tracker_style.phase_complete", key(completed.focus().status()));
    }

    @Test
    void boundedCacheRefreshesAndRefillsRowsWithoutMutatingRuntime() {
        var definition = QuestBuilder.create(QUEST).phase(PhaseBuilder.create(FIRST)
                .objective(goal(1)).objective(goal(2)).objective(goal(3)).objective(goal(4))).build();
        var runtime = runtime(definition);
        var renderer = new TrackerStyleRenderer();
        var first = snapshot(renderer, definition, runtime, 1000);
        assertSame(first, snapshot(renderer, definition, runtime, 1050));
        runtime.setObjectiveProgress(FIRST, 0, 1);
        var before = runtime.serializeNBT();
        var updated = snapshot(renderer, definition, runtime, 1100);
        assertEquals(List.of(1, 2, 3), updated.details().stream().map(row -> row.objective().index()).toList());
        assertEquals(before, runtime.serializeNBT());
        assertNotSame(first, updated);
        assertEquals(1, first.otherObjectives());
        assertEquals(0, updated.otherObjectives());
    }

    @Test
    void anyPhaseCompletionPolicyDoesNotBecomeAnAllPhasesRequirement() {
        var definition = QuestBuilder.create(QUEST).completionPolicy(QuestCompletionPolicy.ANY)
                .phase(PhaseBuilder.create(FIRST).objective(goal(1)))
                .phase(PhaseBuilder.create(SECOND).objective(goal(100))).build();
        var runtime = runtime(definition);
        runtime.activatePhase(SECOND, 1);
        runtime.setObjectiveProgress(FIRST, 0, 1);
        runtime.completePhase(FIRST);
        var before = runtime.serializeNBT();
        var snapshot = new TrackerStyleRenderer().snapshot(runtime, definition, SECOND,
                definition.getPhase(SECOND).getObjectives(), List.of(SECOND), 1000);
        assertEquals(QuestCompletionPolicy.ANY, definition.getCompletionPolicy());
        assertTrue(runtime.isPhaseCompleted(FIRST));
        assertEquals(before, runtime.serializeNBT());
        assertEquals(1, snapshot.lanes().size());
        assertEquals(SECOND, snapshot.focus().id());
        assertEquals(0, snapshot.focus().summary().progress());
        // The bar only describes this lane. No synthetic global quest percentage
        // or completion decision is introduced by the alternative HUD styles.
    }

    private static ObjectiveBuilder goal(int count) {
        return ObjectiveBuilder.custom(ResourceLocation.parse("test:goal"), count).display("Goal");
    }

    private static QuestRuntimeData runtime(QuestDefinition definition) {
        return new QuestRuntimeData(QUEST, FIRST, definition.getPhase(FIRST).getObjectives().size(), 0, 0, 0);
    }

    private static TrackerStyleRenderer.Snapshot snapshot(TrackerStyleRenderer renderer, QuestDefinition definition,
                                                         QuestRuntimeData runtime, long now) {
        return renderer.snapshot(runtime, definition, FIRST, definition.getPhase(FIRST).getObjectives(), List.of(FIRST), now);
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
