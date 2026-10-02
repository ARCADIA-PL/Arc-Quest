package org.arcadia.arc_quest.quest.api;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public final class CollectionQuestConfig {

    private final List<CollectionCategoryDefinition> categories;
    private final List<CollectionCompletionRule> questCompletionRules;
    private final List<CollectionRewardNode> questRewardNodes;
    private final TrackerPresentationMode trackerMode;
    private final CollectionPresentationMode journalMode;
    private final boolean revealAllEntriesByDefault;
    private final boolean allowManualRewardClaim;
    private final boolean showCategories;
    private final List<CollectionEntryDefinition> entries;
    private final Map<ResourceLocation, CollectionEntryDefinition> entriesById;

    public CollectionQuestConfig(List<CollectionCategoryDefinition> categories,
                                 List<CollectionCompletionRule> questCompletionRules,
                                 List<CollectionRewardNode> questRewardNodes,
                                 @Nullable TrackerPresentationMode trackerMode,
                                 @Nullable CollectionPresentationMode journalMode,
                                 boolean revealAllEntriesByDefault,
                                 boolean allowManualRewardClaim,
                                 boolean showCategories) {
        this(categories, questCompletionRules, questRewardNodes, trackerMode, journalMode,
                revealAllEntriesByDefault, allowManualRewardClaim, showCategories, List.of());
    }

    public CollectionQuestConfig(List<CollectionCategoryDefinition> categories,
                                 List<CollectionCompletionRule> questCompletionRules,
                                 List<CollectionRewardNode> questRewardNodes,
                                 @Nullable TrackerPresentationMode trackerMode,
                                 @Nullable CollectionPresentationMode journalMode,
                                 boolean revealAllEntriesByDefault,
                                 boolean allowManualRewardClaim,
                                 boolean showCategories,
                                 List<CollectionEntryDefinition> entries) {
        this.categories = List.copyOf(categories != null ? categories : List.of());
        this.questCompletionRules = List.copyOf(questCompletionRules != null ? questCompletionRules : List.of());
        this.questRewardNodes = List.copyOf(questRewardNodes != null ? questRewardNodes : List.of());
        this.trackerMode = trackerMode != null ? trackerMode : TrackerPresentationMode.SUMMARY;
        this.journalMode = journalMode != null ? journalMode : CollectionPresentationMode.GRID_WITH_DETAIL;
        this.revealAllEntriesByDefault = revealAllEntriesByDefault;
        this.allowManualRewardClaim = allowManualRewardClaim;
        this.showCategories = showCategories;
        this.entries = List.copyOf(entries == null ? List.of() : entries);
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> byId = new LinkedHashMap<>();
        for (CollectionEntryDefinition entry : this.entries) {
            if (byId.putIfAbsent(entry.getEntryId(), entry) != null) {
                throw new IllegalArgumentException("Duplicate collection entryId: " + entry.getEntryId());
            }
        }
        this.entriesById = Map.copyOf(byId);
    }

    public List<CollectionCategoryDefinition> getCategories() {
        return categories;
    }

    public List<CollectionCompletionRule> getQuestCompletionRules() {
        return questCompletionRules;
    }

    public List<CollectionRewardNode> getQuestRewardNodes() {
        return questRewardNodes;
    }

    public TrackerPresentationMode getTrackerMode() {
        return trackerMode;
    }

    public CollectionPresentationMode getJournalMode() {
        return journalMode;
    }

    public boolean isRevealAllEntriesByDefault() {
        return revealAllEntriesByDefault;
    }

    public boolean isAllowManualRewardClaim() {
        return allowManualRewardClaim;
    }

    public boolean isShowCategories() {
        return showCategories;
    }

    public List<CollectionEntryDefinition> getEntries() { return entries; }

    @Nullable public CollectionEntryDefinition getEntry(ResourceLocation entryId) { return entriesById.get(entryId); }
}
