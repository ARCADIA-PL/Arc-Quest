package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestTrackingMenuEntryTest {

    @Test
    void fallsBackToFirstPhaseHistoryImageWhenQuestHasNoSplash() {
        ResourceLocation historyImage = ResourceLocation.fromNamespaceAndPath(
                "arc_quest", "textures/gui/splash/phase_history.png");
        QuestDefinition definition = QuestBuilder.create("arc_quest:history_fallback")
                .phase(PhaseBuilder.create("arc_quest:first")
                        .historyImage(historyImage)
                        .objective(ObjectiveBuilder.nullObjective()))
                .build();

        assertEquals(historyImage, QuestTrackingMenuEntry.resolveSplashTexture(definition));
    }

    @Test
    void keepsFallbackEmptyWhenQuestAndFirstPhaseHaveNoSplash() {
        QuestDefinition definition = QuestBuilder.create("arc_quest:no_splash")
                .phase(PhaseBuilder.create("arc_quest:first")
                        .objective(ObjectiveBuilder.nullObjective()))
                .build();

        assertNull(QuestTrackingMenuEntry.resolveSplashTexture(definition));
    }

    @Test
    void ordersActiveParallelPhasesByQuestDefinition() {
        QuestDefinition definition = QuestBuilder.create("arc_quest:parallel_test")
                .phase(PhaseBuilder.create("arc_quest:first")
                        .objective(ObjectiveBuilder.nullObjective()))
                .phase(PhaseBuilder.create("arc_quest:second")
                        .objective(ObjectiveBuilder.nullObjective()))
                .phase(PhaseBuilder.create("arc_quest:third")
                        .objective(ObjectiveBuilder.nullObjective()))
                .build();
        QuestRuntimeData runtime = new QuestRuntimeData(
                "arc_quest:parallel_test", "arc_quest:third", 1, 0L, 0L, 0L);
        runtime.activatePhase("arc_quest:first", 1);
        runtime.activatePhase("arc_quest:second", 1);

        List<String> phaseIds = QuestTrackingMenuEntry.orderedActivePhases(definition, runtime)
                .stream()
                .map(QuestTrackingMenuPhaseEntry::phaseId)
                .toList();

        assertEquals(List.of("arc_quest:first", "arc_quest:second", "arc_quest:third"), phaseIds);
    }
}
