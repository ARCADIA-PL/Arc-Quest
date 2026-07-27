// file_name: GuideContentPanel.java
package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class GuideContentPanel {
    private static final int NAVIGATION_HEIGHT = 34;
    private static final int NAVIGATION_BUTTON_WIDTH = 34;
    private static final int NAVIGATION_BUTTON_HEIGHT = 22;

    private final GuideListScreen screen;
    private double descScrollOffset = 0, descTargetScroll = 0, dragDescYOffset = 0;
    private boolean isDraggingDescScrollbar = false;
    private int descContentHeight = 0;

    // 高级导航控件动画状态
    private float leftBtnHover = 0f, rightBtnHover = 0f;
    private final int[] leftBtnRect = new int[]{0, 0, 0, 0};
    private final int[] rightBtnRect = new int[]{0, 0, 0, 0};
    private final int[] lastMediaRect = new int[]{0, 0, 0, 0};

    public GuideContentPanel(GuideListScreen screen) { this.screen = screen; }

    public void resetState() {
        descTargetScroll = 0;
        descScrollOffset = 0;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        GuideDefinition guide = screen.getSelectedGuide();
        if (guide == null) return;

        descScrollOffset = HudAnimUtil.lerp((float) descScrollOffset, (float) descTargetScroll, 0.25f, dt);

        float dAlpha = screen.getEffectiveAlpha();
        int safeA = (int) (255 * dAlpha);
        if (safeA <= 8) return;

        int scrollAreaY = y;
        int scrollAreaH = scrollAreaHeight(guide, h);
        int scrollAreaW = w - 8;
        screen.enableScissor(g, x, scrollAreaY, x + scrollAreaW, scrollAreaY + scrollAreaH);

        g.pose().pushPose();
        g.pose().translate(x + 12, scrollAreaY + 12 - descScrollOffset, 0);

        int localY = 0;
        GuidePageDefinition page = guide.getPage(screen.getSelectedPageIndex());
        String titleStr = guide.getTitle().getString();

        // 大标题渲染
        g.pose().pushPose();
        g.pose().translate(0, localY, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(screen.getFont(), titleStr, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
        g.pose().popPose();

        localY += 18;
        String summary = guide.getSummary().getString();
        if (!summary.isBlank()) {
            float summaryScale = 0.85f;
            int summaryWidth = (int) ((scrollAreaW - 24) / summaryScale);
            List<FormattedCharSequence> summaryLines = screen.getFont().split(
                    guide.getSummary(), Math.max(1, summaryWidth));
            g.pose().pushPose();
            g.pose().translate(0, localY, 0);
            g.pose().scale(summaryScale, summaryScale, 1f);
            for (FormattedCharSequence line : summaryLines) {
                g.drawString(screen.getFont(), line, 0, 0,
                        HudAnimUtil.withAlpha(0xAAB3BD, safeA), false);
                g.pose().translate(0, screen.getFont().lineHeight + 1, 0);
            }
            g.pose().popPose();
            localY += summaryLines.size() * (int) (screen.getFont().lineHeight * summaryScale + 1) + 7;
        }
        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(0x333333, safeA));
        localY += 12;

        lastMediaRect[0] = 0; lastMediaRect[1] = 0; lastMediaRect[2] = 0; lastMediaRect[3] = 0;

        if (screen.getSelectedPageIndex() == 0 && guide.getVisualConfig().shouldRenderLargeIconOnIntro()) {
            int iconSize = GuideConstants.INTRO_ICON_SIZE;
            int iconX = ((scrollAreaW - 24) - iconSize) / 2;
            g.pose().pushPose();
            g.pose().translate(iconX, localY + 2, 0);
            g.pose().scale(GuideConstants.INTRO_ICON_SCALE, GuideConstants.INTRO_ICON_SCALE, 1f);
            g.renderItem(guide.getVisualConfig().getIcon(), 0, 0);
            g.pose().popPose();
            localY += iconSize + 14;
        }

        if (page.getMedia() != null && page.getMedia().getType() != GuideMediaType.NONE) {
            int mediaW = scrollAreaW - 24;
            int mediaH = (int) (mediaW * 9.0f / 16.0f);

            int maxMediaH = (int)(scrollAreaH * 0.55f);
            if (mediaH > maxMediaH) {
                mediaH = maxMediaH;
                mediaW = (int) (mediaH * 16.0f / 9.0f);
            }

            int offsetX = ((scrollAreaW - 24) - mediaW) / 2;
            int absMediaX = x + 12 + offsetX;
            int absMediaY = (int) (scrollAreaY + 12 - descScrollOffset + localY);

            HudAnimUtil.drawFrame(g, offsetX - 1, localY - 1, mediaW + 2, mediaH + 2,
                    HudAnimUtil.withAlpha(0x000000, (int)(safeA * 0.4f)),
                    HudAnimUtil.withAlpha(0x333333, safeA));

            lastMediaRect[0] = absMediaX; lastMediaRect[1] = absMediaY; lastMediaRect[2] = mediaW; lastMediaRect[3] = mediaH;

            float pt = Minecraft.getInstance().getFrameTime();
            g.pose().popPose();

            GuideMediaRenderer.drawMedia(screen, g, absMediaX, absMediaY, mediaW, mediaH, page.getMedia(), screen.ponderPanel(), mx, my, pt, safeA, theme);

            g.pose().pushPose();
            g.pose().translate(x + 12, scrollAreaY + 12 - descScrollOffset, 0);

            localY += mediaH + 16;
        }

        var description = page.getDescriptionText().resolve(null, null);
        if (!description.getString().isEmpty()) {
            float textScale = 0.95f;
            int safeMaxWidth = (int) ((scrollAreaW - 24) / textScale);

            List<FormattedCharSequence> wrappedLines = screen.getFont().split(
                    description, Math.max(1, safeMaxWidth));
            for (FormattedCharSequence line : wrappedLines) {
                g.pose().pushPose();
                g.pose().translate(0, localY, 0);
                g.pose().scale(textScale, textScale, 1f);
                g.drawString(screen.getFont(), line, 0, 0,
                        HudAnimUtil.withAlpha(0xCCCCCC, safeA), false);
                g.pose().popPose();

                localY += (int) (screen.getFont().lineHeight * textScale) + 5;
            }
            localY += 12;
        }

        descContentHeight = localY + 24;
        g.pose().popPose();
        g.disableScissor();

        int maxScroll = Math.max(0, descContentHeight - scrollAreaH);

        if (maxScroll > 0) {
            if (descScrollOffset > 1.0)
                g.fillGradient(x, scrollAreaY, x + scrollAreaW, scrollAreaY + 12, HudAnimUtil.withAlpha(0x000000, (int)(0xCC * dAlpha)), HudAnimUtil.withAlpha(0x000000, 0));
            if (descScrollOffset < maxScroll - 1.0)
                g.fillGradient(x, scrollAreaY + scrollAreaH - 12, x + scrollAreaW, scrollAreaY + scrollAreaH, HudAnimUtil.withAlpha(0x000000, 0), HudAnimUtil.withAlpha(0x000000, (int)(0xCC * dAlpha)));
        }

        clampScroll(scrollAreaH);

        // 优化点：更加右侧对齐，上下增加留白(16px padding)
        int barX = x + w - 6;
        int barY = scrollAreaY + 16;
        int barH = scrollAreaH - 32;
        renderScrollbar(g, barX, barY, barH, descContentHeight, maxScroll, safeA);
        renderPageNavigation(g, guide, x, y, w, h, mx, my, theme, dt, safeA);
    }

    private void renderPageNavigation(GuiGraphics g, GuideDefinition guide,
                                      int x, int y, int w, int h, int mx, int my,
                                      int theme, float dt, int alpha) {
        if (guide.getPageCount() <= 1) {
            clearRect(leftBtnRect);
            clearRect(rightBtnRect);
            leftBtnHover = HudAnimUtil.step(leftBtnHover, 0f, 10f, dt);
            rightBtnHover = HudAnimUtil.step(rightBtnHover, 0f, 10f, dt);
            return;
        }

        int navigationHeight = navigationHeight(guide);
        int navigationY = y + h - navigationHeight;
        int buttonY = navigationY + 6;
        int buttonWidth = NAVIGATION_BUTTON_WIDTH;
        int buttonHeight = NAVIGATION_BUTTON_HEIGHT;
        String pageLabel = "PAGE " + (screen.getSelectedPageIndex() + 1) + " / " + guide.getPageCount();
        int pageWidth = screen.getFont().width(pageLabel);
        int centerX = x + w / 2;
        int leftX = centerX - pageWidth / 2 - 14 - buttonWidth;
        int rightX = centerX + pageWidth / 2 + 14;
        boolean canPrevious = screen.getSelectedPageIndex() > 0;
        boolean canNext = screen.getSelectedPageIndex() + 1 < guide.getPageCount();
        boolean leftHovered = canPrevious && hit(mx, my, leftX, buttonY, buttonWidth, buttonHeight);
        boolean rightHovered = canNext && hit(mx, my, rightX, buttonY, buttonWidth, buttonHeight);
        leftBtnHover = HudAnimUtil.step(leftBtnHover, leftHovered ? 1f : 0f, 10f, dt);
        rightBtnHover = HudAnimUtil.step(rightBtnHover, rightHovered ? 1f : 0f, 10f, dt);

        g.fill(x + 12, navigationY, x + w - 12, navigationY + 1,
                HudAnimUtil.withAlpha(theme, (int) (80 * alpha / 255f)));
        g.fill(leftX, navigationY + 1, rightX + buttonWidth, navigationY + 3,
                HudAnimUtil.withAlpha(theme, (int) (55 * alpha / 255f)));
        g.drawCenteredString(screen.getFont(), pageLabel, centerX,
                buttonY + (buttonHeight - screen.getFont().lineHeight) / 2,
                HudAnimUtil.withAlpha(0xD4DAE1, alpha));

        if (canPrevious) {
            renderNavigationButton(g, "<", leftX, buttonY, buttonWidth, buttonHeight,
                    theme, leftBtnHover, alpha, false);
            setRect(leftBtnRect, leftX, buttonY, buttonWidth, buttonHeight);
        } else {
            clearRect(leftBtnRect);
        }
        if (canNext) {
            renderNavigationButton(g, ">", rightX, buttonY, buttonWidth, buttonHeight,
                    theme, rightBtnHover, alpha, true);
            if (!ClientGuideCache.INSTANCE.isSeen(guide.getId())) {
                HudRenderUtil.drawBreathingRedDot(g, rightX + buttonWidth - 4, buttonY + 4,
                        alpha / 255f);
            }
            setRect(rightBtnRect, rightX, buttonY, buttonWidth, buttonHeight);
        } else {
            clearRect(rightBtnRect);
        }
    }

    private void renderNavigationButton(GuiGraphics g, String arrow,
                                        int x, int y, int width, int height,
                                        int theme, float hover, int alpha,
                                        boolean bandOnRight) {
        float easedHover = HudAnimUtil.easeOutCubic(hover);
        int border = HudAnimUtil.lerpColor(0x7D8792, theme, easedHover);
        int background = HudAnimUtil.lerpColor(0x080C10, theme, easedHover * 0.18f);
        int borderAlpha = (int) ((125 + 105 * easedHover) * alpha / 255f);
        int bandAlpha = (int) ((135 + 100 * easedHover) * alpha / 255f);

        g.fill(x, y, x + width, y + height,
                HudAnimUtil.withAlpha(background, (int) ((150 + 40 * easedHover) * alpha / 255f)));
        g.fill(x, y, x + width, y + 1, HudAnimUtil.withAlpha(border, borderAlpha));
        g.fill(x, y + height - 1, x + width, y + height, HudAnimUtil.withAlpha(border, borderAlpha));
        g.fill(x, y, x + 1, y + height, HudAnimUtil.withAlpha(border, borderAlpha));
        g.fill(x + width - 1, y, x + width, y + height, HudAnimUtil.withAlpha(border, borderAlpha));
        int verticalBandX = bandOnRight ? x + width - 5 : x + 2;
        g.fill(verticalBandX, y + 2, verticalBandX + 3, y + height - 2,
                HudAnimUtil.withAlpha(theme, bandAlpha));
        int bottomBandX1 = bandOnRight ? x + 2 : x + 5;
        int bottomBandX2 = bandOnRight ? x + width - 5 : x + width - 2;
        g.fill(bottomBandX1, y + height - 3, bottomBandX2, y + height - 1,
                HudAnimUtil.withAlpha(theme, (int) (bandAlpha * 0.65f)));

        int arrowColor = HudAnimUtil.lerpColor(0xFFFFFF, theme, easedHover);
        float scale = 1.2f + 0.08f * easedHover;
        g.pose().pushPose();
        g.pose().translate(x + width / 2f + 1f,
                y + (height - screen.getFont().lineHeight * scale) / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(screen.getFont(), arrow, 0, 0, HudAnimUtil.withAlpha(arrowColor, alpha));
        g.pose().popPose();
    }

    private int navigationHeight(GuideDefinition guide) {
        return guide.getPageCount() > 1 ? NAVIGATION_HEIGHT : 0;
    }

    private int scrollAreaHeight(GuideDefinition guide, int panelHeight) {
        return Math.max(32, panelHeight - 4 - navigationHeight(guide));
    }

    private boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void setRect(int[] rect, int x, int y, int width, int height) {
        rect[0] = x;
        rect[1] = y;
        rect[2] = width;
        rect[3] = height;
    }

    private void clearRect(int[] rect) {
        setRect(rect, 0, 0, 0, 0);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int maxScroll, int safeA) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH));
        int thumbY = y + (int) ((descScrollOffset / maxScroll) * (viewH - thumbH));

        float alphaRatio = safeA / 255f;
        // 黑色半透明轨道
        g.fill(x, y, x + 4, y + viewH, HudAnimUtil.withAlpha(0x000000, (int) (40 * alphaRatio)));
        // 白色指示块
        int thumbAlpha = isDraggingDescScrollbar ? 180 : 120;
        g.fill(x, thumbY, x + 4, thumbY + thumbH, HudAnimUtil.withAlpha(0xFFFFFF, (int) (thumbAlpha * alphaRatio)));
    }

    public void clampScroll(int scrollAreaH) { descTargetScroll = Math.max(0, Math.min(descTargetScroll, Math.max(0, descContentHeight - scrollAreaH))); }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        GuideDefinition guide = screen.getSelectedGuide();
        if (guide == null) return false;

        int scrollAreaH = scrollAreaHeight(guide, h);
        int maxDescScroll = Math.max(0, descContentHeight - scrollAreaH);

        // 匹配新滑条的坐标：右侧触发区更精准，高度带有 16px 上下边距
        int barY = y + 16;
        int barH = scrollAreaH - 32;

        if (maxDescScroll > 0 && mx >= x + w - 12 && mx <= x + w && my >= barY && my <= barY + barH) {
            isDraggingDescScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) barH / descContentHeight) * barH));
            int thumbY = barY + (int) ((descScrollOffset / maxDescScroll) * (barH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragDescYOffset = my - thumbY;
            else { dragDescYOffset = thumbH / 2.0; updateScrollFromMouse(my, barY, barH, maxDescScroll); }
            return true;
        }

        if (guide.getPageCount() > 1) {
            if (mx >= leftBtnRect[0] && mx < leftBtnRect[0] + leftBtnRect[2] && my >= leftBtnRect[1] && my < leftBtnRect[1] + leftBtnRect[3]) {
                screen.prevPage(); return true;
            }
            if (mx >= rightBtnRect[0] && mx < rightBtnRect[0] + rightBtnRect[2] && my >= rightBtnRect[1] && my < rightBtnRect[1] + rightBtnRect[3]) {
                screen.nextPage(); return true;
            }
        }

        GuidePageDefinition page = guide.getPage(screen.getSelectedPageIndex());
        if (page != null && page.getMedia() != null && page.getMedia().getType() == GuideMediaType.PONDER) {
            if (lastMediaRect[2] > 0 && screen.ponderPanel().mouseClicked(mx, my, 0, lastMediaRect[0], lastMediaRect[1], lastMediaRect[2], lastMediaRect[3])) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int y, int h) {
        if (isDraggingDescScrollbar) {
            GuideDefinition guide = screen.getSelectedGuide();
            if (guide == null) return false;
            int scrollAreaH = scrollAreaHeight(guide, h);
            int maxDescScroll = Math.max(0, descContentHeight - scrollAreaH);
            int barY = y + 16;
            int barH = scrollAreaH - 32;
            updateScrollFromMouse(my, barY, barH, maxDescScroll);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int button) { if (button == 0) isDraggingDescScrollbar = false; return isDraggingDescScrollbar; }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        GuideDefinition guide = screen.getSelectedGuide();
        if (guide != null && mx >= x && mx <= x + w && my >= y
                && my <= y + scrollAreaHeight(guide, h)) {
            descTargetScroll -= delta * 25.0;
            clampScroll(scrollAreaHeight(guide, h));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuideDefinition guide = screen.getSelectedGuide();
        if (guide == null || guide.getPageCount() <= 1) return false;

        if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT) {
            screen.prevPage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT) {
            screen.nextPage();
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double my, int barY, int barH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) barH / descContentHeight) * barH));
        descTargetScroll = Math.max(0.0, Math.min(1.0, (my - barY - dragDescYOffset) / (barH - thumbH))) * maxScroll;
    }
}
