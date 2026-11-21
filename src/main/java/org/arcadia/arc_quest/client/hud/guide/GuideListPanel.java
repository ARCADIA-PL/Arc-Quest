package org.arcadia.arc_quest.client.hud.guide;

import org.arcadia.arc_quest.client.hud.style.OreUiRenderer;

import org.arcadia.arc_quest.client.hud.style.HudDraw;
import org.arcadia.arc_quest.client.hud.style.HudStyleRenderer;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.GroupExpansionStatePersistence;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalButtonRenderer;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.network.C2SMarkAllGuidesSeenPacket;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideGroupRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuideListPanel {
    private static final int ORE_PANEL_LEFT = 8;
    private static final int ORE_PANEL_RIGHT = 10;
    private static final int ENTRY_GAP = 2;
    private static final int ORE_ROW_TOP = OreUiRenderer.cardTextTop(GuideConstants.ENTRY_HEIGHT - ENTRY_GAP);
    private static final int ORE_ROW_BOTTOM = GuideConstants.ENTRY_HEIGHT
            - OreUiRenderer.cardTextBottom(GuideConstants.ENTRY_HEIGHT - ENTRY_GAP);
    private static final int MARK_ALL_READ_FOOTER_HEIGHT = 26;
    private static final int MARK_ALL_READ_BUTTON_HEIGHT = 17;

    private final GuideListScreen screen;
    private final GuideGroupEntryRenderer groupRenderer;
    private final Map<ResourceLocation, Boolean> groupExpanded = new HashMap<>();
    private final Map<ResourceLocation, Float> groupExpansion = new HashMap<>();
    private List<GuideListLayout.Row> rows = List.of();
    private float selectedSlide = -1f;
    private float[] entryHoverAnim = new float[0];
    private double scrollOffset;
    private double targetScroll;
    private boolean draggingListScrollbar;
    private double dragListYOffset;

    public GuideListPanel(GuideListScreen screen) {
        this.screen = screen;
        this.groupRenderer = new GuideGroupEntryRenderer(screen);
    }

    public void resetState() {
        rebuildRows();
        selectedSlide = -1f;
        targetScroll = 0;
        scrollOffset = 0;
        scrollToSelected();
    }

    private void rebuildRows() {
        rows = GuideListLayout.build(
                screen.guidesForSelectedCategory(),
                guide -> GuideGroupRegistry.getGroupForGuide(guide.getId()));
        initializeGroupExpansions(rows);
        entryHoverAnim = new float[rows.size()];
    }

    private void refreshRows() {
        List<GuideListLayout.Row> next = GuideListLayout.build(
                screen.guidesForSelectedCategory(),
                guide -> GuideGroupRegistry.getGroupForGuide(guide.getId()));
        if (!next.equals(rows)) {
            rows = next;
            initializeGroupExpansions(rows);
            entryHoverAnim = new float[rows.size()];
        }
    }

    private void initializeGroupExpansions(List<GuideListLayout.Row> currentRows) {
        for (GuideListLayout.Row row : currentRows) {
            if (row instanceof GuideListLayout.GroupRow groupRow) {
                ResourceLocation groupId = groupRow.group().getId();
                groupExpansion.putIfAbsent(groupId, isGroupExpanded(groupId) ? 1f : 0f);
            }
        }
    }

    private boolean isGroupExpanded(ResourceLocation groupId) {
        return groupExpanded.computeIfAbsent(groupId,
                ignored -> GroupExpansionStatePersistence.getInstance()
                        .isExpanded(GroupExpansionStatePersistence.GUIDE_SCOPE, groupId));
    }

    private void updateGroupAnimations(float deltaTime) {
        for (GuideListLayout.Row row : rows) {
            if (!(row instanceof GuideListLayout.GroupRow groupRow)) continue;
            ResourceLocation groupId = groupRow.group().getId();
            float current = groupExpansion.getOrDefault(groupId, 1f);
            float target = isGroupExpanded(groupId) ? 1f : 0f;
            groupExpansion.put(groupId, HudAnimUtil.step(current, target, 4.5f, deltaTime));
        }
    }

    private float getExpansion(GuideListLayout.GuideRow row) {
        if (!row.grouped()) return 1f;
        return HudAnimUtil.smoothStep(groupExpansion.getOrDefault(row.groupId(), 1f));
    }

    private float getRowHeight(GuideListLayout.Row row) {
        if (row instanceof GuideListLayout.GroupRow) return GuideConstants.ENTRY_HEIGHT;
        return GuideConstants.ENTRY_HEIGHT * getExpansion((GuideListLayout.GuideRow) row);
    }

    private float getRowTop(int targetIndex) {
        float top = 0f;
        for (int index = 0; index < targetIndex && index < rows.size(); index++) {
            top += getRowHeight(rows.get(index));
        }
        return top;
    }

    public void scrollToSelected() {
        ResourceLocation selectedGuideId = screen.getSelectedGuideId();
        if (selectedGuideId == null) return;
        var group = GuideGroupRegistry.getGroupForGuide(selectedGuideId);
        if (group != null) {
            groupExpanded.put(group.getId(), true);
            groupExpansion.put(group.getId(), 1f);
        }
        refreshRows();

        int index = getSelectedRowIndex();
        if (index < 0) return;
        float selectedTop = getRowTop(index);
        selectedSlide = selectedTop;
        targetScroll = Math.max(0, selectedTop - GuideConstants.ENTRY_HEIGHT);
        scrollOffset = targetScroll;
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height,
                       int mouseX, int mouseY, int theme, float deltaTime) {
        refreshRows();
        updateGroupAnimations(deltaTime);
        int listHeight = contentViewportHeight(height);
        clampScroll(listHeight);
        scrollOffset += Math.abs(targetScroll - scrollOffset) > 0.5
                ? (targetScroll - scrollOffset) * Math.min(1.0, deltaTime * 14.0)
                : targetScroll - scrollOffset;

        screen.enableScissor(graphics, x, y, x + width - 6, y + listHeight);
        int selectedIndex = getSelectedRowIndex();
        float effectiveAlpha = screen.getEffectiveAlpha();
        if (effectiveAlpha <= 0.08f) {
            JeiScreenIngredients.disableScissor(screen, graphics);
            return;
        }

        if (!HudDraw.isOre() && selectedIndex >= 0) {
            float selectedTop = getRowTop(selectedIndex);
            if (selectedSlide < 0) selectedSlide = selectedTop;
            selectedSlide = HudAnimUtil.lerp(selectedSlide, selectedTop, 0.25f, deltaTime);
            float selectedHeight = getRowHeight(rows.get(selectedIndex));
            int highlightY = (int) (y + 2 - scrollOffset + selectedSlide);
            if (HudDraw.isStyled()) HudStyleRenderer.card(graphics, x + 2, highlightY, width - 10,
                    Math.max(1, (int) selectedHeight - 2), true, false, effectiveAlpha, theme);
            else HudDraw.fill(graphics, x + 2, highlightY, x + width - 8,
                    highlightY + Math.max(1, (int) selectedHeight - 2),
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * effectiveAlpha)));
            HudRenderUtil.drawCyberneticEdge(graphics, x + 2, highlightY,
                    Math.max(1, (int) selectedHeight - 2), theme, (int) (0xFF * effectiveAlpha));
        }

        boolean ore = HudDraw.isOre();
        if (ore) renderOreGuidePanels(graphics, x, y, width, listHeight, theme, effectiveAlpha,
                selectedIndex, mouseX, mouseY, deltaTime);

        float rowTop = 0f;
        for (int index = 0; index < rows.size(); index++) {
            GuideListLayout.Row row = rows.get(index);
            float rowHeight = getRowHeight(row);
            int entryY = (int) (y + 2 - scrollOffset + rowTop);
            rowTop += rowHeight;
            if (rowHeight < 0.5f || entryY + rowHeight < y || entryY > y + listHeight) {
                entryHoverAnim[index] = 0f;
                continue;
            }
            float hover = ore && row instanceof GuideListLayout.GuideRow
                    ? HudAnimUtil.easeOutCubic(entryHoverAnim[index])
                    : updateHover(index, x, y, width, listHeight, entryY, rowHeight,
                            mouseX, mouseY, effectiveAlpha, deltaTime);

            if (row instanceof GuideListLayout.GroupRow groupRow) {
                ResourceLocation groupId = groupRow.group().getId();
                groupRenderer.render(graphics, groupRow, x, entryY, width, theme,
                        groupExpansion.getOrDefault(groupId, 1f), hover, effectiveAlpha);
            } else {
                renderGuideRow(graphics, (GuideListLayout.GuideRow) row, index == selectedIndex,
                        x, entryY, width, rowHeight, hover, effectiveAlpha);
            }
        }
        JeiScreenIngredients.disableScissor(screen, graphics);

        int contentHeight = contentHeight();
        int maxScroll = Math.max(0, contentHeight - listHeight);
        renderScrollbar(graphics, x + width - 6, y + 2,
                Math.max(1, listHeight - 4), contentHeight, maxScroll);
        renderMarkAllRead(graphics, x, y, width, height, mouseX, mouseY, theme, effectiveAlpha);
    }

    /** Guide rows share the same inset riveted panel and row highlights as journal tasks. */
    private void renderOreGuidePanels(GuiGraphics graphics, int x, int y, int width, int listHeight,
                                      int theme, float alpha, int selectedIndex, int mouseX, int mouseY, float dt) {
        double origin = y + 2 - scrollOffset;
        float top = 0;
        for (int index = 0; index < rows.size();) {
            if (!(rows.get(index) instanceof GuideListLayout.GuideRow first)) {
                top += getRowHeight(rows.get(index++));
                continue;
            }
            float bottom = top;
            List<OreUiRenderer.TaskHighlight> highlights = List.of();
            do {
                float height = getRowHeight(rows.get(index));
                int rowY = (int) (origin + bottom);
                bottom += height;
                if (height >= .5f && rowY + height >= y && rowY <= y + listHeight) {
                    float hover = updateHover(index, x, y, width, listHeight, rowY, height, mouseX, mouseY, alpha, dt);
                    float light = index == selectedIndex ? .15f : .075f * hover;
                    if (light > .00075f) {
                        if (highlights.isEmpty()) highlights = new ArrayList<>();
                        highlights.add(new OreUiRenderer.TaskHighlight(rowY, (int) (origin + bottom), light));
                    }
                }
                index++;
            } while (index < rows.size() && rows.get(index) instanceof GuideListLayout.GuideRow next
                    && java.util.Objects.equals(first.groupId(), next.groupId()));

            int panelY = (int) (origin + top);
            int panelBottom = (int) (origin + bottom) - ENTRY_GAP;
            if (panelBottom - panelY >= 4 && panelBottom > y && panelY < y + listHeight) {
                var group = first.grouped() ? GuideGroupRegistry.getGroupForGuide(first.guide().getId()) : null;
                int panelTheme = group == null || group.getThemeColor() == 0xFFFFFFFF ? theme : group.getThemeColor();
                OreUiRenderer.journalTaskPanel(graphics, x + ORE_PANEL_LEFT, panelY,
                        width - ORE_PANEL_LEFT - ORE_PANEL_RIGHT, panelBottom - panelY,
                        alpha * getExpansion(first), panelTheme, highlights);
            }
            top = bottom;
        }
    }

    private float updateHover(int index, int x, int y, int width, int listHeight, int entryY, float rowHeight,
                              int mouseX, int mouseY, float effectiveAlpha, float deltaTime) {
        boolean hovered = mouseX >= x && mouseX <= x + width - 8
                && mouseY >= entryY && mouseY <= entryY + rowHeight
                && mouseY >= y && mouseY <= y + listHeight;
        HudCursorManager.requestPointer(hovered && effectiveAlpha > 0.05f);
        entryHoverAnim[index] = HudAnimUtil.step(
                entryHoverAnim[index], hovered ? 1f : 0f, 8f, deltaTime);
        return HudAnimUtil.easeOutCubic(entryHoverAnim[index]);
    }

    private void renderGuideRow(GuiGraphics graphics, GuideListLayout.GuideRow row,
                                boolean selected, int x, int y, int width, float rowHeight,
                                float hover, float effectiveAlpha) {
        float expansion = getExpansion(row);
        float rowAlpha = effectiveAlpha * expansion;
        boolean ore = HudDraw.isOre();
        if (!ore && !selected && hover > 0.01f) {
            HudDraw.fill(graphics, x + 2, y, x + width - 8, y + Math.max(1, (int) rowHeight - 2),
                    HudAnimUtil.withAlpha(HudDraw.isStyled() ? row.guide().getCategory().getThemeColor() : 0xFFFFFF,
                            (int) (hover * (HudDraw.isStyled() ? 0x38 : 0x15) * rowAlpha)));
        }
        if (rowAlpha <= 0.03f) return;

        GuideDefinition guide = row.guide();
        int baseGray = ore ? (int) (0xAA + 0x55 * hover) : (int) (0x99 + 0x66 * hover);
        int nameColor = selected
                ? HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * rowAlpha))
                : HudAnimUtil.withAlpha((baseGray << 16) | (baseGray << 8) | baseGray,
                (int) (255 * rowAlpha));
        int indent = row.grouped() ? (ore ? 16 : 14) : ore ? 10 : 0;
        float slideX = row.grouped() && !ore ? (1f - expansion) * -8f : 0f;
        int faceTop = ore ? ORE_ROW_TOP : 0;
        int faceBottom = ore ? (int) rowHeight - ORE_ROW_BOTTOM : (int) rowHeight;
        int faceHeight = faceBottom - faceTop;
        if (ore && faceHeight < screen.getFont().lineHeight) return;
        boolean hasIcon = guide.getVisualConfig().hasIcon() && (!ore || faceHeight >= 16);
        int iconX = (int) (x + 9 + indent + slideX);
        int titleX = (int) (x + 10 + indent + (hasIcon ? 23 : 0) + slideX);
        int maxTitleWidth = x + width - 22 - titleX;
        var displayTitle = StyledTextUtil.fitSingleLine(screen.getFont(), guide.getTitle(), maxTitleWidth);
        int lineHeight = screen.getFont().lineHeight;
        float summaryScale = .72f;
        boolean hasSummary = !guide.getSummary().getString().isBlank()
                && (!ore || faceHeight >= lineHeight * (1f + summaryScale));
        var displaySummary = StyledTextUtil.fitSingleLine(
                screen.getFont(), guide.getSummary(), (int) (maxTitleWidth / 0.72f));
        float titleY = ore ? y + faceTop + (float) Math.floor((faceHeight - lineHeight
                - (hasSummary ? lineHeight * summaryScale : 0)) / 2f)
                : hasSummary ? y + 2f : y + (rowHeight - lineHeight) / 2f - .5f;
        int iconY = y + faceTop + Math.max(0, (faceHeight - 16) / 2);

        if (hasIcon && screen.shouldRenderOpaqueItems()) {
            var displayedIcon = guide.getVisualConfig().getIcon();
            GuideIconRenderer.draw(graphics, guide.getVisualConfig(), iconX, iconY);
            JeiScreenIngredients.guideIcon(screen, graphics, guide, -1, displayedIcon, iconX, iconY, 16, 16);
        }
        HudDraw.drawString(graphics, screen.getFont(), displayTitle, titleX, (int) titleY, nameColor, false);
        if (hasSummary) {
            graphics.pose().pushPose();
            graphics.pose().translate(titleX, ore ? titleY + lineHeight : y + 13f, 0);
            graphics.pose().scale(summaryScale, summaryScale, 1f);
            HudDraw.drawString(graphics, screen.getFont(), displaySummary, 0, 0,
                    HudAnimUtil.withAlpha(ore ? 0xBBBBBB : 0x89939E, (int) (235 * rowAlpha)), false);
            graphics.pose().popPose();
        }

        if (!ClientGuideCache.INSTANCE.isSeen(guide.getId())) {
            HudRenderUtil.drawBreathingRedDot(graphics, x + width - 14,
                    y + faceTop + Math.max(1, faceHeight / 2), rowAlpha);
        }
    }

    private int getSelectedRowIndex() {
        ResourceLocation selectedGuideId = screen.getSelectedGuideId();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index) instanceof GuideListLayout.GuideRow guideRow
                    && guideRow.guide().getId().equals(selectedGuideId)
                    && getRowHeight(guideRow) > 1f) return index;
        }
        return -1;
    }

    private int contentHeight() {
        float height = 0f;
        for (GuideListLayout.Row row : rows) height += getRowHeight(row);
        return (int) Math.ceil(height);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int viewHeight,
                                 int contentHeight, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbHeight = Math.max(16, (int) (((float) viewHeight / contentHeight) * viewHeight));
        int thumbY = y + (int) ((scrollOffset / maxScroll) * (viewHeight - thumbHeight));
        if (HudDraw.oreScrollbar(graphics, x, y, 4, viewHeight, thumbY, thumbHeight,
                draggingListScrollbar, screen.getEffectiveAlpha(), 0xFFFFFF)) return;
        HudDraw.fill(graphics, x, y, x + 4, y + viewHeight,
                HudAnimUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        HudDraw.fill(graphics, x, thumbY, x + 4, thumbY + thumbHeight,
                HudAnimUtil.withAlpha(0xFFFFFF,
                        (int) ((draggingListScrollbar ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    private void renderMarkAllRead(GuiGraphics graphics, int x, int y, int width, int height,
                                   int mouseX, int mouseY, int theme, float effectiveAlpha) {
        if (!ArcQuestConfig.shouldShowGuideMarkAllReadButton()
                || !ClientGuideCache.INSTANCE.hasUnreadGuides()) return;
        HudRect bounds = markAllReadBounds(x, y, width, height);
        boolean hovered = bounds.contains(mouseX, mouseY);
        HudCursorManager.requestPointer(hovered);
        int footerTop = y + contentViewportHeight(height);
        HudDraw.fill(graphics, x + 3, footerTop, x + width - 3, footerTop + 1,
                HudAnimUtil.withAlpha(theme, (int) (150 * effectiveAlpha)));
        JournalButtonRenderer.drawCyberButton(graphics, screen.getFont(), bounds,
                Component.translatable("gui.arc_quest.mark_all_read").getString(),
                theme, hovered ? 1f : 0f, true, effectiveAlpha);
    }

    private int contentViewportHeight(int height) {
        return Math.max(1, height - (ArcQuestConfig.shouldShowGuideMarkAllReadButton()
                && ClientGuideCache.INSTANCE.hasUnreadGuides()
                ? MARK_ALL_READ_FOOTER_HEIGHT : 0));
    }

    private HudRect markAllReadBounds(int x, int y, int width, int height) {
        return new HudRect(x + 4, y + height - MARK_ALL_READ_BUTTON_HEIGHT - 4,
                Math.max(1, width - 8), MARK_ALL_READ_BUTTON_HEIGHT);
    }

    public void clampScroll(int listHeight) {
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, contentHeight() - listHeight)));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int x, int y, int width, int height) {
        refreshRows();
        if (ArcQuestConfig.shouldShowGuideMarkAllReadButton()
                && ClientGuideCache.INSTANCE.hasUnreadGuides()
                && markAllReadBounds(x, y, width, height).contains(mouseX, mouseY)) {
            ClientGuideCache.INSTANCE.applyLocalAllSeen();
            ArcQuestNetwork.sendMarkAllGuidesSeen(new C2SMarkAllGuidesSeenPacket());
            screen.playClick();
            return true;
        }
        int listHeight = contentViewportHeight(height);
        int maxScroll = Math.max(0, contentHeight() - listHeight);
        int scrollbarX = x + width - 6;
        if (maxScroll > 0 && mouseX >= scrollbarX && mouseX <= scrollbarX + 6
                && mouseY >= y && mouseY <= y + listHeight) {
            draggingListScrollbar = true;
            int thumbHeight = Math.max(16, (int) (((float) listHeight / contentHeight()) * listHeight));
            int thumbY = y + (int) ((scrollOffset / maxScroll) * (listHeight - thumbHeight));
            if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) dragListYOffset = mouseY - thumbY;
            else {
                dragListYOffset = thumbHeight / 2.0;
                updateScrollFromMouse(mouseY, y, listHeight, maxScroll);
            }
            return true;
        }

        if (mouseX >= x && mouseX <= x + width - 6 && mouseY >= y && mouseY <= y + listHeight) {
            double relativeY = mouseY - y + scrollOffset;
            float rowTop = 0f;
            for (GuideListLayout.Row row : rows) {
                float rowHeight = getRowHeight(row);
                if (rowHeight >= 1f && relativeY >= rowTop && relativeY < rowTop + rowHeight) {
                    if (row instanceof GuideListLayout.GroupRow groupRow) {
                        ResourceLocation groupId = groupRow.group().getId();
                        boolean expanded = !isGroupExpanded(groupId);
                        groupExpanded.put(groupId, expanded);
                        GroupExpansionStatePersistence.getInstance().setExpanded(
                                GroupExpansionStatePersistence.GUIDE_SCOPE, groupId, expanded);
                        clampScroll(listHeight);
                        screen.playClick();
                    } else if (rowHeight > 4f) {
                        screen.selectGuide(((GuideListLayout.GuideRow) row).guide().getId());
                    }
                    return true;
                }
                rowTop += rowHeight;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int y, int height) {
        if (!draggingListScrollbar) return false;
        int listHeight = contentViewportHeight(height);
        updateScrollFromMouse(mouseY, y, listHeight, Math.max(0, contentHeight() - listHeight));
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0 || !draggingListScrollbar) return false;
        draggingListScrollbar = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta,
                                 int x, int y, int width, int height) {
        int listHeight = contentViewportHeight(height);
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + listHeight) return false;
        targetScroll -= delta * GuideConstants.ENTRY_HEIGHT;
        clampScroll(listHeight);
        return true;
    }

    private void updateScrollFromMouse(double mouseY, int y, int viewHeight, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbHeight = Math.max(16, (int) (((float) viewHeight / contentHeight()) * viewHeight));
        targetScroll = Math.max(0.0, Math.min(1.0,
                (mouseY - y - dragListYOffset) / (viewHeight - thumbHeight))) * maxScroll;
    }
}
