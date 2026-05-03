package org.arcadia.arc_quest.mutil.theme;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.quest.api.VisualAsset;

public final class ArcIconRenderer {
    private ArcIconRenderer() {
    }

    public static void renderVisualAsset(GuiGraphics graphics, VisualAsset asset, int x, int y, int width, int height) {
        if (asset == null || !asset.enabled() || asset.texture() == null) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x + asset.offsetX(), y + asset.offsetY(), 0);
        if (asset.scale() != 1.0f) {
            float halfW = width / 2f;
            float halfH = height / 2f;
            graphics.pose().translate(halfW, halfH, 0);
            graphics.pose().scale(asset.scale(), asset.scale(), 1.0f);
            graphics.pose().translate(-halfW, -halfH, 0);
        }
        int tint = asset.tintColor();
        if (tint != 0xFFFFFFFF) {
            float alpha = ((tint >> 24) & 0xFF) / 255f;
            float red = ((tint >> 16) & 0xFF) / 255f;
            float green = ((tint >> 8) & 0xFF) / 255f;
            float blue = (tint & 0xFF) / 255f;
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(red, green, blue, alpha);
        }
        graphics.blit(asset.texture(), 0, 0, 0, 0, width, height, width, height);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        graphics.pose().popPose();
    }
}
