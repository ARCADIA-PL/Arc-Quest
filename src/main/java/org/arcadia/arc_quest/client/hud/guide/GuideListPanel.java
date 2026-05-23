package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;

import java.util.Arrays;
import java.util.List;

final class GuideListPanel {
    private final GuideListScreen screen;
    private float[] entryHoverAnim = new float[0];
    private float selectedSlide = -1f;

    GuideListPanel(GuideListScreen screen) {
        this.screen = screen;
    }

    void render(GuiGraphics g, int mouseX, int mouseY, int alpha, float dt, int slideOffset) {
        int[] r = screen.listRect();
        int lx = r[0] - slideOffset;
        int[] rAdj = new int[]{lx, r[1], r[2], r[3]};

        HudAnimUtil.drawFrame(g, rAdj[0], rAdj[1], rAdj[2], rAdj[3],
                HudAnimUtil.withAlpha(0x000000, (int) (0x55 * alpha)),
                HudAnimUtil.withAlpha(screen.getThemeColor(), (int) (0x55 * alpha)));
        HudRenderUtil.drawCyberneticEdge(g, rAdj[0], rAdj[1], rAdj[3], screen.getThemeColor(), alpha);

        List<GuideDefinition> guides = screen.guidesForSelectedCategory();
        if (guides.isEmpty()) {
            g.drawString(Minecraft.getInstance().font, "NO GUIDES", rAdj[0] + 12, rAdj[1] + 14,
                    HudAnimUtil.withAlpha(0x888888, alpha), false);
            return;
        }

        int visible = Math.max(1, (rAdj[3] - 12) / 24);
        int maxScroll = Math.max(0, guides.size() - visible);

        float scroll = screen.getSmoothListScroll();
        int y = rAdj[1] + 6;

        String selectedId = screen.getSelectedGuideId() != null ? screen.getSelectedGuideId().toString() : null;
        int selectedIdx = -1;
        for (int i = 0; i < guides.size(); i++) {
            if (guides.get(i).getId().toString().equals(selectedId)) { selectedIdx = i; break; }
        }

        if (entryHoverAnim.length != guides.size()) entryHoverAnim = new float[guides.size()];

        if (selectedIdx >= 0) {
            if (selectedSlide < 0) selectedSlide = selectedIdx;
            selectedSlide = HudAnimUtil.lerp(selectedSlide, selectedIdx, 0.25f, dt);
            int hlY = (int) (y + (selectedSlide - scroll) * 24);
            g.fill(rAdj[0] + 4, hlY, rAdj[0] + rAdj[2] - 4, hlY + 22,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * alpha * alpha / 255f)));
            HudRenderUtil.drawCyberneticEdge(g, rAdj[0] + 4, hlY, 22, screen.getThemeColor(), alpha);
        }

        g.enableScissor(rAdj[0] + 2, rAdj[1] + 4, rAdj[0] + rAdj[2] - 2, rAdj[1] + rAdj[3] - 4);

        int startIdx = Math.max(0, (int) Math.floor(scroll));
        int endIdx = Math.min(guides.size(), startIdx + visible + 2);

        for (int i = startIdx; i < endIdx; i++) {
            GuideDefinition guide = guides.get(i);
            boolean isSelected = i == selectedIdx;
            boolean hovered = hit(mouseX, mouseY, rAdj[0] + 4, y + (int) ((i - scroll) * 24), rAdj[2] - 8, 22);
            int itemY = y + (int) ((i - scroll) * 24);

            entryHoverAnim[i] = HudAnimUtil.step(entryHoverAnim[i], hovered && !isSelected ? 1f : 0f, 8f, dt);
            float eHover = HudAnimUtil.easeOutCubic(entryHoverAnim[i]);

            if (!isSelected && eHover > 0.01f) {
                g.fill(rAdj[0] + 4, itemY, rAdj[0] + rAdj[2] - 4, itemY + 22,
                        HudAnimUtil.withAlpha(0xFFFFFF, (int) (eHover * 0x22 * alpha)));
            }

            int baseGray = (int) (0xAA + 0x55 * eHover);
            int nameColor = isSelected
                    ? HudAnimUtil.withAlpha(0xFFFFFF, alpha)
                    : HudAnimUtil.withAlpha((baseGray << 16) | (baseGray << 8) | baseGray, alpha);

            String displayTitle = Minecraft.getInstance().font.plainSubstrByWidth(guide.getTitle().getString(), rAdj[2] - 20);
            g.drawString(Minecraft.getInstance().font, displayTitle, rAdj[0] + 12, itemY + 7, nameColor, false);

            if (!org.arcadia.arc_quest.guide.network.ClientGuideCache.INSTANCE.isSeen(guide.getId())) {
                float time = System.currentTimeMillis() / 1000.0F;
                HudRenderUtil.drawBreathingRhombus(g, rAdj[0] + rAdj[2] - 14, itemY + 11,
                        HudAnimUtil.withAlpha(screen.getThemeColor(), alpha), time, alpha / 255.0F);
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackH = rAdj[3] - 10;
            int th = Math.max(16, (int) (trackH * (visible / (float) guides.size())));
            int travel = Math.max(0, trackH - th);
            int ty = rAdj[1] + 5 + (int) (travel * (scroll / (double) maxScroll));
            g.fill(rAdj[0] + rAdj[2] - 3, rAdj[1] + 5, rAdj[0] + rAdj[2] - 1, rAdj[1] + rAdj[3] - 5, HudAnimUtil.withAlpha(0x111823, alpha));
            g.fill(rAdj[0] + rAdj[2] - 3, ty, rAdj[0] + rAdj[2] - 1, ty + th, HudAnimUtil.withAlpha(screen.getThemeColor(), (int) (alpha * 0.8F)));
        }

        g.drawString(Minecraft.getInstance().font, "W/S NAVIGATE  Q/E CATEGORY", rAdj[0] + 8, rAdj[1] + rAdj[3] - 12,
                HudAnimUtil.withAlpha(0x666666, (int) (alpha * 0.7F)));
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
}
