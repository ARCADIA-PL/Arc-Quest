package org.arcadia.arc_quest.client.hud.quest.journal.component;

import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalConstants;

public record JournalScreenLayout(HudRect listPanel, HudRect detailPanel) {
    public static JournalScreenLayout calculate(int scaledWidth, int scaledHeight, float easeProgress) {
        float slideOffset = (1f - easeProgress) * 200f;
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int panelY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int panelHeight = Math.max(1, scaledHeight - 20 - panelY);
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH
                + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailWidth = Math.max(1, scaledWidth - detailX - JournalConstants.DETAIL_MARGIN);
        return new JournalScreenLayout(
                new HudRect(listX, panelY, JournalConstants.LIST_WIDTH, panelHeight),
                new HudRect(detailX, panelY, detailWidth, panelHeight));
    }

    public int rightEdge() {
        return detailPanel.right();
    }
}
