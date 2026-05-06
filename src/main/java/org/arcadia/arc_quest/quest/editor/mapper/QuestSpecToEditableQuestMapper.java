package org.arcadia.arc_quest.quest.editor.mapper;

import org.arcadia.arc_quest.quest.editor.model.*;
import org.arcadia.arc_quest.quest.spec.*;

import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestSpecToEditableQuestMapper {
    public EditableQuest map(QuestSpec spec) {
        EditableQuest editable = new EditableQuest();
        if (spec == null) return editable;

        mapMeta(editable.meta, spec);

        Map<String, EditablePhase> phaseByPhaseId = new LinkedHashMap<>();
        int index = 0;
        for (PhaseSpec phaseSpec : spec.phases) {
            EditablePhase editablePhase = mapPhase(phaseSpec, index++);
            editable.phases.add(editablePhase);
            editable.layout.phasePositions.put(editablePhase.nodeId, new EditorNodePosition(220.0 * (index - 1), 0.0));
            phaseByPhaseId.put(phaseSpec.phaseId, editablePhase);
            if (spec.initialPhaseId != null && spec.initialPhaseId.equals(phaseSpec.phaseId)) {
                editable.initialPhaseNodeId = editablePhase.nodeId;
            }
        }

        int transitionIndex = 0;
        for (PhaseSpec phaseSpec : spec.phases) {
            EditablePhase source = phaseByPhaseId.get(phaseSpec.phaseId);
            if (source == null) continue;

            for (TransitionSpec transitionSpec : phaseSpec.transitions) {
                EditablePhase target = phaseByPhaseId.get(transitionSpec.targetPhaseId);
                if (target == null) continue;
                EditableConnection connection = new EditableConnection();
                connection.connectionId = source.nodeId + "::transition::" + transitionIndex++;
                connection.sourcePhaseNodeId = source.nodeId;
                connection.targetPhaseNodeId = target.nodeId;
                connection.connectionType = EditableConnectionType.TRANSITION;
                connection.condition = transitionSpec.condition;
                editable.connections.add(connection);
            }

            int choiceIndex = 0;
            for (ChoiceSpec choiceSpec : phaseSpec.choices) {
                EditableChoice editableChoice = new EditableChoice();
                editableChoice.choiceId = source.nodeId + "::choice::" + choiceIndex;
                editableChoice.text = choiceSpec.text;
                editableChoice.flagToSet = choiceSpec.flagToSet;
                editableChoice.visibleCondition = choiceSpec.visibleCondition;
                EditablePhase target = phaseByPhaseId.get(choiceSpec.targetPhaseId);
                editableChoice.targetPhaseNodeId = target == null ? "" : target.nodeId;
                source.choices.add(editableChoice);

                if (target != null) {
                    EditableConnection connection = new EditableConnection();
                    connection.connectionId = source.nodeId + "::choice-connection::" + choiceIndex;
                    connection.sourcePhaseNodeId = source.nodeId;
                    connection.targetPhaseNodeId = target.nodeId;
                    connection.connectionType = EditableConnectionType.CHOICE;
                    connection.choiceId = editableChoice.choiceId;
                    editable.connections.add(connection);
                }
                choiceIndex++;
            }
        }

        if ((editable.initialPhaseNodeId == null || editable.initialPhaseNodeId.isBlank()) && !editable.phases.isEmpty()) {
            editable.initialPhaseNodeId = editable.phases.get(0).nodeId;
        }

        return editable;
    }

    private void mapMeta(EditableQuestMeta meta, QuestSpec spec) {
        meta.questId = spec.id;
        meta.category = spec.category;
        meta.displayName = spec.displayName;
        meta.description = spec.description;
        meta.iconTexture = spec.iconTexture;
        meta.sortOrder = spec.sortOrder;
        meta.repeatable = spec.repeatable;
        meta.mode = spec.mode;
        meta.unlockConditions.addAll(spec.unlockConditions);
        meta.completionRewards.addAll(spec.completionRewards);
        meta.flagsToSetOnAccept.addAll(spec.flagsToSetOnAccept);
        meta.flagsToSetOnComplete.addAll(spec.flagsToSetOnComplete);
        meta.relatedMarks.addAll(spec.relatedMarks);
        meta.chapterShopId = spec.chapterShopId;
        meta.chapterShopType = spec.chapterShopType;
        meta.chapterShopPersistent = spec.chapterShopPersistent;
        meta.completionPolicy = spec.completionPolicy;
        meta.completionRequiredCount = spec.completionRequiredCount;
        meta.completionTargetPhaseId = spec.completionTargetPhaseId;
        meta.timeLimitType = spec.timeLimitType;
        meta.timeLimitValue = spec.timeLimitValue;
        meta.chapterStartSound = spec.chapterStartSound;
        meta.chapterFailSound = spec.chapterFailSound;
        meta.chapterCompleteSound = spec.chapterCompleteSound;
        meta.visualConfig = spec.visualConfig;
    }

    private EditablePhase mapPhase(PhaseSpec spec, int index) {
        EditablePhase editable = new EditablePhase();
        editable.nodeId = buildPhaseNodeId(spec.phaseId, index);
        editable.phaseId = spec.phaseId;
        editable.displayName = spec.displayName;
        editable.description = spec.description;
        editable.story = spec.story;
        for (int i = 0; i < spec.objectives.size(); i++) {
            editable.objectives.add(mapObjective(spec.objectives.get(i), editable.nodeId, i));
        }
        editable.phaseRewards.addAll(spec.phaseRewards);
        editable.flagsToSetOnEnter.addAll(spec.flagsToSetOnEnter);
        editable.flagsToSetOnComplete.addAll(spec.flagsToSetOnComplete);
        editable.relatedMarks.addAll(spec.relatedMarks);
        editable.tradeShopId = spec.tradeShopId;
        editable.intelSceneId = spec.intelSceneId;
        editable.phaseStartSound = spec.phaseStartSound;
        editable.phaseCompleteSound = spec.phaseCompleteSound;
        editable.enterCondition = spec.enterCondition;
        editable.autoEnterByCondition = spec.autoEnterByCondition;
        editable.visualConfig = spec.visualConfig;
        return editable;
    }

    private EditableObjective mapObjective(ObjectiveSpec spec, String phaseNodeId, int index) {
        EditableObjective editable = new EditableObjective();
        editable.objectiveId = phaseNodeId + "::objective::" + index;
        editable.type = spec.type;
        editable.targetId = spec.targetId;
        editable.requiredCount = spec.requiredCount;
        editable.displayText = spec.displayText;
        editable.hidden = spec.hidden;
        editable.optional = spec.optional;
        editable.npcId = spec.npcId;
        editable.itemTag = spec.itemTag;
        editable.x = spec.x;
        editable.y = spec.y;
        editable.z = spec.z;
        editable.radius = spec.radius;
        editable.countMode = spec.countMode;
        editable.countBase = spec.countBase;
        editable.countPerLevel = spec.countPerLevel;
        editable.countMin = spec.countMin;
        editable.countMax = spec.countMax;
        editable.relatedMarks.addAll(spec.relatedMarks);
        editable.extraData.putAll(spec.extraData);
        return editable;
    }

    private String buildPhaseNodeId(String phaseId, int index) {
        String base = phaseId == null || phaseId.isBlank() ? "phase" + index : phaseId.replace(':', '_');
        return "node_" + index + "_" + base;
    }
}
