package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.client.hud.HudText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalButtonRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScaledTextRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.history.*;

import java.util.List;

final class JournalDetailHistory {
    private static final int ROW_H = 42;

    private final QuestJournalScreen screen;
    private float reveal = 0f;
    private float scroll = 0f;
    private float targetScroll = 0f;
    private boolean currentQuestOnly = false;
    private QuestChangeHistoryCategory selectedCategory = null;

    JournalDetailHistory(QuestJournalScreen screen) {
        this.screen = screen;
    }

    void reset() {
        reveal = 0f;
        scroll = 0f;
        targetScroll = 0f;
        currentQuestOnly = false;
        selectedCategory = null;
    }

    int render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        QuestChangeHistoryStore.INSTANCE.ensureLoaded();
        reveal = HudAnimUtil.lerp(reveal, 1f, 0.15f, dt);
        int alpha = (int) (255 * screen.getEffectiveAlpha() * HudAnimUtil.easeOutCubic(Math.min(1f, reveal)));
        if (alpha <= 4) return 0;

        Font font = screen.getFont();
        int localY = 0;
        JournalScaledTextRenderer.draw(g, font, "// QUEST CHANGE LOG", 0, localY,
                0.92f, HudAnimUtil.withAlpha(theme, alpha), false);
        localY += 18;

        boolean allHovered = inside(mx, my, x, y + localY, 44, 16);
        boolean currentHovered = inside(mx, my, x + 48, y + localY, 64, 16);
        HudCursorManager.requestPointer((allHovered || currentHovered) && alpha > 8);
        JournalButtonRenderer.drawToggleButton(g, font, new HudRect(0, localY, 44, 16),
                "ALL", theme, alpha, !currentQuestOnly || allHovered, 0.7f);
        JournalButtonRenderer.drawToggleButton(g, font, new HudRect(48, localY, 64, 16),
                "CURRENT", theme, alpha, currentQuestOnly || currentHovered, 0.7f);
        localY += 22;

        int fx = 0;
        for (QuestChangeHistoryCategory category : QuestChangeHistoryCategory.values()) {
            if (category == QuestChangeHistoryCategory.SYSTEM) continue;
            int fw = Math.max(34, font.width(category.shortLabel()) + 12);
            boolean hovered = inside(mx, my, x + fx, y + localY, fw, 12);
            HudCursorManager.requestPointer(hovered && alpha > 8);
            JournalButtonRenderer.drawToggleButton(g, font, new HudRect(fx, localY, fw, 12),
                    category.shortLabel(), theme, alpha,
                    selectedCategory == category || hovered, 0.7f);
            fx += fw + 4;
        }
        localY += 20;

        int listY = localY;
        int listH = h - 40 - listY;
        int listW = w - 24;
        List<QuestChangeHistoryEntry> rows = queryRows();
        int maxScroll = Math.max(0, rows.size() * ROW_H - listH + 4);
        targetScroll = clamp(targetScroll, 0, maxScroll);
        scroll = HudAnimUtil.lerp(scroll, targetScroll, 0.25f, dt);
        scroll = clamp(scroll, 0, maxScroll);

        screen.enableScissor(g, x, y + listY, x + listW, y + listY + listH);
        if (rows.isEmpty()) {
            String empty = currentQuestOnly ? "NO HISTORY FOR CURRENT QUEST" : "NO CHANGE HISTORY RECORDED";
            JournalScaledTextRenderer.draw(g, font, empty, 4, listY + 8,
                    0.8f, HudAnimUtil.withAlpha(0x667788, alpha), false);
        } else {
            int rowY = listY - (int) scroll;
            for (QuestChangeHistoryEntry entry : rows) {
                if (rowY > listY - ROW_H && rowY < listY + listH) {
                    drawEntry(g, font, entry, 0, rowY, listW, alpha);
                    if (inside(mx, my, x, y + rowY, listW, ROW_H - 4))
                        screen.setHoveredCustomTooltip(List.of(HudText.of("history.time", QuestChangeHistoryFormatter.fullTime(entry.timeMs)).withStyle(Style.EMPTY.withColor(0xFFD166))));
                }
                rowY += ROW_H;
            }
        }
        g.disableScissor();
        return localY + Math.max(listH, rows.size() * ROW_H);
    }

    boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int localX = (int) mx - x;
        int localY = (int) my - y;
        if (localX < 0 || localY < 0 || localX > w || localY > h) return false;
        if (inside(localX, localY, 0, 18, 44, 16)) {
            currentQuestOnly = false;
            targetScroll = 0;
            screen.playClick();
            return true;
        }
        if (inside(localX, localY, 48, 18, 64, 16)) {
            currentQuestOnly = true;
            targetScroll = 0;
            screen.playClick();
            return true;
        }
        int fx = 0;
        int fy = 40;
        for (QuestChangeHistoryCategory category : QuestChangeHistoryCategory.values()) {
            if (category == QuestChangeHistoryCategory.SYSTEM) continue;
            int fw = Math.max(34, screen.getFont().width(category.shortLabel()) + 12);
            if (inside(localX, localY, fx, fy, fw, 12)) {
                selectedCategory = selectedCategory == category ? null : category;
                targetScroll = 0;
                screen.playClick();
                return true;
            }
            fx += fw + 4;
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (!inside(mx, my, x, y, w, h)) return false;
        targetScroll -= (float) delta * 24f;
        return true;
    }

    private List<QuestChangeHistoryEntry> queryRows() {
        QuestChangeHistoryFilters filters = new QuestChangeHistoryFilters();
        filters.category = selectedCategory;
        filters.limit = 240;
        if (currentQuestOnly) {
            String questId = currentQuestId();
            if (questId.isEmpty()) return List.of();
            filters.questId = questId;
        }
        return QuestChangeHistoryStore.INSTANCE.query(filters);
    }

    private String currentQuestId() {
        int idx = screen.getSelectedIndex();
        List<JournalTypes.QuestListEntry> entries = screen.getCurrentEntries();
        if (idx < 0 || idx >= entries.size()) return "";
        return entries.get(idx).questId();
    }

    private void drawEntry(GuiGraphics g, Font font, QuestChangeHistoryEntry entry, int x, int y, int w, int alpha) {
        int color = entry.themeColor != 0 ? entry.themeColor : entry.type.accentColor();
        g.fill(x, y, x + w, y + ROW_H - 4, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * (alpha / 255f))));
        HudRenderUtil.drawCyberneticEdge(g, x, y, ROW_H - 4, color, alpha);
        JournalScaledTextRenderer.draw(g, font, entry.type.displayName(), x + 8, y + 7,
                0.72f, HudAnimUtil.withAlpha(color, alpha), false);
        String title = safe(entry.detail, Component.translatable(entry.type.displayName()).getString());
        title = font.plainSubstrByWidth(title, (int) ((w - 18) / 0.85f));
        JournalScaledTextRenderer.draw(g, font, title, x + 8, y + 21,
                0.85f, HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private String safe(String primary, String fallback) {
        return primary != null && !primary.isEmpty() ? primary : fallback != null ? fallback : "";
    }
}
