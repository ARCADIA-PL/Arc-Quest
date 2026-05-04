package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiRect;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiText;

public class ArcQuestTrackerCollectionSummary extends ArcGuiElement {
    private final ArcGuiRect background;
    private final ArcGuiText heading;
    private final ArcGuiText discovered;
    private final ArcGuiText completed;
    private final ArcGuiRect discoveredTrack;
    private final ArcGuiRect discoveredFill;
    private final ArcGuiRect completedTrack;
    private final ArcGuiRect completedFill;
    private final ArcGuiText rewards;
    private final int barWidth;

    public ArcQuestTrackerCollectionSummary(int x, int y, int width) {
        super(x, y, width, ArcQuestTrackerConstants.COLLECTION_SUMMARY_HEIGHT);
        barWidth = width - 12;
        background = new ArcGuiRect(0, 0, width, ArcQuestTrackerConstants.COLLECTION_SUMMARY_HEIGHT, 0x1AA98BFF);
        heading = new ArcGuiText(6, 4, width - 12, "Collection progress").setColor(0xFFEDE7FF);
        discovered = new ArcGuiText(6, 15, width - 12, "").setColor(0xFFE8E8E8);
        discoveredTrack = new ArcGuiRect(6, 25, barWidth, 2, 0x33000000);
        discoveredFill = new ArcGuiRect(6, 25, 0, 2, 0xFFA98BFF);
        completed = new ArcGuiText(6, 29, width - 12, "").setColor(0xFF7CFFB2);
        completedTrack = new ArcGuiRect(6, 39, barWidth, 2, 0x33000000);
        completedFill = new ArcGuiRect(6, 39, 0, 2, 0xFF7CFFB2);
        rewards = new ArcGuiText(6, 43, width - 12, "").setColor(0xFFFFD166);
        addChild(background);
        addChild(heading);
        addChild(discovered);
        addChild(discoveredTrack);
        addChild(discoveredFill);
        addChild(completed);
        addChild(completedTrack);
        addChild(completedFill);
        addChild(rewards);
    }

    public void apply(QuestTrackerCollectionViewModel collection, int themeColor) {
        int total = Math.max(1, collection.totalCount);
        background.setColor((0x22 << 24) | (themeColor & 0x00FFFFFF));
        heading.setColor((0xFF << 24) | (themeColor & 0x00FFFFFF));
        discovered.setText("Discovered " + collection.discoveredCount + " / " + total);
        completed.setText("Completed " + collection.completedCount + " / " + total);
        discoveredFill.setWidth(Math.round(barWidth * Math.min(1f, collection.discoveredCount / (float) total)));
        completedFill.setWidth(Math.round(barWidth * Math.min(1f, collection.completedCount / (float) total)));
        discoveredFill.setColor((0xFF << 24) | (themeColor & 0x00FFFFFF));
        rewards.setText(collection.rewardLines.isEmpty() ? "Rewards pending" : collection.rewardLines.get(0));
    }
}
