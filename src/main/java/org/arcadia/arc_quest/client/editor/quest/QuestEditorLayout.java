package org.arcadia.arc_quest.client.editor.quest;

import org.arcadia.arc_quest.client.hud.component.HudRect;

record QuestEditorLayout(HudRect header, HudRect phaseList, HudRect graph,
                         HudRect inspector, HudRect statusBar, HudRect listViewport,
                         HudRect graphViewport, HudRect inspectorViewport,
                         HudRect narrowTabs, boolean narrow) {
    static final int PANEL_HEADER_HEIGHT = 24;
    static final int TOP_HEIGHT = 52;
    static final int TAB_HEIGHT = 22;

    static QuestEditorLayout calculate(int screenWidth, int screenHeight) {
        int margin = screenWidth < 560 ? 10 : 16;
        int gap = screenWidth < 720 ? 8 : 12;
        int statusHeight = 18;
        HudRect header = new HudRect(0, 0, screenWidth, TOP_HEIGHT);
        HudRect status = new HudRect(margin, Math.max(TOP_HEIGHT, screenHeight - statusHeight - 6),
                Math.max(1, screenWidth - margin * 2), statusHeight);
        int contentWidth = Math.max(1, screenWidth - margin * 2);

        if (screenWidth < 560) {
            HudRect tabs = new HudRect(margin, TOP_HEIGHT, contentWidth, TAB_HEIGHT);
            int panelY = tabs.bottom() + 7;
            HudRect activePanel = new HudRect(margin, panelY, contentWidth,
                    Math.max(1, status.y() - panelY - 7));
            return create(header, activePanel, activePanel, activePanel, status, tabs, true);
        }

        int contentY = TOP_HEIGHT + 5;
        int contentHeight = Math.max(1, status.y() - contentY - 7);
        int listWidth = clamp(Math.round(contentWidth * 0.22f), 160, 220);
        int inspectorWidth = clamp(Math.round(contentWidth * 0.27f), 220, 310);
        int graphWidth = contentWidth - listWidth - inspectorWidth - gap * 2;
        if (graphWidth >= 310) {
            HudRect list = new HudRect(margin, contentY, listWidth, contentHeight);
            HudRect graph = new HudRect(list.right() + gap, contentY, graphWidth, contentHeight);
            HudRect inspector = new HudRect(graph.right() + gap, contentY, inspectorWidth, contentHeight);
            return create(header, list, graph, inspector, status, new HudRect(0, 0, 0, 0), false);
        }

        listWidth = clamp(Math.round(contentWidth * 0.29f), 150, 190);
        int workWidth = Math.max(1, contentWidth - listWidth - gap);
        int stackedHeight = Math.max(1, contentHeight - gap);
        int graphHeight = Math.max(1, Math.round(stackedHeight * 0.61f));
        HudRect list = new HudRect(margin, contentY, listWidth, contentHeight);
        HudRect graph = new HudRect(list.right() + gap, contentY, workWidth, graphHeight);
        HudRect inspector = new HudRect(graph.x(), graph.bottom() + gap, workWidth,
                Math.max(1, stackedHeight - graphHeight));
        return create(header, list, graph, inspector, status, new HudRect(0, 0, 0, 0), false);
    }

    private static QuestEditorLayout create(HudRect header, HudRect phaseList, HudRect graph,
                                            HudRect inspector, HudRect statusBar,
                                            HudRect narrowTabs, boolean narrow) {
        HudRect listViewport = new HudRect(phaseList.x() + 7,
                phaseList.y() + PANEL_HEADER_HEIGHT + 4,
                Math.max(1, phaseList.width() - 13),
                Math.max(1, phaseList.height() - PANEL_HEADER_HEIGHT - 9));
        HudRect graphViewport = new HudRect(graph.x() + 4,
                graph.y() + PANEL_HEADER_HEIGHT + 2,
                Math.max(1, graph.width() - 8),
                Math.max(1, graph.height() - PANEL_HEADER_HEIGHT - 6));
        HudRect inspectorViewport = new HudRect(inspector.x() + 10,
                inspector.y() + PANEL_HEADER_HEIGHT + 7,
                Math.max(1, inspector.width() - 18),
                Math.max(1, inspector.height() - PANEL_HEADER_HEIGHT - 14));
        return new QuestEditorLayout(header, phaseList, graph, inspector, statusBar,
                listViewport, graphViewport, inspectorViewport, narrowTabs, narrow);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
