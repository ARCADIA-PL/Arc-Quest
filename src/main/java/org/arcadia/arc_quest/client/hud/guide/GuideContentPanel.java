package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
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

    public GuideContentPanel(GuideListScreen screen) {
        this.screen = screen;
    }

    public void resetState() {
        detailReveal = 0f;
        descTargetScroll = 0;
        descScrollOffset = 0;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        GuideDefinition guide = screen.getSelectedGuide();
        if (guide == null) {
            if (screen.getEffectiveAlpha() > 0.05f)
                g.drawCenteredString(screen.getFont(), "SELECT A GUIDE", x + w / 2, y + h / 2, HudAnimUtil.withAlpha(0x666666, (int) (120 * screen.getEffectiveAlpha())));
            return;
        }

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
        int maxTitleDrawW = scrollAreaW - 24 - 100;
        String displayTitle = titleStr;
        if (screen.getFont().width(titleStr) * 1.2f > maxTitleDrawW) {
            displayTitle = screen.getFont().plainSubstrByWidth(titleStr, (int)(maxTitleDrawW / 1.2f) - 10) + "...";
        }

        g.pose().pushPose();
        g.pose().translate(0, localY, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(screen.getFont(), displayTitle, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
        g.pose().popPose();

        if (guide.getPageCount() > 1) {
            String pageStr = "PAGE " + (screen.getSelectedPageIndex() + 1) + " / " + guide.getPageCount();
            int pageStrW = screen.getFont().width(pageStr);

            int rightEdge = scrollAreaW - 24;
            int btnW = 46, btnH = 14;
            int nextBtnX = rightEdge - btnW;
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

            GuideNavigationControls.drawCyberButton(g, screen.getFont(), prevBtnX, localY + 1, btnW, btnH,
                    "< PREV", theme, dAlpha,
                    HudAnimUtil.easeOutCubic(prevHoverAnim), hPrev && canPrev);
            GuideNavigationControls.drawCyberButton(g, screen.getFont(), nextBtnX, localY + 1, btnW, btnH,
                    "NEXT >", theme, dAlpha,
                    HudAnimUtil.easeOutCubic(nextHoverAnim), hNext && canNext);

            prevRect[0] = absPrevX; prevRect[1] = absY; prevRect[2] = btnW; prevRect[3] = btnH;
            nextRect[0] = absNextX; nextRect[1] = absY; nextRect[2] = btnW; nextRect[3] = btnH;
        }

        localY += 20;

        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(theme, (int) (120 * dAlpha)));
        localY += 12;

        if (page.getMedia() != null && page.getMedia().getType() != null) {
            int mediaH = Math.min(160, (int)((scrollAreaW - 24) * 0.45f));

            HudAnimUtil.drawFrame(g, -1, localY - 1, scrollAreaW - 24 + 2, mediaH + 2,
                    HudAnimUtil.withAlpha(0x000000, (int)(safeA * 0.4f)),
                    HudAnimUtil.withAlpha(theme, (int)(safeA * 0.3f)));

            float pt = Minecraft.getInstance().getFrameTime();
            GuideMediaRenderer.drawMedia(screen, g, 0, localY, scrollAreaW - 24, mediaH, page.getMedia(), screen.ponderPanel(),
                    mx, my, pt, safeA, theme);
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

    public void clampScroll(int scrollAreaH) {
        descTargetScroll = Math.max(0, Math.min(descTargetScroll, Math.max(0, descContentHeight - scrollAreaH)));
    }

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
            if (mx >= prevRect[0] && mx <= prevRect[0] + prevRect[2] && my >= prevRect[1] && my <= prevRect[1] + prevRect[3]) {
                screen.prevPage(); return true;
            }
            if (mx >= nextRect[0] && mx <= nextRect[0] + nextRect[2] && my >= nextRect[1] && my <= nextRect[1] + nextRect[3]) {
                screen.nextPage(); return true;
            }
        }

        GuidePageDefinition page = guide.getPage(screen.getSelectedPageIndex());
        if (page != null && page.getMedia() != null && page.getMedia().getType() == org.arcadia.arc_quest.guide.api.GuideMediaType.PONDER) {
            int mediaH = Math.min(160, (int)((w - 32) * 0.45f));
            int mediaX = x + 12;
            int mediaY = (int)(y + 12 - descScrollOffset + 32);
            if (screen.ponderPanel().mouseClicked(mx, my, 0, mediaX, mediaY, w - 32, mediaH)) return true;
        }

        return false;
    }

    public boolean mouseDragged(double mx, double my, int y, int h) {
        if (isDraggingDescScrollbar) {
            updateScrollFromMouse(my, y, h - 4, Math.max(0, descContentHeight - (h - 4)));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 0) isDraggingDescScrollbar = false;
        return isDraggingDescScrollbar;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            descTargetScroll -= delta * 25.0;
            clampScroll(h - 4);
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / descContentHeight) * viewH));
        descTargetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragDescYOffset) / (viewH - thumbH))) * maxScroll;
    }
}
