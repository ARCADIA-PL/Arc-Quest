package org.arcadia.arc_quest.mutil.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class ArcVectorDrawUtil {
    private ArcVectorDrawUtil() {
    }

    public static void drawSmoothRhombus(GuiGraphics graphics, int accentColor, int darkEdge, int darkFill, float breath) {
        graphics.pose().pushPose();
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45f));
        graphics.fill(-6, -6, 6, 6, darkEdge);
        graphics.fill(-5, -5, 5, 5, darkFill);
        int alpha = (accentColor >> 24) & 0xFF;
        int rgb = accentColor & 0x00FFFFFF;
        int haloAlpha = (int) (alpha * 0.3f * breath);
        graphics.fill(-4, -4, 4, 4, (haloAlpha << 24) | rgb);
        int coreAlpha = (int) (alpha * (0.6f + 0.4f * breath));
        graphics.fill(-2, -2, 2, 2, (coreAlpha << 24) | rgb);
        graphics.pose().popPose();
    }

    public static void drawVectorPointer(GuiGraphics graphics, int accentColor, int darkEdge, int darkFill) {
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        buildChevron(buffer, matrix, 0, -10, 7, 7, 0, 2, darkEdge);
        buildChevron(buffer, matrix, 0, -8, 5, 5, 0, 1, darkFill);
        buildChevron(buffer, matrix, 0, -8, 3, -1, 0, -3, accentColor);
        tesselator.end();
        RenderSystem.disableBlend();
    }

    private static void buildChevron(BufferBuilder buffer, Matrix4f matrix, float tipX, float tipY, float wingX, float wingY, float innerX, float innerY, int color) {
        float alpha = (color >> 24 & 255) / 255.0F;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        buffer.vertex(matrix, tipX, tipY, 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, -wingX, wingY, 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, innerX, innerY, 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, tipX, tipY, 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, innerX, innerY, 0).color(red, green, blue, alpha).endVertex();
        buffer.vertex(matrix, wingX, wingY, 0).color(red, green, blue, alpha).endVertex();
    }
}
