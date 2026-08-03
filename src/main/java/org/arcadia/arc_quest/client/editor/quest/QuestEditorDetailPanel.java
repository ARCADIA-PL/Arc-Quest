package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;

import java.util.List;

final class QuestEditorDetailPanel {
    static final int WIDTH = 284;
    private static final int PADDING = 13;
    private String selectedPhaseId;
    private boolean open;
    private float animation;
    private double scrollOffset;
    private double targetScroll;
    private int maxScroll;
    private float closeHover;

    void select(String phaseId) {
        if (phaseId == null) return;
        if (!phaseId.equals(selectedPhaseId)) {
            scrollOffset = 0;
            targetScroll = 0;
        }
        selectedPhaseId = phaseId;
        open = true;
    }

    void close() {
        open = false;
    }

    void update(float deltaTime) {
        animation = HudAnimUtil.smoothExp(animation, open ? 1f : 0f, open ? 13f : 16f, deltaTime);
        scrollOffset = HudAnimUtil.smoothExp((float) scrollOffset, (float) targetScroll, 15f, deltaTime);
        if (!open && animation < 0.002f) {
            animation = 0f;
            selectedPhaseId = null;
        }
    }

    int getReservedWidth() {
        return Math.round((WIDTH + 5) * easedAnimation());
    }

    boolean isSelected(String phaseId) {
        return selectedPhaseId != null && selectedPhaseId.equals(phaseId) && animation > 0.02f;
    }

    void render(GuiGraphics graphics, Font font, HudRect workspace, PhaseSpec phase,
                int mouseX, int mouseY, int themeColor, float deltaTime) {
        if (phase == null || selectedPhaseId == null || animation <= 0.002f) return;
        int panelTop = workspace.y() + 8;
        int panelHeight = Math.max(1, workspace.height() - 16);
        int panelRight = workspace.right() - 3;
        int x = currentX(panelRight);
        int contentTop = panelTop + 10;
        int contentBottom = panelTop + panelHeight - 9;
        boolean closeHovered = mouseX >= x + WIDTH - 29 && mouseX <= x + WIDTH - 13
                && mouseY >= panelTop + 8 && mouseY <= panelTop + 24;
        closeHover = HudAnimUtil.smoothExp(closeHover, closeHovered ? 1f : 0f, 16f, deltaTime);

        graphics.enableScissor(panelRight - WIDTH, panelTop, panelRight, panelTop + panelHeight);
        graphics.fill(x, panelTop, x + WIDTH, panelTop + panelHeight,
                HudAnimUtil.withAlpha(0x000000, 0xCC));
        graphics.fill(x, panelTop, x + 2, panelTop + panelHeight,
                HudAnimUtil.withAlpha(themeColor, 255));
        drawFrame(graphics, x, panelTop, WIDTH, panelHeight, 1,
                HudAnimUtil.withAlpha(themeColor, 0x55));
        renderCloseButton(graphics, font, x + WIDTH - 29, panelTop + 8, themeColor, closeHover);

        graphics.enableScissor(x + 2, contentTop, x + WIDTH - 2, contentBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(x + PADDING, contentTop - scrollOffset, 2f);
        int contentHeight = renderContent(graphics, font, phase, WIDTH - PADDING * 2, themeColor);
        graphics.pose().popPose();
        graphics.disableScissor();
        maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
        if (maxScroll > 0) renderScrollbar(graphics, x, contentTop, contentBottom, themeColor);
        graphics.disableScissor();
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, HudRect workspace) {
        if (!contains(mouseX, mouseY, workspace)) return false;
        int panelTop = workspace.y() + 8;
        int x = currentX(workspace.right() - 3);
        if (button == 0 && mouseX >= x + WIDTH - 29 && mouseX <= x + WIDTH - 13
                && mouseY >= panelTop + 8 && mouseY <= panelTop + 24) close();
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta, HudRect workspace) {
        if (!contains(mouseX, mouseY, workspace)) return false;
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - delta * 24));
        return true;
    }

    boolean isCloseHovered(double mouseX, double mouseY, HudRect workspace) {
        if (!contains(mouseX, mouseY, workspace)) return false;
        int panelTop = workspace.y() + 8;
        int x = currentX(workspace.right() - 3);
        return mouseX >= x + WIDTH - 29 && mouseX <= x + WIDTH - 13
                && mouseY >= panelTop + 8 && mouseY <= panelTop + 24;
    }

    private int renderContent(GuiGraphics graphics, Font font, PhaseSpec phase,
                              int width, int themeColor) {
        int y = 0;
        graphics.fill(0, 2, 4, 13, HudAnimUtil.withAlpha(themeColor, 255));
        graphics.drawString(font, "EDITING", 9, 3, HudAnimUtil.withAlpha(themeColor, 255), false);
        y += 20;
        String displayName = phase.displayName == null || phase.displayName.value == null
                || phase.displayName.value.isBlank() ? phase.phaseId : phase.displayName.value;
        y = drawWrapped(graphics, font, Component.literal(displayName)
                .withStyle(Style.EMPTY.withBold(true)), 0, y, width - 28, 0xFFFFFF, 255, 1.22f, 3);
        y += 7;
        graphics.drawString(font, phase.phaseId, 0, y, HudAnimUtil.withAlpha(0x888888, 230), false);
        y += font.lineHeight + 10;
        if (phase.description != null && phase.description.value != null
                && !phase.description.value.isBlank()) {
            y = drawWrapped(graphics, font, Component.literal(phase.description.value),
                    0, y, width, 0xAAAAAA, 245, 0.95f, 3);
            y += 10;
        }
        y = renderSectionTitle(graphics, font, "CONTENT", y, width, themeColor);
        y = drawWrapped(graphics, font, Component.literal("Objectives  " + phase.objectives.size()
                        + "   Transitions  " + phase.transitions.size()
                        + "   Choices  " + phase.choices.size()),
                0, y, width, 0xFFFFFF, 245, 0.92f, 3);
        y += 10;
        y = renderSectionTitle(graphics, font, "BEHAVIOR", y, width, themeColor);
        String behavior = "Auto enter  " + (phase.autoEnterByCondition ? "ON" : "OFF")
                + "   Auto advance  " + (phase.autoAdvanceOnComplete ? "ON" : "OFF");
        y = drawWrapped(graphics, font, Component.literal(behavior),
                0, y, width, 0xFFFFFF, 245, 0.92f, 3);
        if (phase.story != null && phase.story.value != null && !phase.story.value.isBlank()) {
            y += 10;
            y = renderSectionTitle(graphics, font, "STORY", y, width, themeColor);
            y = drawWrapped(graphics, font, Component.literal(phase.story.value),
                    0, y, width, 0xAAAAAA, 245, 0.95f, 3);
        }
        return y + 18;
    }

    private static int renderSectionTitle(GuiGraphics graphics, Font font, String text,
                                          int y, int width, int themeColor) {
        graphics.drawString(font, text, 0, y, HudAnimUtil.withAlpha(themeColor, 255), false);
        graphics.fill(font.width(text) + 8, y + 4, width, y + 5,
                HudAnimUtil.withAlpha(themeColor, 75));
        return y + font.lineHeight + 7;
    }

    private static int drawWrapped(GuiGraphics graphics, Font font, Component text,
                                   int x, int y, int width, int color, int alpha,
                                   float scale, int spacing) {
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

    private boolean contains(double mouseX, double mouseY, HudRect workspace) {
        if (selectedPhaseId == null || animation < 0.08f) return false;
        int top = workspace.y() + 8;
        int height = Math.max(1, workspace.height() - 16);
        int x = currentX(workspace.right() - 3);
        return mouseX >= x && mouseX <= x + WIDTH && mouseY >= top && mouseY <= top + height;
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int top, int bottom, int themeColor) {
        int viewport = bottom - top;
        int thumbHeight = Math.max(18, Math.round(viewport * (viewport / (float) (viewport + maxScroll))));
        int travel = viewport - thumbHeight;
        int thumbY = top + (maxScroll == 0 ? 0 : Math.round((float) (scrollOffset / maxScroll) * travel));
        graphics.fill(x + WIDTH - 6, top, x + WIDTH - 5, bottom, HudAnimUtil.withAlpha(0xFFFFFF, 22));
        graphics.fill(x + WIDTH - 7, thumbY, x + WIDTH - 4, thumbY + thumbHeight,
                HudAnimUtil.withAlpha(themeColor, 170));
    }

    private static void renderCloseButton(GuiGraphics graphics, Font font, int x, int y,
                                          int themeColor, float hover) {
        float scale = 1f + hover * 0.10f;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 8, y + 8, 4f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-8, -8, 0);
        graphics.fill(0, 0, 16, 16,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x222222, 0x3A2020, hover), 230));
        drawFrame(graphics, 0, 0, 16, 16, 1,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(themeColor, 0xFF5B66, hover), 185));
        graphics.drawCenteredString(font, "X", 8, 4,
                HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0xFFFFFF, 0xFFCDD2, hover), 255));
        graphics.pose().popPose();
    }

    private float easedAnimation() {
        return HudAnimUtil.easeOutCubic(Math.max(0f, Math.min(1f, animation)));
    }

    private int currentX(int panelRight) {
        return panelRight - Math.round(WIDTH * easedAnimation());
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y,
                                  int width, int height, int thickness, int color) {
        graphics.fill(x, y, x + width, y + thickness, color);
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        graphics.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        graphics.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }
}
