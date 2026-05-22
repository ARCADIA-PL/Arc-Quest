package org.arcadia.arc_quest.client.hud.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;

final class GuideMediaRenderer {
    private GuideMediaRenderer() {
    }

    static void drawMedia(GuideScreen screen, GuiGraphics g, GuideScreenLayout.Layout l,
                          GuideMediaDefinition media, EmbeddedPonderScenePanel ponderPanel,
                          int mouseX, int mouseY, float partialTick,
                          int alpha, int sectionColor, int mutedColor, int subColor, int themeColor) {
        g.fill(l.mx(), l.my(), l.mx() + l.mw(), l.my() + l.mh(), withAlpha(sectionColor, alpha));
        if (media.getType() == GuideMediaType.PONDER) {
            ponderPanel.render(g, l.mx(), l.my(), l.mw(), l.mh(), mouseX, mouseY, partialTick);
            return;
        }
        if (media.getType() == GuideMediaType.IMAGE && media.getTexture() != null) {
            drawImage(g, l, media, alpha, themeColor);
            return;
        }
        GuideNavigationControls.drawScaledText(screen, g, l.mx() + 10, l.my() + 10, .72f, "NO MEDIA SIGNAL", withAlpha(mutedColor, alpha));
        g.drawString(Minecraft.getInstance().font, "Description-only page", l.mx() + 10, l.my() + 26, withAlpha(subColor, alpha), false);
    }

    private static void drawImage(GuiGraphics g, GuideScreenLayout.Layout l, GuideMediaDefinition media, int alpha, int themeColor) {
        int fx = l.mx() + 8, fy = l.my() + 8, fw = l.mw() - 16, fh = l.mh() - 16;
        g.fill(fx, fy, fx + fw, fy + fh, withAlpha(0x11151B, alpha));
        int tw = Math.max(1, media.getWidth()), th = Math.max(1, media.getHeight());
        float s = Math.min(fw / (float) tw, fh / (float) th);
        int dw = Math.max(1, Math.round(tw * s)), dh = Math.max(1, Math.round(th * s));
        int dx = fx + (fw - dw) / 2, dy = fy + (fh - dh) / 2;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, media.getTexture());
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
        g.blit(media.getTexture(), dx, dy, 0, 0, dw, dh, tw, th);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.fill(fx, fy, fx + fw, fy + 1, withAlpha(themeColor, (int) (alpha * .5f)));
        g.fill(fx, fy + fh - 1, fx + fw, fy + fh, withAlpha(themeColor, (int) (alpha * .3f)));
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }
}
