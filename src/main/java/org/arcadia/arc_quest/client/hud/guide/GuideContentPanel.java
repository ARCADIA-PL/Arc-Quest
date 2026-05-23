// file_name: GuideContentPanel.java
package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;

import java.util.List;

public class GuideContentPanel {
    private final GuideListScreen screen;
    private double descScrollOffset = 0, descTargetScroll = 0, dragDescYOffset = 0;
    private boolean isDraggingDescScrollbar = false;
    private int descContentHeight = 0;
    private float detailReveal = 0f;
    private float prevHoverAnim = 0f, nextHoverAnim = 0f;

    private int[] prevRect = new int[]{0, 0, 0, 0};
    private int[] nextRect = new int[]{0, 0, 0, 0};
    private int[] lastMediaRect = new int[]{0, 0, 0, 0};

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
        float dAlpha = screen.getEffectiveAlpha() * HudAnimUtil.easeOutCubic(Math.min(1f, detailReveal));
        int safeA = (int) (255 * dAlpha);
        if (safeA <= 8) return;

        int scrollAreaY = y, scrollAreaH = h - 4, scrollAreaW = w - 8;
        screen.enableScissor(g, x, scrollAreaY, x + w - 8, scrollAreaY + scrollAreaH);

        g.pose().pushPose();
        g.pose().translate(x + 12, scrollAreaY + 12 - descScrollOffset, 0);

        int localY = 0;
        GuidePageDefinition page = guide.getPage(screen.getSelectedPageIndex());
        String titleStr = guide.getTitle().getString();

        g.pose().pushPose();
        g.pose().translate(0, localY, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(screen.getFont(), titleStr, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
        g.pose().popPose();

        if (guide.getPageCount() > 1) {
            String pageStr = "PAGE " + (screen.getSelectedPageIndex() + 1) + " / " + guide.getPageCount();
            int pageStrW = screen.getFont().width(pageStr);
            int btnW = 46, btnH = 14;
            int nextBtnX = scrollAreaW - 24 - btnW;
            int textX = nextBtnX - 6 - pageStrW;
            int prevBtnX = textX - btnW - 6;

            g.drawString(screen.getFont(), pageStr, textX, localY + 2, HudAnimUtil.withAlpha(0x888888, safeA), false);

            int absPrevX = x + 12 + prevBtnX, absNextX = x + 12 + nextBtnX;
            int absY = (int) (scrollAreaY + 12 - descScrollOffset + localY + 1);

            boolean canPrev = screen.getSelectedPageIndex() > 0;
            boolean canNext = screen.getSelectedPageIndex() < guide.getPageCount() - 1;
            boolean hPrev = canPrev && mx >= absPrevX && mx <= absPrevX + btnW && my >= absY && my <= absY + btnH;
            boolean hNext = canNext && mx >= absNextX && mx <= absNextX + btnW && my >= absY && my <= absY + btnH;

            prevHoverAnim = HudAnimUtil.step(prevHoverAnim, hPrev ? 1f : 0f, 15f, dt);
            nextHoverAnim = HudAnimUtil.step(nextHoverAnim, hNext ? 1f : 0f, 15f, dt);

            GuideNavigationControls.drawCyberButton(g, screen.getFont(), prevBtnX, localY + 1, btnW, btnH, "< PREV", theme, dAlpha, HudAnimUtil.easeOutCubic(prevHoverAnim), hPrev && canPrev);
            GuideNavigationControls.drawCyberButton(g, screen.getFont(), nextBtnX, localY + 1, btnW, btnH, "NEXT >", theme, dAlpha, HudAnimUtil.easeOutCubic(nextHoverAnim), hNext && canNext);

            prevRect[0] = absPrevX; prevRect[1] = absY; prevRect[2] = btnW; prevRect[3] = btnH;
            nextRect[0] = absNextX; nextRect[1] = absY; nextRect[2] = btnW; nextRect[3] = btnH;
        }

        localY += 20;
        // 修复：分割线移除主题色，改为极简灰
        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(0x333333, safeA));
        localY += 12;

        lastMediaRect[0] = 0; lastMediaRect[1] = 0; lastMediaRect[2] = 0; lastMediaRect[3] = 0;

        if (page.getMedia() != null && page.getMedia().getType() != null) {
            // 修复：严格限制媒体高度，不能超过可用高度的45%，且绝对高度不超过130px
            int mediaH = Math.min(130, (int)(scrollAreaH * 0.45f));

            // 修复：移除媒体边框的主题色侵入，改为硬核深灰色
            HudAnimUtil.drawFrame(g, -1, localY - 1, scrollAreaW - 24 + 2, mediaH + 2,
                    HudAnimUtil.withAlpha(0x000000, (int)(safeA * 0.4f)),
                    HudAnimUtil.withAlpha(0x333333, safeA));

            int absMediaX = x + 12;
            int absMediaY = (int) (scrollAreaY + 12 - descScrollOffset + localY);
            lastMediaRect[0] = absMediaX; lastMediaRect[1] = absMediaY; lastMediaRect[2] = scrollAreaW - 24; lastMediaRect[3] = mediaH;

            float pt = Minecraft.getInstance().getFrameTime();
            g.pose().popPose();

            GuideMediaRenderer.drawMedia(screen, g, absMediaX, absMediaY, scrollAreaW - 24, mediaH, page.getMedia(), screen.ponderPanel(), mx, my, pt, safeA, theme);

            g.pose().pushPose();
            g.pose().translate(x + 12, scrollAreaY + 12 - descScrollOffset, 0);

            localY += mediaH + 16;
        }

        String rawDesc = page.getDescriptionText().resolve(null, null).getString();
        if (!rawDesc.isEmpty()) {
            List<FormattedCharSequence> lines = screen.getFont().split(page.getDescriptionText().resolve(null, null), scrollAreaW - 24);
            for (FormattedCharSequence line : lines) {
                g.drawString(screen.getFont(), line, 0, localY, HudAnimUtil.withAlpha(0xAAAAAA, safeA));
                localY += screen.getFont().lineHeight + 2;
            }
            localY += 8;
        }

        descContentHeight = localY;
        g.pose().popPose();
        g.disableScissor();

        clampScroll(scrollAreaH);
        renderScrollbar(g, x + w - 6, scrollAreaY + 2, scrollAreaH - 4, descContentHeight, Math.max(0, descContentHeight - scrollAreaH));
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH)), thumbY = y + (int) ((descScrollOffset / maxScroll) * (viewH - thumbH));
        g.fill(x, y, x + 4, y + viewH, HudAnimUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isDraggingDescScrollbar ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    public void clampScroll(int scrollAreaH) { descTargetScroll = Math.max(0, Math.min(descTargetScroll, Math.max(0, descContentHeight - scrollAreaH))); }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        GuideDefinition guide = screen.getSelectedGuide();
        if (guide == null) return false;

        int scrollAreaH = h - 4, maxDescScroll = Math.max(0, descContentHeight - scrollAreaH);
        if (maxDescScroll > 0 && mx >= x + w - 6 && mx <= x + w && my >= y && my <= y + scrollAreaH) {
            isDraggingDescScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) scrollAreaH / descContentHeight) * scrollAreaH)), thumbY = y + (int) ((descScrollOffset / maxDescScroll) * (scrollAreaH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragDescYOffset = my - thumbY;
            else { dragDescYOffset = thumbH / 2.0; updateScrollFromMouse(my, y, scrollAreaH, maxDescScroll); }
            return true;
        }

        if (guide.getPageCount() > 1) {
            if (mx >= prevRect[0] && mx <= prevRect[0] + prevRect[2] && my >= prevRect[1] && my <= prevRect[1] + prevRect[3]) { screen.prevPage(); return true; }
            if (mx >= nextRect[0] && mx <= nextRect[0] + nextRect[2] && my >= nextRect[1] && my <= nextRect[1] + nextRect[3]) { screen.nextPage(); return true; }
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
        if (isDraggingDescScrollbar) { updateScrollFromMouse(my, y, h - 4, Math.max(0, descContentHeight - (h - 4))); return true; }
        return false;
    }

    public boolean mouseReleased(int button) { if (button == 0) isDraggingDescScrollbar = false; return isDraggingDescScrollbar; }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) { descTargetScroll -= delta * 25.0; clampScroll(h - 4); return true; }
        return false;
    }

    private void updateScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / descContentHeight) * viewH));
        descTargetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragDescYOffset) / (viewH - thumbH))) * maxScroll;
    }
}