package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Small, fixed-cost GUI primitives: no extra textures, item passes or persistent glow. */
final class CollectionJournalVisuals {
    static final int TEXT = 0xE3EBEF;
    static final int MUTED = 0xA0B2BC;
    static final int FAINT = 0x71868F;
    static final int COMPLETE = 0xA4D3B2;
    private static final int LINE = 0xC6D8DF;

    private CollectionJournalVisuals() {}

    static void softRect(GuiGraphics g, HudRect box, int color) {
        if (box.width() < 5 || box.height() < 5) {
            g.fill(box.x(), box.y(), box.right(), box.bottom(), color);
            return;
        }
        g.fill(box.x() + 2, box.y(), box.right() - 2, box.bottom(), color);
        g.fill(box.x(), box.y() + 2, box.x() + 2, box.bottom() - 2, color);
        g.fill(box.right() - 2, box.y() + 2, box.right(), box.bottom() - 2, color);
    }

    static void specimen(GuiGraphics g, HudRect box, boolean selected, float hover, int theme, int alpha) {
        int tint = HudAnimUtil.lerpColor(0x243039, theme, selected ? .18f : .06f * hover);
        softRect(g, box, color(tint, Math.round(alpha * (.38f + .12f * hover))));
        g.fill(box.x() + 6, box.bottom() - 1, box.right() - 6, box.bottom(), color(LINE, alpha / 16));
        if (selected) {
            int mark = Math.max(20, box.width() / 3);
            g.fill(box.x() + (box.width() - mark) / 2, box.bottom() - 1,
                    box.x() + (box.width() + mark) / 2, box.bottom(), color(theme, alpha));
        }
    }

    static void iconPlate(GuiGraphics g, int x, int y, int size, int theme, int alpha, float hover) {
        // Seven fixed strips approximate the circular specimen mount with a bounded draw cost.
        int narrow = size / 3, medium = size / 6, edge = Math.max(2, size / 16);
        int ink = color(theme, Math.round(alpha * (.07f + .04f * hover)));
        g.fill(x + narrow, y, x + size - narrow, y + edge, ink);
        g.fill(x + medium, y + edge, x + size - medium, y + edge * 3, ink);
        g.fill(x + edge, y + edge * 3, x + size - edge, y + size / 3, ink);
        g.fill(x, y + size / 3, x + size, y + size - size / 3, ink);
        g.fill(x + edge, y + size - size / 3, x + size - edge, y + size - edge * 3, ink);
        g.fill(x + medium, y + size - edge * 3, x + size - medium, y + size - edge, ink);
        g.fill(x + narrow, y + size - edge, x + size - narrow, y + size, ink);
    }

    static void panel(GuiGraphics g, HudRect box, int theme, int alpha) {
        softRect(g, box, color(HudAnimUtil.lerpColor(0x1B1B1B, theme, .18f), alpha * 3 / 4));
        g.fill(box.x() + 10, box.y(), box.right() - 10, box.y() + 1, color(theme, alpha / 2));
    }

    static void bookmark(GuiGraphics g, int x, int y, boolean selected, int theme, int alpha) {
        int ink = color(theme, alpha);
        g.fill(x, y, x + 7, y + 1, ink);
        g.fill(x, y, x + 1, y + 9, ink);
        g.fill(x + 6, y, x + 7, y + 9, ink);
        g.fill(x + 1, y + 7, x + 2, y + 8, ink);
        g.fill(x + 2, y + 6, x + 3, y + 7, ink);
        g.fill(x + 3, y + 5, x + 4, y + 6, ink);
        g.fill(x + 4, y + 6, x + 5, y + 7, ink);
        g.fill(x + 5, y + 7, x + 6, y + 8, ink);
        if (selected) {
            g.fill(x + 1, y + 1, x + 6, y + 5, ink);
            g.fill(x + 1, y + 5, x + 3, y + 6, ink);
            g.fill(x + 4, y + 5, x + 6, y + 6, ink);
            g.fill(x + 1, y + 6, x + 2, y + 7, ink);
            g.fill(x + 5, y + 6, x + 6, y + 7, ink);
        }
    }

    static void search(GuiGraphics g, int x, int y, int theme, int alpha) {
        int ink = color(theme, alpha);
        g.fill(x + 2, y, x + 6, y + 1, ink);
        g.fill(x + 2, y + 7, x + 6, y + 8, ink);
        g.fill(x, y + 2, x + 1, y + 6, ink);
        g.fill(x + 7, y + 2, x + 8, y + 6, ink);
        g.fill(x + 1, y + 1, x + 2, y + 2, ink);
        g.fill(x + 6, y + 1, x + 7, y + 2, ink);
        g.fill(x + 1, y + 6, x + 2, y + 7, ink);
        g.fill(x + 6, y + 6, x + 7, y + 7, ink);
        g.fill(x + 7, y + 7, x + 9, y + 9, ink);
        g.fill(x + 9, y + 9, x + 10, y + 10, ink);
    }

    static void status(GuiGraphics g, int x, int y, boolean complete, int ink, int alpha) {
        int color = color(ink, alpha);
        if (complete) {
            g.fill(x, y + 3, x + 2, y + 5, color);
            g.fill(x + 2, y + 5, x + 4, y + 7, color);
            g.fill(x + 4, y + 3, x + 6, y + 5, color);
            g.fill(x + 6, y + 1, x + 8, y + 3, color);
        } else {
            g.fill(x + 2, y + 2, x + 5, y + 5, color);
        }
    }

    static void focus(GuiGraphics g, int x, int y, int theme, int alpha) {
        int ink = color(theme, alpha);
        g.fill(x + 3, y, x + 4, y + 3, ink);
        g.fill(x + 3, y + 4, x + 4, y + 7, ink);
        g.fill(x, y + 3, x + 3, y + 4, ink);
        g.fill(x + 4, y + 3, x + 7, y + 4, ink);
    }

    static int color(int rgb, int alpha) { return HudAnimUtil.withAlpha(rgb, alpha); }
}
