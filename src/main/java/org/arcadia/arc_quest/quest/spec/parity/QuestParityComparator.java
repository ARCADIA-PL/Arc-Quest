package org.arcadia.arc_quest.quest.spec.parity;

import org.arcadia.arc_quest.quest.spec.ObjectiveSpec;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class QuestParityComparator {

    public record Diff(String path, Object expected, Object actual) {}

    public List<Diff> compare(QuestSpec expected, QuestSpec actual) {
        List<Diff> diffs = new ArrayList<>();
        if (expected == null || actual == null) {
            if (expected != actual) diffs.add(new Diff("quest", expected, actual));
            return diffs;
        }

        compareValue(diffs, "id", expected.id, actual.id);
        compareValue(diffs, "category", expected.category, actual.category);
        compareValue(diffs, "mode", expected.mode, actual.mode);
        compareValue(diffs, "initialPhaseId", expected.initialPhaseId, actual.initialPhaseId);
        compareValue(diffs, "sortOrder", expected.sortOrder, actual.sortOrder);
        compareValue(diffs, "repeatable", expected.repeatable, actual.repeatable);
        compareValue(diffs, "phases.size", expected.phases.size(), actual.phases.size());

        int size = Math.min(expected.phases.size(), actual.phases.size());
        for (int i = 0; i < size; i++) {
            comparePhase(diffs, i, expected.phases.get(i), actual.phases.get(i));
        }
        return diffs;
    }

    private void comparePhase(List<Diff> diffs, int idx, PhaseSpec expected, PhaseSpec actual) {
        String p = "phases[" + idx + "]";
        compareValue(diffs, p + ".phaseId", expected.phaseId, actual.phaseId);
        compareValue(diffs, p + ".objectives.size", safeSize(expected.objectives), safeSize(actual.objectives));
        compareValue(diffs, p + ".transitions.size", safeSize(expected.transitions), safeSize(actual.transitions));
        compareValue(diffs, p + ".choices.size", safeSize(expected.choices), safeSize(actual.choices));
        compareValue(diffs, p + ".flagsToSetOnEnter.size", safeSize(expected.flagsToSetOnEnter), safeSize(actual.flagsToSetOnEnter));
        compareValue(diffs, p + ".flagsToSetOnComplete.size", safeSize(expected.flagsToSetOnComplete), safeSize(actual.flagsToSetOnComplete));

        int objectiveCount = Math.min(safeSize(expected.objectives), safeSize(actual.objectives));
        for (int i = 0; i < objectiveCount; i++) {
            compareObjective(diffs, p + ".objectives[" + i + "]", expected.objectives.get(i), actual.objectives.get(i));
        }
    }

    private void compareObjective(List<Diff> diffs, String path, ObjectiveSpec expected, ObjectiveSpec actual) {
        compareValue(diffs, path + ".type", expected.type, actual.type);
        compareValue(diffs, path + ".targetId", expected.targetId, actual.targetId);
        compareValue(diffs, path + ".requiredCount", expected.requiredCount, actual.requiredCount);
        compareValue(diffs, path + ".hidden", expected.hidden, actual.hidden);
        compareValue(diffs, path + ".optional", expected.optional, actual.optional);
    }

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private void compareValue(List<Diff> diffs, String path, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            diffs.add(new Diff(path, expected, actual));
        }
    }
}
