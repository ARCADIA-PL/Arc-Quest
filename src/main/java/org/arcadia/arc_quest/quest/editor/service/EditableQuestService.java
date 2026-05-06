package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.editor.model.*;
import org.arcadia.arc_quest.quest.spec.ConditionSpec;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class EditableQuestService {
    public EditablePhase addPhase(EditableQuest quest, String phaseId, double x, double y) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase phase = new EditablePhase();
        phase.nodeId = newNodeId();
        phase.phaseId = phaseId == null ? "" : phaseId;
        phase.displayName = QuestTextSpec.literal(phase.phaseId);
        quest.phases.add(phase);
        quest.layout.phasePositions.put(phase.nodeId, new EditorNodePosition(x, y));
        if (quest.initialPhaseNodeId == null || quest.initialPhaseNodeId.isBlank()) {
            quest.initialPhaseNodeId = phase.nodeId;
        }
        return phase;
    }

    public boolean removePhase(EditableQuest quest, String phaseNodeId) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase phase = getPhase(quest, phaseNodeId);
        if (phase == null) return false;
        quest.phases.remove(phase);
        quest.connections.removeIf(c -> phaseNodeId.equals(c.sourcePhaseNodeId) || phaseNodeId.equals(c.targetPhaseNodeId));
        quest.layout.phasePositions.remove(phaseNodeId);
        if (phaseNodeId.equals(quest.initialPhaseNodeId)) {
            quest.initialPhaseNodeId = quest.phases.isEmpty() ? "" : quest.phases.get(0).nodeId;
        }
        return true;
    }

    public boolean updateQuestId(EditableQuest quest, String questId) {
        Objects.requireNonNull(quest, "quest");
        quest.meta.questId = questId == null ? "" : questId;
        return true;
    }

    public boolean updateQuestDisplay(EditableQuest quest, QuestTextSpec displayName, QuestTextSpec description) {
        Objects.requireNonNull(quest, "quest");
        quest.meta.displayName = displayName == null ? QuestTextSpec.literal("") : displayName;
        quest.meta.description = description == null ? QuestTextSpec.literal("") : description;
        return true;
    }

    public boolean updatePhaseId(EditableQuest quest, String phaseNodeId, String phaseId) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase phase = getPhase(quest, phaseNodeId);
        if (phase == null) return false;
        phase.phaseId = phaseId == null ? "" : phaseId;
        return true;
    }

    public boolean updatePhaseDisplay(EditableQuest quest, String phaseNodeId, QuestTextSpec displayName, QuestTextSpec description, QuestTextSpec story) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase phase = getPhase(quest, phaseNodeId);
        if (phase == null) return false;
        phase.displayName = displayName == null ? QuestTextSpec.literal("") : displayName;
        phase.description = description == null ? QuestTextSpec.literal("") : description;
        phase.story = story == null ? QuestTextSpec.literal("") : story;
        return true;
    }

    public EditableObjective addObjective(EditableQuest quest, String phaseNodeId) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase phase = requirePhase(quest, phaseNodeId);
        EditableObjective objective = new EditableObjective();
        objective.objectiveId = newObjectiveId();
        phase.objectives.add(objective);
        return objective;
    }

    public boolean removeObjective(EditableQuest quest, String phaseNodeId, String objectiveId) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase phase = getPhase(quest, phaseNodeId);
        if (phase == null || phase.objectives == null) return false;
        return phase.objectives.removeIf(objective -> objectiveId.equals(objective.objectiveId));
    }

    public boolean moveObjective(EditableQuest quest, String phaseNodeId, String objectiveId, int newIndex) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase phase = getPhase(quest, phaseNodeId);
        if (phase == null || phase.objectives == null) return false;
        int currentIndex = indexOfObjective(phase.objectives, objectiveId);
        if (currentIndex < 0) return false;
        int boundedIndex = Math.max(0, Math.min(newIndex, phase.objectives.size() - 1));
        if (currentIndex == boundedIndex) return true;
        EditableObjective objective = phase.objectives.remove(currentIndex);
        phase.objectives.add(boundedIndex, objective);
        return true;
    }

    public boolean updateObjectiveBasic(EditableQuest quest, String phaseNodeId, String objectiveId, String targetId, int requiredCount, QuestTextSpec displayText) {
        Objects.requireNonNull(quest, "quest");
        EditableObjective objective = getObjective(quest, phaseNodeId, objectiveId);
        if (objective == null) return false;
        objective.targetId = targetId == null ? "" : targetId;
        objective.requiredCount = requiredCount;
        objective.displayText = displayText == null ? QuestTextSpec.literal("???") : displayText;
        return true;
    }

    public EditableConnection connectTransition(EditableQuest quest, String sourcePhaseNodeId, String targetPhaseNodeId, ConditionSpec condition) {
        Objects.requireNonNull(quest, "quest");
        EditableConnection connection = new EditableConnection();
        connection.connectionId = newConnectionId();
        connection.sourcePhaseNodeId = sourcePhaseNodeId;
        connection.targetPhaseNodeId = targetPhaseNodeId;
        connection.connectionType = EditableConnectionType.TRANSITION;
        connection.condition = condition;
        connection.priority = nextPriority(quest, sourcePhaseNodeId, EditableConnectionType.TRANSITION);
        quest.connections.add(connection);
        return connection;
    }

    public EditableChoice addChoice(EditableQuest quest, String sourcePhaseNodeId, String targetPhaseNodeId, String text, String flagToSet, ConditionSpec visibleCondition) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase source = requirePhase(quest, sourcePhaseNodeId);
        EditableChoice choice = new EditableChoice();
        choice.choiceId = newChoiceId();
        choice.text = QuestTextSpec.literal(text == null ? "" : text);
        choice.flagToSet = flagToSet == null ? "" : flagToSet;
        choice.visibleCondition = visibleCondition;
        choice.targetPhaseNodeId = targetPhaseNodeId;
        source.choices.add(choice);

        EditableConnection connection = new EditableConnection();
        connection.connectionId = newConnectionId();
        connection.sourcePhaseNodeId = sourcePhaseNodeId;
        connection.targetPhaseNodeId = targetPhaseNodeId;
        connection.connectionType = EditableConnectionType.CHOICE;
        connection.choiceId = choice.choiceId;
        connection.priority = nextPriority(quest, sourcePhaseNodeId, EditableConnectionType.CHOICE);
        quest.connections.add(connection);
        return choice;
    }

    public boolean removeChoice(EditableQuest quest, String sourcePhaseNodeId, String choiceId) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase source = getPhase(quest, sourcePhaseNodeId);
        if (source == null) return false;
        boolean removed = source.choices.removeIf(choice -> choiceId.equals(choice.choiceId));
        if (removed) {
            quest.connections.removeIf(c -> c.connectionType == EditableConnectionType.CHOICE && choiceId.equals(c.choiceId));
        }
        return removed;
    }

    public boolean updateChoiceTarget(EditableQuest quest, String sourcePhaseNodeId, String choiceId, String targetPhaseNodeId) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase source = getPhase(quest, sourcePhaseNodeId);
        if (source == null || source.choices == null) return false;
        for (EditableChoice choice : source.choices) {
            if (choiceId.equals(choice.choiceId)) {
                choice.targetPhaseNodeId = targetPhaseNodeId == null ? "" : targetPhaseNodeId;
                if (quest.connections != null) {
                    for (EditableConnection connection : quest.connections) {
                        if (connection.connectionType == EditableConnectionType.CHOICE && choiceId.equals(connection.choiceId)) {
                            connection.targetPhaseNodeId = choice.targetPhaseNodeId;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    public boolean updateChoiceText(EditableQuest quest, String sourcePhaseNodeId, String choiceId, QuestTextSpec text, String flagToSet, ConditionSpec visibleCondition) {
        Objects.requireNonNull(quest, "quest");
        EditablePhase source = getPhase(quest, sourcePhaseNodeId);
        if (source == null || source.choices == null) return false;
        for (EditableChoice choice : source.choices) {
            if (choiceId.equals(choice.choiceId)) {
                choice.text = text == null ? QuestTextSpec.literal("") : text;
                choice.flagToSet = flagToSet == null ? "" : flagToSet;
                choice.visibleCondition = visibleCondition;
                return true;
            }
        }
        return false;
    }

    public boolean setInitialPhase(EditableQuest quest, String phaseNodeId) {
        Objects.requireNonNull(quest, "quest");
        if (getPhase(quest, phaseNodeId) == null) return false;
        quest.initialPhaseNodeId = phaseNodeId;
        return true;
    }

    public boolean movePhaseNode(EditableQuest quest, String phaseNodeId, double x, double y) {
        Objects.requireNonNull(quest, "quest");
        if (getPhase(quest, phaseNodeId) == null) return false;
        quest.layout.phasePositions.put(phaseNodeId, new EditorNodePosition(x, y));
        return true;
    }

    public EditablePhase getPhase(EditableQuest quest, String phaseNodeId) {
        for (EditablePhase phase : quest.phases) {
            if (phaseNodeId.equals(phase.nodeId)) return phase;
        }
        return null;
    }

    public EditableObjective getObjective(EditableQuest quest, String phaseNodeId, String objectiveId) {
        EditablePhase phase = getPhase(quest, phaseNodeId);
        if (phase == null || phase.objectives == null) return null;
        for (EditableObjective objective : phase.objectives) {
            if (objectiveId.equals(objective.objectiveId)) return objective;
        }
        return null;
    }

    private EditablePhase requirePhase(EditableQuest quest, String phaseNodeId) {
        EditablePhase phase = getPhase(quest, phaseNodeId);
        if (phase == null) throw new IllegalArgumentException("Unknown phase node id: " + phaseNodeId);
        return phase;
    }

    private int nextPriority(EditableQuest quest, String sourcePhaseNodeId, EditableConnectionType type) {
        return quest.connections.stream()
                .filter(c -> sourcePhaseNodeId.equals(c.sourcePhaseNodeId) && c.connectionType == type)
                .max(Comparator.comparingInt(c -> c.priority))
                .map(c -> c.priority + 1)
                .orElse(0);
    }

    private int indexOfObjective(List<EditableObjective> objectives, String objectiveId) {
        for (int i = 0; i < objectives.size(); i++) {
            if (objectiveId.equals(objectives.get(i).objectiveId)) return i;
        }
        return -1;
    }

    private String newNodeId() {
        return "node_" + UUID.randomUUID().toString().replace('-', '_');
    }

    private String newChoiceId() {
        return "choice_" + UUID.randomUUID().toString().replace('-', '_');
    }

    private String newObjectiveId() {
        return "objective_" + UUID.randomUUID().toString().replace('-', '_');
    }

    private String newConnectionId() {
        return "connection_" + UUID.randomUUID().toString().replace('-', '_');
    }
}
