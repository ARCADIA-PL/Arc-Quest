package org.arcadia.arc_quest.client.hud.guide;

public final class GuideConstants {
    public static final float SCROLL_SPEED = 14f;
    public static final float TAB_INDICATOR_SPEED = 17f;

    public static final int LIST_WIDTH = 205;
    public static final int LIST_MARGIN = 16;
    public static final int DETAIL_MARGIN = 12;
    public static final int ENTRY_HEIGHT = 24;
    public static final int TAB_HEIGHT = 22;

    static final int INTRO_ICON_SIZE = 32;
    static final float INTRO_ICON_SCALE = INTRO_ICON_SIZE / 16f;
    static final int INTRO_ICON_SECTION_HEIGHT = 44;

    public static final float OPEN_DURATION = 0.24f;
    public static final float CLOSE_DURATION = 0.18f;

    static int guidePanelWidth(int screenWidth) {
        return Math.max(210, Math.min(270, (int) (screenWidth * 0.28f)));
    }

    static int guidePopupHeight(int screenHeight, int desiredHeight) {
        int maxHeight = Math.max(80, Math.min(360, screenHeight - 32));
        int minHeight = Math.min(112, maxHeight);
        return Math.max(minHeight, Math.min(desiredHeight, maxHeight));
    }

    private GuideConstants() {}
}
