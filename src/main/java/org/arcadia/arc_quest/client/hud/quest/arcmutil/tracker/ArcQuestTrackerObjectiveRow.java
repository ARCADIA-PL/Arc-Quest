package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiRect;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiText;

public class ArcQuestTrackerObjectiveRow extends ArcGuiElement {
    private final ArcGuiRect background;
    private final ArcGuiText label;
    private final ArcGuiRect progressTrack;
    private final ArcGuiRect progressFill;
    private int barWidth;

    public ArcQuestTrackerObjectiveRow(int x, int y, int width) {
        super(x, y, width, 17);
        this.barWidth = width;
        background = new ArcGuiRect(0, 0, width, 16, 0x18000000);
        label = new ArcGuiText(4, 1, width - 8, "").setColor(0xFFE8E8E8);
        progressTrack = new ArcGuiRect(4, 13, width - 8, 2, 0x33000000);
        progressFill = new ArcGuiRect(4, 13, 0, 2, 0xFF4FC3F7);
        addChild(background);
        addChild(label);
        addChild(progressTrack);
        addChild(progressFill);
    }

    public void apply(QuestTrackerObjectiveViewModel objective, int themeColor) {
        boolean complete = objective.complete;
        String text = complete ? "✓ " + objective.text : "• " + objective.text + " " + objective.progress + "/" + objective.required;
        int color = complete ? 0xFF7CFFB2 : 0xFFE8E8E8;
        label.setText(text);
        label.setColor(color);
        progressTrack.setVisible(true);
        progressFill.setVisible(true);
        progressFill.setWidth(Math.round((barWidth - 8) * Math.max(0f, Math.min(1f, objective.progressVisual))));
        progressFill.setColor(complete ? 0xFF7CFFB2 : themeColor);
        background.setColor(complete ? 0x1A7CFFB2 : 0x18000000);
    }

    public void applyLine(String text, int color) {
        label.setText(text);
        label.setColor(color);
        progressTrack.setVisible(false);
        progressFill.setVisible(false);
        background.setColor(0x12000000);
    }
}
