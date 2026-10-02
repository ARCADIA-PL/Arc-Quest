package org.arcadia.arc_quest.client.hud.guide;

import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;


import org.arcadia.arc_quest.client.hud.HudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

        int textW = Minecraft.getInstance().font.width(HudText.of("guide.no_media"));
        g.drawString(Minecraft.getInstance().font, HudText.of("guide.no_media"), x + (w - textW) / 2, y + h / 2 - 4, HudAnimUtil.withAlpha(0x888888, alpha), false);
    }

    private static void drawImageCover(@Nullable Screen screen, GuiGraphics g, int x, int y, int w, int h,
                                       GuideMediaDefinition media, int alpha) {
        // 核心修复：根据屏幕类型分发 Scissor，适配自定义 uiScale
        if (screen instanceof GuideListScreen gls) {
            gls.enableScissor(g, x, y, x + w, y + h);
        } else {
            JeiScreenIngredients.enableScissor(screen, g, x, y, x + w, y + h);
        }

        GuideImageRenderer.draw(g, media, x, y, w, h, GuideImageLayout.Fit.COVER, alpha / 255f);
        JeiScreenIngredients.disableScissor(screen, g);
    }
}
