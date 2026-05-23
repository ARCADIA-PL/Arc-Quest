package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import java.util.List;

final class GuideContentPanel {
    private final GuideListScreen screen;
    private float prevBtnHoverAnim, nextBtnHoverAnim;

    GuideContentPanel(GuideListScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int alpha) {
        int[] r = screen.contentRect();
        int effectiveAlpha = alpha;

        HudAnimUtil.drawFrame(g, r[0], r[1], r[2], r[3],
                HudAnimUtil.withAlpha(0x000000, (int) (0x55 * alpha / 255f * 255)),
                HudAnimUtil.withAlpha(screen.getThemeColor(), (int) (0x55 * alpha / 255f * 255)));
        HudRenderUtil.drawCyberneticEdge(g, r[0], r[1], r[3], screen.getThemeColor(), alpha);

        if (!screen.hasAnyVisibleGuide()) {
            int textW = Minecraft.getInstance().font.width("NO GUIDES UNLOCKED");
            int cx = r[0] + (r[2] - textW) / 2;
            int cy = r[1] + r[3] / 2 - 4;
            g.drawString(Minecraft.getInstance().font, "NO GUIDES UNLOCKED", cx, cy, HudAnimUtil.withAlpha(0x888888, alpha), false);
            return;
        }

        GuideListScreen.GuidePageView view = screen.currentPageView();
        if (view == null) return;

        String titleStr = view.guide().getTitle().getString();
        int titleMaxW = r[2] - 32;
        String displayTitle = Minecraft.getInstance().font.plainSubstrByWidth(titleStr, titleMaxW);
        int titleX = r[0] + 16;
        int titleY = r[1] + 16;
        g.drawString(Minecraft.getInstance().font, displayTitle, titleX, titleY, HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);

        String pageStr = "PAGE " + (view.pageIndex() + 1) + " / " + view.guide().getPageCount();
        g.drawString(Minecraft.getInstance().font, pageStr, r[0] + r[2] - 16 - Minecraft.getInstance().font.width(pageStr), titleY,
                HudAnimUtil.withAlpha(0x888888, alpha), false);

        int btnW = 58, btnH = 18;
        int btnX = r[0] + r[2] - 16 - btnW - 8 - btnW;
        int btnY = titleY + 18;
        prevBtnHoverAnim = HudAnimUtil.step(prevBtnHoverAnim,
                hit(mouseX, mouseY, btnX, btnY, btnW, btnH) && view.pageIndex() > 0 ? 1f : 0f, 8f, 1f / 60f);
        nextBtnHoverAnim = HudAnimUtil.step(nextBtnHoverAnim,
                hit(mouseX, mouseY, btnX + btnW + 8, btnY, btnW, btnH) && view.pageIndex() < view.guide().getPageCount() - 1 ? 1f : 0f, 8f, 1f / 60f);
        GuideNavigationControls.drawCyberButton(g, Minecraft.getInstance().font, btnX, btnY, btnW, btnH,
                view.pageIndex() > 0 ? "< PREV" : "", screen.getThemeColor(), alpha / 255f,
                HudAnimUtil.easeOutCubic(prevBtnHoverAnim), hit(mouseX, mouseY, btnX, btnY, btnW, btnH) && view.pageIndex() > 0);
        GuideNavigationControls.drawCyberButton(g, Minecraft.getInstance().font, btnX + btnW + 8, btnY, btnW, btnH,
                view.pageIndex() < view.guide().getPageCount() - 1 ? "NEXT >" : "", screen.getThemeColor(), alpha / 255f,
                HudAnimUtil.easeOutCubic(nextBtnHoverAnim), hit(mouseX, mouseY, btnX + btnW + 8, btnY, btnW, btnH) && view.pageIndex() < view.guide().getPageCount() - 1);

        int mediaX = r[0] + 16;
        int mediaY = r[1] + 52;
        int mediaW = r[2] - 32;
        int mediaH = 110;
        GuideMediaRenderer.drawMedia(screen, g, mediaX, mediaY, mediaW, mediaH, view.page().getMedia(), screen.ponderPanel(),
                mouseX, mouseY, partialTick, alpha, screen.getThemeColor());

        int[] d = descriptionRect();
        int descX = d[0], descY = d[1], descW = d[2], descH = d[3];
        List<FormattedCharSequence> lines = screen.descriptionLines(descW);
        int contentH = lines.size() * GuideScreenLayout.textLineHeight();
        int max = Math.max(0, contentH - descH);

        screen.setDescTargetScroll(Math.max(0, Math.min(screen.getDescTargetScroll(), max)));
        screen.setDescScroll(Math.max(0, Math.min(screen.getDescScroll(), max)));

        g.enableScissor(descX - 2, descY - 2, descX + descW + 2, descY + descH + 2);
        int sy = descY - (int) Math.round(screen.getDescScroll());
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(Minecraft.getInstance().font, lines.get(i), descX, sy + i * GuideScreenLayout.textLineHeight(),
                    HudAnimUtil.withAlpha(0xCCCCCC, alpha));
        }
        g.disableScissor();

        if (max > 0) {
            int rx = descX + descW + 4;
            g.fill(rx, descY, rx + 2, descY + descH, HudAnimUtil.withAlpha(0x111823, alpha));
            int th = Math.max(12, (int) (descH * (descH / (float) contentH)));
            int travel = Math.max(0, descH - th);
            int ty = descY + (int) (travel * (screen.getDescScroll() / (double) max));
            g.fill(rx, ty, rx + 2, ty + th, HudAnimUtil.withAlpha(screen.getThemeColor(), alpha));
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        int[] r = screen.contentRect();
        GuideListScreen.GuidePageView view = screen.currentPageView();
        if (view == null) return false;

        int btnW = 58, btnH = 18;
        int btnX = r[0] + r[2] - 16 - btnW - 8 - btnW;
        int btnY = r[1] + 34;
        if (hit(mouseX, mouseY, btnX, btnY, btnW, btnH) && view.pageIndex() > 0) { screen.prevPage(); return true; }
        if (hit(mouseX, mouseY, btnX + btnW + 8, btnY, btnW, btnH) && view.pageIndex() < view.guide().getPageCount() - 1) { screen.nextPage(); return true; }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int[] d = descriptionRect();
        if (!hit(mouseX, mouseY, d[0], d[1], d[2] + 8, d[3])) return false;
        screen.adjustDescScroll(-delta * 18.0);
        return true;
    }

    int[] descriptionRect() {
        int[] r = screen.contentRect();
        int descY = r[1] + 52 + 110 + 16;
        int descW = r[2] - 54;
        int descH = Math.max(42, r[3] - (descY - r[1]) - 32);
        return new int[]{r[0] + 16, descY, descW, descH};
    }

    int descriptionLineCount() { return screen.descriptionLines(descriptionRect()[2]).size(); }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
