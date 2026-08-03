package org.arcadia.arc_quest.client.hud.quest.journal.component;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class JournalMarqueeTextRenderer {
    private static final double SPEED_PIXELS_PER_SECOND = 30.0;
    private static final int END_PAUSE_MILLIS = 1_500;

    private JournalMarqueeTextRenderer() {
    }

    public static void drawString(GuiGraphics graphics, Font font, String text,
                                  int localX, int localY, int maxWidth, int color, boolean shadow,
                                  int absoluteX, int absoluteY, int parentClipX1, int parentClipY1,
                                  int parentClipX2, int parentClipY2, ScissorController scissorController) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, localX, localY, color, shadow);
            return;
        }
        ClipBounds clip = clipBounds(font, absoluteX, absoluteY, maxWidth,
                parentClipX1, parentClipY1, parentClipX2, parentClipY2);
        if (!clip.valid()) return;
        scissorController.set(graphics, clip.x1(), clip.y1(), clip.x2(), clip.y2());
        graphics.drawString(font, text, localX - scrollShift(textWidth, maxWidth), localY, color, shadow);
        scissorController.set(graphics, parentClipX1, parentClipY1, parentClipX2, parentClipY2);
    }

    public static void drawComponent(GuiGraphics graphics, Font font, Component text,
                                     int localX, int localY, int maxWidth, int color, boolean shadow,
                                     int absoluteX, int absoluteY, int parentClipX1, int parentClipY1,
                                     int parentClipX2, int parentClipY2, ScissorController scissorController) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, localX, localY, color, shadow);
            return;
        }
        ClipBounds clip = clipBounds(font, absoluteX, absoluteY, maxWidth,
                parentClipX1, parentClipY1, parentClipX2, parentClipY2);
        if (!clip.valid()) return;
        scissorController.set(graphics, clip.x1(), clip.y1(), clip.x2(), clip.y2());
        graphics.drawString(font, text, localX - scrollShift(textWidth, maxWidth), localY, color, shadow);
        scissorController.set(graphics, parentClipX1, parentClipY1, parentClipX2, parentClipY2);
    }

    private static int scrollShift(int textWidth, int maxWidth) {
        double maxShift = textWidth - maxWidth;
        double scrollMillis = maxShift / SPEED_PIXELS_PER_SECOND * 1_000.0;
        double halfPeriod = END_PAUSE_MILLIS + scrollMillis;
        double time = Util.getMillis() % (halfPeriod * 2.0);
        double shift;
        if (time < halfPeriod) {
            shift = time <= END_PAUSE_MILLIS
                    ? 0
                    : (time - END_PAUSE_MILLIS) / 1_000.0 * SPEED_PIXELS_PER_SECOND;
        } else {
            double reverseTime = time - halfPeriod;
            shift = reverseTime <= END_PAUSE_MILLIS
                    ? maxShift
                    : maxShift - (reverseTime - END_PAUSE_MILLIS) / 1_000.0 * SPEED_PIXELS_PER_SECOND;
        }
        return (int) Math.max(0, Math.min(maxShift, shift));
    }

    private static ClipBounds clipBounds(Font font, int absoluteX, int absoluteY, int maxWidth,
                                         int parentClipX1, int parentClipY1,
                                         int parentClipX2, int parentClipY2) {
        int x1 = Math.max(parentClipX1, absoluteX);
        int y1 = Math.max(parentClipY1, absoluteY);
        int x2 = Math.min(parentClipX2, absoluteX + maxWidth);
        int y2 = Math.min(parentClipY2, absoluteY + font.lineHeight + 4);
        return new ClipBounds(x1, y1, x2, y2);
    }

    @FunctionalInterface
    public interface ScissorController {
        void set(GuiGraphics graphics, int x1, int y1, int x2, int y2);
    }

    private record ClipBounds(int x1, int y1, int x2, int y2) {
        private boolean valid() {
            return x1 < x2 && y1 < y2;
        }
    }
}
