// file_name: GuideListPanel.java
package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;

import java.util.List;

public class GuideListPanel {
    private final GuideListScreen screen;
    private float selectedSlide = -1f;
    private float[] entryHoverAnim = new float[0];
    private double scrollOffset = 0;
    private double targetScroll = 0;
    private boolean isDraggingListScrollbar = false;
    private double dragListYOffset = 0;

    public GuideListPanel(GuideListScreen screen) {
        this.screen = screen;
    }

    public void resetState() {
        entryHoverAnim = new float[screen.guidesForSelectedCategory().size()];
        selectedSlide = -1f;
        targetScroll = 0;
        scrollOffset = 0;
        scrollToSelected();
    }

    public void scrollToSelected() {
        List<GuideDefinition> guides = screen.guidesForSelectedCategory();
        int idx = -1;
        for (int i = 0; i < guides.size(); i++) {
            if (guides.get(i).getId().equals(screen.getSelectedGuideId())) { idx = i; break; }
        }
        if (idx < 0) return;
        selectedSlide = idx;
        int selectedTop = idx * GuideConstants.ENTRY_HEIGHT;
        targetScroll = Math.max(0, selectedTop - GuideConstants.ENTRY_HEIGHT);
        scrollOffset = targetScroll;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        List<GuideDefinition> guides = screen.guidesForSelectedCategory();
        clampScroll(h, guides.size());
        scrollOffset += Math.abs(targetScroll - scrollOffset) > 0.5 ? (targetScroll - scrollOffset) * Math.min(1.0, dt * 14.0) : (targetScroll - scrollOffset);

        screen.enableScissor(g, x, y, x + w - 6, y + h);

        int selectedIndex = -1;
        for (int i = 0; i < guides.size(); i++) {
            if (guides.get(i).getId().equals(screen.getSelectedGuideId())) { selectedIndex = i; break; }
        }

        float effectiveAlpha = screen.getEffectiveAlpha();

        if (selectedIndex >= 0) {
            if (selectedSlide < 0) selectedSlide = selectedIndex;
            selectedSlide = HudAnimUtil.lerp(selectedSlide, selectedIndex, 0.25f, dt);
            int hlY = (int) (y + 2 - scrollOffset + selectedSlide * GuideConstants.ENTRY_HEIGHT);

            g.fill(x + 2, hlY, x + w - 8, hlY + GuideConstants.ENTRY_HEIGHT - 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * effectiveAlpha)));
            HudRenderUtil.drawCyberneticEdge(g, x + 2, hlY, GuideConstants.ENTRY_HEIGHT - 2, theme, (int) (0xFF * effectiveAlpha));
        }

        if (entryHoverAnim.length != guides.size()) entryHoverAnim = new float[guides.size()];

        int firstVisible = Math.max(0, (int) ((scrollOffset - 2 - GuideConstants.ENTRY_HEIGHT) / GuideConstants.ENTRY_HEIGHT));
        int lastVisible = Math.min(guides.size() - 1, (int) ((scrollOffset + h) / GuideConstants.ENTRY_HEIGHT) + 1);

        for (int i = firstVisible; i <= lastVisible; i++) {
            GuideDefinition guide = guides.get(i);
            int entryY = (int) (y + 2 - scrollOffset + i * GuideConstants.ENTRY_HEIGHT);

            boolean hovered = mx >= x && mx <= x + w - 8 && my >= entryY && my <= entryY + GuideConstants.ENTRY_HEIGHT && my >= y && my <= y + h;
            entryHoverAnim[i] = HudAnimUtil.step(entryHoverAnim[i], hovered ? 1f : 0f, 8f, dt);
            float eHover = HudAnimUtil.easeOutCubic(entryHoverAnim[i]);

            if (i != selectedIndex && eHover > 0.01f) {
                g.fill(x + 2, entryY, x + w - 8, entryY + GuideConstants.ENTRY_HEIGHT - 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (eHover * 0x15 * effectiveAlpha)));
            }

            if (effectiveAlpha > 0.05f) {
                int baseGray = (int) (0x99 + 0x66 * eHover);
                int nameColor = (i == selectedIndex) ? HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * effectiveAlpha)) : HudAnimUtil.withAlpha((baseGray << 16) | (baseGray << 8) | baseGray, (int) (255 * effectiveAlpha));

                String displayTitle = screen.getFont().plainSubstrByWidth(guide.getTitle().getString(), w - 24);
                float textY = entryY + (GuideConstants.ENTRY_HEIGHT - screen.getFont().lineHeight) / 2f - 0.5f;

                g.drawString(screen.getFont(), displayTitle, x + 10, (int) textY, nameColor, false);

                if (!org.arcadia.arc_quest.guide.network.ClientGuideCache.INSTANCE.isSeen(guide.getId())) {
                    HudRenderUtil.drawBreathingRhombus(g, x + w - 14, entryY + GuideConstants.ENTRY_HEIGHT / 2, theme | 0xFF000000, (System.currentTimeMillis() / 1000f), effectiveAlpha);
                }
            }
        }
        g.disableScissor();

        int maxScroll = Math.max(0, guides.size() * GuideConstants.ENTRY_HEIGHT - h);
        renderScrollbar(g, x + w - 6, y + 2, h - 4, guides.size() * GuideConstants.ENTRY_HEIGHT, maxScroll);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH));
        int thumbY = y + (int) ((scrollOffset / maxScroll) * (viewH - thumbH));
        g.fill(x, y, x + 4, y + viewH, HudAnimUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isDraggingListScrollbar ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    public void clampScroll(int listH, int size) {
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, size * GuideConstants.ENTRY_HEIGHT - listH)));
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        List<GuideDefinition> guides = screen.guidesForSelectedCategory();
        int maxListScroll = Math.max(0, guides.size() * GuideConstants.ENTRY_HEIGHT - h);
        int listScrollbarX = x + w - 6;

        if (maxListScroll > 0 && mx >= listScrollbarX && mx <= listScrollbarX + 6 && my >= y && my <= y + h) {
            isDraggingListScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) h / (guides.size() * GuideConstants.ENTRY_HEIGHT)) * h));
            int thumbY = y + (int) ((scrollOffset / maxListScroll) * (h - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragListYOffset = my - thumbY;
            else {
                dragListYOffset = thumbH / 2.0;
                updateScrollFromMouse(my, y, h, maxListScroll, guides.size());
            }
            return true;
        }

        if (mx >= x && mx <= x + w - 6 && my >= y && my <= y + h) {
            double relY = my - y + scrollOffset;
            int idx = (int) (relY / GuideConstants.ENTRY_HEIGHT);
            if (idx >= 0 && idx < guides.size()) {
                screen.selectGuide(guides.get(idx).getId());
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int y, int h) {
        if (isDraggingListScrollbar) {
            updateScrollFromMouse(my, y, h, Math.max(0, screen.guidesForSelectedCategory().size() * GuideConstants.ENTRY_HEIGHT - h), screen.guidesForSelectedCategory().size());
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 0) isDraggingListScrollbar = false;
        return isDraggingListScrollbar;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            targetScroll -= delta * GuideConstants.ENTRY_HEIGHT;
            clampScroll(h, screen.guidesForSelectedCategory().size());
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double my, int y0, int viewH, int maxScroll, int size) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / (size * GuideConstants.ENTRY_HEIGHT)) * viewH));
        targetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragListYOffset) / (viewH - thumbH))) * maxScroll;
    }
}