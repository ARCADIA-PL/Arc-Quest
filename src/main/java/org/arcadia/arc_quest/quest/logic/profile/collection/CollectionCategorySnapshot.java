package org.arcadia.arc_quest.quest.logic.profile.collection;

public final class CollectionCategorySnapshot {

    private final String categoryId;
    private final int totalEntries;
    private final int completedEntries;
    private final int visibleEntries;
    private final int discoveredEntries;

    public CollectionCategorySnapshot(String categoryId,
                                      int totalEntries,
                                      int completedEntries,
                                      int visibleEntries,
                                      int discoveredEntries) {
        this.categoryId = categoryId == null ? "" : categoryId;
        this.totalEntries = Math.max(0, totalEntries);
        this.completedEntries = Math.max(0, completedEntries);
        this.visibleEntries = Math.max(0, visibleEntries);
        this.discoveredEntries = Math.max(0, discoveredEntries);
    }

    public String getCategoryId() {
        return categoryId;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public int getCompletedEntries() {
        return completedEntries;
    }

    public int getVisibleEntries() {
        return visibleEntries;
    }

    public int getDiscoveredEntries() {
        return discoveredEntries;
    }

    public boolean hasEntries() {
        return totalEntries > 0;
    }

    public boolean isCompleted() {
        return totalEntries > 0 && completedEntries >= totalEntries;
    }

    public float getCompletionRatio() {
        return totalEntries <= 0 ? 0f : completedEntries / (float) totalEntries;
    }

    public float getDiscoveryRatio() {
        return totalEntries <= 0 ? 0f : discoveredEntries / (float) totalEntries;
    }
}
