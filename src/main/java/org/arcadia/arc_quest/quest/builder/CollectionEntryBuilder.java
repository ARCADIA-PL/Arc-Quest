package org.arcadia.arc_quest.quest.builder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CollectionEntryBuilder {
    private final ResourceLocation entryId;
    private String categoryId = "";
    private QuestText displayName;
    private boolean explicitName;
    private QuestText description = QuestText.literal("");
    private QuestText publicClue = QuestText.literal("");
    private CollectionSubjectKind kind = CollectionSubjectKind.CUSTOM;
    private ResourceLocation subjectId;
    private ResourceLocation itemTag;
    private ObjectiveIconSpec icon = ObjectiveIconSpec.AUTO;
    private final List<CollectionContentBlock> content = new ArrayList<>();
    private final List<ResourceLocation> relatedItems = new ArrayList<>();
    private final List<ObjectiveEntry> discovery = new ArrayList<>();
    private final List<ObjectiveEntry> research = new ArrayList<>();
    private final List<ICondition> conditions = new ArrayList<>();
    private VisibilityMode visibility = VisibilityMode.VISIBLE_BY_DEFAULT;
    private HiddenPresentationMode hidden = HiddenPresentationMode.FULLY_HIDDEN;
    private int sortOrder;
    private boolean researchAfterDiscovery;
    private final List<CollectionEntryRewardDefinition> rewards = new ArrayList<>();
    private boolean legacyGameplay;
    private boolean unifiedFeatures;
    private final List<CollectionOutcomeDefinition> outcomes = new ArrayList<>();
    private final Map<String, String> legacyResearchOutcomeMappings = new LinkedHashMap<>();
    private final List<ObjectiveEntry> legacyResearchObjectives = new ArrayList<>();
    private CollectionEntryBuilder(ResourceLocation entryId) { this.entryId = entryId; displayName = QuestText.literal(entryId.toString()); }
    public static CollectionEntryBuilder create(ResourceLocation entryId) { return new CollectionEntryBuilder(entryId); }
    public static CollectionEntryBuilder create(String entryId) { return create(ResourceLocation.parse(entryId)); }
    public CollectionEntryBuilder category(String categoryId) { this.categoryId = categoryId; return this; }
    public CollectionEntryBuilder displayName(String name) { return displayName(QuestText.literal(name)); }
    public CollectionEntryBuilder displayName(QuestText name) { displayName = name; explicitName = true; return this; }
    public CollectionEntryBuilder description(String text) { return description(QuestText.literal(text)); }
    public CollectionEntryBuilder description(QuestText text) { description = text; return this; }
    public CollectionEntryBuilder publicClue(String text) { return publicClue(QuestText.literal(text)); }
    public CollectionEntryBuilder publicClue(QuestText text) { publicClue = java.util.Objects.requireNonNull(text, "publicClue"); return this; }
    public CollectionEntryBuilder entity(EntityType<?> type) {
        if (!explicitName) displayName = QuestText.translatable(type.getDescriptionId());
        return subject(CollectionSubjectKind.ENTITY, BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }
    public CollectionEntryBuilder item(ItemLike item) {
        if (!explicitName) displayName = QuestText.component(item.asItem().getDescription());
        return subject(CollectionSubjectKind.ITEM, BuiltInRegistries.ITEM.getKey(item.asItem()));
    }
    public CollectionEntryBuilder subject(CollectionSubjectKind kind, ResourceLocation subjectId) { this.kind = kind; this.subjectId = subjectId; itemTag = null; return this; }
    public CollectionEntryBuilder itemTag(ResourceLocation tag) { kind = CollectionSubjectKind.ITEM; subjectId = null; itemTag = tag; return this; }
    public CollectionEntryBuilder icon(ObjectiveIconSpec icon) { this.icon = icon; return this; }
    public CollectionEntryBuilder iconItem(ItemLike item) { return icon(ObjectiveIcons.item(BuiltInRegistries.ITEM.getKey(item.asItem()))); }
    public CollectionEntryBuilder iconItem(ResourceLocation item) { return icon(ObjectiveIcons.item(item)); }
    public CollectionEntryBuilder iconTexture(ResourceLocation texture) { return icon(ObjectiveIcons.texture(texture)); }
    public CollectionEntryBuilder iconTexture(ItemLike item) { return iconItem(item); }
    public CollectionEntryBuilder content(CollectionContentBlock block) { content.add(block); return this; }
    public CollectionEntryBuilder text(String blockId, String text) { return text(blockId, QuestText.literal(text)); }
    /** Adds discovered-only archive text without resolving its translation on the server. */
    public CollectionEntryBuilder text(String blockId, QuestText text) { return content(new CollectionContentBlock(blockId, text, null, QuestText.literal(""))); }
    public CollectionEntryBuilder image(String blockId, ResourceLocation texture, int width, int height, String caption) {
        return image(blockId, texture, width, height, QuestText.literal(caption));
    }
    /** Adds a discovered-only image with a translated/component-backed caption. */
    public CollectionEntryBuilder image(String blockId, ResourceLocation texture, int width, int height, QuestText caption) {
        return content(new CollectionContentBlock(blockId, QuestText.literal(""), new GuideMediaDefinition(GuideMediaType.IMAGE, texture, null, width, height, false, false), caption));
    }
    public CollectionEntryBuilder relatedItem(ItemLike item) { return relatedItem(BuiltInRegistries.ITEM.getKey(item.asItem())); }
    public CollectionEntryBuilder relatedItem(ResourceLocation item) { relatedItems.add(item); return this; }
    public CollectionEntryBuilder discover(ObjectiveEntry objective) { discovery.add(objective); return this; }
    public CollectionEntryBuilder discover(ObjectiveBuilder objective) { return discover(objective.build()); }
    /** Compatibility only. New investigations put their objectives on the phase binding. */
    @Deprecated
    public CollectionEntryBuilder research(ObjectiveEntry objective) { useLegacy(); research.add(objective); return this; }
    public CollectionEntryBuilder research(ObjectiveBuilder objective) { return research(objective.build()); }
    public CollectionEntryBuilder recordWhen(ICondition condition) { conditions.add(condition); return this; }
    public CollectionEntryBuilder visibility(VisibilityMode visibility, HiddenPresentationMode hidden) { this.visibility = visibility; this.hidden = hidden; return this; }
    /**
     * Sets the card display order within its category; defaults to 0, with smaller values first and negatives allowed.
     * Favorites and category order take precedence. Ties retain the entry's first binding declaration in the current
     * phase, independently of that binding's sort order. This does not change investigation requirements.
     */
    public CollectionEntryBuilder sortOrder(int order) { sortOrder = order; return this; }
    public CollectionEntryBuilder researchAfterDiscovery(boolean value) { useLegacy(); researchAfterDiscovery = value; return this; }
    public CollectionEntryBuilder outcome(String outcomeId, String name) { return outcome(outcomeId, QuestText.literal(name)); }
    public CollectionEntryBuilder outcome(String outcomeId, QuestText name) { useUnified(); outcomes.add(new CollectionOutcomeDefinition(outcomeId, name)); return this; }
    public CollectionEntryBuilder legacyResearchObjective(ObjectiveEntry original) { useUnified(); legacyResearchObjectives.add(original); return this; }
    public CollectionEntryBuilder migrateResearchStep(ObjectiveEntry original, String outcomeId) {
        legacyResearchObjective(original); return migrateResearchStep(original.getObjectiveId(), outcomeId);
    }
    public CollectionEntryBuilder migrateResearchStep(String oldStepId, String outcomeId) {
        useUnified();
        if (legacyResearchOutcomeMappings.putIfAbsent(oldStepId, outcomeId) != null)
            throw new IllegalArgumentException("Duplicate research migration source: " + oldStepId);
        return this;
    }
    public CollectionEntryBuilder migrateResearchComplete(String outcomeId) { return migrateResearchStep(CollectionEntryDefinition.LEGACY_RESEARCH_COMPLETE, outcomeId); }
    public CollectionEntryBuilder reward(CollectionEntryRewardDefinition reward) {
        if (reward.trigger() == CollectionEntryRewardTrigger.RESEARCH_COMPLETE || reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE) useLegacy();
        if (reward.trigger() == CollectionEntryRewardTrigger.OUTCOME) useUnified();
        rewards.add(reward); return this;
    }
    public CollectionEntryBuilder reward(String rewardId, CollectionEntryRewardTrigger trigger, IReward... items) {
        return reward(new CollectionEntryRewardDefinition(rewardId, trigger, EntryRewardGrantMode.MANUAL, List.of(items), "", CollectionRewardPreviewVisibility.PUBLIC));
    }
    public CollectionEntryBuilder reward(String rewardId, CollectionEntryRewardTrigger trigger, EntryRewardGrantMode mode, IReward... items) {
        return reward(new CollectionEntryRewardDefinition(rewardId, trigger, mode, List.of(items), "", CollectionRewardPreviewVisibility.PUBLIC));
    }
    public CollectionEntryBuilder discoveryReward(String rewardId, IReward... items) { return reward(rewardId, CollectionEntryRewardTrigger.DISCOVERED, items); }
    @Deprecated
    public CollectionEntryBuilder researchReward(String rewardId, IReward... items) { return reward(new CollectionEntryRewardDefinition(rewardId, CollectionEntryRewardTrigger.RESEARCH_COMPLETE, List.of(items))); }
    @Deprecated
    public CollectionEntryBuilder bindingReward(String rewardId, IReward... items) { return reward(new CollectionEntryRewardDefinition(rewardId, CollectionEntryRewardTrigger.BINDING_COMPLETE, List.of(items))); }
    public CollectionEntryBuilder outcomeReward(String outcomeId, String rewardId, IReward... items) {
        return outcomeReward(outcomeId, rewardId, EntryRewardGrantMode.MANUAL, CollectionRewardPreviewVisibility.PUBLIC, items);
    }
    public CollectionEntryBuilder outcomeReward(String outcomeId, String rewardId, EntryRewardGrantMode mode, IReward... items) {
        return outcomeReward(outcomeId, rewardId, mode, CollectionRewardPreviewVisibility.PUBLIC, items);
    }
    public CollectionEntryBuilder outcomeReward(String outcomeId, String rewardId, EntryRewardGrantMode mode,
                                               CollectionRewardPreviewVisibility preview, IReward... items) {
        return reward(new CollectionEntryRewardDefinition(rewardId, CollectionEntryRewardTrigger.OUTCOME, mode, List.of(items), outcomeId, preview));
    }
    private void useLegacy() {
        if (unifiedFeatures) throw new IllegalStateException("Cannot mix legacy research/entry binding rewards with unified outcomes");
        legacyGameplay = true;
    }
    /** Explicit opt-in for old saved rules; new entries should keep the default version 2. */
    public CollectionEntryBuilder legacyGameplay() { useLegacy(); return this; }
    private void useUnified() {
        if (legacyGameplay) throw new IllegalStateException("Cannot mix unified outcomes with legacy research");
        unifiedFeatures = true;
    }
    public CollectionEntryDefinition build() { return new CollectionEntryDefinition(entryId, categoryId, displayName, description, kind, subjectId, itemTag, icon, content, relatedItems, discovery, research, conditions, visibility, hidden, sortOrder, researchAfterDiscovery, null, rewards,
            legacyGameplay ? CollectionEntryDefinition.LEGACY_GAMEPLAY_VERSION : CollectionEntryDefinition.UNIFIED_GAMEPLAY_VERSION,
            outcomes, legacyResearchOutcomeMappings, legacyResearchObjectives, publicClue); }
}
