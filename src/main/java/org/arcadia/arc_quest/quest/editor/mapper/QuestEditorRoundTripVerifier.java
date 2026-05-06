package org.arcadia.arc_quest.quest.editor.mapper;

import org.arcadia.arc_quest.quest.editor.model.EditableQuest;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class QuestEditorRoundTripVerifier {
    private final QuestSpecToEditableQuestMapper toEditable = new QuestSpecToEditableQuestMapper();
    private final EditableQuestToQuestSpecMapper toSpec = new EditableQuestToQuestSpecMapper();

    public RoundTripReport verify(QuestSpec original) {
        EditableQuest editable = toEditable.map(original);
        QuestSpec remapped = toSpec.map(editable);

        List<String> errors = new ArrayList<>();
        compareField(errors, "id", original == null ? null : original.id, remapped.id);
        compareField(errors, "category", original == null ? null : original.category, remapped.category);
        compareField(errors, "initialPhaseId", original == null ? null : original.initialPhaseId, remapped.initialPhaseId);
        compareField(errors, "phaseCount", original == null ? 0 : original.phases.size(), remapped.phases.size());
        compareField(errors, "unlockConditionCount", original == null ? 0 : original.unlockConditions.size(), remapped.unlockConditions.size());
        compareField(errors, "completionRewardCount", original == null ? 0 : original.completionRewards.size(), remapped.completionRewards.size());
        compareField(errors, "questMarkCount", original == null ? 0 : original.relatedMarks.size(), remapped.relatedMarks.size());
        compareField(errors, "completionPolicy", original == null ? null : original.completionPolicy, remapped.completionPolicy);

        if (original != null) {
            for (int i = 0; i < Math.min(original.phases.size(), remapped.phases.size()); i++) {
                comparePhase(errors, i, original.phases.get(i), remapped.phases.get(i));
            }
        }

        return new RoundTripReport(errors.isEmpty(), errors, editable, remapped);
    }

    private void comparePhase(List<String> errors, int index, PhaseSpec original, PhaseSpec remapped) {
        compareField(errors, "phases[" + index + "].phaseId", original.phaseId, remapped.phaseId);
        compareField(errors, "phases[" + index + "].objectiveCount", original.objectives.size(), remapped.objectives.size());
        compareField(errors, "phases[" + index + "].transitionCount", original.transitions.size(), remapped.transitions.size());
        compareField(errors, "phases[" + index + "].choiceCount", original.choices.size(), remapped.choices.size());
        compareField(errors, "phases[" + index + "].phaseRewardCount", original.phaseRewards.size(), remapped.phaseRewards.size());
        compareField(errors, "phases[" + index + "].markCount", original.relatedMarks.size(), remapped.relatedMarks.size());
    }

    private void compareField(List<String> errors, String label, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            errors.add(label + " mismatch: expected=" + expected + ", actual=" + actual);
        }
    }

    public record RoundTripReport(boolean success, List<String> errors, EditableQuest editable, QuestSpec remappedSpec) {
    }
}
