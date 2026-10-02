package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
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
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        if (categoryId == null || categoryId.isBlank()) throw new IllegalArgumentException("Entry categoryId is required");
        this.categoryId = categoryId.trim();
        this.displayName = Objects.requireNonNull(displayName, "entry displayName");
        this.description = description == null ? QuestText.literal("") : description;
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
        HashSet<String> blocks = new HashSet<>();
        HashSet<String> researchIds = new HashSet<>();
        for (ObjectiveEntry objective : this.researchObjectives) researchIds.add(objective.getObjectiveId());
        for (CollectionContentBlock block : this.content) {
            if (!blocks.add(block.blockId())) throw new IllegalArgumentException("Duplicate content blockId: " + block.blockId());
            if (block.reveal() == CollectionContentReveal.RESEARCH_STEP && !researchIds.contains(block.revealStepId())) {
                throw new IllegalArgumentException("Unknown content research step: " + block.revealStepId());
            }
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
}
