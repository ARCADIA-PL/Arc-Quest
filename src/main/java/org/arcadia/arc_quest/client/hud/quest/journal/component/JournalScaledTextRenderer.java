package org.arcadia.arc_quest.client.hud.quest.journal.component;

import org.arcadia.arc_quest.client.hud.style.HudDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.client.hud.component.HudTextMetrics;
import org.arcadia.arc_quest.client.hud.component.HudRect;

public final class JournalScaledTextRenderer {
    private JournalScaledTextRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font font, String text, float x, float y,
                            float scale, int color, boolean shadow) {
        draw(graphics, font, Component.literal(text), x, y, scale, color, shadow);
    }

    public static void draw(GuiGraphics graphics, Font font, Component text, float x, float y,
                            float scale, int color, boolean shadow) {
        draw(graphics, font, text.getVisualOrderText(), x, y, scale, color, shadow);
    }

    public static double pixelsPerUnit(GuiGraphics graphics) {
        var matrix = graphics.pose().last().pose();
        return Math.hypot(matrix.m00(), matrix.m01()) * Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static float readableScale(GuiGraphics graphics, float scale) {
        return HudTextMetrics.readableScale(scale, pixelsPerUnit(graphics));
    }

    public static void centered(GuiGraphics graphics, Font font, String text, HudRect bounds,
            float scale, int color, boolean shadow) {
        centered(graphics, font, Component.literal(text), bounds, scale, color, shadow);
    }

    public static void centered(GuiGraphics graphics, Font font, Component text, HudRect bounds,
            float scale, int color, boolean shadow) {
        centered(graphics, font, text.getVisualOrderText(), bounds, scale, color, shadow);
    }

    public static void centered(GuiGraphics graphics, Font font, net.minecraft.util.FormattedCharSequence text, HudRect bounds,
            float scale, int color, boolean shadow) {
        var point = HudTextMetrics.centered(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                font.width(text), font.lineHeight - 1, scale);
        draw(graphics, font, text, point.x(), point.y(), scale, color, shadow);
    }

    public static void draw(GuiGraphics graphics, Font font, net.minecraft.util.FormattedCharSequence text,
            float x, float y, float scale, int color, boolean shadow) {
        var matrix = graphics.pose().last().pose();
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        x = HudTextMetrics.snap(x, matrix.m00() * gui, matrix.m30() * gui);
        y = HudTextMetrics.snap(y, matrix.m11() * gui, matrix.m31() * gui);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        HudDraw.drawString(graphics, font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }
}
