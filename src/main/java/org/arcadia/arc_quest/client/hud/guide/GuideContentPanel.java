// file_name: GuideContentPanel.java
package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class GuideContentPanel {
    private final GuideListScreen screen;
    private double descScrollOffset = 0, descTargetScroll = 0, dragDescYOffset = 0;
    private boolean isDraggingDescScrollbar = false;
    private int descContentHeight = 0;
    private float detailReveal = 0f;

    // 高级导航控件动画状态
    private float leftBtnHover = 0f, rightBtnHover = 0f;
    private final int[] leftBtnRect = new int[]{0, 0, 0, 0};
    private final int[] rightBtnRect = new int[]{0, 0, 0, 0};
    private final int[] lastMediaRect = new int[]{0, 0, 0, 0};

    public GuideContentPanel(GuideListScreen screen) { this.screen = screen; }

    public void resetState() {
        detailReveal = 0f;
        descTargetScroll = 0;
        descScrollOffset = 0;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        GuideDefinition guide = screen.getSelectedGuide();
        if (guide == null) return;

        detailReveal = HudAnimUtil.lerp(detailReveal, 1f, 0.15f, dt);
        descScrollOffset = HudAnimUtil.lerp((float) descScrollOffset, (float) descTargetScroll, 0.25f, dt);

        float dAlpha = screen.getEffectiveAlpha() * HudAnimUtil.easeOutCubic(Math.min(1f, detailReveal));
        int safeA = (int) (255 * dAlpha);
        if (safeA <= 8) return;

        int scrollAreaY = y, scrollAreaH = h - 4, scrollAreaW = w - 8;
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

        // 参照 ParallelPhase 的游标滑轨分页设计
        if (guide.getPageCount() > 1) {
            int btnW = 12, trackGap = 6, absTrackW = 60;
            int totalCtrlW = btnW * 2 + trackGap * 2 + absTrackW;
            int ctrlX = scrollAreaW - 12 - totalCtrlW;
            int ctrlY = localY + 2;

            int absLeftX = x + 12 + ctrlX;
            int absCtrlY = (int) (scrollAreaY + 12 - descScrollOffset + ctrlY);

            boolean lHover = mx >= absLeftX && mx < absLeftX + btnW && my >= absCtrlY - 2 && my < absCtrlY + 8 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
            leftBtnHover = HudAnimUtil.step(leftBtnHover, lHover ? 1f : 0f, 10f, dt);
            int leftColor = HudAnimUtil.lerpColor(0x777777, theme, leftBtnHover);
            g.drawString(screen.getFont(), "<", ctrlX, ctrlY - 2, HudAnimUtil.withAlpha(leftColor, safeA), false);
            leftBtnRect[0] = absLeftX; leftBtnRect[1] = absCtrlY - 2; leftBtnRect[2] = btnW; leftBtnRect[3] = 10;

            int absRightX = x + 12 + ctrlX + btnW + trackGap * 2 + absTrackW;
            boolean rHover = mx >= absRightX && mx < absRightX + btnW && my >= absCtrlY - 2 && my < absCtrlY + 8 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
            rightBtnHover = HudAnimUtil.step(rightBtnHover, rHover ? 1f : 0f, 10f, dt);
            int rightColor = HudAnimUtil.lerpColor(0x777777, theme, rightBtnHover);
            g.drawString(screen.getFont(), ">", ctrlX + btnW + trackGap * 2 + absTrackW, ctrlY - 2, HudAnimUtil.withAlpha(rightColor, safeA), false);
            rightBtnRect[0] = absRightX; rightBtnRect[1] = absCtrlY - 2; rightBtnRect[2] = btnW; rightBtnRect[3] = 10;

            int trackX = ctrlX + btnW + trackGap;
            g.fill(trackX, ctrlY + 2, trackX + absTrackW, ctrlY + 3, HudAnimUtil.withAlpha(0xFFFFFF, (int) (25 * dAlpha)));
            g.fill(trackX, ctrlY + 1, trackX + 1, ctrlY + 4, HudAnimUtil.withAlpha(0xFFFFFF, (int) (50 * dAlpha)));
            g.fill(trackX + absTrackW - 1, ctrlY + 1, trackX + absTrackW, ctrlY + 4, HudAnimUtil.withAlpha(0xFFFFFF, (int) (50 * dAlpha)));

            int maxPageIdx = guide.getPageCount() - 1;
            int thumbW = Math.max(8, (int) (((float) 1 / guide.getPageCount()) * absTrackW));
            int thumbX = trackX + (int) (((float) screen.getSelectedPageIndex() / maxPageIdx) * (absTrackW - thumbW));

            g.fill(thumbX, ctrlY + 2, thumbX + thumbW, ctrlY + 3, HudAnimUtil.withAlpha(theme, safeA));
            int centerX = thumbX + thumbW / 2;
            g.fill(centerX, ctrlY + 1, centerX + 1, ctrlY + 4, HudAnimUtil.withAlpha(0xFFFFFF, safeA));
            g.fill(centerX - 2, ctrlY + 2, centerX, ctrlY + 3, HudAnimUtil.withAlpha(theme, safeA));
            g.fill(centerX + 1, ctrlY + 2, centerX + 3, ctrlY + 3, HudAnimUtil.withAlpha(theme, safeA));
        }

        localY += 20;
        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(0x333333, safeA));
        localY += 12;

        lastMediaRect[0] = 0; lastMediaRect[1] = 0; lastMediaRect[2] = 0; lastMediaRect[3] = 0;

        if (page.getMedia() != null && page.getMedia().getType() != null) {
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

            float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            g.pose().popPose();

            GuideMediaRenderer.drawMedia(screen, g, absMediaX, absMediaY, mediaW, mediaH, page.getMedia(), screen.ponderPanel(), mx, my, pt, safeA, theme);

            g.pose().pushPose();
            g.pose().translate(x + 12, scrollAreaY + 12 - descScrollOffset, 0);

            localY += mediaH + 16;
        }

        String rawDesc = page.getDescriptionText().resolve(null, null).getString();
        if (!rawDesc.isEmpty()) {
            float textScale = 0.95f;
            int safeMaxWidth = (int) ((scrollAreaW - 24) / textScale);

            for (String para : rawDesc.split("\n")) {
                if (para.trim().isEmpty()) {
                    localY += (int) (screen.getFont().lineHeight * textScale);
                    continue;
                }
                List<String> wrappedLines = HudRenderUtil.wrapText(para, safeMaxWidth, screen.getFont());
                for (String dLine : wrappedLines) {
                    g.pose().pushPose();
                    g.pose().translate(0, localY, 0);
                    g.pose().scale(textScale, textScale, 1f);
                    g.drawString(screen.getFont(), dLine, 0, 0, HudAnimUtil.withAlpha(0xCCCCCC, safeA), false);
                    g.pose().popPose();

                    localY += (int) (screen.getFont().lineHeight * textScale) + 5;
                }
            }
            localY += 12;
        }

        descContentHeight = localY;
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

        int scrollAreaH = h - 4, maxDescScroll = Math.max(0, descContentHeight - scrollAreaH);

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
            int scrollAreaH = h - 4;
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
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) { descTargetScroll -= delta * 25.0; clampScroll(h - 4); return true; }
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