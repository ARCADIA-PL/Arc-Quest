package org.arcadia.arc_quest.client.hud.guide;

/** Left sidebar geometry shared by rendering, clipping and input. The world stays visible on the right. */
public record GuideReaderLayout(Box panel, Box title, Box media, Box body,
                                Box close, Box previous, Box next, Box settings,
                                int dividerY, int footerY, boolean columns) {
    public record Box(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public static Box frame(int width, int height) {
        int w = Math.max(1, Math.min(Math.round(width * .62f),
                Math.max(210, Math.min(340, Math.round(width * .30f)))));
        int h = Math.max(1, Math.min(580, height - 24));
        return new Box(0, (height - h) / 2, w, h);
    }

    public static GuideReaderLayout measure(int width, int height, int titleLines, int lineHeight,
                                             boolean hasMedia, boolean iconOnly, float textScale) {
        Box panel = frame(width, height);
        int pad = panel.width < 240 ? 12 : 16;
        int x = panel.x + pad;
        int innerWidth = Math.max(1, panel.width - pad * 2);
        int titleHeight = Math.max(1, Math.min(2, titleLines)) * (lineHeight + 3);
        Box title = new Box(x, panel.y + 24, Math.max(1, innerWidth - 26), titleHeight);
        int divider = title.bottom() + 8;
        int footer = panel.bottom() - 31;
        int contentY = divider + 12;
        int contentHeight = Math.max(1, footer - 10 - contentY);
        // The illustration must never take the text's reading space, even at a
        // large GUI scale. Keep the sidebar vertical at every window size.
        int mediaHeight = !hasMedia ? 0 : iconOnly ? Math.min(36, contentHeight / 3)
                : Math.min(104, Math.min(Math.round(innerWidth * 9f / 16f), contentHeight * 2 / 5));
        Box media = new Box(x, contentY, innerWidth, mediaHeight);
        int gap = mediaHeight > 0 ? 10 : 0;
        Box body = new Box(x, contentY + mediaHeight + gap, innerWidth,
                Math.max(1, contentHeight - mediaHeight - gap));
        return new GuideReaderLayout(panel, title, media, body,
                new Box(panel.right() - pad - 19, panel.y + 10, 22, 22),
                new Box(panel.right() - pad - 52, footer + 2, 24, 22),
                new Box(panel.right() - pad - 24, footer + 2, 24, 22),
                new Box(x, footer + 2, 30, 22), divider, footer, false);
    }

    public static int lineAdvance(int fontHeight, float scale) {
        return Math.max(1, (int) Math.ceil(fontHeight * scale)) + Math.max(3, Math.round(4 * scale));
    }

    public static int thumbHeight(int viewport, int contentHeight) {
        return Math.min(viewport, Math.max(16, Math.round(viewport * (viewport / (float) Math.max(1, contentHeight)))));
    }
}
