// file_name: GuideMediaRenderer.java
package org.arcadia.arc_quest.client.hud.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.jetbrains.annotations.Nullable;

final class GuideMediaRenderer {
    private GuideMediaRenderer() {}

    static void drawMedia(@Nullable Screen screen, GuiGraphics g, int x, int y, int w, int h,
                          GuideMediaDefinition media, EmbeddedPonderScenePanel ponderPanel,
                          int mouseX, int mouseY, float partialTick,
                          int alpha, int themeColor) {

        if (media.getType() == GuideMediaType.PONDER) {
            ponderPanel.render(g, x, y, w, h, mouseX, mouseY, partialTick, alpha);
            return;
        }

        g.renderOutline(x, y, w, h, HudAnimUtil.withAlpha(themeColor, (int) (alpha * 0.35F)));

        if (media.getType() == GuideMediaType.IMAGE && media.getTexture() != null) {
            drawImageCover(screen, g, x, y, w, h, media, alpha);
            return;
        }

        int textW = Minecraft.getInstance().font.width("NO MEDIA");
        g.drawString(Minecraft.getInstance().font, "NO MEDIA", x + (w - textW) / 2, y + h / 2 - 4, HudAnimUtil.withAlpha(0x888888, alpha), false);
    }

    private static void drawImageCover(@Nullable Screen screen, GuiGraphics g, int x, int y, int w, int h,
                                       GuideMediaDefinition media, int alpha) {
        int texW = Math.max(1, media.getWidth());
        int texH = Math.max(1, media.getHeight());

        float scale = Math.max((float) w / texW, (float) h / texH);
        int drawW = (int) (texW * scale);
        int drawH = (int) (texH * scale);

        int drawX = x + (w - drawW) / 2;
        int drawY = y + (h - drawH) / 2;

        // 核心修复：根据屏幕类型分发 Scissor，适配自定义 uiScale
        if (screen instanceof GuideListScreen gls) {
            gls.enableScissor(g, x, y, x + w, y + h);
        } else {
            g.enableScissor(x, y, x + w, y + h);
        }

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, media.getTexture());
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);

        g.blit(media.getTexture(), drawX, drawY, 0, 0, drawW, drawH, drawW, drawH);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        g.disableScissor();
    }
}
