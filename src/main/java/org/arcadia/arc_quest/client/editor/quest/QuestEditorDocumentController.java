package org.arcadia.arc_quest.client.editor.quest;

import org.arcadia.arc_quest.quest.spec.ObjectiveSpec;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.quest.spec.validate.ValidationIssue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

final class QuestEditorDocumentController {
    private static final int MAX_HISTORY = 64;
    private QuestSpec document;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private List<ValidationIssue> issues = List.of();
    private long generation;

    QuestEditorDocumentController(QuestSpec document) {
        this.document = document;
        validate();
    }

    QuestSpec document() { return document; }
    long generation() { return generation; }
    List<ValidationIssue> issues() { return issues; }
    boolean hasErrors() { return issues.stream().anyMatch(issue -> issue.severity == ValidationIssue.Severity.ERROR); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    void mutate(Consumer<QuestSpec> mutation) {
        String before = QuestSpecJsonWriter.write(document);
        mutation.accept(document);
        String after = QuestSpecJsonWriter.write(document);
        if (before.equals(after)) return;
        undo.addLast(before);
        while (undo.size() > MAX_HISTORY) undo.removeFirst();
        redo.clear();
        generation++;
        validate();
    }

    void undo() {
        if (undo.isEmpty()) return;
        redo.addLast(QuestSpecJsonWriter.write(document));
        document = QuestSpecJsonReader.read(undo.removeLast());
        generation++;
        validate();
    }

    void redo() {
        if (redo.isEmpty()) return;
        undo.addLast(QuestSpecJsonWriter.write(document));
        document = QuestSpecJsonReader.read(redo.removeLast());
        generation++;
        validate();
    }

    PhaseSpec addPhase() {
        String id = nextPhaseId();
        PhaseSpec phase = new PhaseSpec();
        phase.phaseId = id;
        phase.displayName.value = id;
        ObjectiveSpec objective = new ObjectiveSpec();
        objective.id = "objective_1";
        phase.objectives.add(objective);
        mutate(spec -> {
            spec.phases.add(phase);
            if (spec.initialPhaseId == null || spec.initialPhaseId.isBlank()) spec.initialPhaseId = id;
        });
        return phase;
    }

    boolean deletePhase(String phaseId) {
        if (phaseId == null || phaseId.equals(document.initialPhaseId)
                || document.initialPhaseIds.contains(phaseId)) return false;
        mutate(spec -> {
            spec.phases.removeIf(phase -> phaseId.equals(phase.phaseId));
            spec.phases.forEach(phase -> {
                phase.transitions.removeIf(transition -> phaseId.equals(transition.targetPhaseId)
                        || transition.targetPhaseIds.contains(phaseId));
                phase.choices.removeIf(choice -> phaseId.equals(choice.targetPhaseId));
            });
            if (phaseId.equals(spec.completionTargetPhaseId)) spec.completionTargetPhaseId = "";
        });
        return true;
    }

    private String nextPhaseId() {
        int suffix = document.phases.size() + 1;
        while (containsPhase("phase_" + suffix)) suffix++;
        return "phase_" + suffix;
    }

    private boolean containsPhase(String id) {
        return document.phases.stream().anyMatch(phase -> id.equals(phase.phaseId));
    }

    private void validate() {
        issues = List.copyOf(new QuestSpecValidator().validate(document).getIssues());
    }
}
