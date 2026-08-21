package org.arcadia.arc_quest.client.hud.quest.splash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.jetbrains.annotations.Nullable;

public final class QuestSplashCardRenderer {
    public static final int FRAME_WIDTH = 400;
    public static final int FRAME_HEIGHT = 225;

    private QuestSplashCardRenderer() {
    }

    public static void render(GuiGraphics graphics, QuestDefinition quest,
                              @Nullable ResourceLocation texture,
                              int x, int y, int width, int height,
                              int themeColor, float alpha) {
        if (quest == null || width <= 0 || height <= 0 || alpha <= 0.001f) return;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / (float) FRAME_WIDTH, height / (float) FRAME_HEIGHT, 1f);

        if (texture != null) renderTexture(graphics, texture, alpha);
        else renderFallback(graphics, themeColor, alpha);
        renderFrame(graphics, themeColor, alpha);

        graphics.pose().popPose();
    }

    private static void renderTexture(GuiGraphics graphics, ResourceLocation texture, float alpha) {
        graphics.setColor(1f, 1f, 1f, alpha);
        graphics.blit(texture, 0, 0, 0, 0, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static void renderFallback(GuiGraphics graphics, int themeColor, float alpha) {
        int topColor = HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x05070C, themeColor, 0.30f),
                Math.round(245 * alpha));
        int bottomColor = HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x000000, themeColor, 0.12f),
                Math.round(255 * alpha));
        graphics.fillGradient(0, 0, FRAME_WIDTH, FRAME_HEIGHT, topColor, bottomColor);

        int gridColor = HudAnimUtil.withAlpha(themeColor, Math.round(25 * alpha));
        for (int gridX = 0; gridX < FRAME_WIDTH; gridX += 32) {
            graphics.fill(gridX, 0, gridX + 1, FRAME_HEIGHT, gridColor);
        }
        for (int gridY = 0; gridY < FRAME_HEIGHT; gridY += 24) {
            graphics.fill(0, gridY, FRAME_WIDTH, gridY + 1, gridColor);
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        int headSize = 72;
        int headX = (FRAME_WIDTH - headSize) / 2;
        int headY = (FRAME_HEIGHT - headSize) / 2 - 6;
        int panelColor = HudAnimUtil.withAlpha(0x000000, Math.round(145 * alpha));
        int outlineColor = HudAnimUtil.withAlpha(themeColor, Math.round(220 * alpha));
        graphics.fill(headX - 8, headY - 8, headX + headSize + 8, headY + headSize + 8, panelColor);
        graphics.fill(headX - 8, headY - 8, headX + headSize + 8, headY - 5, outlineColor);
        graphics.fill(headX - 8, headY + headSize + 5, headX + headSize + 8, headY + headSize + 8, outlineColor);
        graphics.fill(headX - 8, headY - 8, headX - 5, headY + headSize + 8, outlineColor);
        graphics.fill(headX + headSize + 5, headY - 8, headX + headSize + 8, headY + headSize + 8, outlineColor);

        ResourceLocation skin = minecraft.player.getSkinTextureLocation();
        graphics.setColor(1f, 1f, 1f, alpha);
        graphics.blit(skin, headX, headY, headSize, headSize, 8f, 8f, 8, 8, 64, 64);
        graphics.blit(skin, headX, headY, headSize, headSize, 40f, 8f, 8, 8, 64, 64);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static void renderFrame(GuiGraphics graphics, int themeColor, float alpha) {
        int borderAlpha = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        int borderColor = HudAnimUtil.withAlpha(themeColor, borderAlpha);
        int decorationColor = HudAnimUtil.withAlpha(0xFFFFFF, borderAlpha);

        graphics.fill(0, 0, 3, FRAME_HEIGHT, borderColor);
        graphics.fill(0, 0, 20, 2, decorationColor);
        graphics.fill(0, 0, 2, 20, decorationColor);
        graphics.fill(FRAME_WIDTH - 20, FRAME_HEIGHT - 2, FRAME_WIDTH, FRAME_HEIGHT, decorationColor);
        graphics.fill(FRAME_WIDTH - 2, FRAME_HEIGHT - 20, FRAME_WIDTH, FRAME_HEIGHT, decorationColor);
    }
}
