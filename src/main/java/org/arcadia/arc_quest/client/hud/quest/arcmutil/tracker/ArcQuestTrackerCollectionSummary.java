package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiRect;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiText;

public class ArcQuestTrackerCollectionSummary extends ArcGuiElement {
    private final ArcGuiRect background;
    private final ArcGuiText discovered;
    private final ArcGuiText completed;
    private final ArcGuiText rewards;

    public ArcQuestTrackerCollectionSummary(int x, int y, int width) {
        super(x, y, width, 38);
        background = new ArcGuiRect(0, 0, width, 38, 0x1AA98BFF);
        discovered = new ArcGuiText(6, 4, width - 12, "").setColor(0xFFE8E8E8);
        completed = new ArcGuiText(6, 15, width - 12, "").setColor(0xFF7CFFB2);
        rewards = new ArcGuiText(6, 26, width - 12, "").setColor(0xFFFFD166);
        addChild(background);
        addChild(discovered);
        addChild(completed);
        addChild(rewards);
    }

    public void apply(QuestTrackerCollectionViewModel collection, int themeColor) {
        background.setColor((0x22 << 24) | (themeColor & 0x00FFFFFF));
        discovered.setText("Discovered " + collection.discoveredCount + " / " + collection.totalCount);
        completed.setText("Completed " + collection.completedCount + " / " + collection.totalCount);
        rewards.setText(collection.rewardLines.isEmpty() ? "Rewards pending" : collection.rewardLines.get(0));
    }
}
