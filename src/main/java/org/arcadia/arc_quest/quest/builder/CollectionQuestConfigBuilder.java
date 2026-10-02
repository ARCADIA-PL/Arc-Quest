package org.arcadia.arc_quest.quest.builder;

import org.arcadia.arc_quest.quest.api.*;
import java.util.ArrayList;
import java.util.List;

public final class CollectionQuestConfigBuilder {
    private final List<CollectionCategoryDefinition> categories = new ArrayList<>();
    private final List<CollectionEntryDefinition> entries = new ArrayList<>();
    private final List<CollectionRewardNode> rewards = new ArrayList<>();
    private boolean revealAll;
    private boolean manualClaim = true;
    private boolean showCategories = true;
    private CollectionQuestConfigBuilder() {}
    public static CollectionQuestConfigBuilder create() { return new CollectionQuestConfigBuilder(); }
    public CollectionQuestConfigBuilder category(CollectionCategoryDefinition category) { categories.add(category); return this; }
    public CollectionQuestConfigBuilder category(String id, String name) { return category(new CollectionCategoryDefinition(id, QuestText.literal(name), null, categories.size(), List.of(), List.of(), List.of())); }
    public CollectionQuestConfigBuilder entry(CollectionEntryDefinition entry) { entries.add(entry); return this; }
    public CollectionQuestConfigBuilder entry(CollectionEntryBuilder entry) { return entry(entry.build()); }
    public CollectionQuestConfigBuilder reward(CollectionRewardNode reward) { rewards.add(reward); return this; }
    public CollectionQuestConfigBuilder revealAllEntries(boolean reveal) { revealAll = reveal; return this; }
    public CollectionQuestConfigBuilder manualRewardClaim(boolean manual) { manualClaim = manual; return this; }
    public CollectionQuestConfigBuilder showCategories(boolean show) { showCategories = show; return this; }
    public CollectionQuestConfig build() { return new CollectionQuestConfig(categories, List.of(), rewards, TrackerPresentationMode.SUMMARY, CollectionPresentationMode.GRID_WITH_DETAIL, revealAll, manualClaim, showCategories, entries); }
}
