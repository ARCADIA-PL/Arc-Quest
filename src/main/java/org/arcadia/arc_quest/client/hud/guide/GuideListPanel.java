package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import java.util.List;

final class GuideListPanel {
    private final GuideListScreen screen;

    GuideListPanel(GuideListScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g, int mouseX, int mouseY, int alpha) {
        int[] r = screen.listRect();

        // 绘制高科技外框 (带微弱发光)
        HudAnimUtil.drawFrame(g, r[0], r[1], r[2], r[3], withAlpha(0x050811, (int) (alpha * 0.7F)), withAlpha(screen.getThemeColor(), (int) (alpha * 0.4F)));
        GuideNavigationControls.drawCornerBrackets(g, r[0], r[1], r[2], r[3], 6, withAlpha(screen.getThemeColor(), (int) (alpha * 0.6F)));

        List<GuideDefinition> guides = screen.guidesForSelectedCategory();
        if (guides.isEmpty()) {
            GuideNavigationControls.drawScaledText(screen, g, r[0] + 12, r[1] + 14, 0.75F, Component.translatable("gui.arc_quest.guide_list.no_guides").getString(), withAlpha(GuideConstants.TEXT, alpha));
            return;
        }

        int visible = Math.max(1, (r[3] - 12) / 24);
        int maxScroll = Math.max(0, guides.size() - visible);

        // 平滑滚动插值
        float scroll = screen.getSmoothListScroll();
        int y = r[1] + 6;

        // 启用裁剪保护
        g.enableScissor(r[0] + 2, r[1] + 4, r[0] + r[2] - 2, r[1] + r[3] - 4);

        int startIdx = Math.max(0, (int) Math.floor(scroll));
        int endIdx = Math.min(guides.size(), startIdx + visible + 2);

        for (int i = startIdx; i < endIdx; i++) {
            GuideDefinition guide = guides.get(i);
            boolean selected = guide.getId().equals(screen.getSelectedGuideId());
            boolean hovered = hit(mouseX, mouseY, r[0] + 4, y + (int)((i - scroll) * 24), r[2] - 8, 22);

            int itemY = y + (int) ((i - scroll) * 24);

            if (selected || hovered) {
                g.fill(r[0] + 4, itemY, r[0] + r[2] - 4, itemY + 22, withAlpha(selected ? 0x142235 : 0x0A101A, (int) (alpha * 0.9F)));
            }
            if (selected) {
                HudRenderUtil.drawCyberneticEdge(g, r[0] + 4, itemY, 22, screen.getThemeColor(), alpha);
                g.renderOutline(r[0] + 4, itemY, r[2] - 8, 22, withAlpha(screen.getThemeColor(), (int) (alpha * 0.5F)));
            } else if (hovered) {
                g.renderOutline(r[0] + 4, itemY, r[2] - 8, 22, withAlpha(screen.getThemeColor(), (int) (alpha * 0.2F)));
            }

            GuideNavigationControls.drawScaledText(screen, g, r[0] + 12, itemY + 7, 0.75F, guide.getTitle().getString(), withAlpha(GuideConstants.TEXT, alpha));

            // 未读提示呼吸灯 (带脉冲外圈)
            if (!org.arcadia.arc_quest.guide.network.ClientGuideCache.INSTANCE.isSeen(guide.getId())) {
                float time = System.currentTimeMillis() / 1000.0F;
                HudRenderUtil.drawBreathingRhombus(g, r[0] + r[2] - 14, itemY + 11, withAlpha(screen.getThemeColor(), alpha), time, alpha / 255.0F);
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = r[3] - 10;
            int th = Math.max(16, (int) (trackH * (visible / (float) guides.size())));
            int travel = Math.max(0, trackH - th);
            int ty = r[1] + 5 + (int) (travel * (scroll / (double) maxScroll));
            g.fill(r[0] + r[2] - 3, r[1] + 5, r[0] + r[2] - 1, r[1] + r[3] - 5, withAlpha(0x111823, alpha));
            g.fill(r[0] + r[2] - 3, ty, r[0] + r[2] - 1, ty + th, withAlpha(screen.getThemeColor(), (int) (alpha * 0.8F)));
        }

        GuideNavigationControls.drawScaledText(screen, g, r[0] + 8, r[1] + r[3] - 12, GuideConstants.SMALL_SCALE,
                "W/S NAVIGATE  Q/E CATEGORY", withAlpha(GuideConstants.MUTED, (int) (alpha * 0.7F)));
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        int[] r = screen.listRect();
        List<GuideDefinition> guides = screen.guidesForSelectedCategory();
        float scroll = screen.getSmoothListScroll();
        int y = r[1] + 6;

        for (int i = 0; i < guides.size(); i++) {
            int itemY = y + (int) ((i - scroll) * 24);
            if (hit(mouseX, mouseY, r[0] + 4, itemY, r[2] - 8, 22)) {
                screen.selectGuide(guides.get(i).getId());
                return true;
            }
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int[] r = screen.listRect();
        if (!hit(mouseX, mouseY, r[0], r[1], r[2], r[3])) return false;
        screen.adjustListScroll(delta < 0 ? 1 : -1);
        return true;
    }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int withAlpha(int c, int a) {
        return ((a & 0xFF) << 24) | (c & 0x00FFFFFF);
    }
}