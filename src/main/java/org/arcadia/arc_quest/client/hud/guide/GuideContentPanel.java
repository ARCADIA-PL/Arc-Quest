package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import java.util.List;

final class GuideContentPanel {
    private final GuideListScreen screen;

    GuideContentPanel(GuideListScreen screen) {
        this.screen = screen;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick, int alpha) {
        int[] r = screen.contentRect();

        HudAnimUtil.drawFrame(g, r[0], r[1], r[2], r[3], withAlpha(0x050811, (int) (alpha * 0.5F)), withAlpha(screen.getThemeColor(), (int) (alpha * 0.35F)));
        GuideNavigationControls.drawCornerBrackets(g, r[0], r[1], r[2], r[3], 6, withAlpha(screen.getThemeColor(), (int) (alpha * 0.5F)));

        if (!screen.hasAnyVisibleGuide()) {
            int textW = Minecraft.getInstance().font.width("TERMINAL OFFLINE");
            int offlineX = (int) (r[0] + (r[2] - textW * 0.9F) / 2);
            int offlineY = r[1] + r[3] / 2 - 4;
            GuideNavigationControls.drawScaledText(screen, g, offlineX, offlineY, 0.9F, "TERMINAL OFFLINE", withAlpha(GuideConstants.TEXT, alpha));
            return;
        }

        GuideListScreen.GuidePageView view = screen.currentPageView();
        if (view == null) return;

        String titleStr = view.guide().getTitle().getString();
        int titleW = (int) (Minecraft.getInstance().font.width(titleStr) * 0.95F);
        int titleX = r[0] + 16;
        int titleY = r[1] + 16;
        GuideNavigationControls.drawScaledText(screen, g, titleX, titleY, 0.95F, titleStr, withAlpha(GuideConstants.TEXT, alpha));

        int arrowX = titleX + titleW + 12;
        int arrowY = titleY - 2;
        boolean prevHover = hit(mouseX, mouseY, arrowX, arrowY, 14, 14);
        boolean nextHover = hit(mouseX, mouseY, arrowX + 18, arrowY, 14, 14);

        GuideNavigationControls.drawArrowButton(g, arrowX, arrowY, 14, true, view.pageIndex() > 0, prevHover, screen.getThemeColor(), alpha);
        GuideNavigationControls.drawArrowButton(g, arrowX + 18, arrowY, 14, false, view.pageIndex() < view.guide().getPageCount() - 1, nextHover, screen.getThemeColor(), alpha);

        String pageStr = "PAGE " + (view.pageIndex() + 1) + " / " + view.guide().getPageCount();
        g.drawString(Minecraft.getInstance().font, pageStr, r[0] + r[2] - 84, titleY, withAlpha(GuideConstants.SUB, alpha), false);

        int mediaX = r[0] + 16;
        int mediaY = r[1] + 36;
        int mediaW = r[2] - 32;
        int mediaH = 110;
        GuideMediaRenderer.drawMedia(screen, g, mediaX, mediaY, mediaW, mediaH, view.page().getMedia(), screen.ponderPanel(), mouseX, mouseY, partialTick, alpha, GuideConstants.SEC, GuideConstants.MUTED, GuideConstants.SUB, screen.getThemeColor());

        int[] d = descriptionRect();
        int descX = d[0], descY = d[1], descW = d[2], descH = d[3];
        List<FormattedCharSequence> lines = screen.descriptionLines(descW);
        int contentH = lines.size() * GuideScreenLayout.textLineHeight();
        int max = Math.max(0, contentH - descH);

        screen.setDescTargetScroll(clamp(screen.getDescTargetScroll(), 0.0, max));
        screen.setDescScroll(clamp(screen.getDescScroll(), 0.0, max));

        g.enableScissor(descX - 2, descY - 2, descX + descW + 2, descY + descH + 2);
        int sy = descY - (int) Math.round(screen.getDescScroll());
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(Minecraft.getInstance().font, lines.get(i), descX, sy + i * GuideScreenLayout.TEXT_LINE_H, withAlpha(GuideConstants.TEXT, alpha));
        }
        g.disableScissor();

        if (max > 0) {
            int rx = descX + descW + 4;
            g.fill(rx, descY, rx + 2, descY + descH, withAlpha(0x111823, alpha));
            int th = Math.max(12, (int) (descH * (descH / (float) contentH)));
            int travel = Math.max(0, descH - th);
            int ty = descY + (int) (travel * (screen.getDescScroll() / (double) max));
            g.fill(rx, ty, rx + 2, ty + th, withAlpha(screen.getThemeColor(), alpha));
        }
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        int[] r = screen.contentRect();
        GuideListScreen.GuidePageView view = screen.currentPageView();
        if (view == null) return false;

        String titleStr = view.guide().getTitle().getString();
        int titleW = (int) (Minecraft.getInstance().font.width(titleStr) * 0.95F);
        int arrowX = r[0] + 16 + titleW + 12;
        int arrowY = r[1] + 14;

        if (hit(mouseX, mouseY, arrowX, arrowY, 14, 14) && view.pageIndex() > 0) { screen.prevPage(); return true; }
        if (hit(mouseX, mouseY, arrowX + 18, arrowY, 14, 14) && view.pageIndex() < view.guide().getPageCount() - 1) { screen.nextPage(); return true; }
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
        int descY = r[1] + 36 + 110 + 16;
        int descW = r[2] - 54;
        int descH = Math.max(42, r[3] - (descY - r[1]) - 32);
        return new int[]{r[0] + 16, descY, descW, descH};
    }

    int descriptionLineCount() { return screen.descriptionLines(descriptionRect()[2]).size(); }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }
}
