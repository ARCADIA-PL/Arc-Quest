package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconVisual;

import java.util.function.BooleanSupplier;

/** Direct normalized 2D sampling, shared by multi-layer UV portraits and fixed resource pictures. */
final class PortraitTextureVisual implements ObjectiveIconVisual {
    private final ResourceLocation texture;
    private final TexturePortraitDefinition definition;
    private final TexturePortraitDefinition.Rect crop;
    private final TexturePortraitDefinition.Size source;
    private final BooleanSupplier current;

    PortraitTextureVisual(TexturePortraitDefinition definition, BooleanSupplier current) {
        this.texture = definition.texture();
        this.definition = definition;
        this.crop = null;
        this.source = definition.referenceSize();
        this.current = current;
    }

    PortraitTextureVisual(ResourceLocation texture, TexturePortraitDefinition.Size source,
                          TexturePortraitDefinition.Rect crop, BooleanSupplier current) {
        this.texture = texture;
        this.definition = null;
        this.crop = crop;
        this.source = source;
        this.current = current;
    }

    @Override public boolean available() { return current.getAsBoolean(); }

    @Override public void render(GuiGraphics graphics, int x, int y, int size) {
        if (!available() || size <= 0) return;
        float[] color = RenderSystem.getShaderColor().clone();
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean cull = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        var shader = RenderSystem.getShader();
        int shaderTexture = RenderSystem.getShaderTexture(0);
        int srcRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        graphics.flush();
        graphics.pose().pushPose();
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull(); // Mirrored 2D layers reverse vertex winding.
            int width = definition == null ? crop.width() : definition.canvasSize().width();
            int height = definition == null ? crop.height() : definition.canvasSize().height();
            float scale = size / (float) Math.max(width, height);
            graphics.pose().translate(x + (size - width * scale) / 2, y + (size - height * scale) / 2, 0);
            graphics.pose().scale(scale, scale, 1);
            if (definition == null) {
                draw(graphics, crop, new TexturePortraitDefinition.Rect(0, 0, width, height), false, false);
            } else {
                for (var layer : definition.layers()) {
                    int tint = layer.tint();
                    RenderSystem.setShaderColor(color[0] * ((tint >>> 16) & 255) / 255f,
                            color[1] * ((tint >>> 8) & 255) / 255f, color[2] * (tint & 255) / 255f,
                            color[3] * ((tint >>> 24) & 255) / 255f);
                    draw(graphics, layer.region(), layer.destination(), layer.flipX(), layer.flipY());
                }
            }
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, shaderTexture);
        }
    }

    private void draw(GuiGraphics graphics, TexturePortraitDefinition.Rect region,
                      TexturePortraitDefinition.Rect destination, boolean flipX, boolean flipY) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(destination.x() + (flipX ? destination.width() : 0),
                    destination.y() + (flipY ? destination.height() : 0), 0);
            graphics.pose().scale(flipX ? -1 : 1, flipY ? -1 : 1, 1);
            graphics.blit(texture, 0, 0, destination.width(), destination.height(),
                    region.x(), region.y(), region.width(), region.height(), source.width(), source.height());
        } finally { graphics.pose().popPose(); }
    }
}
