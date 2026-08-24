package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalButtonRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalUnreadBadgeRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;
import org.arcadia.arc_quest.quest.api.IconPosition;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestGroupRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JournalListPanel {
    private final QuestJournalScreen screen;
    private final JournalGroupEntryRenderer groupRenderer;
    private final Map<String, TextCache> textCache = new HashMap<>();
    private final Map<ResourceLocation, Boolean> groupExpanded = new HashMap<>();
    private final Map<ResourceLocation, Float> groupExpansion = new HashMap<>();
    private final Map<ResourceLocation, JournalListLayout.GroupDefinition> explicitGroupDefinitions = new HashMap<>();
    private final Map<ResourceLocation, JournalListLayout.GroupDefinition> categoryGroupDefinitions = new HashMap<>();
    private List<JournalListLayout.Row> rows = List.of();
    private float selectedSlide = -1f;
    private float[] entryHoverAnim = new float[0];
    private double scrollOffset;
    private double targetScroll;
    private final JournalScrollbar scrollbar = new JournalScrollbar();

    public JournalListPanel(QuestJournalScreen screen) {
        this.screen = screen;
        this.groupRenderer = new JournalGroupEntryRenderer(screen);
    }

    public void resetState() {
        rebuildRows();
        selectedSlide = getSelectedRowTop();
        scrollToSelected();
    }

    public void refreshEntries() {
        rebuildRows();
        float selectedTop = getSelectedRowTop();
        if (selectedSlide < 0f || selectedTop < 0f) selectedSlide = selectedTop;
    }

    private void rebuildRows() {
        rows = JournalListLayout.build(screen.getCurrentEntries(), this::resolveJournalGroup);
        for (JournalListLayout.Row row : rows) {
            if (row instanceof JournalListLayout.GroupRow groupRow) {
                ResourceLocation groupId = groupRow.group().id();
                groupExpansion.putIfAbsent(groupId, isGroupExpanded(groupId) ? 1f : 0f);
            }
        }
        entryHoverAnim = new float[rows.size()];
        textCache.clear();
    }

    private JournalListLayout.GroupDefinition resolveJournalGroup(JournalTypes.QuestListEntry entry) {
        QuestGroupDefinition explicitGroup = QuestGroupRegistry.getGroupForQuest(entry.questId());
        if (explicitGroup != null) {
            return explicitGroupDefinitions.computeIfAbsent(
                    explicitGroup.getId(),
                    ignored -> JournalListLayout.GroupDefinition.explicit(explicitGroup)
            );
        }

        if (entry.def() == null || entry.def().getCategory() == null) return null;
        QuestCategory category = entry.def().getCategory();
        return categoryGroupDefinitions.computeIfAbsent(
                category.getId(),
                ignored -> JournalListLayout.GroupDefinition.category(category)
        );
    }

    private boolean isGroupExpanded(ResourceLocation groupId) {
        return groupExpanded.getOrDefault(groupId, true);
    }

    private void updateGroupAnimations(float deltaTime) {
        for (JournalListLayout.Row row : rows) {
            if (!(row instanceof JournalListLayout.GroupRow groupRow)) continue;
            ResourceLocation groupId = groupRow.group().id();
            float current = groupExpansion.getOrDefault(groupId, 1f);
            float target = isGroupExpanded(groupId) ? 1f : 0f;
            groupExpansion.put(groupId, HudAnimUtil.step(current, target, 4.5f, deltaTime));
        }
    }

    private float getExpansion(JournalListLayout.QuestRow row) {
        if (!row.grouped()) return 1f;
        return HudAnimUtil.smoothStep(groupExpansion.getOrDefault(row.groupId(), 1f));
    }

    private float getRowHeight(JournalListLayout.Row row) {
        if (row instanceof JournalListLayout.GroupRow) return JournalConstants.GROUP_ENTRY_HEIGHT;
        JournalListLayout.QuestRow questRow = (JournalListLayout.QuestRow) row;
        return JournalConstants.ENTRY_HEIGHT * getExpansion(questRow);
    }

    private float getRowTop(int targetIndex) {
        float top = 0f;
        for (int index = 0; index < targetIndex && index < rows.size(); index++) {
            top += getRowHeight(rows.get(index));
        }
        return top;
    }

    private int getSelectedRowIndex() {
        int selectedQuestIndex = screen.getSelectedIndex();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (rows.get(rowIndex) instanceof JournalListLayout.QuestRow questRow
                    && questRow.questIndex() == selectedQuestIndex
                    && getRowHeight(questRow) > 1f) {
                return rowIndex;
            }
        }
        return -1;
    }

    private float getSelectedRowTop() {
        int selectedRowIndex = getSelectedRowIndex();
        return selectedRowIndex >= 0 ? getRowTop(selectedRowIndex) : -1f;
    }

    private int getContentHeight() {
        float height = 0f;
        for (JournalListLayout.Row row : rows) height += getRowHeight(row);
        return (int) Math.ceil(height);
    }

    private void scrollToSelected() {
        float selectedTop = getSelectedRowTop();
        if (selectedTop < 0f) {
            targetScroll = 0;
            scrollOffset = 0;
            return;
        }
        targetScroll = Math.max(0, selectedTop - JournalConstants.ENTRY_HEIGHT);
        scrollOffset = targetScroll;
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height,
                       int mouseX, int mouseY, int theme, float deltaTime) {
        updateGroupAnimations(deltaTime);
        clampScroll(height);
        scrollOffset += Math.abs(targetScroll - scrollOffset) > 0.5
                ? (targetScroll - scrollOffset) * Math.min(1.0, deltaTime * 14.0)
                : targetScroll - scrollOffset;

        screen.enableScissor(graphics, x, y, x + width - 6, y + height);
        float effectiveAlpha = screen.getEffectiveAlpha();
        int selectedRowIndex = getSelectedRowIndex();
        float selectedTop = getSelectedRowTop();

        if (selectedRowIndex >= 0 && selectedTop >= 0f) {
            if (selectedSlide < 0f) selectedSlide = selectedTop;
            selectedSlide = HudAnimUtil.lerp(selectedSlide, selectedTop, 0.25f, deltaTime);
            JournalListLayout.QuestRow selectedRow = (JournalListLayout.QuestRow) rows.get(selectedRowIndex);
            float selectedHeight = getRowHeight(selectedRow);
            int highlightY = (int) (y + 2 - scrollOffset + selectedSlide);
            int entryTheme = theme;
            if (selectedRow.entry().def() != null) {
                int definitionTheme = selectedRow.entry().def().getThemeColor();
                if (definitionTheme != 0xFFFFFFFF) entryTheme = definitionTheme;
            }
            graphics.fill(x + 2, highlightY, x + width - 8,
                    highlightY + Math.max(1, (int) selectedHeight - 2),
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * effectiveAlpha)));
            HudRenderUtil.drawCyberneticEdge(graphics, x + 2, highlightY,
                    Math.max(1, (int) selectedHeight - 2), entryTheme,
                    (int) (0xFF * effectiveAlpha));
        }

        float rowTop = 0f;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            JournalListLayout.Row row = rows.get(rowIndex);
            float rowHeight = getRowHeight(row);
            int entryY = (int) (y + 2 - scrollOffset + rowTop);
            rowTop += rowHeight;
            if (rowHeight < 0.5f || entryY + rowHeight < y || entryY > y + height) {
                if (rowIndex < entryHoverAnim.length) entryHoverAnim[rowIndex] = 0f;
                continue;
            }

            boolean hovered = mouseX >= x && mouseX <= x + width - 8
                    && mouseY >= entryY && mouseY <= entryY + rowHeight
                    && mouseY >= y && mouseY <= y + height;
            entryHoverAnim[rowIndex] = HudAnimUtil.step(
                    entryHoverAnim[rowIndex], hovered ? 1f : 0f, 8f, deltaTime);
            float hover = HudAnimUtil.easeOutCubic(entryHoverAnim[rowIndex]);

            if (row instanceof JournalListLayout.GroupRow groupRow) {
                ResourceLocation groupId = groupRow.group().id();
                groupRenderer.render(graphics, groupRow, x, entryY, width, theme,
                        groupExpansion.getOrDefault(groupId, 1f), hover, effectiveAlpha);
            } else if (row instanceof JournalListLayout.QuestRow questRow) {
                if (rowIndex != selectedRowIndex && hover > 0.01f) {
                    graphics.fill(x + 2, entryY, x + width - 8,
                            entryY + Math.max(1, (int) rowHeight - 2),
                            HudAnimUtil.withAlpha(0xFFFFFF,
                                    (int) (hover * 0x22 * effectiveAlpha * getExpansion(questRow))));
                }
                renderQuestRow(graphics, questRow, rowIndex == selectedRowIndex,
                        x, entryY, width, rowHeight, hover, effectiveAlpha);
            }
        }
        graphics.disableScissor();

        int contentHeight = getContentHeight();
        int maxScroll = Math.max(0, contentHeight - height);
        scrollbar.render(graphics, scrollbarTrack(x, y, width, height), contentHeight,
                scrollOffset, screen.getEffectiveAlpha(), 0xFFFFFF);
        renderMarkAllRead(graphics, x, y, width, height, mouseX, mouseY, theme, effectiveAlpha);
    }

    private void renderQuestRow(GuiGraphics graphics, JournalListLayout.QuestRow row,
                                boolean selected, int x, int y, int width, float rowHeight,
                                float hover, float effectiveAlpha) {
        float expansion = getExpansion(row);
        float rowAlpha = effectiveAlpha * expansion;
        if (rowAlpha <= 0.03f) return;

        JournalTypes.QuestListEntry entry = row.entry();
        int baseGray = (int) (0xAA + 0x55 * hover);
        int nameColor = selected
                ? HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * rowAlpha))
                : HudAnimUtil.withAlpha((baseGray << 16) | (baseGray << 8) | baseGray,
                (int) (255 * rowAlpha));

        int indent = row.grouped() ? 16 : 0;
        int textOffsetX = 10 + indent;
        int iconX = x + 10 + indent;

        if (entry.def() != null) {
            entry.def().getVisualConfig().getIcon(IconPosition.QUEST_LIST).ifPresent(icon ->
                    QuestIconRenderer.renderIcon(graphics, icon, iconX,
                            y + Math.max(0, ((int) rowHeight - 12) / 2), 12, 12, rowAlpha));
            if (entry.def().getVisualConfig().getIcon(IconPosition.QUEST_LIST).isPresent()) {
                textOffsetX += 16;
            }
        }

        int maxDrawWidth = width - textOffsetX - 16;
        TextCache cachedText = getTextCache("quest:" + entry.questId(), entry.displayName());
        float baseScale = cachedText.width > maxDrawWidth
                ? Math.max(0.75f, (float) maxDrawWidth / cachedText.width)
                : 1f;
        FormattedCharSequence displayName = cachedText.width * baseScale > maxDrawWidth
                ? fitText(cachedText, maxDrawWidth, 0.75f)
                : cachedText.displayName;
        float finalScale = baseScale * (1f + 0.03f * hover);
        float slideX = row.grouped() ? (1f - expansion) * -8f : 0f;

        graphics.pose().pushPose();
        float textY = y + (rowHeight - screen.getFont().lineHeight * finalScale) / 2f - 0.5f;
        graphics.pose().translate(x + textOffsetX + slideX, textY, 0);
        graphics.pose().scale(finalScale, finalScale, 1f);
        graphics.drawString(screen.getFont(), displayName, 0, 0, nameColor, false);
        graphics.pose().popPose();

        if (entry.def() != null && entry.def().isCollectionQuest() && rowHeight > 17f) {
            int done = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(entry.questId());
            int total = ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(entry.questId());
            graphics.pose().pushPose();
            graphics.pose().translate(x + textOffsetX + slideX, y + rowHeight - 9, 0);
            graphics.pose().scale(0.75f, 0.75f, 1f);
            graphics.drawString(screen.getFont(), done + "/" + total + " collected", 0, 0,
                    HudAnimUtil.withAlpha(0xAAAAAA, (int) (255 * rowAlpha)), false);
            graphics.pose().popPose();
        }

        String trackedQuestId = QuestHudOverlay.INSTANCE.getTrackedQuestId();
        if (QuestChangeNotificationManager.INSTANCE.hasUnread(entry.questId())
                && !entry.questId().equals(trackedQuestId)) {
            JournalUnreadBadgeRenderer.draw(graphics, x + width - 18,
                    y + Math.max(1, (int) rowHeight / 2), (int) (255 * rowAlpha));
        }
    }

    private void renderMarkAllRead(GuiGraphics graphics, int x, int y, int width, int height,
                                   int mouseX, int mouseY, int theme, float effectiveAlpha) {
        if (!QuestChangeNotificationManager.INSTANCE.hasAnyUnread()) return;
        int buttonX = x + 4;
        int buttonY = y + height - 18;
        int buttonWidth = 80;
        int buttonHeight = 14;
        boolean hovered = mouseX >= buttonX && mouseX <= buttonX + buttonWidth
                && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
        if (hovered) screen.requestPointerCursor();
        JournalButtonRenderer.drawCompactButton(graphics, screen.getFont(),
                new HudRect(buttonX, buttonY, buttonWidth, buttonHeight),
                Component.translatable("gui.arc_quest.mark_all_read").getString(),
                theme, effectiveAlpha, hovered, 0.65f);
    }

    private TextCache getTextCache(String key, Component displayName) {
        return textCache.computeIfAbsent(key, ignored -> {
            TextCache cache = new TextCache();
            cache.component = displayName;
            cache.displayName = displayName.getVisualOrderText();
            cache.width = screen.getFont().width(displayName);
            return cache;
        });
    }

    private FormattedCharSequence fitText(TextCache cachedText, int maxDrawWidth, float scale) {
        if (cachedText.lastMaxWidth != maxDrawWidth || cachedText.lastScale != scale) {
            int allowedWidth = (int) (maxDrawWidth / scale);
            cachedText.lastDrawName = StyledTextUtil.fitSingleLine(
                    screen.getFont(), cachedText.component, allowedWidth);
            cachedText.lastMaxWidth = maxDrawWidth;
            cachedText.lastScale = scale;
        }
        return cachedText.lastDrawName;
    }

    public void clampScroll(int listHeight) {
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, getContentHeight() - listHeight)));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int width, int height) {
        int contentHeight = getContentHeight();
        JournalScrollbar.ScrollInteraction scrollInteraction = scrollbar.mouseClicked(
                mouseX, mouseY, scrollbarTrack(x, y, width, height), 6, contentHeight, scrollOffset);
        if (scrollInteraction.consumed()) {
            targetScroll = scrollInteraction.scrollOffset();
            return true;
        }

        if (mouseX < x || mouseX > x + width - 6 || mouseY < y || mouseY > y + height) return false;
        if (QuestChangeNotificationManager.INSTANCE.hasAnyUnread()
                && mouseX >= x + 4 && mouseX <= x + 84
                && mouseY >= y + height - 18 && mouseY <= y + height - 4) {
            QuestChangeNotificationManager.INSTANCE.markAllRead();
            screen.playClick();
            return true;
        }

        double relativeY = mouseY - y + scrollOffset;
        float rowTop = 0f;
        for (JournalListLayout.Row row : rows) {
            float rowHeight = getRowHeight(row);
            if (rowHeight >= 1f && relativeY >= rowTop && relativeY < rowTop + rowHeight) {
                if (row instanceof JournalListLayout.GroupRow groupRow) {
                    ResourceLocation groupId = groupRow.group().id();
                    groupExpanded.put(groupId, !isGroupExpanded(groupId));
                    screen.playClick();
                } else if (row instanceof JournalListLayout.QuestRow questRow && rowHeight > 4f) {
                    screen.onEntrySelected(questRow.questIndex());
                }
                return true;
            }
            rowTop += rowHeight;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int y, int height) {
        JournalScrollbar.ScrollInteraction interaction = scrollbar.mouseDragged(mouseY,
                new HudRect(0, y + 2, 4, Math.max(1, height - 4)), getContentHeight(), targetScroll);
        if (interaction.consumed()) targetScroll = interaction.scrollOffset();
        return interaction.consumed();
    }

    public boolean mouseReleased(int button) {
        return scrollbar.mouseReleased(button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta,
                                 int x, int y, int width, int height) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            targetScroll -= delta * JournalConstants.ENTRY_HEIGHT;
            clampScroll(height);
            return true;
        }
        return false;
    }

    private HudRect scrollbarTrack(int x, int y, int width, int height) {
        return new HudRect(x + width - 6, y + 2, 4, Math.max(1, height - 4));
    }

    private static class TextCache {
        Component component;
        FormattedCharSequence displayName;
        int width;
        FormattedCharSequence lastDrawName;
        int lastMaxWidth = Integer.MIN_VALUE;
        float lastScale = -1f;
    }
}
