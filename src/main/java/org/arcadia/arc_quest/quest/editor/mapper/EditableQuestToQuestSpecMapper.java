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
        mapCollection(spec, editable);
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
        spec.mode = meta.mode == null ? org.arcadia.arc_quest.quest.api.QuestMode.PROGRESSION : meta.mode;
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
        spec.collectionEntryConfig = editableObjective.collectionEntryConfig;
        return spec;
    }

    private void mapCollection(QuestSpec spec, EditableQuest editable) {
        if (editable.collectionConfig == null) return;
        spec.collection.trackerMode = editable.collectionConfig.trackerMode;
        spec.collection.journalMode = editable.collectionConfig.journalMode;
        spec.collection.revealAllEntriesByDefault = editable.collectionConfig.revealAllEntriesByDefault;
        spec.collection.allowManualRewardClaim = editable.collectionConfig.allowManualRewardClaim;
        spec.collection.showCategories = editable.collectionConfig.showCategories;

        for (EditableCollectionCategory category : editable.collectionConfig.categories) {
            org.arcadia.arc_quest.quest.spec.CollectionCategorySpec categorySpec = new org.arcadia.arc_quest.quest.spec.CollectionCategorySpec();
            categorySpec.categoryId = category.categoryId;
            categorySpec.displayName = category.displayName;
            categorySpec.iconTexture = category.iconTexture;
            categorySpec.sortOrder = category.sortOrder;
            categorySpec.visibilityConditions.addAll(category.visibilityConditions);
            for (EditableCollectionCompletionRule rule : category.completionRules) {
                org.arcadia.arc_quest.quest.spec.CollectionCompletionRuleSpec ruleSpec = new org.arcadia.arc_quest.quest.spec.CollectionCompletionRuleSpec();
                ruleSpec.type = rule.type;
                ruleSpec.expression = rule.expression;
                categorySpec.completionRules.add(ruleSpec);
            }
            for (EditableCollectionRewardNode rewardNode : category.rewardNodes) {
                categorySpec.rewardNodes.add(mapCollectionRewardNode(rewardNode));
            }
            spec.collection.categories.add(categorySpec);
        }

        for (EditableCollectionCompletionRule rule : editable.collectionConfig.questCompletionRules) {
            org.arcadia.arc_quest.quest.spec.CollectionCompletionRuleSpec ruleSpec = new org.arcadia.arc_quest.quest.spec.CollectionCompletionRuleSpec();
            ruleSpec.type = rule.type;
            ruleSpec.expression = rule.expression;
            spec.collection.questCompletionRules.add(ruleSpec);
        }

        for (EditableCollectionRewardNode rewardNode : editable.collectionConfig.questRewardNodes) {
            spec.collection.questRewardNodes.add(mapCollectionRewardNode(rewardNode));
        }

        if (editable.collectionEntries != null) {
            for (EditableCollectionEntry entry : editable.collectionEntries) {
                EditablePhase phase = editable.phases.stream().filter(p -> p.nodeId.equals(entry.ownerPhaseNodeId)).findFirst().orElse(null);
                if (phase == null) continue;
                EditableObjective objective = phase.objectives.stream().filter(o -> o.objectiveId.equals(entry.objectiveId)).findFirst().orElse(null);
                if (objective == null) continue;
                objective.collectionEntryConfig = toCollectionEntryConfig(entry);
            }
        }
    }

    private org.arcadia.arc_quest.quest.spec.CollectionRewardNodeSpec mapCollectionRewardNode(EditableCollectionRewardNode rewardNode) {
        org.arcadia.arc_quest.quest.spec.CollectionRewardNodeSpec spec = new org.arcadia.arc_quest.quest.spec.CollectionRewardNodeSpec();
        spec.rewardNodeId = rewardNode.rewardNodeId;
        spec.scope = rewardNode.scope;
        spec.grantMode = rewardNode.grantMode;
        spec.rewards.addAll(rewardNode.rewards);
        spec.ownerId = rewardNode.ownerId;
        for (EditableCollectionCompletionRule unlockRule : rewardNode.unlockRules) {
            org.arcadia.arc_quest.quest.spec.CollectionCompletionRuleSpec ruleSpec = new org.arcadia.arc_quest.quest.spec.CollectionCompletionRuleSpec();
            ruleSpec.type = unlockRule.type;
            ruleSpec.expression = unlockRule.expression;
            spec.unlockRules.add(ruleSpec);
        }
        return spec;
    }

    private org.arcadia.arc_quest.quest.api.CollectionEntryConfig toCollectionEntryConfig(EditableCollectionEntry entry) {
        return new org.arcadia.arc_quest.quest.api.CollectionEntryConfig(
                entry.categoryId,
                entry.visibilityMode,
                entry.hiddenPresentationMode,
                entry.visibilityConditions,
                entry.countingMode,
                entry.completionTarget,
                entry.repeatableProgress,
                entry.repeatableCompletion,
                entry.maxCount,
                entry.rewardGrantMode,
                java.util.List.of(),
                entry.sortOrder,
                entry.showInTrackerByDefault
        );
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
