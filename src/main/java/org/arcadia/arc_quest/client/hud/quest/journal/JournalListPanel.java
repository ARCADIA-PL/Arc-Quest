package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.quest.api.IconPosition;

import java.util.HashMap;
import java.util.Map;

public class JournalListPanel {
    private final QuestJournalScreen screen;

    private float selectedSlide = -1f;
    private float[] entryHoverAnim = new float[0];
    private double scrollOffset = 0;
    private double targetScroll = 0;
    private boolean isDraggingListScrollbar = false;
    private double dragListYOffset = 0;
    private final Map<String, TextCache> textCache = new HashMap<>();

    private static class TextCache {
        String displayName;
        int width;
        String lastDrawName;
        int lastMaxWidth = Integer.MIN_VALUE;
        float lastScale = 1f;
    }

    public JournalListPanel(QuestJournalScreen screen) {
        this.screen = screen;
    }

    public void resetState() {
        entryHoverAnim = new float[screen.getCurrentEntries().size()];
        selectedSlide = screen.getSelectedIndex();
        targetScroll = 0;
        scrollOffset = 0;
        textCache.clear();
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        clampScroll(h);
        scrollOffset += Math.abs(targetScroll - scrollOffset) > 0.5 ? (targetScroll - scrollOffset) * Math.min(1.0, dt * 14.0) : (targetScroll - scrollOffset);

        screen.enableScissor(g, x, y, x + w - 6, y + h);

        int selectedIndex = screen.getSelectedIndex();
        float effectiveAlpha = screen.getEffectiveAlpha();

        if (selectedIndex >= 0) {
            if (selectedSlide < 0) selectedSlide = selectedIndex;
            selectedSlide = HudAnimUtil.lerp(selectedSlide, selectedIndex, 0.25f, dt);
            int hlY = (int) (y + 2 - scrollOffset + selectedSlide * JournalConstants.ENTRY_HEIGHT);

            int entryTheme = theme;
            if (selectedIndex < screen.getCurrentEntries().size() && screen.getCurrentEntries().get(selectedIndex).def() != null) {
                int defTheme = screen.getCurrentEntries().get(selectedIndex).def().getThemeColor();
                if (defTheme != 0xFFFFFFFF) entryTheme = defTheme;
            }

            g.fill(x + 2, hlY, x + w - 8, hlY + JournalConstants.ENTRY_HEIGHT - 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * effectiveAlpha)));
            drawCyberneticEdge(g, x + 2, hlY, JournalConstants.ENTRY_HEIGHT - 2, entryTheme, (int) (0xFF * effectiveAlpha));
        }

        int firstVisible = Math.max(0, (int) ((scrollOffset - 2 - JournalConstants.ENTRY_HEIGHT) / JournalConstants.ENTRY_HEIGHT));
        int lastVisible = Math.min(screen.getCurrentEntries().size() - 1, (int) ((scrollOffset + h) / JournalConstants.ENTRY_HEIGHT) + 1);

        for (int i = firstVisible; i <= lastVisible; i++) {
            JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(i);
            int entryY = (int) (y + 2 - scrollOffset + i * JournalConstants.ENTRY_HEIGHT);
            if (entryY + JournalConstants.ENTRY_HEIGHT < y || entryY > y + h) {
                if (i < entryHoverAnim.length) entryHoverAnim[i] = 0f;
                continue;
            }

            boolean hovered = mx >= x && mx <= x + w - 8 && my >= entryY && my <= entryY + JournalConstants.ENTRY_HEIGHT && my >= y && my <= y + h;
            if (i < entryHoverAnim.length)
                entryHoverAnim[i] = HudAnimUtil.step(entryHoverAnim[i], hovered ? 1f : 0f, 8f, dt);
            float eHover = HudAnimUtil.easeOutCubic(i < entryHoverAnim.length ? entryHoverAnim[i] : 0f);

            if (i != selectedIndex && eHover > 0.01f) {
                g.fill(x + 2, entryY, x + w - 8, entryY + JournalConstants.ENTRY_HEIGHT - 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (eHover * 0x22 * effectiveAlpha)));
            }

            if (effectiveAlpha > 0.05f) {
                int baseGray = (int) (0xAA + 0x55 * eHover);
                int nameColor = (i == selectedIndex) ? HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * effectiveAlpha)) : HudAnimUtil.withAlpha((baseGray << 16) | (baseGray << 8) | baseGray, (int) (255 * effectiveAlpha));

                int textOffsetX = 10;
                if (entry.def() != null) {
                    entry.def().getVisualConfig().getIcon(IconPosition.QUEST_LIST).ifPresent(icon -> {
                        QuestIconRenderer.renderIcon(g, icon, x + 10, entryY + (JournalConstants.ENTRY_HEIGHT - 12) / 2, 12, 12);
                    });
                    if (entry.def().getVisualConfig().getIcon(IconPosition.QUEST_LIST).isPresent()) {
                        textOffsetX = 26;
                    }
                }

                int maxDrawWidth = w - textOffsetX - 16;
                TextCache cachedText = getTextCache(entry);
                String displayName = cachedText.displayName;
                int textW = cachedText.width;
                float baseScale = 1f;

                if (textW > maxDrawWidth) {
                    baseScale = Math.max(0.75f, (float) maxDrawWidth / textW);
                    if (textW * baseScale > maxDrawWidth) {
                        if (cachedText.lastMaxWidth != maxDrawWidth) {
                            int allowedW = (int) (maxDrawWidth / 0.75f) - screen.getFont().width("...");
                            cachedText.lastDrawName = screen.getFont().plainSubstrByWidth(displayName, allowedW) + "...";
                            cachedText.lastMaxWidth = maxDrawWidth;
                        }
                        displayName = cachedText.lastDrawName;
                    }
                }

                float finalScale = baseScale * (1f + 0.03f * eHover);

                g.pose().pushPose();
                float textY = entryY + (JournalConstants.ENTRY_HEIGHT - screen.getFont().lineHeight * finalScale) / 2f - 0.5f;

                g.pose().translate(x + textOffsetX, textY, 0);
                g.pose().scale(finalScale, finalScale, 1f);
                g.drawString(screen.getFont(), displayName, 0, 0, nameColor, false);
                g.pose().popPose();
            }
        }
        g.disableScissor();

        int maxScroll = Math.max(0, screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT - h);
        renderScrollbar(g, x + w - 6, y + 2, h - 4, screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT, maxScroll);
    }

    private TextCache getTextCache(JournalTypes.QuestListEntry entry) {
        String key = entry.displayName();
        return textCache.computeIfAbsent(key, k -> {
            TextCache cache = new TextCache();
            cache.displayName = k;
            cache.width = screen.getFont().width(k);
            return cache;
        });
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH));
        int thumbY = y + (int) ((scrollOffset / maxScroll) * (viewH - thumbH));
        g.fill(x, y, x + 4, y + viewH, HudAnimUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isDraggingListScrollbar ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    public void clampScroll(int listH) {
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT - listH)));
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int maxListScroll = Math.max(0, screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT - h);
        int listScrollbarX = x + w - 6;

        if (maxListScroll > 0 && mx >= listScrollbarX && mx <= listScrollbarX + 6 && my >= y && my <= y + h) {
            isDraggingListScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) h / (screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT)) * h));
            int thumbY = y + (int) ((scrollOffset / maxListScroll) * (h - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragListYOffset = my - thumbY;
            else {
                dragListYOffset = thumbH / 2.0;
                updateScrollFromMouse(my, y, h, maxListScroll);
            }
            return true;
        }

        if (mx >= x && mx <= x + w - 6 && my >= y && my <= y + h) {
            double relY = my - y + scrollOffset;
            int idx = (int) (relY / JournalConstants.ENTRY_HEIGHT);
            if (idx >= 0 && idx < screen.getCurrentEntries().size()) {
                screen.onEntrySelected(idx);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int y, int h) {
        if (isDraggingListScrollbar) {
            updateScrollFromMouse(my, y, h, Math.max(0, screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT - h));
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
            targetScroll -= delta * JournalConstants.ENTRY_HEIGHT;
            clampScroll(h);
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / (screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT)) * viewH));
        targetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragListYOffset) / (viewH - thumbH))) * maxScroll;
    }

    private void drawCyberneticEdge(GuiGraphics g, int x, int y, int height, int themeColor, int alpha) {
        if (alpha < 5) return;

        int coreColor = themeColor & 0xFFFFFF;
        int topAlpha = alpha;
        int botAlpha = (int) (alpha * 0.15f);

        int colorTop = coreColor | (topAlpha << 24);
        int colorBot = coreColor | (botAlpha << 24);

        g.fillGradient(x, y, x + 3, y + height, colorTop, colorBot);

        int glowAlpha = (int) (topAlpha * 0.8f);
        int colorGlow = 0xFFFFFF | (glowAlpha << 24);
        g.fillGradient(x, y, x + 1, y + (height / 2), colorGlow, colorTop);
    }
}