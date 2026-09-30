package org.arcadia.arc_quest.client.hud.quest.icon;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
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
        float scale = (float) size / Math.max(width, height);
        int w = Math.max(1, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
        graphics.blit(texture, left + (size - w) / 2, top + (size - h) / 2, w, h,
                (float) x, (float) y, width, height, textureWidth, textureHeight);
    }
}
