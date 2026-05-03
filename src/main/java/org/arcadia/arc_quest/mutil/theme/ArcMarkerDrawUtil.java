package org.arcadia.arc_quest.mutil.theme;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class ArcMarkerDrawUtil {
    private ArcMarkerDrawUtil() {
    }

    public static void renderCyberMarker(GuiGraphics graphics, float x, float y, int color, float distance, String label) {
        long time = Util.getMillis();
        float floatY = (float) Math.sin(time / 300.0) * 4.0f;
        float pulse = (float) Math.sin(time / 200.0) * 0.25f + 0.75f;
        float rotateAngle = (time % 4000) / 4000.0f * 360f;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y + floatY, 0);

        fillFloat(graphics, -0.5f, 15f, 0.5f, 35f, withAlpha(color, 180));
        drawDiamond(graphics, 0, 37f, 4f, 4f, withAlpha(color, 255));

        float bracketSpread = 16f;
        fillFloat(graphics, -bracketSpread - 6, -1, -bracketSpread, 1, withAlpha(color, 150));
        fillFloat(graphics, -bracketSpread - 6, -4, -bracketSpread - 4, -1, withAlpha(color, 150));
        fillFloat(graphics, bracketSpread, -1, bracketSpread + 6, 1, withAlpha(color, 150));
        fillFloat(graphics, bracketSpread + 4, -4, bracketSpread + 6, -1, withAlpha(color, 150));

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-rotateAngle));
        float coreSize = 10f * pulse;
        drawDiamond(graphics, 0, 0, coreSize, coreSize, withAlpha(color, 255));
        drawDiamond(graphics, 0, 0, 18f, 18f, withAlpha(color, 80));
        pose.popPose();

        pose.pushPose();
        pose.translate(0, -20, 0);
        pose.scale(0.75f, 0.75f, 1f);
        Font font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, String.format("%.1fm", distance), 0, 0, 0xFFFFFF);
        if (label != null && !label.isEmpty()) graphics.drawCenteredString(font, label, 0, -10, color);
        pose.popPose();
        pose.popPose();
    }

    public static void drawDiamond(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45f));
        graphics.fill(Math.round(-width / 2f), Math.round(-height / 2f), Math.round(width / 2f), Math.round(height / 2f), color);
        graphics.pose().popPose();
    }

    public static void fillFloat(GuiGraphics graphics, float minX, float minY, float maxX, float maxY, int color) {
        graphics.fill(Math.round(minX), Math.round(minY), Math.round(maxX), Math.round(maxY), color);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
