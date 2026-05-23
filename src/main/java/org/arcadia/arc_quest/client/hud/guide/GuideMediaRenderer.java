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

final class GuideMediaRenderer {
    private GuideMediaRenderer() {}

    static void drawMedia(Screen screen, GuiGraphics g, int x, int y, int w, int h,
                          GuideMediaDefinition media, EmbeddedPonderScenePanel ponderPanel,
                          int mouseX, int mouseY, float partialTick,
                          int alpha, int themeColor) {

        if (media.getType() == GuideMediaType.PONDER) {
            ponderPanel.render(g, x, y, w, h, mouseX, mouseY, partialTick);
            return;
        }

        g.renderOutline(x, y, w, h, HudAnimUtil.withAlpha(themeColor, (int) (alpha * 0.35F)));

        if (media.getType() == GuideMediaType.IMAGE && media.getTexture() != null) {
            drawImage(g, x, y, w, h, media, alpha);
            return;
        }

        int textW = Minecraft.getInstance().font.width("NO MEDIA");
        g.drawString(Minecraft.getInstance().font, "NO MEDIA", x + (w - textW) / 2, y + h / 2 - 4,
                HudAnimUtil.withAlpha(0x888888, alpha), false);
    }

    private static void drawImage(GuiGraphics g, int x, int y, int w, int h, GuideMediaDefinition media, int alpha) {
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
        RenderSystem.disableBlend();
    }
}
