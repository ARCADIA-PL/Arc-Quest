package org.arcadia.arc_quest.client.editor.quest;

import org.arcadia.arc_quest.client.hud.component.HudRect;

record QuestEditorLayout(HudRect header, HudRect phaseList, HudRect workspace,
                         HudRect statusBar, HudRect listViewport,
                         HudRect workspaceViewport) {
    static final int PANEL_HEADER_HEIGHT = 24;
    static final int TOP_HEIGHT = 52;

    static QuestEditorLayout calculate(int screenWidth, int screenHeight) {
        int margin = screenWidth < 560 ? 8 : 16;
        int gap = screenWidth < 560 ? 7 : 12;
        int statusHeight = 18;
        HudRect header = new HudRect(0, 0, screenWidth, TOP_HEIGHT);
        HudRect status = new HudRect(margin, Math.max(TOP_HEIGHT, screenHeight - statusHeight - 6),
                Math.max(1, screenWidth - margin * 2), statusHeight);
        int contentY = TOP_HEIGHT + 5;
        int contentHeight = Math.max(1, status.y() - contentY - 7);
        int contentWidth = Math.max(1, screenWidth - margin * 2);
        int preferredListWidth = screenWidth < 560 ? Math.round(contentWidth * 0.34f) : 205;
        int listWidth = clamp(preferredListWidth, screenWidth < 560 ? 104 : 160,
                screenWidth < 560 ? 164 : 224);
        int minimumWorkspaceWidth = screenWidth < 560 ? 116 : 260;
        listWidth = Math.min(listWidth, Math.max(88, contentWidth - gap - minimumWorkspaceWidth));
        HudRect phaseList = new HudRect(margin, contentY, Math.max(1, listWidth), contentHeight);
        HudRect workspace = new HudRect(phaseList.right() + gap, contentY,
                Math.max(1, contentWidth - phaseList.width() - gap), contentHeight);
        HudRect listViewport = new HudRect(phaseList.x() + 7,
                phaseList.y() + PANEL_HEADER_HEIGHT + 4,
                Math.max(1, phaseList.width() - 13),
                Math.max(1, phaseList.height() - PANEL_HEADER_HEIGHT - 9));
        HudRect workspaceViewport = new HudRect(workspace.x() + 4,
                workspace.y() + PANEL_HEADER_HEIGHT + 2,
                Math.max(1, workspace.width() - 8),
                Math.max(1, workspace.height() - PANEL_HEADER_HEIGHT - 6));
        return new QuestEditorLayout(header, phaseList, workspace, status,
                listViewport, workspaceViewport);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
