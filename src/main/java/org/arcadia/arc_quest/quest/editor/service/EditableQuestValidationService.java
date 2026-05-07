package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.editor.model.*;

import java.util.HashSet;
import java.util.Set;

public class EditableQuestValidationService {
    public EditableValidationReport validate(EditableQuest quest) {
        EditableValidationReport report = new EditableValidationReport();
        if (quest == null) {
            report.add(EditableValidationSeverity.ERROR, "quest", "EditableQuest is null");
            return report;
        }
        if (quest.meta == null) {
            report.add(EditableValidationSeverity.ERROR, "meta", "EditableQuestMeta is required");
            return report;
        }
        if (quest.meta.questId == null || quest.meta.questId.isBlank()) {
            report.add(EditableValidationSeverity.ERROR, "meta.questId", "Quest id is required");
        }

        if (quest.meta.mode == QuestMode.COLLECTION) {
            validateCollection(quest, report);
            return report;
        }

        if (quest.phases == null || quest.phases.isEmpty()) {
            report.add(EditableValidationSeverity.ERROR, "phases", "At least one phase is required");
            return report;
        }

        Set<String> nodeIds = new HashSet<>();
        Set<String> phaseIds = new HashSet<>();
        Set<String> choiceIds = new HashSet<>();

        for (int i = 0; i < quest.phases.size(); i++) {
            EditablePhase phase = quest.phases.get(i);
            String path = "phases[" + i + "]";
            if (phase.nodeId == null || phase.nodeId.isBlank()) {
                report.add(EditableValidationSeverity.ERROR, path + ".nodeId", "Phase nodeId is required");
            } else if (!nodeIds.add(phase.nodeId)) {
                report.add(EditableValidationSeverity.ERROR, path + ".nodeId", "Duplicate phase nodeId: " + phase.nodeId);
            }
            if (phase.phaseId == null || phase.phaseId.isBlank()) {
                report.add(EditableValidationSeverity.ERROR, path + ".phaseId", "Phase phaseId is required");
            } else if (!phaseIds.add(phase.phaseId)) {
                report.add(EditableValidationSeverity.ERROR, path + ".phaseId", "Duplicate phaseId: " + phase.phaseId);
            }
            if (phase.objectives == null || phase.objectives.isEmpty()) {
                report.add(EditableValidationSeverity.ERROR, path + ".objectives", "Phase must contain at least one objective");
            } else {
                for (int j = 0; j < phase.objectives.size(); j++) {
                    EditableObjective objective = phase.objectives.get(j);
                    String objectivePath = path + ".objectives[" + j + "]";
                    if (objective.objectiveId == null || objective.objectiveId.isBlank()) {
                        report.add(EditableValidationSeverity.WARNING, objectivePath + ".objectiveId", "Objective id is blank");
                    }
                    if (objective.targetId == null || objective.targetId.isBlank()) {
                        report.add(EditableValidationSeverity.ERROR, objectivePath + ".targetId", "Objective targetId is required");
                    }
                    if (objective.requiredCount < 1) {
                        report.add(EditableValidationSeverity.ERROR, objectivePath + ".requiredCount", "Objective requiredCount must be >= 1");
                    }
                }
            }
            if (phase.choices != null) {
                for (int j = 0; j < phase.choices.size(); j++) {
                    EditableChoice choice = phase.choices.get(j);
                    String choicePath = path + ".choices[" + j + "]";
                    if (choice.choiceId == null || choice.choiceId.isBlank()) {
                        report.add(EditableValidationSeverity.ERROR, choicePath + ".choiceId", "Choice id is required");
                    } else if (!choiceIds.add(choice.choiceId)) {
                        report.add(EditableValidationSeverity.ERROR, choicePath + ".choiceId", "Duplicate choiceId: " + choice.choiceId);
                    }
                    if (choice.targetPhaseNodeId == null || choice.targetPhaseNodeId.isBlank()) {
                        report.add(EditableValidationSeverity.ERROR, choicePath + ".targetPhaseNodeId", "Choice targetPhaseNodeId is required");
                    }
                }
            }
        }

        if (quest.initialPhaseNodeId == null || quest.initialPhaseNodeId.isBlank()) {
            report.add(EditableValidationSeverity.ERROR, "initialPhaseNodeId", "Initial phase node id is required");
        } else if (!nodeIds.contains(quest.initialPhaseNodeId)) {
            report.add(EditableValidationSeverity.ERROR, "initialPhaseNodeId", "Initial phase node id not found: " + quest.initialPhaseNodeId);
        }

        if (quest.connections != null) {
            Set<String> connectionIds = new HashSet<>();
            for (int i = 0; i < quest.connections.size(); i++) {
                EditableConnection connection = quest.connections.get(i);
                String path = "connections[" + i + "]";
                if (connection.connectionId == null || connection.connectionId.isBlank()) {
                    report.add(EditableValidationSeverity.ERROR, path + ".connectionId", "Connection id is required");
                } else if (!connectionIds.add(connection.connectionId)) {
                    report.add(EditableValidationSeverity.ERROR, path + ".connectionId", "Duplicate connectionId: " + connection.connectionId);
                }
                if (connection.sourcePhaseNodeId == null || connection.sourcePhaseNodeId.isBlank() || !nodeIds.contains(connection.sourcePhaseNodeId)) {
                    report.add(EditableValidationSeverity.ERROR, path + ".sourcePhaseNodeId", "Connection sourcePhaseNodeId is invalid");
                }
                if (connection.targetPhaseNodeId == null || connection.targetPhaseNodeId.isBlank() || !nodeIds.contains(connection.targetPhaseNodeId)) {
                    report.add(EditableValidationSeverity.ERROR, path + ".targetPhaseNodeId", "Connection targetPhaseNodeId is invalid");
                }
                if (connection.connectionType == null) {
                    report.add(EditableValidationSeverity.ERROR, path + ".connectionType", "Connection type is required");
                }
                if (connection.priority < 0) {
                    report.add(EditableValidationSeverity.ERROR, path + ".priority", "Connection priority must be >= 0");
                }
                if (connection.connectionType == EditableConnectionType.CHOICE && (connection.choiceId == null || connection.choiceId.isBlank())) {
                    report.add(EditableValidationSeverity.ERROR, path + ".choiceId", "Choice connection requires choiceId");
                }
            }
        }

        if (quest.layout != null) {
            for (String nodeId : nodeIds) {
                if (!quest.layout.phasePositions.containsKey(nodeId)) {
                    report.add(EditableValidationSeverity.WARNING, "layout.phasePositions", "Missing layout position for nodeId: " + nodeId);
                }
            }
        }

        return report;
    }

    private void validateCollection(EditableQuest quest, EditableValidationReport report) {
        if (quest.collectionConfig == null) {
            report.add(EditableValidationSeverity.ERROR, "collection", "Collection config is required for COLLECTION mode");
            return;
        }

        Set<String> categoryIds = new HashSet<>();
        for (int i = 0; i < quest.collectionConfig.categories.size(); i++) {
            EditableCollectionCategory category = quest.collectionConfig.categories.get(i);
            String path = "collection.categories[" + i + "]";
            if (category.categoryId == null || category.categoryId.isBlank()) {
                report.add(EditableValidationSeverity.ERROR, path + ".categoryId", "Category id is required");
            } else if (!categoryIds.add(category.categoryId)) {
                report.add(EditableValidationSeverity.ERROR, path + ".categoryId", "Duplicate categoryId: " + category.categoryId);
            }
        }

        if (quest.collectionConfig.categories.isEmpty()) {
            report.add(EditableValidationSeverity.ERROR, "collection.categories", "At least one category is required");
        }

        Set<String> entryIds = new HashSet<>();
        for (int i = 0; i < quest.collectionEntries.size(); i++) {
            EditableCollectionEntry entry = quest.collectionEntries.get(i);
            String path = "collection.entries[" + i + "]";
            if (entry.entryId == null || entry.entryId.isBlank()) {
                report.add(EditableValidationSeverity.ERROR, path + ".entryId", "Entry id is required");
            } else if (!entryIds.add(entry.entryId)) {
                report.add(EditableValidationSeverity.ERROR, path + ".entryId", "Duplicate entryId: " + entry.entryId);
            }
            if (entry.categoryId == null || entry.categoryId.isBlank() || !categoryIds.contains(entry.categoryId)) {
                report.add(EditableValidationSeverity.ERROR, path + ".categoryId", "Entry categoryId is invalid");
            }
            if (entry.completionTarget < 0) {
                report.add(EditableValidationSeverity.ERROR, path + ".completionTarget", "completionTarget must be >= 0");
            }
            if (entry.maxCount < 0) {
                report.add(EditableValidationSeverity.ERROR, path + ".maxCount", "maxCount must be >= 0");
            }
            if (entry.countingMode == null) {
                report.add(EditableValidationSeverity.ERROR, path + ".countingMode", "countingMode is required");
            }
        }
    }
}
