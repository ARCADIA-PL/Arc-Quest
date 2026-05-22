package org.arcadia.arc_quest.client.hud.guide;

final class GuideScreenLayout {
    static final int PANEL_X = 26;
    static final int PANEL_Y = 28;
    static final int PANEL_W = 470;
    static final int PANEL_MIN_H = 280;
    static final int PANEL_MAX_H = 360;
    static final int CONTROL_W = 54;
    static final int CONTROL_H = 18;
    static final int TEXT_LINE_H = 10;

    private GuideScreenLayout() {
    }

    static Layout compute(int screenWidth, int screenHeight) {
        int topWidth = Math.min(PANEL_W, screenWidth - PANEL_X - 24);
        int bottomWidth = Math.max(360, topWidth - 78);
        int height = Math.min(PANEL_MAX_H, Math.max(PANEL_MIN_H, screenHeight - 60));
        int left = PANEL_X;
        int top = PANEL_Y;
        int headerH = 58;
        int mediaW = topWidth - 24;
        int mediaH = 116;
        int mediaX = left + 12;
        int mediaY = top + headerH + 8;
        int textX = left + 14;
        int descY = mediaY + mediaH + 14;
        int descW = bottomWidth - 34;
        int bottom = top + height;
        int controlY = bottom - 24;
        int prevX = left + 14;
        int nextX = prevX + CONTROL_W + 8;
        int closeX = left + bottomWidth - CONTROL_W - 12;
        int descH = Math.max(40, controlY - descY - 18);
        return new Layout(left, top, topWidth, bottomWidth, height, headerH, mediaX, mediaY, mediaW, mediaH, textX, descY, descW, descH, bottom, controlY, prevX, nextX, closeX);
    }

    record Layout(int x, int y, int tw, int bw, int h, int hh,
                  int mx, int my, int mw, int mh,
                  int tx, int dy, int dw, int dh,
                  int b, int cy, int px, int nx, int cx) {
        Layout offset(int dx) {
            return new Layout(x - dx, y, tw, bw, h, hh, mx - dx, my, mw, mh, tx - dx, dy, dw, dh, b, cy, px - dx, nx - dx, cx - dx);
        }
    }
}
