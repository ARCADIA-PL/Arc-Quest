package org.arcadia.arc_quest.quest.editor.mapper;

import org.arcadia.arc_quest.quest.editor.model.*;
import org.arcadia.arc_quest.quest.spec.*;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EditableQuestToQuestSpecMapper {
    public QuestSpec map(EditableQuest editable) {
        QuestSpec spec = new QuestSpec();
        if (editable == null) return spec;

        mapMeta(spec, editable.meta);
        spec.initialPhaseId = resolveInitialPhaseId(editable);

        Map<String, PhaseSpec> phaseByNodeId = new LinkedHashMap<>();
        for (EditablePhase editablePhase : editable.phases) {
            PhaseSpec phaseSpec = mapPhase(editablePhase);
            spec.phases.add(phaseSpec);
            phaseByNodeId.put(editablePhase.nodeId, phaseSpec);
        }

        for (EditableConnection connection : editable.connections) {
            PhaseSpec source = phaseByNodeId.get(connection.sourcePhaseNodeId);
            PhaseSpec target = phaseByNodeId.get(connection.targetPhaseNodeId);
            if (source == null || target == null) continue;
            if (connection.connectionType == EditableConnectionType.TRANSITION) {
                TransitionSpec transition = new TransitionSpec();
                transition.targetPhaseId = target.phaseId;
                transition.condition = connection.condition;
                source.transitions.add(transition);
            }
        }

        for (EditablePhase editablePhase : editable.phases) {
            PhaseSpec phaseSpec = phaseByNodeId.get(editablePhase.nodeId);
            if (phaseSpec == null) continue;
            for (EditableChoice editableChoice : editablePhase.choices) {
                ChoiceSpec choiceSpec = new ChoiceSpec();
                choiceSpec.text = editableChoice.text;
                choiceSpec.flagToSet = editableChoice.flagToSet;
                choiceSpec.visibleCondition = editableChoice.visibleCondition;
                PhaseSpec target = phaseByNodeId.get(editableChoice.targetPhaseNodeId);
                choiceSpec.targetPhaseId = target == null ? "" : target.phaseId;
                phaseSpec.choices.add(choiceSpec);
            }
        }

        return spec;
    }

    private void mapMeta(QuestSpec spec, EditableQuestMeta meta) {
        if (meta == null) return;
        spec.id = meta.questId;
        spec.category = meta.category;
        spec.displayName = meta.displayName;
        spec.description = meta.description;
        spec.iconTexture = meta.iconTexture;
        spec.sortOrder = meta.sortOrder;
        spec.repeatable = meta.repeatable;
        spec.mode = meta.mode;
        spec.unlockConditions.addAll(meta.unlockConditions);
        spec.completionRewards.addAll(meta.completionRewards);
        spec.flagsToSetOnAccept.addAll(meta.flagsToSetOnAccept);
        spec.flagsToSetOnComplete.addAll(meta.flagsToSetOnComplete);
        spec.relatedMarks.addAll(meta.relatedMarks);
        spec.chapterShopId = meta.chapterShopId;
        spec.chapterShopType = meta.chapterShopType;
        spec.chapterShopPersistent = meta.chapterShopPersistent;
        spec.completionPolicy = meta.completionPolicy;
        spec.completionRequiredCount = meta.completionRequiredCount;
        spec.completionTargetPhaseId = meta.completionTargetPhaseId;
        spec.timeLimitType = meta.timeLimitType;
        spec.timeLimitValue = meta.timeLimitValue;
        spec.chapterStartSound = meta.chapterStartSound;
        spec.chapterFailSound = meta.chapterFailSound;
        spec.chapterCompleteSound = meta.chapterCompleteSound;
        spec.visualConfig = meta.visualConfig;
    }

    private PhaseSpec mapPhase(EditablePhase editablePhase) {
        PhaseSpec spec = new PhaseSpec();
        spec.phaseId = editablePhase.phaseId;
        spec.displayName = editablePhase.displayName;
        spec.description = editablePhase.description;
        spec.story = editablePhase.story;
        for (EditableObjective editableObjective : editablePhase.objectives) {
            spec.objectives.add(mapObjective(editableObjective));
        }
        spec.phaseRewards.addAll(editablePhase.phaseRewards);
        spec.flagsToSetOnEnter.addAll(editablePhase.flagsToSetOnEnter);
        spec.flagsToSetOnComplete.addAll(editablePhase.flagsToSetOnComplete);
        spec.relatedMarks.addAll(editablePhase.relatedMarks);
        spec.tradeShopId = editablePhase.tradeShopId;
        spec.intelSceneId = editablePhase.intelSceneId;
        spec.phaseStartSound = editablePhase.phaseStartSound;
        spec.phaseCompleteSound = editablePhase.phaseCompleteSound;
        spec.enterCondition = editablePhase.enterCondition;
        spec.autoEnterByCondition = editablePhase.autoEnterByCondition;
        spec.visualConfig = editablePhase.visualConfig;
        return spec;
    }

    private ObjectiveSpec mapObjective(EditableObjective editableObjective) {
        ObjectiveSpec spec = new ObjectiveSpec();
        spec.type = editableObjective.type;
        spec.targetId = editableObjective.targetId;
        spec.requiredCount = editableObjective.requiredCount;
        spec.displayText = editableObjective.displayText;
        spec.hidden = editableObjective.hidden;
        spec.optional = editableObjective.optional;
        spec.npcId = editableObjective.npcId;
        spec.itemTag = editableObjective.itemTag;
        spec.x = editableObjective.x;
        spec.y = editableObjective.y;
        spec.z = editableObjective.z;
        spec.radius = editableObjective.radius;
        spec.countMode = editableObjective.countMode;
        spec.countBase = editableObjective.countBase;
        spec.countPerLevel = editableObjective.countPerLevel;
        spec.countMin = editableObjective.countMin;
        spec.countMax = editableObjective.countMax;
        spec.relatedMarks.addAll(editableObjective.relatedMarks);
        spec.extraData.putAll(editableObjective.extraData);
        return spec;
    }

    private String resolveInitialPhaseId(EditableQuest editable) {
        if (editable.initialPhaseNodeId != null && !editable.initialPhaseNodeId.isBlank()) {
            for (EditablePhase phase : editable.phases) {
                if (editable.initialPhaseNodeId.equals(phase.nodeId)) {
                    return phase.phaseId;
                }
            }
        }
        return editable.phases.isEmpty() ? "" : editable.phases.get(0).phaseId;
    }
}
