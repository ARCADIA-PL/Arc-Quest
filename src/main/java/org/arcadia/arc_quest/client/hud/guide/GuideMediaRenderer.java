package org.arcadia.arc_quest.client.hud.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;

final class GuideMediaRenderer {
    private GuideMediaRenderer() {}

    static void drawMedia(Screen screen, GuiGraphics g, int x, int y, int w, int h,
                          GuideMediaDefinition media, EmbeddedPonderScenePanel ponderPanel,
                          int mouseX, int mouseY, float partialTick,
                          int alpha, int sectionColor, int mutedColor, int subColor, int themeColor) {
        // 1. 绘制暗色背景与科技感网格
        g.fill(x, y, x + w, y + h, withAlpha(GuideConstants.BG, (int) (alpha * 0.6F)));
        drawHolographicGrid(g, x, y, w, h, 8, withAlpha(themeColor, (int) (alpha * 0.12F)));

        g.renderOutline(x, y, w, h, withAlpha(themeColor, (int) (alpha * 0.35F)));
        GuideNavigationControls.drawCornerBrackets(g, x, y, w, h, 4, withAlpha(themeColor, (int) (alpha * 0.8F)));

        if (media.getType() == GuideMediaType.PONDER) {
            ponderPanel.render(g, x + 2, y + 2, w - 4, h - 4, mouseX, mouseY, partialTick);
            drawScanlineEffect(g, x + 2, y + 2, w - 4, h - 4, alpha, themeColor);
            return;
        }
        if (media.getType() == GuideMediaType.IMAGE && media.getTexture() != null) {
            drawImage(g, x, y, w, h, media, alpha, themeColor);
            RenderSystem.disableBlend();
            drawScanlineEffect(g, x + 2, y + 2, w - 4, h - 4, alpha, themeColor);
            return;
        }

        // 无信号状态
        int textW = Minecraft.getInstance().font.width("SYSTEM OFFLINE");
        GuideNavigationControls.drawScaledText(screen, g, x + (w - textW) / 2, y + h / 2 - 4, 1.0F, "SYSTEM OFFLINE", withAlpha(mutedColor, alpha));
    }

    private static void drawImage(GuiGraphics g, int x, int y, int w, int h, GuideMediaDefinition media, int alpha, int themeColor) {
        int fx = x + 4, fy = y + 4, fw = w - 8, fh = h - 8;
        int tw = Math.max(1, media.getWidth()), th = Math.max(1, media.getHeight());
        float s = Math.min(fw / (float) tw, fh / (float) th);
        int dw = Math.max(1, Math.round(tw * s)), dh = Math.max(1, Math.round(th * s));
        int dx = fx + (fw - dw) / 2, dy = fy + (fh - dh) / 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, media.getTexture());
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
        g.blit(media.getTexture(), dx, dy, 0, 0, dw, dh, tw, th);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // 绘制全息网格
    private static void drawHolographicGrid(GuiGraphics g, int x, int y, int w, int h, int size, int color) {
        for (int gx = x + size; gx < x + w; gx += size) {
            g.fill(gx, y, gx + 1, y + h, color);
        }
        for (int gy = y + size; gy < y + h; gy += size) {
            g.fill(x, gy, x + w, gy + 1, color);
        }
    }

    // 绘制动态扫描线效果
    private static void drawScanlineEffect(GuiGraphics g, int x, int y, int w, int h, int alpha, int themeColor) {
        long time = System.currentTimeMillis();
        int scanY = y + (int) ((time / 14) % h);
        g.fill(x, scanY - 1, x + w, scanY, withAlpha(themeColor, (int) (alpha * 0.06F)));
        g.fill(x, scanY, x + w, scanY + 1, withAlpha(themeColor, (int) (alpha * 0.12F)));
        g.fill(x, scanY + 1, x + w, scanY + 2, withAlpha(themeColor, (int) (alpha * 0.06F)));
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }
}