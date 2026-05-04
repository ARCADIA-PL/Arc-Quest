package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.quest.api.IconPosition;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.HashMap;
import java.util.Map;

public class ArcQuestJournalListElement extends ArcGuiElement {
    private final QuestJournalScreen screen;
    private final Map<String, TextCache> textCache = new HashMap<>();
    private float selectedSlide = -1f;
    private float[] entryHoverAnim = new float[0];
    private double scrollOffset = 0;
    private double targetScroll = 0;
    private boolean draggingScrollbar = false;
    private double dragListYOffset = 0;

    public ArcQuestJournalListElement(QuestJournalScreen screen) {
        super(0, 0, JournalConstants.LIST_WIDTH, 0);
        this.screen = screen;
    }

    public void resetState() {
        entryHoverAnim = new float[screen.getCurrentEntries().size()];
        selectedSlide = screen.getSelectedIndex();
        targetScroll = 0;
        scrollOffset = 0;
        textCache.clear();
        draggingScrollbar = false;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        this.x = JournalConstants.LIST_MARGIN - (int) screen.getJournalSlideOffset();
        this.y = 38 + JournalConstants.TAB_HEIGHT + 6;
        this.height = screen.getScaledHeight() - 20 - y;
        if (entryHoverAnim.length != screen.getCurrentEntries().size()) {
            entryHoverAnim = new float[screen.getCurrentEntries().size()];
        }
        clampScroll();
        scrollOffset += Math.abs(targetScroll - scrollOffset) > 0.5 ? (targetScroll - scrollOffset) * Math.min(1.0, screen.getDt() * 14.0) : (targetScroll - scrollOffset);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        float effectiveAlpha = screen.getEffectiveAlpha() * inheritedOpacity;
        int theme = screen.getThemeColor();
        ArcDrawUtil.drawFrame(graphics, x, y, width, height, ArcDrawUtil.withAlpha(0x000000, (int) (0x55 * effectiveAlpha)), ArcDrawUtil.withAlpha(theme, (int) (0x55 * effectiveAlpha)));

        screen.enableScissor(graphics, x, y, x + width - 6, y + height);
        renderSelection(graphics, theme, effectiveAlpha);
        renderRows(graphics, context, effectiveAlpha);
        graphics.disableScissor();
        renderScrollbar(graphics, effectiveAlpha);
    }

    private void renderSelection(GuiGraphics graphics, int theme, float effectiveAlpha) {
        int selectedIndex = screen.getSelectedIndex();
        if (selectedIndex < 0) return;
        if (selectedSlide < 0) selectedSlide = selectedIndex;
        selectedSlide = ArcAnimClock.lerp(selectedSlide, selectedIndex, 0.25f, screen.getDt());
        int hlY = (int) (y + 2 - scrollOffset + selectedSlide * JournalConstants.ENTRY_HEIGHT);
        int entryTheme = theme;
        if (selectedIndex < screen.getCurrentEntries().size() && screen.getCurrentEntries().get(selectedIndex).def() != null) {
            int defTheme = screen.getCurrentEntries().get(selectedIndex).def().getThemeColor();
            if (defTheme != 0xFFFFFFFF) entryTheme = defTheme;
        }
        graphics.fill(x + 2, hlY, x + width - 8, hlY + JournalConstants.ENTRY_HEIGHT - 2, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (0x44 * effectiveAlpha)));
        ArcDrawUtil.drawCyberneticEdge(graphics, x + 2, hlY, JournalConstants.ENTRY_HEIGHT - 2, entryTheme, (int) (0xFF * effectiveAlpha), 3);
    }

    private void renderRows(GuiGraphics graphics, ArcGuiContext context, float effectiveAlpha) {
        int firstVisible = Math.max(0, (int) ((scrollOffset - 2 - JournalConstants.ENTRY_HEIGHT) / JournalConstants.ENTRY_HEIGHT));
        int lastVisible = Math.min(screen.getCurrentEntries().size() - 1, (int) ((scrollOffset + height) / JournalConstants.ENTRY_HEIGHT) + 1);
        int selectedIndex = screen.getSelectedIndex();
        for (int i = firstVisible; i <= lastVisible; i++) {
            JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(i);
            int entryY = (int) (y + 2 - scrollOffset + i * JournalConstants.ENTRY_HEIGHT);
            if (entryY + JournalConstants.ENTRY_HEIGHT < y || entryY > y + height) {
                if (i < entryHoverAnim.length) entryHoverAnim[i] = 0f;
                continue;
            }
            renderRow(graphics, context, entry, i, entryY, selectedIndex, effectiveAlpha);
        }
    }

    private void renderRow(GuiGraphics graphics, ArcGuiContext context, JournalTypes.QuestListEntry entry, int index, int entryY, int selectedIndex, float effectiveAlpha) {
        boolean hovered = context.mouseX() >= x && context.mouseX() <= x + width - 8 && context.mouseY() >= entryY && context.mouseY() <= entryY + JournalConstants.ENTRY_HEIGHT && context.mouseY() >= y && context.mouseY() <= y + height;
        if (index < entryHoverAnim.length) entryHoverAnim[index] = ArcAnimClock.step(entryHoverAnim[index], hovered ? 1f : 0f, 8f, screen.getDt());
        float hoverEase = ArcAnimClock.easeOutCubic(index < entryHoverAnim.length ? entryHoverAnim[index] : 0f);
        if (index != selectedIndex && hoverEase > 0.01f) {
            graphics.fill(x + 2, entryY, x + width - 8, entryY + JournalConstants.ENTRY_HEIGHT - 2, ArcDrawUtil.withAlpha(0xFFFFFF, (int) (hoverEase * 0x22 * effectiveAlpha)));
        }
        if (effectiveAlpha <= 0.05f) return;

        int baseGray = (int) (0xAA + 0x55 * hoverEase);
        int nameColor = index == selectedIndex ? ArcDrawUtil.withAlpha(0xFFFFFF, (int) (255 * effectiveAlpha)) : ArcDrawUtil.withAlpha((baseGray << 16) | (baseGray << 8) | baseGray, (int) (255 * effectiveAlpha));
        int textOffsetX = renderOptionalIcon(graphics, entry, entryY);
        renderEntryName(graphics, entry, entryY, textOffsetX, hoverEase, nameColor);
        renderCollectionSummary(graphics, entry, entryY, textOffsetX, effectiveAlpha);
    }

    private int renderOptionalIcon(GuiGraphics graphics, JournalTypes.QuestListEntry entry, int entryY) {
        int textOffsetX = 10;
        if (entry.def() == null) return textOffsetX;
        entry.def().getVisualConfig().getIcon(IconPosition.QUEST_LIST).ifPresent(icon -> QuestIconRenderer.renderIcon(graphics, icon, x + 10, entryY + (JournalConstants.ENTRY_HEIGHT - 12) / 2, 12, 12));
        if (entry.def().getVisualConfig().getIcon(IconPosition.QUEST_LIST).isPresent()) textOffsetX = 26;
        return textOffsetX;
    }

    private void renderEntryName(GuiGraphics graphics, JournalTypes.QuestListEntry entry, int entryY, int textOffsetX, float hoverEase, int nameColor) {
        int maxDrawWidth = width - textOffsetX - 16;
        TextCache cachedText = getTextCache(entry);
        String displayName = cachedText.displayName;
        float baseScale = 1f;
        if (cachedText.width > maxDrawWidth) {
            baseScale = Math.max(0.75f, (float) maxDrawWidth / cachedText.width);
            if (cachedText.width * baseScale > maxDrawWidth) {
                if (cachedText.lastMaxWidth != maxDrawWidth) {
                    int allowedW = (int) (maxDrawWidth / 0.75f) - screen.getFont().width("...");
                    cachedText.lastDrawName = screen.getFont().plainSubstrByWidth(displayName, allowedW) + "...";
                    cachedText.lastMaxWidth = maxDrawWidth;
                }
                displayName = cachedText.lastDrawName;
            }
        }
        float finalScale = baseScale * (1f + 0.03f * hoverEase);
        graphics.pose().pushPose();
        float textY = entryY + (JournalConstants.ENTRY_HEIGHT - screen.getFont().lineHeight * finalScale) / 2f - 0.5f;
        graphics.pose().translate(x + textOffsetX, textY, 0);
        graphics.pose().scale(finalScale, finalScale, 1f);
        graphics.drawString(screen.getFont(), displayName, 0, 0, nameColor, false);
        graphics.pose().popPose();
    }

    private void renderCollectionSummary(GuiGraphics graphics, JournalTypes.QuestListEntry entry, int entryY, int textOffsetX, float effectiveAlpha) {
        if (entry.def() == null || !entry.def().isCollectionQuest()) return;
        int done = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(entry.questId());
        int total = ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(entry.questId());
        String summary = done + "/" + total + " collected";
        graphics.pose().pushPose();
        graphics.pose().translate(x + textOffsetX, entryY + JournalConstants.ENTRY_HEIGHT - 9, 0);
        graphics.pose().scale(0.75f, 0.75f, 1f);
        graphics.drawString(screen.getFont(), summary, 0, 0, ArcDrawUtil.withAlpha(0xAAAAAA, (int) (255 * effectiveAlpha)), false);
        graphics.pose().popPose();
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

    private void renderScrollbar(GuiGraphics graphics, float effectiveAlpha) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int trackX = x + width - 6;
        int trackY = y + 2;
        int viewH = height - 4;
        int contentH = contentHeight();
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH));
        int thumbY = trackY + (int) ((scrollOffset / maxScroll) * (viewH - thumbH));
        graphics.fill(trackX, trackY, trackX + 4, trackY + viewH, ArcDrawUtil.withAlpha(0x000000, (int) (40 * effectiveAlpha)));
        graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, ArcDrawUtil.withAlpha(0xFFFFFF, (int) ((draggingScrollbar ? 180 : 120) * effectiveAlpha)));
    }

    private void clampScroll() {
        targetScroll = Math.max(0, Math.min(targetScroll, maxScroll()));
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0 || screen.isJournalClosing()) return false;
        int maxScroll = maxScroll();
        int scrollbarX = x + width - 6;
        if (maxScroll > 0 && mouseX >= scrollbarX && mouseX <= scrollbarX + 6 && mouseY >= y && mouseY <= y + height) {
            draggingScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) height / contentHeight()) * height));
            int thumbY = y + (int) ((scrollOffset / maxScroll) * (height - thumbH));
            if (mouseY >= thumbY && mouseY <= thumbY + thumbH) dragListYOffset = mouseY - thumbY;
            else {
                dragListYOffset = thumbH / 2.0;
                updateScrollFromMouse(mouseY, maxScroll);
            }
            return true;
        }
        if (mouseX >= x && mouseX <= x + width - 6 && mouseY >= y && mouseY <= y + height) {
            int index = (int) ((mouseY - y + scrollOffset) / JournalConstants.ENTRY_HEIGHT);
            if (index >= 0 && index < screen.getCurrentEntries().size()) {
                screen.onEntrySelected(index);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            targetScroll -= distance * JournalConstants.ENTRY_HEIGHT;
            clampScroll();
            return true;
        }
        return false;
    }

    public boolean onMouseDragged(double mouseX, double mouseY) {
        if (!draggingScrollbar) return false;
        updateScrollFromMouse(mouseY, maxScroll());
        return true;
    }

    @Override
    public void onMouseRelease(double mouseX, double mouseY, int button) {
        if (button == 0) draggingScrollbar = false;
    }

    private void updateScrollFromMouse(double mouseY, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) height / contentHeight()) * height));
        targetScroll = Math.max(0.0, Math.min(1.0, (mouseY - y - dragListYOffset) / Math.max(1, height - thumbH))) * maxScroll;
    }

    private int contentHeight() {
        return screen.getCurrentEntries().size() * JournalConstants.ENTRY_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    private static class TextCache {
        String displayName;
        int width;
        String lastDrawName;
        int lastMaxWidth = Integer.MIN_VALUE;
    }
}
