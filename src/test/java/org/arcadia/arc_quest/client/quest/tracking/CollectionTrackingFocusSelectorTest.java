package org.arcadia.arc_quest.client.quest.tracking;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CollectionTrackingFocusSelectorTest {
    private static final ResourceLocation A = ResourceLocation.parse("example:a");
    private static final ResourceLocation B = ResourceLocation.parse("example:b");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void defaultSelectionUsesStableProjectionOrderAndDoesNotChangeAuthoritativeProgress() {
        var quest = quest();
        var runtime = runtime(quest);
        var second = binding("b", B, false, requirement(false));
        var first = binding("a", A, false, requirement(false));
        var sheets = Map.of("field", sheet(false, second, first));
        var before = runtime.serializeNBT().copy();
        var selected = CollectionTrackingFocusSelector.select(quest, runtime, null, null, sheets::get);
        assertNotNull(selected);
        assertEquals("field", selected.phaseId());
        assertEquals("b", selected.bindingId());
        assertEquals(before, runtime.serializeNBT());
    }

    @Test void explicitOtherEntryWinsOverTheFirstUnfinishedEntryAndDuplicateIdsStayInTheirPhase() {
        var quest = quest();
        var runtime = runtime(quest);
        runtime.activatePhase("mine", 0);
        var sheets = Map.of("field", sheet(false, binding("a", A, false, requirement(false)),
                        binding("b", B, false, requirement(false))),
                "mine", sheet(false, binding("a", A, false, requirement(false))));
        var second = CollectionTrackingFocusSelector.select(quest, runtime, "field", "b", sheets::get);
        assertNotNull(second);
        assertEquals("b", second.bindingId());
        var duplicate = CollectionTrackingFocusSelector.select(quest, runtime, "mine", "a", sheets::get);
        assertNotNull(duplicate);
        assertEquals("mine", duplicate.phaseId());
        assertEquals("a", duplicate.bindingId());
    }

    @Test void completedHiddenAndUnrevealedEntriesAreSkipped() {
        var quest = quest();
        var runtime = runtime(quest);
        var hiddenRequirement = new CollectionRequirementProgress("secret", Component.literal("secret"),
                ObjectiveBuilder.nullObjective().hidden().build(), 0, 0, 1, false, false);
        var invisible = new CollectionBindingProgress("invisible", A, false, true, false, false, false,
                List.of(requirement(false)), List.of());
        var unrevealed = new CollectionBindingProgress("unrevealed", A, true, false, false, false, false,
                List.of(requirement(false)), List.of());
        var hidden = binding("secret", A, false, hiddenRequirement);
        var done = binding("done", A, true, requirement(true));
        var visible = binding("b", B, false, requirement(false));
        var projected = sheet(false, done, invisible, unrevealed, hidden, visible);
        var selected = CollectionTrackingFocusSelector.select(quest, runtime, "field", "done",
                phase -> projected);
        assertNotNull(selected);
        assertEquals("b", selected.bindingId());
        for (var excluded : List.of(done, invisible, unrevealed, hidden)) {
            assertFalse(CollectionTrackingFocusSelector.actionable(excluded));
        }
    }

    @Test void completionSelectsTheNextActivePhaseAndPendingConfirmationDoesNotCreateAnOverview() {
        var quest = quest();
        var runtime = runtime(quest);
        runtime.activatePhase("mine", 0);
        var sheets = Map.of("field", sheet(true, binding("a", A, true, requirement(true))),
                "mine", sheet(false, binding("a", A, false, requirement(false))));
        runtime.markPhasePendingManualAdvance("field");
        var selected = CollectionTrackingFocusSelector.select(quest, runtime, "field", "a", sheets::get);
        assertNotNull(selected);
        assertEquals("mine", selected.phaseId());
        runtime.completePhase("mine");
        assertNull(CollectionTrackingFocusSelector.select(quest, runtime, "field", null, sheets::get));
    }

    @Test void terminalRunsAndInactivePhasesNeverAcquireNewFocus() {
        var quest = quest();
        var runtime = runtime(quest);
        var sheets = Map.of("field", CollectionSheetProgress.EMPTY,
                "mine", sheet(false, binding("a", A, false, requirement(false))));
        assertNull(CollectionTrackingFocusSelector.select(quest, runtime, "mine", "a", sheets::get));
        runtime.activatePhase("mine", 0);
        for (QuestState terminal : List.of(QuestState.COMPLETED, QuestState.FAILED)) {
            runtime.setState(terminal);
            assertNull(CollectionTrackingFocusSelector.select(quest, runtime, "mine", "a", sheets::get));
        }
    }

    @Test void runIdentityUsesTheRealUuidAndFallsBackWhileTheSheetHasNotInitialized() {
        var quest = quest();
        var runtime = runtime(quest);
        assertEquals("20:10", CollectionTrackingFocusSelector.runId(runtime));
        runtime.setCollectionData(new CollectionRuntimeData());
        assertEquals("20:10", CollectionTrackingFocusSelector.runId(runtime));
        runtime.getCollectionData().initializeSheet("field", List.of("a"), 1, Set.of());
        assertEquals(runtime.getCollectionData().getRunId(), CollectionTrackingFocusSelector.runId(runtime));
        var repeat = runtime(quest);
        repeat.getOrCreateCollectionData().initializeSheet("field", List.of("a"), 1, Set.of());
        assertNotEquals(CollectionTrackingFocusSelector.runId(runtime), CollectionTrackingFocusSelector.runId(repeat));
    }

    private static CollectionRequirementProgress requirement(boolean done) {
        return new CollectionRequirementProgress("discovered", Component.literal("Discover"), null, -1,
                done ? 1 : 0, 1, done, false);
    }

    private static CollectionBindingProgress binding(String id, ResourceLocation entry, boolean done,
                                                      CollectionRequirementProgress... requirements) {
        return new CollectionBindingProgress(id, entry, true, true, done, false, done,
                List.of(requirements), List.of());
    }

    private static CollectionSheetProgress sheet(boolean done, CollectionBindingProgress... bindings) {
        return new CollectionSheetProgress(done ? bindings.length : 0, bindings.length, bindings.length, done,
                List.of(bindings), List.of());
    }

    private static QuestDefinition quest() {
        var entries = CollectionQuestConfigBuilder.create().category("materials", "Materials")
                .entry(CollectionEntryBuilder.create(A).category("materials").displayName("A"))
                .entry(CollectionEntryBuilder.create(B).category("materials").displayName("B")).build();
        return QuestBuilder.create("example:tracking").mode(QuestMode.COLLECTION).collectionConfig(entries)
                .phase(PhaseBuilder.create("field").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("a", A).discovered())
                        .binding(EntryRequirementBuilder.create("b", B).discovered())))
                .phase(PhaseBuilder.create("mine").collectionSheet(CollectionSheetBuilder.create()
                        .binding(EntryRequirementBuilder.create("a", A).discovered())))
                .build();
    }

    private static QuestRuntimeData runtime(QuestDefinition quest) {
        return new QuestRuntimeData(quest.getId().toString(), "field", 0, 10, 20, 30);
    }
}
