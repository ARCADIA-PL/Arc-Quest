package org.arcadia.arc_quest.client.hud.quest.icon;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import java.io.IOException;

/** Texture dimensions are read once during resolution, never in render(). */
public record TextureObjectiveIcon(ResourceLocation texture, int x, int y, int width, int height,
                                    int textureWidth, int textureHeight) implements ObjectiveIconVisual {
    public static TextureObjectiveIcon load(ObjectiveIconSpec spec) throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(spec.texture());
        try (var stream = resource.open(); var image = NativeImage.read(stream)) {
            var r = spec.region();
            int x = r == null ? 0 : r.x(), y = r == null ? 0 : r.y();
            int width = r == null ? image.getWidth() : r.width();
            int height = r == null ? image.getHeight() : r.height();
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                    || (long) x + width > image.getWidth() || (long) y + height > image.getHeight())
                throw new IOException("Objective icon region outside texture: " + spec.texture());
            return new TextureObjectiveIcon(spec.texture(), x, y, width, height, image.getWidth(), image.getHeight());
        }
    }
    @Override public void render(GuiGraphics graphics, int left, int top, int size) {
        if (size <= 0) return;
        float scale = (float) size / Math.max(width, height);
        int w = Math.max(1, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int rgbEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            graphics.flush();
            if (!depth) RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            // Accumulate source-over alpha as well as color; defaultBlendFunc replaces alpha.
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            ObjectiveIconShaders.blit(graphics, texture, left + (size - w) / 2, top + (size - h) / 2, w, h,
                    x, y, width, height, textureWidth, textureHeight);
        } finally {
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(rgbEquation, alphaEquation);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        }
    }
}
