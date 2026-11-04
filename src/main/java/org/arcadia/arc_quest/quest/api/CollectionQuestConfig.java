package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

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
    private final long repeatCooldownTicks;
    private net.minecraft.resources.ResourceLocation atlasId;
    private CollectionAtlasDefinition atlas;
    private CollectionAtlasProfile atlasProfile = CollectionAtlasProfile.defaults();

    public CollectionAtlasProfile getAtlasProfile() {
        return atlasProfile;
    }

    public CollectionQuestConfig withAtlasProfile(CollectionAtlasProfile profile) {
        var copy = withAtlas(atlasId, atlas);
        copy.atlasProfile = profile;
        return copy;
    }

    private List<CollectionSourceSetDefinition> sourceSets = List.of();

    public List<CollectionSourceSetDefinition> getSourceSets() {
        return sourceSets;
    }

    public CollectionQuestConfig withSourceSets(List<CollectionSourceSetDefinition> values) {
        var copy = withAtlas(atlasId, atlas);
        copy.sourceSets = List.copyOf(values);
        return copy;
    }

    @Nullable
    public ResourceLocation getAtlasId() {
        return atlasId;
    }

    @Nullable
    public CollectionAtlasDefinition getAtlas() {
        return atlas;
    }

    public CollectionQuestConfig withAtlas(ResourceLocation id, CollectionAtlasDefinition value) {
        CollectionQuestConfig copy =
                new CollectionQuestConfig(
                        categories,
                        questCompletionRules,
                        questRewardNodes,
                        trackerMode,
                        journalMode,
                        revealAllEntriesByDefault,
                        allowManualRewardClaim,
                        showCategories,
                        entries,
                        repeatCooldownTicks);
        copy.atlasId = id;
        copy.atlas = value;
        copy.sourceSets = sourceSets;
        copy.atlasProfile = atlasProfile;
        return copy;
    }

    public CollectionQuestConfig(
            List<CollectionCategoryDefinition> categories,
            List<CollectionCompletionRule> questCompletionRules,
            List<CollectionRewardNode> questRewardNodes,
            @Nullable TrackerPresentationMode trackerMode,
            @Nullable CollectionPresentationMode journalMode,
            boolean revealAllEntriesByDefault,
            boolean allowManualRewardClaim,
            boolean showCategories) {
        this(
                categories,
                questCompletionRules,
                questRewardNodes,
                trackerMode,
                journalMode,
                revealAllEntriesByDefault,
                allowManualRewardClaim,
                showCategories,
                List.of());
    }

    public CollectionQuestConfig(
            List<CollectionCategoryDefinition> categories,
            List<CollectionCompletionRule> questCompletionRules,
            List<CollectionRewardNode> questRewardNodes,
            @Nullable TrackerPresentationMode trackerMode,
            @Nullable CollectionPresentationMode journalMode,
            boolean revealAllEntriesByDefault,
            boolean allowManualRewardClaim,
            boolean showCategories,
            List<CollectionEntryDefinition> entries) {
        this(
                categories,
                questCompletionRules,
                questRewardNodes,
                trackerMode,
                journalMode,
                revealAllEntriesByDefault,
                allowManualRewardClaim,
                showCategories,
                entries,
                0L);
    }

    public CollectionQuestConfig(
            List<CollectionCategoryDefinition> categories,
            List<CollectionCompletionRule> questCompletionRules,
            List<CollectionRewardNode> questRewardNodes,
            @Nullable TrackerPresentationMode trackerMode,
            @Nullable CollectionPresentationMode journalMode,
            boolean revealAllEntriesByDefault,
            boolean allowManualRewardClaim,
            boolean showCategories,
            List<CollectionEntryDefinition> entries,
            long repeatCooldownTicks) {
        if (repeatCooldownTicks < 0)
            throw new IllegalArgumentException("repeatCooldownTicks must be >= 0");
        this.repeatCooldownTicks = repeatCooldownTicks;
        this.categories = List.copyOf(categories != null ? categories : List.of());
        this.questCompletionRules =
                List.copyOf(questCompletionRules != null ? questCompletionRules : List.of());
        this.questRewardNodes =
                List.copyOf(questRewardNodes != null ? questRewardNodes : List.of());
        this.trackerMode = trackerMode != null ? trackerMode : TrackerPresentationMode.SUMMARY;
        this.journalMode =
                journalMode != null ? journalMode : CollectionPresentationMode.GRID_WITH_DETAIL;
        this.revealAllEntriesByDefault = revealAllEntriesByDefault;
        this.allowManualRewardClaim = allowManualRewardClaim;
        this.showCategories = showCategories;
        this.entries = List.copyOf(entries == null ? List.of() : entries);
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> byId = new LinkedHashMap<>();
        for (CollectionEntryDefinition entry : this.entries) {
            if (byId.putIfAbsent(entry.getEntryId(), entry) != null) {
                throw new IllegalArgumentException(
                        "Duplicate collection entryId: " + entry.getEntryId());
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

    public List<CollectionEntryDefinition> getEntries() {
        return entries;
    }

    public long getRepeatCooldownTicks() {
        return repeatCooldownTicks;
    }

    @Nullable
    public CollectionEntryDefinition getEntry(ResourceLocation entryId) {
        return entriesById.get(entryId);
    }
}
