package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable knowledge definition shared by any number of task bindings. Contains no player state. */
public final class CollectionEntryDefinition {
    private final ResourceLocation entryId;
    private final String categoryId;
    private final QuestText displayName;
    private final QuestText description;
    private final CollectionSubjectKind subjectKind;
    @Nullable private final ResourceLocation subjectId;
    @Nullable private final ResourceLocation itemTag;
    private final ObjectiveIconSpec icon;
    private final List<CollectionContentBlock> content;
    private final List<ResourceLocation> relatedItems;
    private final List<ObjectiveEntry> discoveryObjectives;
    private final List<ObjectiveEntry> researchObjectives;
    private final List<ICondition> recordConditions;
    @Nullable private final String recordConditionSignature;
    private final VisibilityMode visibilityMode;
    private final HiddenPresentationMode hiddenPresentationMode;
    private final int sortOrder;
    private final boolean researchAfterDiscovery;
    private final List<CollectionEntryRewardDefinition> rewards;
    public static final int LEGACY_GAMEPLAY_VERSION = 1;
    public static final int UNIFIED_GAMEPLAY_VERSION = 2;
    public static final String LEGACY_RESEARCH_COMPLETE = "$complete";
    private final int gameplayVersion;
    private final List<CollectionOutcomeDefinition> outcomes;
    private final Map<String, String> legacyResearchOutcomeMappings;
    private final List<ObjectiveEntry> legacyResearchObjectives;
    private final QuestText publicClue;
    @Nullable private final List<ResourceLocation> presentationItemTagMembers;

    public CollectionEntryDefinition(ResourceLocation entryId, String categoryId, QuestText displayName,
                                     QuestText description, CollectionSubjectKind subjectKind,
                                     @Nullable ResourceLocation subjectId, @Nullable ResourceLocation itemTag,
                                     ObjectiveIconSpec icon, List<CollectionContentBlock> content,
                                     List<ResourceLocation> relatedItems, List<ObjectiveEntry> discoveryObjectives,
                                     List<ObjectiveEntry> researchObjectives, List<ICondition> recordConditions,
                                     VisibilityMode visibilityMode, HiddenPresentationMode hiddenPresentationMode,
                                     int sortOrder, boolean researchAfterDiscovery) {
        this(entryId, categoryId, displayName, description, subjectKind, subjectId, itemTag, icon,
                content, relatedItems, discoveryObjectives, researchObjectives, recordConditions,
                visibilityMode, hiddenPresentationMode, sortOrder, researchAfterDiscovery, null);
    }

    public CollectionEntryDefinition(ResourceLocation entryId, String categoryId, QuestText displayName,
                                     QuestText description, CollectionSubjectKind subjectKind,
                                     @Nullable ResourceLocation subjectId, @Nullable ResourceLocation itemTag,
                                     ObjectiveIconSpec icon, List<CollectionContentBlock> content,
                                     List<ResourceLocation> relatedItems, List<ObjectiveEntry> discoveryObjectives,
                                     List<ObjectiveEntry> researchObjectives, List<ICondition> recordConditions,
                                     VisibilityMode visibilityMode, HiddenPresentationMode hiddenPresentationMode,
                                     int sortOrder, boolean researchAfterDiscovery,
                                     @Nullable String recordConditionSignature) {
        this(entryId, categoryId, displayName, description, subjectKind, subjectId, itemTag, icon,
                content, relatedItems, discoveryObjectives, researchObjectives, recordConditions,
                visibilityMode, hiddenPresentationMode, sortOrder, researchAfterDiscovery, recordConditionSignature, List.of());
    }

    public CollectionEntryDefinition(ResourceLocation entryId, String categoryId, QuestText displayName,
                                     QuestText description, CollectionSubjectKind subjectKind,
                                     @Nullable ResourceLocation subjectId, @Nullable ResourceLocation itemTag,
                                     ObjectiveIconSpec icon, List<CollectionContentBlock> content,
                                     List<ResourceLocation> relatedItems, List<ObjectiveEntry> discoveryObjectives,
                                     List<ObjectiveEntry> researchObjectives, List<ICondition> recordConditions,
                                     VisibilityMode visibilityMode, HiddenPresentationMode hiddenPresentationMode,
                                     int sortOrder, boolean researchAfterDiscovery,
                                     @Nullable String recordConditionSignature, List<CollectionEntryRewardDefinition> rewards) {
        this(entryId, categoryId, displayName, description, subjectKind, subjectId, itemTag, icon,
                content, relatedItems, discoveryObjectives, researchObjectives, recordConditions,
                visibilityMode, hiddenPresentationMode, sortOrder, researchAfterDiscovery, recordConditionSignature,
                rewards, LEGACY_GAMEPLAY_VERSION, List.of(), Map.of(), List.of());
    }

    public CollectionEntryDefinition(ResourceLocation entryId, String categoryId, QuestText displayName,
                                     QuestText description, CollectionSubjectKind subjectKind,
                                     @Nullable ResourceLocation subjectId, @Nullable ResourceLocation itemTag,
                                     ObjectiveIconSpec icon, List<CollectionContentBlock> content,
                                     List<ResourceLocation> relatedItems, List<ObjectiveEntry> discoveryObjectives,
                                     List<ObjectiveEntry> researchObjectives, List<ICondition> recordConditions,
                                     VisibilityMode visibilityMode, HiddenPresentationMode hiddenPresentationMode,
                                     int sortOrder, boolean researchAfterDiscovery,
                                     @Nullable String recordConditionSignature, List<CollectionEntryRewardDefinition> rewards,
                                     int gameplayVersion, List<CollectionOutcomeDefinition> outcomes,
                                     Map<String, String> legacyResearchOutcomeMappings,
                                     List<ObjectiveEntry> legacyResearchObjectives) {
        this(entryId, categoryId, displayName, description, subjectKind, subjectId, itemTag, icon,
                content, relatedItems, discoveryObjectives, researchObjectives, recordConditions,
                visibilityMode, hiddenPresentationMode, sortOrder, researchAfterDiscovery, recordConditionSignature,
                rewards, gameplayVersion, outcomes, legacyResearchOutcomeMappings, legacyResearchObjectives, QuestText.literal(""));
    }

    public CollectionEntryDefinition(ResourceLocation entryId, String categoryId, QuestText displayName,
                                     QuestText description, CollectionSubjectKind subjectKind,
                                     @Nullable ResourceLocation subjectId, @Nullable ResourceLocation itemTag,
                                     ObjectiveIconSpec icon, List<CollectionContentBlock> content,
                                     List<ResourceLocation> relatedItems, List<ObjectiveEntry> discoveryObjectives,
                                     List<ObjectiveEntry> researchObjectives, List<ICondition> recordConditions,
                                     VisibilityMode visibilityMode, HiddenPresentationMode hiddenPresentationMode,
                                     int sortOrder, boolean researchAfterDiscovery,
                                     @Nullable String recordConditionSignature, List<CollectionEntryRewardDefinition> rewards,
                                     int gameplayVersion, List<CollectionOutcomeDefinition> outcomes,
                                     Map<String, String> legacyResearchOutcomeMappings,
                                     List<ObjectiveEntry> legacyResearchObjectives, QuestText publicClue) {
        this(entryId, categoryId, displayName, description, subjectKind, subjectId, itemTag, icon,
                content, relatedItems, discoveryObjectives, researchObjectives, recordConditions,
                visibilityMode, hiddenPresentationMode, sortOrder, researchAfterDiscovery, recordConditionSignature,
                rewards, gameplayVersion, outcomes, legacyResearchOutcomeMappings, legacyResearchObjectives, publicClue, null);
    }

    /** Recipient-only frozen candidate projection; null differs from an intentionally empty Tag. */
    public CollectionEntryDefinition(ResourceLocation entryId, String categoryId, QuestText displayName,
                                     QuestText description, CollectionSubjectKind subjectKind,
                                     @Nullable ResourceLocation subjectId, @Nullable ResourceLocation itemTag,
                                     ObjectiveIconSpec icon, List<CollectionContentBlock> content,
                                     List<ResourceLocation> relatedItems, List<ObjectiveEntry> discoveryObjectives,
                                     List<ObjectiveEntry> researchObjectives, List<ICondition> recordConditions,
                                     VisibilityMode visibilityMode, HiddenPresentationMode hiddenPresentationMode,
                                     int sortOrder, boolean researchAfterDiscovery,
                                     @Nullable String recordConditionSignature, List<CollectionEntryRewardDefinition> rewards,
                                     int gameplayVersion, List<CollectionOutcomeDefinition> outcomes,
                                     Map<String, String> legacyResearchOutcomeMappings,
                                     List<ObjectiveEntry> legacyResearchObjectives, QuestText publicClue,
                                     @Nullable List<ResourceLocation> presentationItemTagMembers) {
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        if (categoryId == null || categoryId.isBlank()) throw new IllegalArgumentException("Entry categoryId is required");
        this.categoryId = categoryId.trim();
        this.displayName = Objects.requireNonNull(displayName, "entry displayName");
        this.description = description == null ? QuestText.literal("") : description;
        this.publicClue = publicClue == null ? QuestText.literal("") : publicClue;
        this.presentationItemTagMembers = presentationItemTagMembers == null ? null : List.copyOf(presentationItemTagMembers);
        this.subjectKind = Objects.requireNonNullElse(subjectKind, CollectionSubjectKind.CUSTOM);
        if (this.subjectKind != CollectionSubjectKind.CUSTOM && subjectId == null && itemTag == null) {
            throw new IllegalArgumentException("Entity/item entry needs a subjectId or itemTag");
        }
        if (itemTag != null && this.subjectKind != CollectionSubjectKind.ITEM) {
            throw new IllegalArgumentException("itemTag is only valid for ITEM entries");
        }
        this.subjectId = subjectId;
        this.itemTag = itemTag;
        this.icon = ObjectiveIcons.normalize(Objects.requireNonNullElse(icon, ObjectiveIconSpec.AUTO));
        this.content = List.copyOf(content == null ? List.of() : content);
        this.relatedItems = List.copyOf(relatedItems == null ? List.of() : relatedItems);
        this.discoveryObjectives = stableObjectives(discoveryObjectives, "discovery");
        this.researchObjectives = stableObjectives(researchObjectives, "research");
        this.recordConditions = List.copyOf(recordConditions == null ? List.of() : recordConditions);
        this.recordConditionSignature = recordConditionSignature;
        this.visibilityMode = Objects.requireNonNullElse(visibilityMode, VisibilityMode.VISIBLE_BY_DEFAULT);
        this.hiddenPresentationMode = Objects.requireNonNullElse(hiddenPresentationMode, HiddenPresentationMode.FULLY_HIDDEN);
        this.sortOrder = sortOrder;
        this.researchAfterDiscovery = researchAfterDiscovery;
        if (gameplayVersion != LEGACY_GAMEPLAY_VERSION && gameplayVersion != UNIFIED_GAMEPLAY_VERSION)
            throw new IllegalArgumentException("Unsupported collection gameplayVersion: " + gameplayVersion);
        this.gameplayVersion = gameplayVersion;
        this.outcomes = List.copyOf(outcomes == null ? List.of() : outcomes);
        this.legacyResearchOutcomeMappings = Map.copyOf(legacyResearchOutcomeMappings == null ? Map.of() : legacyResearchOutcomeMappings);
        this.legacyResearchObjectives = stableObjectives(legacyResearchObjectives, "legacy migration");
        if (isUnifiedGameplay() && (!this.researchObjectives.isEmpty() || researchAfterDiscovery))
            throw new IllegalArgumentException("Unified entries cannot contain independent research objectives");
        if (!isUnifiedGameplay() && (!this.outcomes.isEmpty() || !this.legacyResearchOutcomeMappings.isEmpty() || !this.legacyResearchObjectives.isEmpty()))
            throw new IllegalArgumentException("Legacy research and unified outcomes cannot be mixed");
        HashSet<String> outcomeIds = new HashSet<>();
        for (CollectionOutcomeDefinition outcome : this.outcomes)
            if (!outcomeIds.add(outcome.outcomeId())) throw new IllegalArgumentException("Duplicate outcomeId: " + outcome.outcomeId());
        HashSet<String> legacyIds = new HashSet<>();
        for (ObjectiveEntry legacy : this.legacyResearchObjectives) legacyIds.add(legacy.getObjectiveId());
        for (var mapping : this.legacyResearchOutcomeMappings.entrySet()) {
            if (!outcomeIds.contains(mapping.getValue())) throw new IllegalArgumentException("Unknown migration outcome: " + mapping.getValue());
            if (LEGACY_RESEARCH_COMPLETE.equals(mapping.getKey()) ? legacyIds.isEmpty() : !legacyIds.contains(mapping.getKey()))
                throw new IllegalArgumentException("Research migration requires original objective thresholds: " + mapping.getKey());
        }
        this.rewards = List.copyOf(rewards == null ? List.of() : rewards);
        HashSet<String> rewardIds = new HashSet<>();
        for (CollectionEntryRewardDefinition reward : this.rewards) {
            if (!rewardIds.add(reward.rewardId())) throw new IllegalArgumentException("Duplicate entry rewardId: " + reward.rewardId());
            if (reward.trigger() == CollectionEntryRewardTrigger.OUTCOME && !outcomeIds.contains(reward.outcomeId()))
                throw new IllegalArgumentException("Unknown reward outcome: " + reward.outcomeId());
            if (isUnifiedGameplay() && (reward.trigger() == CollectionEntryRewardTrigger.RESEARCH_COMPLETE
                    || reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE))
                throw new IllegalArgumentException("Unified entry rewards belong to discovery or a named outcome; run rewards belong to the binding");
        }
        HashSet<String> blocks = new HashSet<>();
        HashSet<String> researchIds = new HashSet<>();
        for (ObjectiveEntry objective : this.researchObjectives) researchIds.add(objective.getObjectiveId());
        for (CollectionContentBlock block : this.content) {
            if (!blocks.add(block.blockId())) throw new IllegalArgumentException("Duplicate content blockId: " + block.blockId());
            if (block.reveal() == CollectionContentReveal.RESEARCH_STEP && !researchIds.contains(block.revealStepId())) {
                throw new IllegalArgumentException("Unknown content research step: " + block.revealStepId());
            }
            if (block.reveal() == CollectionContentReveal.OUTCOME && !outcomeIds.contains(block.revealStepId()))
                throw new IllegalArgumentException("Unknown content outcome: " + block.revealStepId());
            if (isUnifiedGameplay() && (block.reveal() == CollectionContentReveal.RESEARCH_STEP || block.reveal() == CollectionContentReveal.RESEARCH_COMPLETE))
                throw new IllegalArgumentException("Unified content requires an explicit outcome instead of research reveal");
        }
    }

    private static List<ObjectiveEntry> stableObjectives(List<ObjectiveEntry> source, String scope) {
        List<ObjectiveEntry> objectives = List.copyOf(source == null ? List.of() : source);
        HashSet<String> ids = new HashSet<>();
        for (ObjectiveEntry objective : objectives) {
            if (!objective.hasObjectiveId() || !ids.add(objective.getObjectiveId())) {
                throw new IllegalArgumentException("Entry " + scope + " objectives require unique explicit stable IDs");
            }
        }
        return objectives;
    }

    public ResourceLocation getEntryId() { return entryId; }
    public String getCategoryId() { return categoryId; }
    public Component getDisplayName() { return displayName.resolve(null, QuestTextContext.empty()); }
    public Component getDescription() { return description.resolve(null, QuestTextContext.empty()); }
    public QuestText getDisplayQuestText() { return displayName; }
    public QuestText getDescriptionQuestText() { return description; }
    public CollectionSubjectKind getSubjectKind() { return subjectKind; }
    @Nullable public ResourceLocation getSubjectId() { return subjectId; }
    @Nullable public ResourceLocation getItemTag() { return itemTag; }
    public ObjectiveIconSpec getIcon() { return icon; }
    public List<CollectionContentBlock> getContent() { return content; }
    public List<ResourceLocation> getRelatedItems() { return relatedItems; }
    public List<ObjectiveEntry> getDiscoveryObjectives() { return discoveryObjectives; }
    public List<ObjectiveEntry> getResearchObjectives() { return researchObjectives; }
    public List<ICondition> getRecordConditions() { return recordConditions; }
    /** Canonical source identity for compiled conditions; custom callbacks retain identity equality. */
    @Nullable public String getRecordConditionSignature() { return recordConditionSignature; }
    public VisibilityMode getVisibilityMode() { return visibilityMode; }
    public HiddenPresentationMode getHiddenPresentationMode() { return hiddenPresentationMode; }
    public int getSortOrder() { return sortOrder; }
    public boolean isResearchAfterDiscovery() { return researchAfterDiscovery; }
    public List<CollectionEntryRewardDefinition> getRewards() { return rewards; }
    public int getGameplayVersion() { return gameplayVersion; }
    public boolean isUnifiedGameplay() { return gameplayVersion == UNIFIED_GAMEPLAY_VERSION; }
    public List<CollectionOutcomeDefinition> getOutcomes() { return outcomes; }
    @Nullable public CollectionOutcomeDefinition getOutcome(String outcomeId) {
        return outcomes.stream().filter(o -> o.outcomeId().equals(outcomeId)).findFirst().orElse(null);
    }
    /** Migration-only thresholds; these are never subscribed as new-gameplay objectives. */
    public List<ObjectiveEntry> getLegacyResearchObjectives() { return legacyResearchObjectives; }
    public Map<String, String> getLegacyResearchOutcomeMappings() { return legacyResearchOutcomeMappings; }
    /** Author-approved anonymous hint; it never implies permission to reveal the entry's identity. */
    public QuestText getPublicClueText() { return publicClue; }
    @Nullable public List<ResourceLocation> getPresentationItemTagMembers() { return presentationItemTagMembers; }
    public Component getPublicClue() { return publicClue.resolve(null, QuestTextContext.empty()); }
}
