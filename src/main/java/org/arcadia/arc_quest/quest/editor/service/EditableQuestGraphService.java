package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.editor.model.EditableConnection;
import org.arcadia.arc_quest.quest.editor.model.EditableConnectionType;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;

import java.util.*;

public class EditableQuestGraphService {
    public List<EditableConnection> getOutgoingTransitions(EditableQuest quest, String sourcePhaseNodeId) {
        return getOutgoingConnectionsByType(quest, sourcePhaseNodeId, EditableConnectionType.TRANSITION);
    }

    public List<EditableConnection> getOutgoingChoices(EditableQuest quest, String sourcePhaseNodeId) {
        return getOutgoingConnectionsByType(quest, sourcePhaseNodeId, EditableConnectionType.CHOICE);
    }

    public List<EditableConnection> getIncomingConnections(EditableQuest quest, String targetPhaseNodeId) {
        if (quest == null || quest.connections == null) return List.of();
        List<EditableConnection> result = new ArrayList<>();
        for (EditableConnection connection : quest.connections) {
            if (targetPhaseNodeId.equals(connection.targetPhaseNodeId)) {
                result.add(connection);
            }
        }
        result.sort(Comparator.comparingInt(c -> c.priority));
        return result;
    }

    public Set<String> getReachablePhaseNodeIds(EditableQuest quest) {
        if (quest == null || quest.phases == null || quest.phases.isEmpty()) return Set.of();
        String start = quest.initialPhaseNodeId;
        if (start == null || start.isBlank()) return Set.of();
        return getReachablePhaseNodeIds(quest, start);
    }

    public Set<String> getReachablePhaseNodeIds(EditableQuest quest, String startPhaseNodeId) {
        if (quest == null || quest.connections == null || startPhaseNodeId == null || startPhaseNodeId.isBlank())
            return Set.of();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(startPhaseNodeId);
        queue.add(startPhaseNodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (EditableConnection connection : getOutgoingConnections(quest, current)) {
                if (connection.targetPhaseNodeId != null && !connection.targetPhaseNodeId.isBlank() && visited.add(connection.targetPhaseNodeId)) {
                    queue.addLast(connection.targetPhaseNodeId);
                }
            }
        }
        return visited;
    }

    public List<EditablePhase> getUnreachablePhases(EditableQuest quest) {
        if (quest == null || quest.phases == null || quest.phases.isEmpty()) return List.of();
        Set<String> reachable = getReachablePhaseNodeIds(quest);
        List<EditablePhase> result = new ArrayList<>();
        for (EditablePhase phase : quest.phases) {
            if (!reachable.contains(phase.nodeId)) {
                result.add(phase);
            }
        }
        return result;
    }

    public List<EditableConnection> getDanglingChoiceConnections(EditableQuest quest) {
        if (quest == null || quest.connections == null) return List.of();
        Set<String> choiceIds = new HashSet<>();
        if (quest.phases != null) {
            for (EditablePhase phase : quest.phases) {
                if (phase.choices == null) continue;
                phase.choices.forEach(choice -> choiceIds.add(choice.choiceId));
            }
        }
        List<EditableConnection> result = new ArrayList<>();
        for (EditableConnection connection : quest.connections) {
            if (connection.connectionType == EditableConnectionType.CHOICE && !choiceIds.contains(connection.choiceId)) {
                result.add(connection);
            }
        }
        result.sort(Comparator.comparing(c -> c.connectionId == null ? "" : c.connectionId));
        return result;
    }

    private List<EditableConnection> getOutgoingConnectionsByType(EditableQuest quest, String sourcePhaseNodeId, EditableConnectionType type) {
        if (quest == null || quest.connections == null) return List.of();
        List<EditableConnection> result = new ArrayList<>();
        for (EditableConnection connection : quest.connections) {
            if (sourcePhaseNodeId.equals(connection.sourcePhaseNodeId) && connection.connectionType == type) {
                result.add(connection);
            }
        }
        result.sort(Comparator.comparingInt(c -> c.priority));
        return result;
    }

    private List<EditableConnection> getOutgoingConnections(EditableQuest quest, String sourcePhaseNodeId) {
        if (quest == null || quest.connections == null) return List.of();
        List<EditableConnection> result = new ArrayList<>();
        for (EditableConnection connection : quest.connections) {
            if (sourcePhaseNodeId.equals(connection.sourcePhaseNodeId)) {
                result.add(connection);
            }
        }
        result.sort(Comparator.comparingInt(c -> c.priority));
        return result;
    }
}
