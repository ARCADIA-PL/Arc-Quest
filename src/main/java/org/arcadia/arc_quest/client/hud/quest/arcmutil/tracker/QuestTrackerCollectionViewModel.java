package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import java.util.ArrayList;
import java.util.List;

public class QuestTrackerCollectionViewModel {
    public int discoveredCount;
    public int completedCount;
    public int totalCount;
    public final List<String> visibleEntryLines = new ArrayList<>();
    public final List<String> rewardLines = new ArrayList<>();
}
