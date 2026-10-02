package org.arcadia.arc_quest.data.sync;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.spec.GuideMediaSpec;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.rule.collection.*;
import org.arcadia.arc_quest.quest.spec.*;
import org.arcadia.arc_quest.quest.reward.*;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Client presentation export. Dynamic text is resolved for its recipient, never reverse-engineered. */
public final class CollectionDefinitionSpecExporter {
    private CollectionDefinitionSpecExporter() {}

    /** Exports Java-registered modern quests into the same recipient-filtered presentation channel. */
    public static QuestSpec quest(QuestDefinition quest, ServerPlayer player) {
        QuestSpec spec = new QuestSpec();
        spec.id = quest.getId().toString(); spec.category = quest.getCategory().getId().toString();
        QuestTextContext context = new QuestTextContext(quest, null, null, java.util.Map.of());
        spec.displayName = component(quest.getDisplayName(player, context));
        spec.description = component(quest.getDescription(player, context));
        spec.mode = quest.getMode(); spec.sortOrder = quest.getSortOrder(); spec.repeatable = quest.isRepeatable();
        spec.abandonable = quest.isAbandonAllowed(); spec.canBeAutoTrack = quest.canBeAutoTrack();
        spec.initialPhaseId = quest.getInitialPhaseId(); spec.initialPhaseIds = new ArrayList<>(quest.getInitialPhaseIds());
        spec.completionPolicy = quest.getCompletionPolicy(); spec.completionRequiredCount = quest.getCompletionRequiredCount();
        spec.completionTargetPhaseId = quest.getCompletionTargetPhaseId(); spec.timeLimitType = quest.getTimeLimitType();
        spec.timeLimitValue = quest.getTimeLimitValue(); spec.visualConfig = visual(quest.getVisualConfig());
        spec.iconTexture = quest.getIconTexture() == null ? "" : quest.getIconTexture().toString();
        spec.chapterShopId = quest.getChapterShopId(); spec.chapterShopType = quest.getChapterShopType();
        spec.chapterShopPersistent = quest.isChapterShopPersistent();
        spec.chapterStartSound = quest.getChapterStartSound() == null ? "" : quest.getChapterStartSound().getLocation().toString();
        spec.chapterFailSound = quest.getChapterFailSound() == null ? "" : quest.getChapterFailSound().getLocation().toString();
        spec.chapterCompleteSound = quest.getChapterCompleteSound() == null ? "" : quest.getChapterCompleteSound().getLocation().toString();
        spec.completionRewards = new ArrayList<>(quest.getCompletionRewards().stream().map(CollectionDefinitionSpecExporter::reward).toList());
        CollectionQuestConfig config = quest.getCollectionConfig();
        spec.collectionConfig = new CollectionQuestSpecData();
        spec.collectionConfig.trackerPresentationMode = config.getTrackerMode().name();
        spec.collectionConfig.collectionPresentationMode = config.getJournalMode().name();
        spec.collectionConfig.allowCategoryCollapse = config.isRevealAllEntriesByDefault();
        spec.collectionConfig.showCompletedEntries = config.isAllowManualRewardClaim();
        spec.collectionConfig.showProgressInTracker = config.isShowCategories();
        spec.collectionConfig.completionRules = rules(config.getQuestCompletionRules());
        spec.collectionConfig.rewardNodes = new ArrayList<>(config.getQuestRewardNodes().stream()
                .map(CollectionDefinitionSpecExporter::rewardNode).toList());
        for (CollectionCategoryDefinition category : config.getCategories()) {
            CollectionCategorySpecData cat = new CollectionCategorySpecData();
            cat.categoryId = category.getCategoryId(); cat.displayName = text(category.getDisplayNameText(), player);
            cat.completionRules = rules(category.getCompletionRules());
            cat.rewardNodes = new ArrayList<>(category.getRewardNodes().stream()
                    .map(CollectionDefinitionSpecExporter::rewardNode).toList());
            cat.sortOrder = category.getSortOrder(); spec.collectionConfig.categories.add(cat);
        }
        spec.collectionConfig.entries = new ArrayList<>(config.getEntries().stream().map(e -> entry(e, player)).toList());
        for (PhaseDefinition phase : quest.getAllPhases()) {
            PhaseSpec output = new PhaseSpec(); output.phaseId = phase.getPhaseId();
            QuestTextContext phaseContext = new QuestTextContext(quest, phase, phase.getPhaseId(), java.util.Map.of());
            output.displayName = component(phase.getDisplayName(player, phaseContext));
            output.description = component(phase.getDescription(player, phaseContext));
            output.story = component(phase.getStory(player, phaseContext));
            output.visualConfig = visual(phase.getVisualConfig());
            output.autoAdvanceOnComplete = phase.shouldAutoAdvanceOnComplete();
            output.objectives = new ArrayList<>(phase.getObjectives().stream().map(o -> objective(o, player)).toList());
            if (phase.getCollectionSheet() != null) output.collectionSheet = sheet(phase.getCollectionSheet());
            output.phaseRewards = new ArrayList<>(phase.getPhaseRewards().stream().map(CollectionDefinitionSpecExporter::reward).toList());
            output.tradeShopId = phase.getTradeShopId();
            output.phaseStartSound = phase.getPhaseStartSound() == null ? "" : phase.getPhaseStartSound().getLocation().toString();
            output.phaseCompleteSound = phase.getPhaseCompleteSound() == null ? "" : phase.getPhaseCompleteSound().getLocation().toString();
            output.intelSceneId = phase.getIntelSceneId() == null ? "" : phase.getIntelSceneId().toString();
            output.autoEnterByCondition = phase.isAutoEnterByCondition();
            for (PhaseTransition transition : phase.getTransitions()) {
                TransitionSpec converted = new TransitionSpec();
                converted.targetPhaseId = transition.getTargetPhaseId();
                converted.targetPhaseIds = new ArrayList<>(transition.getTargetPhaseIds());
                output.transitions.add(converted);
            }
            for (ChoiceOption choice : phase.getChoices()) {
                ChoiceSpec converted = new ChoiceSpec(); converted.text = component(choice.getDisplayText());
                converted.targetPhaseId = choice.getTargetPhaseId(); output.choices.add(converted);
            }
            spec.phases.add(output);
        }
        return spec;
    }

    private static CollectionRewardNodeSpecData rewardNode(CollectionRewardNode node) {
        CollectionRewardNodeSpecData spec = new CollectionRewardNodeSpecData();
        spec.nodeId = node.getRewardNodeId(); spec.scope = node.getScope().name();
        spec.grantMode = node.getGrantMode().name();
        spec.scopeRefId = node.getOwnerId() == null ? "" : node.getOwnerId();
        spec.rewards = new ArrayList<>(node.getRewards().stream().map(CollectionDefinitionSpecExporter::reward).toList());
        spec.completionRules = rules(node.getUnlockRules());
        return spec;
    }

    /** Never invoke custom rules during export: client nodes use server-synchronized unlock receipts. */
    private static ArrayList<ConditionSpec> rules(List<CollectionCompletionRule> rules) {
        ArrayList<ConditionSpec> result = new ArrayList<>();
        for (CollectionCompletionRule rule : rules) {
            ConditionSpec converted = rule(rule);
            if (converted != null) result.add(converted);
        }
        return result;
    }

    private static ConditionSpec rule(CollectionCompletionRule rule) {
        ConditionSpec spec = new ConditionSpec();
        if (rule instanceof AllEntriesCompleteRule) spec.type = "all_entries_complete";
        else if (rule instanceof CompletedEntryCountRule count) {
            spec.type = "completed_entry_count"; spec.value = count.getRequiredCount();
        } else if (rule instanceof CategoryCompletedCountRule count) {
            spec.type = "category_completed_count"; spec.value = count.getRequiredCount();
        } else if (rule instanceof CompletedEntryRatioRule ratio) {
            spec.type = "completed_entry_ratio"; spec.ratio = ratio.getRequiredRatio();
            spec.value = Math.round(spec.ratio * 100);
        } else if (rule instanceof CategoryCompletedRatioRule ratio) {
            spec.type = "category_completed_ratio"; spec.ratio = ratio.getRequiredRatio();
            spec.value = Math.round(spec.ratio * 100);
        } else if (rule instanceof AndCollectionRule and) return compound("and", and.getRules());
        else if (rule instanceof OrCollectionRule or) return compound("or", or.getRules());
        else if (rule instanceof NotCollectionRule not) {
            if (not.getRule() == null) return constant(true);
            spec.type = "not"; spec.left = rule(not.getRule());
            if (spec.left == null) return null;
        } else return null;
        return spec;
    }

    private static ConditionSpec compound(String type, List<CollectionCompletionRule> children) {
        if (children.isEmpty()) return constant("and".equals(type));
        ConditionSpec result = null;
        for (CollectionCompletionRule child : children) {
            ConditionSpec converted = rule(child);
            // An unrepresentable child invalidates the entire expression rather than changing NOT/OR semantics.
            if (converted == null) return null;
            if (result == null) result = converted;
            else {
                ConditionSpec combined = new ConditionSpec();
                combined.type = type; combined.left = result; combined.right = converted;
                result = combined;
            }
        }
        return result;
    }

    private static ConditionSpec constant(boolean value) {
        ConditionSpec always = new ConditionSpec();
        always.type = "completed_entry_ratio"; always.ratio = 0f; always.value = 0;
        if (value) return always;
        ConditionSpec never = new ConditionSpec(); never.type = "not"; never.left = always;
        return never;
    }

    private static RewardSpec reward(IReward reward) {
        RewardSpec spec = new RewardSpec();
        if (reward instanceof ItemReward item) {
            spec.type = "item"; spec.itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).toString(); spec.count = item.getCount();
        } else if (reward instanceof FlagReward flag) {
            spec.type = flag.isSet() ? "flag_set" : "flag_clear"; spec.flag = flag.getFlag();
        } else if (reward instanceof VariableReward variable) {
            spec.type = "var_" + variable.getOperation().name().toLowerCase(java.util.Locale.ROOT);
            spec.variable = variable.getVariableName(); spec.value = variable.getValue();
        } else {
            // Client definitions are never used for granting: a generic reward remains a read-only description.
            spec.type = "command"; spec.command = reward.describe();
        }
        return spec;
    }

    private static QuestVisualSpec visual(QuestVisualConfig visual) {
        QuestVisualSpec spec = new QuestVisualSpec();
        if (visual == null) return spec;
        spec.themeColor = visual.getThemeColor(); spec.useQuestSplashPresentation = visual.usesQuestSplashPresentation();
        for (SplashType type : SplashType.values()) visual.getSplash(type).ifPresent(asset -> spec.splashes.put(type.name(), asset(asset)));
        for (IconPosition position : IconPosition.values()) visual.getIcon(position).ifPresent(asset -> spec.icons.put(position.name(), asset(asset)));
        return spec;
    }

    private static QuestVisualSpec.AssetSpec asset(VisualAsset asset) {
        QuestVisualSpec.AssetSpec spec = new QuestVisualSpec.AssetSpec();
        spec.texture = asset.texture() == null ? "" : asset.texture().toString();
        if (asset.item() != null && !asset.item().isEmpty()) {
            spec.itemId = BuiltInRegistries.ITEM.getKey(asset.item().getItem()).toString(); spec.itemCount = asset.item().getCount();
        }
        spec.scale = asset.scale(); spec.offsetX = asset.offsetX(); spec.offsetY = asset.offsetY();
        spec.tintColor = asset.tintColor(); spec.enabled = asset.enabled(); return spec;
    }

    public static CollectionEntrySpecData entry(CollectionEntryDefinition entry, ServerPlayer player) {
        CollectionEntrySpecData spec = new CollectionEntrySpecData();
        spec.entryId = entry.getEntryId().toString();
        spec.categoryId = entry.getCategoryId();
        spec.displayName = text(entry.getDisplayQuestText(), player);
        spec.description = text(entry.getDescriptionQuestText(), player);
        spec.subjectKind = entry.getSubjectKind().name();
        spec.subjectId = entry.getSubjectId() == null ? "" : entry.getSubjectId().toString();
        spec.itemTag = entry.getItemTag() == null ? "" : entry.getItemTag().toString();
        spec.icon = entry.getIcon();
        spec.relatedItems = new ArrayList<>(entry.getRelatedItems().stream().map(Object::toString).toList());
        spec.discoveryObjectives = new ArrayList<>(entry.getDiscoveryObjectives().stream().map(o -> objective(o, player)).toList());
        spec.researchObjectives = new ArrayList<>(entry.getResearchObjectives().stream().map(o -> objective(o, player)).toList());
        spec.visibilityMode = entry.getVisibilityMode().name();
        spec.hiddenPresentationMode = entry.getHiddenPresentationMode().name();
        spec.sortOrder = entry.getSortOrder();
        spec.researchAfterDiscovery = entry.isResearchAfterDiscovery();
        for (CollectionEntryRewardDefinition definition : entry.getRewards()) {
            CollectionEntryRewardSpecData output = new CollectionEntryRewardSpecData();
            output.rewardId = definition.rewardId(); output.trigger = definition.trigger().name(); output.grantMode = definition.grantMode().name();
            output.rewards = new ArrayList<>(definition.rewards().stream().map(CollectionDefinitionSpecExporter::reward).toList());
            spec.rewards.add(output);
        }
        for (CollectionContentBlock block : entry.getContent()) {
            CollectionContentBlockSpecData output = new CollectionContentBlockSpecData();
            output.blockId = block.blockId(); output.text = text(block.text(), player);
            output.caption = text(block.caption(), player); output.fit = block.fit().name();
            output.zoomable = block.zoomable(); output.reveal = block.reveal().name();
            output.revealStepId = block.revealStepId();
            if (block.media() != null) {
                output.media = new GuideMediaSpec();
                output.media.type = block.media().getType().name().toLowerCase(java.util.Locale.ROOT);
                output.media.texture = block.media().getTexture() == null ? "" : block.media().getTexture().toString();
                output.media.width = block.media().getWidth(); output.media.height = block.media().getHeight();
            }
            spec.content.add(output);
        }
        return spec;
    }

    public static CollectionSheetSpecData sheet(CollectionSheetDefinition sheet) {
        CollectionSheetSpecData spec = new CollectionSheetSpecData();
        spec.completionPolicy = sheet.getCompletionPolicy().name();
        spec.requiredCount = sheet.getCompletionPolicy() == CollectionSheetCompletionPolicy.ALL ? 0 : sheet.getRequiredCount();
        spec.countDistinctEntries = sheet.isCountDistinctEntries();
        for (EntryRequirementBinding binding : sheet.getBindings()) {
            EntryRequirementBindingSpecData output = new EntryRequirementBindingSpecData();
            output.bindingId = binding.getBindingId(); output.entryId = binding.getEntryId().toString();
            output.objectiveIds = new ArrayList<>(binding.getObjectiveIds());
            output.requirementMode = binding.getRequirementMode().name(); output.recordPolicy = binding.getRecordPolicy().name();
            output.optional = binding.isOptional(); output.sortOrder = binding.getSortOrder();
            for (CollectionRecordRequirement requirement : binding.getRecordRequirements()) {
                CollectionRecordRequirementSpecData record = new CollectionRecordRequirementSpecData();
                record.type = requirement.type().name(); record.stepId = requirement.stepId();
                output.recordRequirements.add(record);
            }
            spec.bindings.add(output);
        }
        return spec;
    }

    public static ObjectiveSpec objective(ObjectiveEntry objective, ServerPlayer player) {
        ObjectiveSpec spec = new ObjectiveSpec();
        spec.id = objective.getObjectiveId(); spec.type = objective.getType().getId().toString();
        spec.targetId = objective.getTargetId().toString(); spec.requiredCount = objective.resolveRequiredCount(player);
        spec.displayText = text(objective.getDisplayQuestText(), player); spec.hidden = objective.isHidden();
        spec.optional = objective.isOptional(); spec.icon = objective.getIcon();
        spec.extraData = new LinkedHashMap<>(objective.getExtraData());
        return spec;
    }

    public static QuestTextSpec text(QuestText text, ServerPlayer player) {
        return component(text.resolve(player, QuestTextContext.empty()));
    }

    private static QuestTextSpec component(Component component) {
        if (component.getContents() instanceof TranslatableContents translated && component.getSiblings().isEmpty()) {
            QuestTextSpec spec = QuestTextSpec.translatable(translated.getKey());
            for (Object argument : translated.getArgs()) spec.args.add(argument instanceof Component value ? value.getString() : String.valueOf(argument));
            return spec;
        }
        return QuestTextSpec.literal(component.getString());
    }
}
