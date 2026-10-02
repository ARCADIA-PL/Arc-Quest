package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import java.util.ArrayList;
import java.util.List;

/** Reward groups and unusually large item lists share one bounded strip instead of adding rows. */
public final class CollectionRewardStripPages {
    private CollectionRewardStripPages() {}
    public record Page(int rewardIndex, int itemStart, int itemEnd) {}

    public static List<Page> pages(List<Integer> itemCounts, int capacity) {
        return pages(itemCounts, java.util.Collections.nCopies(itemCounts.size(), false), capacity);
    }

    public static List<Page> pages(List<Integer> itemCounts, List<Boolean> textGroups, int capacity) {
        capacity = Math.max(1, capacity);
        List<Page> result = new ArrayList<>();
        for (int group = 0; group < itemCounts.size(); group++) {
            int count = Math.max(0, itemCounts.get(group));
            int groupCapacity = group < textGroups.size() && textGroups.get(group) ? 1 : capacity;
            if (count == 0) result.add(new Page(group, 0, 0));
            else for (int start = 0; start < count; start += groupCapacity)
                result.add(new Page(group, start, Math.min(count, start + groupCapacity)));
        }
        return List.copyOf(result);
    }

    public static int clamp(int page, int count) { return Math.max(0, Math.min(page, Math.max(0, count - 1))); }
}
