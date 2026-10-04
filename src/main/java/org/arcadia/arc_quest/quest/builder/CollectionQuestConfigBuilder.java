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
    private long repeatCooldownTicks;
    private CollectionQuestConfigBuilder() {}
    public static CollectionQuestConfigBuilder create() { return new CollectionQuestConfigBuilder(); }
    public CollectionQuestConfigBuilder category(CollectionCategoryDefinition category) { categories.add(category); return this; }
    /** Adds a category with its current declaration index as the default display order. */
    public CollectionQuestConfigBuilder category(String id, String name) { return category(id, QuestText.literal(name)); }
    /** Accepts a translated/component-backed label with the same declaration-index default order. */
    public CollectionQuestConfigBuilder category(String id, QuestText name) { return category(id, name, categories.size()); }
    /**
     * Adds a category with an explicit display order; smaller values appear first and negatives are supported.
     * Equal values retain category declaration order. JSON categories default to 0; the two-argument overload
     * keeps its existing declaration-index default instead. This order only affects presentation.
     */
    public CollectionQuestConfigBuilder category(String id, String name, int sortOrder) {
        return category(id, QuestText.literal(name), sortOrder);
    }
    /** Accepts a translated/component-backed label with an explicit display order. */
    public CollectionQuestConfigBuilder category(String id, QuestText name, int sortOrder) {
        return category(new CollectionCategoryDefinition(id, name, null, sortOrder, List.of(), List.of(), List.of()));
    }
    public CollectionQuestConfigBuilder entry(CollectionEntryDefinition entry) { entries.add(entry); return this; }
    public CollectionQuestConfigBuilder entry(CollectionEntryBuilder entry) { return entry(entry.build()); }
    public CollectionQuestConfigBuilder reward(CollectionRewardNode reward) { rewards.add(reward); return this; }
    public CollectionQuestConfigBuilder revealAllEntries(boolean reveal) { revealAll = reveal; return this; }
    public CollectionQuestConfigBuilder manualRewardClaim(boolean manual) { manualClaim = manual; return this; }
    public CollectionQuestConfigBuilder showCategories(boolean show) { showCategories = show; return this; }
    /** World game ticks since acceptance; abandoning a run does not clear this fulfillment interval. */
    public CollectionQuestConfigBuilder repeatCooldownTicks(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("repeatCooldownTicks must be >= 0");
        repeatCooldownTicks = ticks; return this;
    }
    public CollectionQuestConfig build() { return new CollectionQuestConfig(categories, List.of(), rewards, TrackerPresentationMode.SUMMARY, CollectionPresentationMode.GRID_WITH_DETAIL, revealAll, manualClaim, showCategories, entries, repeatCooldownTicks); }
}
