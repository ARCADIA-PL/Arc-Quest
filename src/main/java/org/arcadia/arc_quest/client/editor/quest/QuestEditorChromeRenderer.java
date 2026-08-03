package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudPanelRenderer;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.component.HudTextUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalButtonRenderer;

final class QuestEditorChromeRenderer {
    private QuestEditorChromeRenderer() {
    }

    static void renderBackground(GuiGraphics graphics, int width, int height, int themeColor) {
        int tint = HudAnimUtil.lerpColor(0x000000, themeColor, 0.05f);
        graphics.fill(0, 0, width, height, HudAnimUtil.withAlpha(tint, 255));
    }

    static void renderHeader(GuiGraphics graphics, Font font, HudRect header,
                             String questSource, int themeColor, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(header.x() + 16, header.y() + 7, 0f);
        graphics.pose().scale(0.85f, 0.85f, 1f);
        graphics.drawString(font, "SYS.ARC_QUEST // QUEST EDITOR  >>  MOUSE-3: FOCUS CURRENT",
                0, 0, HudAnimUtil.withAlpha(0xAAAAAA, 255), false);
        graphics.pose().popPose();

        HudRect saveButton = saveButton(header);
        HudRect fitButton = fitButton(header);
        boolean saveHovered = saveButton.contains(mouseX, mouseY);
        boolean fitHovered = fitButton.width() > 0 && fitButton.contains(mouseX, mouseY);
        JournalButtonRenderer.drawCyberButton(graphics, font, saveButton, "\u4fdd\u5b58",
                themeColor, saveHovered ? 1f : 0f, saveHovered, 1f);
        if (fitButton.width() > 0) {
            JournalButtonRenderer.drawCyberButton(graphics, font, fitButton, "\u9002\u914d\u89c6\u56fe",
                    themeColor, fitHovered ? 1f : 0f, fitHovered, 1f);
        }

        int sourceRight = (fitButton.width() > 0 ? fitButton.x() : saveButton.x()) - 8;
        int maxSourceWidth = Math.max(40, sourceRight - 16);
        graphics.drawString(font, HudTextUtil.ellipsize(font, questSource, maxSourceWidth),
                16, header.y() + 33, HudAnimUtil.withAlpha(0xAAB4C0, 230), false);
        graphics.fill(10, 23, header.right() - 10, 24,
                HudAnimUtil.withAlpha(0xCCCCCC, 90));
    }

    static void renderStatusBar(GuiGraphics graphics, Font font, HudRect statusBar,
                                String statusText, boolean dirty, int themeColor) {
        HudPanelRenderer.drawJournalPanel(graphics, statusBar, themeColor, 0x44, 0x55);
        String visibleText = HudTextUtil.ellipsize(font, statusText,
                Math.max(20, statusBar.width() - 20));
        graphics.drawString(font, visibleText, statusBar.x() + 9,
                statusBar.y() + Math.max(2, (statusBar.height() - font.lineHeight) / 2),
                dirty ? HudAnimUtil.withAlpha(themeColor, 255) : 0xFFAAB4C0, false);
    }

    static HudRect saveButton(HudRect header) {
        return new HudRect(header.right() - 74, header.y() + 27, 58, 18);
    }

    static HudRect fitButton(HudRect header) {
        if (header.width() < 600) return new HudRect(0, 0, 0, 0);
        HudRect save = saveButton(header);
        return new HudRect(save.x() - 78, save.y(), 70, save.height());
    }
}
