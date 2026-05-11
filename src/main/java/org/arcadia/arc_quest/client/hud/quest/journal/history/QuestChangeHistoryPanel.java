// file_name: QuestChangeHistoryPanel.java
package org.arcadia.arc_quest.client.hud.quest.journal.history;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public final class QuestChangeHistoryPanel {
    private final QuestJournalScreen screen;
    private final int rowH = 42; // 更紧凑的行高
    // UI 状态
    private float scroll = 0f;
    private float targetScroll = 0f;
    private float revealAnim = 0f;
    // 数据缓存
    private List<QuestChangeHistoryEntry> cachedRows = new ArrayList<>();
    private String lastQuestId = null;
    private FilterTab selectedTab = FilterTab.ALL;

    public QuestChangeHistoryPanel(QuestJournalScreen screen) {
        this.screen = screen;
    }

    public void reset() {
        scroll = 0f;
        targetScroll = 0f;
        revealAnim = 0f;
        cachedRows.clear();
        lastQuestId = null;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float dt) {
        QuestChangeHistoryStore.INSTANCE.ensureLoaded();

        revealAnim = HudAnimUtil.lerp(revealAnim, 1f, 0.15f, dt);
        int alpha = (int) (255 * screen.getEffectiveAlpha() * HudAnimUtil.easeOutCubic(Math.min(1f, revealAnim)));
        if (alpha <= 4) return;

        int theme = screen.getThemeColor();
        Font font = screen.getFont();

        int localY = y + 16;
        int contentX = x + 16;
        int contentW = w - 32;

        // 1. 顶部大标题 (动态显示当前任务名)
        String qName = currentQuestName();
        g.pose().pushPose();
        g.pose().translate(contentX, localY, 0);
        g.pose().scale(1.05f, 1.05f, 1f);
        g.drawString(font, "// ARCHIVE: " + (qName.isEmpty() ? "UNKNOWN" : qName.toUpperCase()), 0, 0, HudAnimUtil.withAlpha(theme, alpha), true);
        g.pose().popPose();
        localY += 24;

        // 2. 现代化的药丸式(Pill)单排过滤标签
        drawFilterChips(g, font, contentX, localY, mouseX, mouseY, theme, alpha);
        localY += 28; // 留出呼吸空间

        // 3. 数据更新与滚动限制
        updateRowsIfNeeded();

        int listY = localY;
        int listH = y + h - 16 - listY;
        int maxScroll = Math.max(0, cachedRows.size() * rowH - listH);

        targetScroll = clamp(targetScroll, 0, maxScroll);
        scroll = HudAnimUtil.lerp(scroll, targetScroll, 0.25f, dt);

        // 4. 渲染时间轴列表
        screen.enableScissor(g, contentX, listY, contentX + contentW, listY + listH);

        if (cachedRows.isEmpty()) {
            drawScaled(g, font, "[ NO RECORDS FOUND IN THIS CATEGORY ]", contentX + 4, listY + 12, 0.8f, HudAnimUtil.withAlpha(0x556677, alpha), false);
        } else {
            int rowY = listY - (int) scroll;
            for (QuestChangeHistoryEntry entry : cachedRows) {
                if (rowY > listY - rowH && rowY < listY + listH) {
                    drawEntry(g, font, entry, contentX, rowY, contentW, mouseX, mouseY, alpha);
                }
                rowY += rowH;
            }
        }

        g.disableScissor();

        // 5. 极简科幻滚动条
        if (maxScroll > 0) {
            int thumbH = Math.max(16, (int) (((float) listH / (cachedRows.size() * rowH)) * listH));
            int thumbY = listY + (int) ((scroll / maxScroll) * (listH - thumbH));
            g.fill(contentX + contentW + 4, listY, contentX + contentW + 5, listY + listH, HudAnimUtil.withAlpha(0x000000, (int) (40 * (alpha / 255f))));
            g.fill(contentX + contentW + 4, thumbY, contentX + contentW + 5, thumbY + thumbH, HudAnimUtil.withAlpha(theme, (int) (200 * (alpha / 255f))));
        }
    }

    private void updateRowsIfNeeded() {
        String currentQ = currentQuestId();
        // 只有当任务改变，或者切换了标签时，才重新拉取数据
        if (cachedRows.isEmpty() || !Objects.equals(lastQuestId, currentQ)) {
            QuestChangeHistoryFilters filters = new QuestChangeHistoryFilters();
            filters.category = selectedTab.category;
            filters.limit = 240;
            // 强制且唯一锁定当前任务
            if (!currentQ.isEmpty()) {
                filters.questId = currentQ;
            }
            cachedRows = QuestChangeHistoryStore.INSTANCE.query(filters);
            lastQuestId = currentQ;
        }
    }

    private void drawFilterChips(GuiGraphics g, Font font, int px, int py, double mx, double my, int theme, int alpha) {
        int fx = px;
        for (FilterTab tab : FilterTab.values()) {
            int fw = font.width(tab.label) + 16;
            boolean active = (selectedTab == tab);
            boolean hovered = inside(mx, my, fx, py, fw, 18);

            // 背景色
            int bg = active ? HudAnimUtil.withAlpha(theme, (int) (alpha * 0.15f)) : (hovered ? HudAnimUtil.withAlpha(0x334455, alpha) : 0);
            if (bg != 0) g.fill(fx, py, fx + fw, py + 18, bg);

            // 底部指示条
            if (active) {
                g.fill(fx, py + 17, fx + fw, py + 18, HudAnimUtil.withAlpha(theme, alpha));
            } else {
                g.fill(fx, py + 17, fx + fw, py + 18, HudAnimUtil.withAlpha(0x334455, alpha));
            }

            // 文字
            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x778899);
            drawScaled(g, font, tab.label, fx + 8, py + 6, 0.8f, HudAnimUtil.withAlpha(textColor, alpha), false);

            fx += fw + 4; // 紧凑间距
        }
    }

    private void drawEntry(GuiGraphics g, Font font, QuestChangeHistoryEntry entry, int x, int y, int w, int mx, int my, int alpha) {
        boolean hovered = inside(mx, my, x, y, w, rowH - 4);
        int color = entry.themeColor != 0 ? entry.themeColor : entry.type.accentColor();

        // 1. 悬停反馈 (科幻边框发光)
        if (hovered) {
            g.fill(x + 46, y, x + w, y + rowH - 4, HudAnimUtil.withAlpha(color, (int) (0.08f * alpha)));
            HudRenderUtil.drawCyberneticEdge(g, x + 46, y, rowH - 4, color, (int) (0.6f * alpha));
        }

        // 2. 左侧时间文本
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(entry.timeMs);
        String timeStr = String.format("%02d:%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
        drawScaled(g, font, timeStr, x, y + 15, 0.75f, HudAnimUtil.withAlpha(0x8899AA, alpha), false);

        // 3. 贯穿的时间轴竖线与节点
        int axisX = x + 40;
        g.fill(axisX, y, axisX + 1, y + rowH, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0.1f * alpha)));

        int nodeSize = hovered ? 3 : 2;
        int nodeY = y + 18;
        g.fill(axisX - nodeSize, nodeY - nodeSize, axisX + nodeSize + 1, nodeY + nodeSize + 1, HudAnimUtil.withAlpha(color, alpha));
        if (hovered) {
            g.fill(axisX - nodeSize + 1, nodeY - nodeSize + 1, axisX + nodeSize, nodeY + nodeSize, HudAnimUtil.withAlpha(0xFFFFFF, alpha));
        }

        // 4. 右侧内容详情
        int contentX = axisX + 12;
        drawScaled(g, font, entry.type.displayName(), contentX, y + 7, 0.7f, HudAnimUtil.withAlpha(color, alpha), false);

        String title = safe(entry.detail, entry.title);
        title = font.plainSubstrByWidth(title, (int) ((w - contentX + x) / 0.85f));
        drawScaled(g, font, title, contentX, y + 18, 0.85f, HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);

        // 5. 悬停提示 Tooltip
        if (hovered) {
            screen.setHoveredCustomTooltip(List.of(
                    Component.literal("Exact Time: " + QuestChangeHistoryFormatter.fullTime(entry.timeMs)).withStyle(Style.EMPTY.withColor(0xFFD166)),
                    Component.literal("Type: " + entry.type.name()).withStyle(Style.EMPTY.withColor(0x8FA3B6))
            ));
        }
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int w, int h) {
        if (button != 0) return false;
        int contentX = x + 16;
        int py = y + 40; // 过滤标签的Y轴位置

        int fx = contentX;
        for (FilterTab tab : FilterTab.values()) {
            int fw = screen.getFont().width(tab.label) + 16;
            if (inside(mx, my, fx, py, fw, 18)) {
                if (selectedTab != tab) {
                    selectedTab = tab;
                    targetScroll = 0;
                    lastQuestId = null; // 强迫下一帧刷新数据
                    screen.playClick();
                }
                return true;
            }
            fx += fw + 4;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (!inside(mx, my, x, y, w, h)) return false;
        targetScroll -= (float) delta * 36f;
        return true;
    }

    private String currentQuestId() {
        int idx = screen.getSelectedIndex();
        List<JournalTypes.QuestListEntry> entries = screen.getCurrentEntries();
        if (idx < 0 || idx >= entries.size()) return "";
        return entries.get(idx).questId();
    }

    private String currentQuestName() {
        int idx = screen.getSelectedIndex();
        List<JournalTypes.QuestListEntry> entries = screen.getCurrentEntries();
        if (idx < 0 || idx >= entries.size()) return "";
        return entries.get(idx).displayName();
    }

    private void drawScaled(GuiGraphics g, Font font, String text, int x, int y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
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

    // 现代化的单排过滤标签定义
    private enum FilterTab {
        ALL("ALL LOGS", null),
        PHASE("STAGES", QuestChangeHistoryCategory.PHASE),
        OBJ("TASKS", QuestChangeHistoryCategory.OBJECTIVE),
        REWARD("REWARDS", QuestChangeHistoryCategory.REWARD),
        COLL("ITEMS", QuestChangeHistoryCategory.COLLECTION);

        final String label;
        final QuestChangeHistoryCategory category;

        FilterTab(String label, QuestChangeHistoryCategory category) {
            this.label = label;
            this.category = category;
        }
    }
}