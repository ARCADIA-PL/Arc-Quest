package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudPanelRenderer;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalButtonRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;

import java.util.List;

final class QuestEditorInspectorRenderer {
    private QuestEditorInspectorRenderer() {
    }

    static int render(GuiGraphics graphics, Font font, HudRect panel, HudRect viewport,
                      PhaseSpec phase, int scrollOffset, JournalScrollbar scrollbar,
                      int themeColor) {
        HudPanelRenderer.drawJournalPanel(graphics, panel, themeColor,
                QuestEditorTheme.PANEL_BACKGROUND_ALPHA, QuestEditorTheme.PANEL_BORDER_ALPHA);
        HudPanelRenderer.drawJournalHeader(graphics, font, panel,
                QuestEditorLayout.PANEL_HEADER_HEIGHT, "\u9636\u6bb5\u8be6\u60c5", null, themeColor, 255);
        if (phase == null) {
            graphics.drawCenteredString(font, "\u9009\u62e9\u9636\u6bb5\u4ee5\u67e5\u770b\u8be6\u60c5",
                    viewport.x() + viewport.width() / 2,
                    viewport.y() + Math.max(0, viewport.height() / 2 - font.lineHeight / 2),
                    HudAnimUtil.withAlpha(QuestEditorTheme.TEXT_MUTED, 220));
            return 0;
        }

        HudRect contentViewport = new HudRect(viewport.x(), viewport.y(),
                Math.max(1, viewport.width() - 6), viewport.height());
        graphics.enableScissor(contentViewport.x(), contentViewport.y(),
                contentViewport.right(), contentViewport.bottom());
        int y = contentViewport.y() - scrollOffset;
        y = renderField(graphics, font, contentViewport, y, "\u9636\u6bb5 ID", phase.phaseId,
                QuestEditorTheme.TEXT_PRIMARY, themeColor);
        y = renderField(graphics, font, contentViewport, y, "\u663e\u793a\u540d\u79f0",
                phase.displayName == null ? "" : phase.displayName.value,
                QuestEditorTheme.TEXT_PRIMARY, themeColor);
        if (phase.description != null && phase.description.value != null
                && !phase.description.value.isBlank()) {
            y = renderField(graphics, font, contentViewport, y, "\u9636\u6bb5\u63cf\u8ff0",
                    phase.description.value, 0xC8D0DA, themeColor);
        }
        y = renderField(graphics, font, contentViewport, y, "\u5185\u5bb9\u7edf\u8ba1",
                "\u76ee\u6807 " + phase.objectives.size() + "  \u00b7  \u8df3\u8f6c "
                        + phase.transitions.size() + "  \u00b7  \u9009\u9879 " + phase.choices.size(),
                QuestEditorTheme.TEXT_SECONDARY, themeColor);
        y += 2;
        HudPanelRenderer.drawDivider(graphics, contentViewport.x(), y,
                contentViewport.width(), HudAnimUtil.withAlpha(themeColor, 72));
        y += 9;
        JournalButtonRenderer.drawToggleButton(graphics, font,
                new HudRect(contentViewport.x(), y, Math.min(164, contentViewport.width()), 18),
                "\u5b8c\u6210\u540e\u81ea\u52a8\u63a8\u8fdb", themeColor, 255,
                phase.autoAdvanceOnComplete, 0.78f);
        y += 25;
        graphics.drawString(font, "A \u5207\u6362  \u00b7  Delete \u5220\u9664\u9636\u6bb5",
                contentViewport.x(), y, HudAnimUtil.withAlpha(themeColor, 220), false);
        y += font.lineHeight + 9;
        graphics.disableScissor();

        int contentHeight = Math.max(0, y + scrollOffset - contentViewport.y());
        scrollbar.render(graphics, scrollbarTrack(viewport), contentHeight,
                scrollOffset, 1f, themeColor);
        return contentHeight;
    }

    static HudRect scrollbarTrack(HudRect viewport) {
        return new HudRect(viewport.right() - 3, viewport.y(), 2, viewport.height());
    }

    private static int renderField(GuiGraphics graphics, Font font, HudRect viewport, int y,
                                   String label, String value, int valueColor, int themeColor) {
        graphics.drawString(font, label, viewport.x(), y,
                HudAnimUtil.withAlpha(themeColor, 235), false);
        y += font.lineHeight + 3;
        List<FormattedCharSequence> lines = font.split(Component.literal(value == null ? "" : value),
                Math.max(20, viewport.width()));
        if (lines.isEmpty()) lines = List.of(Component.empty().getVisualOrderText());
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, viewport.x(), y,
                    HudAnimUtil.withAlpha(valueColor, 240), false);
            y += font.lineHeight + 2;
        }
        return y + 7;
    }
}
