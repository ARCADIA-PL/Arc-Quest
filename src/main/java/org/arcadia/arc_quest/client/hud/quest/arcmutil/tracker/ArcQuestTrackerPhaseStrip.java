package org.arcadia.arc_quest.client.hud.quest.arcmutil.tracker;

import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiText;

import java.util.ArrayList;
import java.util.List;

public class ArcQuestTrackerPhaseStrip extends ArcGuiElement {
    private final List<ArcGuiText> labels = new ArrayList<>();

    public ArcQuestTrackerPhaseStrip(int x, int y, int width) {
        super(x, y, width, 12);
    }

    public void apply(List<QuestTrackerPhaseViewModel> phases, int themeColor) {
        ensure(phases.size());
        int cursor = 0;
        for (int i = 0; i < phases.size(); i++) {
            QuestTrackerPhaseViewModel phase = phases.get(i);
            ArcGuiText label = labels.get(i);
            label.setVisible(true);
            label.setX(cursor);
            label.setText((phase.displayed ? "◆ " : "◇ ") + phase.phaseName);
            label.setColor(phase.displayed ? themeColor : 0xFF9FB7D8);
            cursor += Math.min(width - cursor, label.getWidth() + 8);
        }
        for (int i = phases.size(); i < labels.size(); i++) labels.get(i).setVisible(false);
    }

    private void ensure(int count) {
        while (labels.size() < count) {
            ArcGuiText label = new ArcGuiText(0, 0, "").setColor(0xFF9FB7D8);
            labels.add(label);
            addChild(label);
        }
    }
}
