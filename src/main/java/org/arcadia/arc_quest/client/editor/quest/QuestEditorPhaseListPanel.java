package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.component.HudListItemRenderer;
import org.arcadia.arc_quest.client.hud.component.HudPanelRenderer;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;

import java.util.List;

final class QuestEditorPhaseListPanel {
    private static final int ROW_HEIGHT = 34;

    private final JournalScrollbar scrollbar = new JournalScrollbar(2, 12);
    private int scrollOffset;

    void render(GuiGraphics graphics, Font font, HudRect panel, HudRect viewport,
                List<PhaseSpec> phases, String selectedPhaseId,
                int mouseX, int mouseY, int themeColor) {
        HudPanelRenderer.drawJournalPanel(graphics, panel, themeColor,
                QuestEditorTheme.PANEL_BACKGROUND_ALPHA, QuestEditorTheme.PANEL_BORDER_ALPHA);
        HudPanelRenderer.drawJournalHeader(graphics, font, panel,
                QuestEditorLayout.PANEL_HEADER_HEIGHT, "\u4efb\u52a1\u9636\u6bb5",
                Integer.toString(phases.size()), themeColor, 255);

        int contentHeight = contentHeight(phases);
        scrollOffset = clamp(scrollOffset, 0, Math.max(0, contentHeight - viewport.height()));
        HudRect contentViewport = contentViewport(viewport);
        graphics.enableScissor(contentViewport.x(), contentViewport.y(),
                contentViewport.right(), contentViewport.bottom());
        for (int index = 0; index < phases.size(); index++) {
            PhaseSpec phase = phases.get(index);
            int rowY = contentViewport.y() + index * ROW_HEIGHT - scrollOffset;
            if (rowY + ROW_HEIGHT < contentViewport.y() || rowY > contentViewport.bottom()) continue;
            HudRect row = new HudRect(contentViewport.x(), rowY,
                    contentViewport.width(), ROW_HEIGHT - 2);
            String displayName = phase.displayName == null || phase.displayName.value == null
                    || phase.displayName.value.isBlank() ? phase.phaseId : phase.displayName.value;
            HudListItemRenderer.drawJournal(graphics, font, row, displayName, phase.phaseId,
                    phase.phaseId.equals(selectedPhaseId), row.contains(mouseX, mouseY),
                    themeColor, 255);
        }
        graphics.disableScissor();
        scrollbar.render(graphics, scrollbarTrack(viewport), contentHeight,
                scrollOffset, 1f, themeColor);
    }

    String mouseClicked(double mouseX, double mouseY, int button, HudRect viewport,
                        List<PhaseSpec> phases) {
        if (button != 0) return null;
        JournalScrollbar.ScrollInteraction interaction = scrollbar.mouseClicked(
                mouseX, mouseY, scrollbarTrack(viewport), 6, contentHeight(phases), scrollOffset);
        if (interaction.consumed()) {
            setScrollOffset(interaction.scrollOffset(), viewport, phases);
            return "";
        }
        HudRect contentViewport = contentViewport(viewport);
        if (!contentViewport.contains(mouseX, mouseY)) return null;
        int index = (int) ((mouseY - contentViewport.y() + scrollOffset) / ROW_HEIGHT);
        if (index >= 0 && index < phases.size()) return phases.get(index).phaseId;
        return "";
    }

    boolean mouseDragged(double mouseY, HudRect viewport, List<PhaseSpec> phases) {
        JournalScrollbar.ScrollInteraction interaction = scrollbar.mouseDragged(
                mouseY, scrollbarTrack(viewport), contentHeight(phases), scrollOffset);
        if (!interaction.consumed()) return false;
        setScrollOffset(interaction.scrollOffset(), viewport, phases);
        return true;
    }

    boolean mouseReleased(int button) {
        return scrollbar.mouseReleased(button);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta,
                          HudRect viewport, List<PhaseSpec> phases) {
        if (!viewport.contains(mouseX, mouseY)) return false;
        scrollOffset -= (int) Math.round(delta * ROW_HEIGHT * 2.0);
        clamp(viewport, phases);
        return true;
    }

    void clamp(HudRect viewport, List<PhaseSpec> phases) {
        scrollOffset = clamp(scrollOffset, 0,
                Math.max(0, contentHeight(phases) - viewport.height()));
    }

    private void setScrollOffset(double value, HudRect viewport, List<PhaseSpec> phases) {
        scrollOffset = clamp((int) Math.round(value), 0,
                Math.max(0, contentHeight(phases) - viewport.height()));
    }

    private static int contentHeight(List<PhaseSpec> phases) {
        return phases.size() * ROW_HEIGHT;
    }

    private static HudRect contentViewport(HudRect viewport) {
        return new HudRect(viewport.x(), viewport.y(),
                Math.max(1, viewport.width() - 6), viewport.height());
    }

    private static HudRect scrollbarTrack(HudRect viewport) {
        return new HudRect(viewport.right() - 3, viewport.y(), 2, viewport.height());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
