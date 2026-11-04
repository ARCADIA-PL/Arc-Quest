package org.arcadia.arc_quest.data.reload;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReloadResourceBudgetTest {
    private static <T> ModulePreparation<T> module(String name, long bytes) {
        return new ModulePreparation<>(name, null,
                Set.of(Path.of("__resources__", "main", "example", name + ".json")), bytes, List.of());
    }

    private static ArcQuestReloadCoordinator.ReloadPlan plan() {
        return new ArcQuestReloadCoordinator.ReloadPlan(module("quest", 100), module("dialogue", 20),
                module("npc", 10), module("trade", 30), module("gacha", 10), module("guide", 40),
                System.nanoTime(), new ArrayList<>());
    }

    @Test
    void jarResourcesUseScannedBytesWithoutOpeningDiagnosticPaths() {
        var plan = plan();
        ArcQuestReloadCoordinator.validateGlobalLimits(plan, new ReloadLimits(6, 1000, 8, 100, 210));
        assertTrue(plan.allDiagnostics().isEmpty(), () -> plan.allDiagnostics().toString());
    }

    @Test
    void combinedResourceBudgetsStillRejectExcessBytesAndFiles() {
        var plan = plan();
        ArcQuestReloadCoordinator.validateGlobalLimits(plan, new ReloadLimits(5, 1000, 8, 100, 209));
        assertEquals(2, plan.allDiagnostics().size());
        assertTrue(plan.allDiagnostics().stream().allMatch(ReloadDiagnostic::isBlocking));
        assertTrue(plan.allDiagnostics().stream().anyMatch(d -> d.message().contains("210")));
    }
}
