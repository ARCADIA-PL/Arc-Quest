package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

import java.util.List;

final class QuestHistoryDetailPanel {
    static final int WIDTH = 284;
    private static final int PADDING = 13;
    private QuestHistoryNodeData selectedNode;
    private boolean open;
    private float animation;
    private double scrollOffset;
    private double targetScroll;
    private int maxScroll;
    private float closeHover;

    void reset() {
        selectedNode = null;
        open = false;
        animation = 0f;
        scrollOffset = 0;
        targetScroll = 0;
        maxScroll = 0;
        closeHover = 0f;
    }

    void select(QuestHistoryNodeData node) {
        if (node == null || !node.reached()) return;
        if (selectedNode == null || !selectedNode.id().equals(node.id())) {
            scrollOffset = 0;
            targetScroll = 0;
        }
        selectedNode = node;
        open = true;
    }

    void close() {
        open = false;
    }

    boolean isOpen() {
        return open;
    }

    boolean isSelected(String phaseId) {
        return selectedNode != null && selectedNode.id().equals(phaseId) && animation > 0.02f;
    }

    int getReservedWidth() {
        return Math.round((WIDTH + 5) * easedAnimation());
    }

    void update(float deltaTime) {
        animation = HudAnimUtil.smoothExp(animation, open ? 1f : 0f, open ? 13f : 16f, deltaTime);
        scrollOffset = HudAnimUtil.smoothExp((float) scrollOffset, (float) targetScroll, 15f, deltaTime);
        if (!open && animation < 0.002f) {
            animation = 0f;
            selectedNode = null;
        }
    }

    boolean mouseClicked(float mouseX, float mouseY, int panelRight, int panelTop, int panelHeight, int button) {
        if (!contains(mouseX, mouseY, panelRight, panelTop, panelHeight)) return false;
        int x = currentX(panelRight);
        if (button == 0 && mouseX >= x + WIDTH - 29 && mouseX <= x + WIDTH - 13
                && mouseY >= panelTop + 8 && mouseY <= panelTop + 24) close();
        return true;
    }

    boolean mouseScrolled(float mouseX, float mouseY, double delta, int panelRight, int panelTop, int panelHeight) {
        if (!contains(mouseX, mouseY, panelRight, panelTop, panelHeight)) return false;
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - delta * 24));
        return true;
    }

    void render(GuiGraphics graphics, Font font, QuestRuntimeData runtime, int panelRight, int panelTop,
                int panelHeight, int themeColor, float parentAlpha, float screenX, float screenY, float screenScale,
                float mouseX, float mouseY, float deltaTime) {
        if (selectedNode == null || animation <= 0.002f) return;
        float alphaFactor = parentAlpha;
        int alpha = Math.round(255 * alphaFactor);
        int x = currentX(panelRight);
        int contentTop = panelTop + 10;
        int contentBottom = panelTop + panelHeight - 9;
        boolean closeHovered = mouseX >= x + WIDTH - 29 && mouseX <= x + WIDTH - 13
                && mouseY >= panelTop + 8 && mouseY <= panelTop + 24;
        closeHover = HudAnimUtil.smoothExp(closeHover, closeHovered ? 1f : 0f, 16f, deltaTime);
        if (closeHovered && Minecraft.getInstance().screen instanceof QuestJournalScreen journalScreen) {
            journalScreen.requestPointerCursor();
        }
        enableScissor(graphics, panelRight - WIDTH, panelTop, panelRight, panelTop + panelHeight,
                screenX, screenY, screenScale);
        graphics.fill(x, panelTop, x + WIDTH, panelTop + panelHeight,
                HudAnimUtil.withAlpha(0x090C11, Math.round(244 * alphaFactor)));
        graphics.fill(x, panelTop, x + 2, panelTop + panelHeight, HudAnimUtil.withAlpha(themeColor, alpha));
        drawFrame(graphics, x, panelTop, WIDTH, panelHeight, 1,
                HudAnimUtil.withAlpha(0xAAB4C0, Math.round(78 * alphaFactor)));
        renderCloseButton(graphics, font, x + WIDTH - 29, panelTop + 8, alpha, themeColor, closeHover);
        enableScissor(graphics, x + 2, contentTop, x + WIDTH - 2, contentBottom, screenX, screenY, screenScale);
        graphics.pose().pushPose();
        graphics.pose().translate(x + PADDING, contentTop - scrollOffset, 2f);
        int contentHeight = renderContent(graphics, font, runtime, WIDTH - PADDING * 2, themeColor, alphaFactor, alpha);
        graphics.pose().popPose();
        graphics.disableScissor();
        maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
        if (maxScroll > 0) renderScrollbar(graphics, x, contentTop, contentBottom, alphaFactor, themeColor);
        graphics.disableScissor();
    }

    private int renderContent(GuiGraphics graphics, Font font, QuestRuntimeData runtime, int width,
                              int themeColor, float alphaFactor, int alpha) {
        int y = 0;
        int stateColor = selectedNode.completed() ? 0x69E79A : selectedNode.active() ? themeColor : 0x7B8591;
        String stateText = selectedNode.completed() ? "COMPLETED" : selectedNode.active() ? "IN PROGRESS" : "UNREACHED";
        graphics.fill(0, 2, 4, 13, HudAnimUtil.withAlpha(stateColor, alpha));
        graphics.drawString(font, stateText, 9, 3, HudAnimUtil.withAlpha(stateColor, alpha), false);
        y += 20;
        Component title = selectedNode.displayName().copy().withStyle(Style.EMPTY.withBold(true));
        y = drawWrapped(graphics, font, title, 0, y, width - 28, 0xFFFFFF, alpha, 1.22f, 3);
        graphics.fill(0, y + 2, width, y + 3, HudAnimUtil.withAlpha(themeColor, Math.round(70 * alphaFactor)));
        y += 11;
        Component description = selectedNode.phase().getDescription();
        if (!description.getString().isBlank()) {
            y = drawWrapped(graphics, font, description, 0, y, width, 0xC5CBD3, alpha, 0.96f, 2) + 9;
        }
        y = renderImage(graphics, font, y, width, themeColor, alphaFactor, alpha);
        y = renderObjectives(graphics, font, runtime, y, width, themeColor, alphaFactor, alpha);
        return renderStory(graphics, font, y, width, themeColor, alpha) + 10;
    }

    private int renderImage(GuiGraphics graphics, Font font, int y, int width, int themeColor,
                            float alphaFactor, int alpha) {
        if (selectedNode.image() == null) return y;
        int imageWidth = Math.round(width * 0.92f);
        int imageHeight = Math.round(imageWidth * 9f / 16f);
        int imageX = (width - imageWidth) / 2;
        QuestHistoryImageRenderer.RenderResult result = QuestHistoryImageRenderer.renderCover(
                graphics, selectedNode.image(), imageX, y, imageWidth, imageHeight, alphaFactor,
                !selectedNode.reached(), themeColor);
        if (!selectedNode.reached() && result != QuestHistoryImageRenderer.RenderResult.UNAVAILABLE) {
            graphics.pose().pushPose();
            graphics.pose().translate(imageX + imageWidth / 2f, y + imageHeight / 2f, 3f);
            graphics.pose().scale(2f, 2f, 1f);
                graphics.drawCenteredString(font, "?", 0, -font.lineHeight / 2, HudAnimUtil.withAlpha(0xFFFFFF, alpha));
            graphics.pose().popPose();
        }
        drawFrame(graphics, imageX, y, imageWidth, imageHeight, 1,
                HudAnimUtil.withAlpha(0xAAB4C0, Math.round(95 * alphaFactor)));
        return y + imageHeight + 13;
    }

    private int renderObjectives(GuiGraphics graphics, Font font, QuestRuntimeData runtime, int y, int width,
                                 int themeColor, float alphaFactor, int alpha) {
        if (selectedNode.phase().getObjectives().stream().noneMatch(objective -> !objective.isHidden())) return y;
        y = renderSectionTitle(graphics, font, "PROGRESS", y, width, themeColor, alpha);
        int index = 0;
        for (ObjectiveEntry objective : selectedNode.phase().getObjectives()) {
            if (!objective.isHidden()) {
                int progress = runtime == null ? 0 : runtime.getObjectiveProgress(selectedNode.id(), index);
                y = renderObjective(graphics, font, objective, progress, y, width, themeColor, alphaFactor, alpha);
            }
            index++;
        }
        return y + 8;
    }

    private int renderStory(GuiGraphics graphics, Font font, int y, int width, int themeColor, int alpha) {
        Component story = selectedNode.phase().getStory();
        if (story.getString().isBlank()) return y;
        y = renderSectionTitle(graphics, font, "STORY", y, width, themeColor, alpha);
        return drawWrapped(graphics, font, story, 1, y, width - 2, 0xD6D0C5, alpha, 0.94f, 3) + 8;
    }

    private int renderObjective(GuiGraphics graphics, Font font, ObjectiveEntry objective, int progress, int y,
                                int width, int themeColor, float alphaFactor, int alpha) {
        int required = Math.max(1, objective.getRequiredCount());
        boolean complete = selectedNode.completed() || progress >= required;
        String progressText = objective.isBooleanProgress() ? "" : Math.min(progress, required) + "/" + required;
        int progressWidth = progressText.isEmpty() ? 0 : font.width(progressText) + 5;
        List<FormattedCharSequence> lines = font.split(objective.getDisplayText(),
                Math.max(40, width - 25 - progressWidth));
        int rowHeight = Math.max(22, 8 + lines.size() * font.lineHeight);
        int stateColor = complete ? 0x69E79A : themeColor;
        graphics.fill(0, y, width, y + rowHeight, HudAnimUtil.withAlpha(0x11161D, Math.round(185 * alphaFactor)));
        graphics.fill(0, y, 2, y + rowHeight, HudAnimUtil.withAlpha(stateColor, Math.round(150 * alphaFactor)));
        graphics.fill(8, y + 8, 14, y + 14, HudAnimUtil.withAlpha(complete ? stateColor : 0x27303A, alpha));
        int textY = y + 6;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, 20, textY, HudAnimUtil.withAlpha(0xD3D8DF, alpha), false);
            textY += font.lineHeight;
        }
        if (!progressText.isEmpty()) {
            graphics.drawString(font, progressText, width - font.width(progressText) - 5, y + 6,
                    HudAnimUtil.withAlpha(stateColor, alpha), false);
        }
        return y + rowHeight + 4;
    }

    private int renderSectionTitle(GuiGraphics graphics, Font font, String text, int y, int width, int themeColor, int alpha) {
        graphics.drawString(font, text, 0, y, HudAnimUtil.withAlpha(themeColor, alpha), false);
        graphics.fill(font.width(text) + 8, y + 4, width, y + 5,
                HudAnimUtil.withAlpha(themeColor, Math.min(alpha, 75)));
        return y + font.lineHeight + 7;
    }

    private int drawWrapped(GuiGraphics graphics, Font font, Component text, int x, int y, int width,
                            int color, int alpha, float scale, int spacing) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(1, Math.round(width / scale)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        int localY = 0;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, 0, localY, HudAnimUtil.withAlpha(color, alpha), false);
            localY += font.lineHeight + spacing;
        }
        graphics.pose().popPose();
        return y + Math.round(localY * scale);
    }

    private boolean contains(float mouseX, float mouseY, int panelRight, int panelTop, int panelHeight) {
        if (selectedNode == null || animation < 0.08f) return false;
        int x = currentX(panelRight);
        return mouseX >= x && mouseX <= x + WIDTH && mouseY >= panelTop && mouseY <= panelTop + panelHeight;
    }

    private void enableScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2,
                               float screenX, float screenY, float screenScale) {
        int sx1 = Math.round(screenX + x1 * screenScale);
        int sy1 = Math.round(screenY + y1 * screenScale);
        int sx2 = Math.round(screenX + x2 * screenScale);
        int sy2 = Math.round(screenY + y2 * screenScale);
        if (Minecraft.getInstance().screen instanceof QuestJournalScreen journalScreen) {
            journalScreen.enableScissor(graphics, sx1, sy1, sx2, sy2);
        } else {
            graphics.enableScissor(sx1, sy1, sx2, sy2);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int top, int bottom, float alphaFactor, int themeColor) {
        int viewport = bottom - top;
        int thumbHeight = Math.max(18, Math.round(viewport * (viewport / (float) (viewport + maxScroll))));
        int travel = viewport - thumbHeight;
        int thumbY = top + (maxScroll == 0 ? 0 : Math.round((float) (scrollOffset / maxScroll) * travel));
        graphics.fill(x + WIDTH - 6, top, x + WIDTH - 5, bottom,
                HudAnimUtil.withAlpha(0xFFFFFF, Math.round(22 * alphaFactor)));
        graphics.fill(x + WIDTH - 7, thumbY, x + WIDTH - 4, thumbY + thumbHeight,
                HudAnimUtil.withAlpha(themeColor, Math.round(170 * alphaFactor)));
    }

    private void renderCloseButton(GuiGraphics graphics, Font font, int x, int y, int alpha, int themeColor,
                                   float hover) {
        float scale = 1f + hover * 0.10f;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 8, y + 8, 4f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-8, -8, 0);
        graphics.fill(0, 0, 16, 16,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x11161D, 0x2A1115, hover), Math.min(alpha, 230)));
        drawFrame(graphics, 0, 0, 16, 16, 1,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(themeColor, 0xFF5B66, hover), Math.min(alpha, 185)));
        graphics.drawCenteredString(font, "X", 8, 4,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0xFFFFFF, 0xFFCDD2, hover), alpha));
        graphics.pose().popPose();
    }

    private float easedAnimation() {
        return HudAnimUtil.easeOutCubic(Math.max(0f, Math.min(1f, animation)));
    }

    private int currentX(int panelRight) {
        return panelRight - Math.round(WIDTH * easedAnimation());
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int thickness, int color) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }
}
