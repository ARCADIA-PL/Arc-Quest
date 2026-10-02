package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.guide.GuideImageLayout;
import org.arcadia.arc_quest.client.hud.guide.GuideImageRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;

/** In-screen modal: returning from the image never rebuilds the quest browser or grants guide state. */
final class CollectionImageViewer {
    private GuideMediaDefinition media;
    private Component caption = Component.empty();
    private HudRect close = new HudRect(0, 0, 0, 0);

    boolean isOpen() { return media != null; }
    void open(GuideMediaDefinition image, Component description) {
        if (GuideImageRenderer.available(image)) { media = image; caption = description; }
    }
    void close() { media = null; }

    void render(QuestJournalScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (media == null) return;
        JeiScreenIngredients.modal(screen, false);
        int width = screen.getScaledWidth(), height = screen.getScaledHeight();
        float alpha = screen.getEffectiveAlpha();
        int a = (int) (255 * alpha);
        int margin = Math.max(12, Math.min(width, height) / 12);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 800);
        graphics.fill(0, 0, width, height, HudAnimUtil.withAlpha(0x050809, (int) (235 * alpha)));
        int imageWidth = Math.max(1, width - margin * 2), imageHeight = Math.max(1, height - margin * 2 - 30);
        screen.enableScissor(graphics, margin, margin, margin + imageWidth, margin + imageHeight);
        GuideImageRenderer.draw(graphics, media, margin, margin, imageWidth, imageHeight,
                GuideImageLayout.Fit.CONTAIN, alpha);
        screen.disableScissor(graphics);
        var lines = screen.getFont().split(caption, imageWidth);
        int y = margin + imageHeight + 8;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(screen.getFont(), lines.get(i), margin, y, HudAnimUtil.withAlpha(0xDDDDDD, a), false);
            y += screen.getFont().lineHeight + 1;
        }
        close = new HudRect(width - margin - 24, 6, 24, 16);
        boolean hover = close.contains(mouseX, mouseY);
        graphics.fill(close.x(), close.y(), close.right(), close.bottom(),
                HudAnimUtil.withAlpha(hover ? screen.getCurrentThemeColor() : 0x222D2F, a / 2));
        graphics.drawCenteredString(screen.getFont(), Component.literal("×"), close.x() + 12, close.y() + 3,
                HudAnimUtil.withAlpha(0xFFFFFF, a));
        if (hover) screen.requestPointerCursor();
        graphics.pose().popPose();
    }

    boolean click(QuestJournalScreen screen, double x, double y, int button) {
        if (!isOpen()) return false;
        if (button == 0 && close.contains(x, y)) { close(); screen.playClick(); }
        return true;
    }
}
