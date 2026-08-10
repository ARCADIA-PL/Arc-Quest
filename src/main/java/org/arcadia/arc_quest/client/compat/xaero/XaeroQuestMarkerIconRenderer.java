package org.arcadia.arc_quest.client.compat.xaero;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import xaero.hud.render.util.RenderBufferUtil;

public final class XaeroQuestMarkerIconRenderer {

    private static final int OUTER_RADIUS = 5;
    private static final int INNER_RADIUS = 3;

    private XaeroQuestMarkerIconRenderer() {
    }

    public static void render(GuiGraphics gui,
                              VertexConsumer colorConsumer,
                              int centerX,
                              int centerY,
                              int opacityPercent,
                              int colorArgb) {
        float opacity = Mth.clamp(opacityPercent / 100.0F, 0.0F, 1.0F);
        float markerAlpha = (colorArgb >>> 24 & 0xFF) / 255.0F;
        Matrix4f matrix = gui.pose().last().pose();

        drawDiamond(matrix, colorConsumer, centerX, centerY, OUTER_RADIUS,
                0.035F, 0.025F, 0.02F, opacity * markerAlpha * 0.92F);

        float red = (colorArgb >>> 16 & 0xFF) / 255.0F;
        float green = (colorArgb >>> 8 & 0xFF) / 255.0F;
        float blue = (colorArgb & 0xFF) / 255.0F;
        drawDiamond(matrix, colorConsumer, centerX, centerY, INNER_RADIUS,
                red, green, blue, opacity * markerAlpha);

        RenderBufferUtil.addColoredRect(matrix, colorConsumer,
                centerX - 1.0F, centerY - 2.0F, 2, 2,
                1.0F, 1.0F, 1.0F, opacity * markerAlpha * 0.72F);
    }

    private static void drawDiamond(Matrix4f matrix,
                                    VertexConsumer consumer,
                                    int centerX,
                                    int centerY,
                                    int radius,
                                    float red,
                                    float green,
                                    float blue,
                                    float alpha) {
        for (int offsetY = -radius; offsetY <= radius; offsetY++) {
            int halfWidth = radius - Math.abs(offsetY);
            RenderBufferUtil.addColoredRect(matrix, consumer,
                    centerX - halfWidth, centerY + offsetY,
                    halfWidth * 2 + 1, 1,
                    red, green, blue, alpha);
        }
    }
}
