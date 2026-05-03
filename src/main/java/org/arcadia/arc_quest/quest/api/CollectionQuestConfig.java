package org.arcadia.arc_quest.quest.api;

import javax.annotation.Nullable;
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

    public CollectionQuestConfig(List<CollectionCategoryDefinition> categories,
                                 List<CollectionCompletionRule> questCompletionRules,
                                 List<CollectionRewardNode> questRewardNodes,
                                 @Nullable TrackerPresentationMode trackerMode,
                                 @Nullable CollectionPresentationMode journalMode,
                                 boolean revealAllEntriesByDefault,
                                 boolean allowManualRewardClaim,
                                 boolean showCategories) {
        this.categories = List.copyOf(categories != null ? categories : List.of());
        this.questCompletionRules = List.copyOf(questCompletionRules != null ? questCompletionRules : List.of());
        this.questRewardNodes = List.copyOf(questRewardNodes != null ? questRewardNodes : List.of());
        this.trackerMode = trackerMode != null ? trackerMode : TrackerPresentationMode.SUMMARY;
        this.journalMode = journalMode != null ? journalMode : CollectionPresentationMode.GRID_WITH_DETAIL;
        this.revealAllEntriesByDefault = revealAllEntriesByDefault;
        this.allowManualRewardClaim = allowManualRewardClaim;
        this.showCategories = showCategories;
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
}
