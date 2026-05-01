package org.arcadia.arc_quest.client.hud.quest.story;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;

public final class QuestStoryPanel {

    // 档案尺寸
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;

    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
    private static boolean active = false;
    private static boolean closing = false;
    private static long lastRenderMs = 0L;
    private static float enterTimer = 0f;
    private static float exitTimer = 0f;

    private static String questId;
    private static String phaseId;
    private static int themeColor = 0x5AD7FF;

    private static float currentScale = 1.0f;
    private static float currentDrawX = 0;
    private static float currentDrawY = 0;

    // 翻页与数据缓存
    private static final List<PageData> pages = new ArrayList<>();
    private static int currentPageIndex = 0;

    // 翻页动画（淡入淡出+滑动）
    private static float flipAnimTimer = 0f;
    private static int flipDirection = 0;
    private static int oldPageIndex = 0;

    // 按钮悬浮动画
    private static float prevHoverAnim = 0f;
    private static float nextHoverAnim = 0f;

    private QuestStoryPanel() {}

    public static void trigger(String qid, String pid) {
        questId = qid;
        phaseId = pid;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(qid, 0x5AD7FF);

        active = true;
        closing = false;
        enterTimer = 0f;
        exitTimer = 0f;
        lastRenderMs = System.currentTimeMillis();

        currentPageIndex = 0;
        oldPageIndex = 0;
        flipAnimTimer = 0f;
        flipDirection = 0;
        prevHoverAnim = 0f;
        nextHoverAnim = 0f;

        buildPages();
    }

    public static boolean isActive() {
        return active;
    }

    public static void close() {
        if (!active || closing) return;
        closing = true;
        exitTimer = 0f;
    }

    public static boolean keyPressed(int keyCode) {
        if (!active || closing) return false;
        // ESC = 256, E = 69
        if (keyCode == 256 || keyCode == 69) {
            close();
            return true;
        }
        // A / 左方向键 翻上一页
        if (keyCode == 263 || keyCode == 65) {
            turnPage(-1);
            return true;
        }
        // D / 右方向键 翻下一页
        if (keyCode == 262 || keyCode == 68) {
            turnPage(1);
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || closing || button != 0) return false;

        float scaledW = PANEL_W * currentScale;
        float scaledH = PANEL_H * currentScale;

        if (mx < currentDrawX || mx > currentDrawX + scaledW || my < currentDrawY || my > currentDrawY + scaledH) {
            close();
            return true;
        }

        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        int btnW = 80, btnH = 16;
        int btnY = PANEL_H - btnH - 12;
        int prevX = 20;
        int nextX = PANEL_W - btnW - 20;

        if (currentPageIndex > 0 && lx >= prevX && lx <= prevX + btnW && ly >= btnY && ly <= btnY + btnH) {
            turnPage(-1);
            return true;
        }
        if (currentPageIndex < pages.size() - 1 && lx >= nextX && lx <= nextX + btnW && ly >= btnY && ly <= btnY + btnH) {
            turnPage(1);
            return true;
        }

        return true;
    }

    private static void turnPage(int dir) {
        if (dir == -1 && currentPageIndex > 0) {
            oldPageIndex = currentPageIndex;
            currentPageIndex--;
            flipDirection = -1;
            flipAnimTimer = 1.0f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.2f));
        } else if (dir == 1 && currentPageIndex < pages.size() - 1) {
            oldPageIndex = currentPageIndex;
            currentPageIndex++;
            flipDirection = 1;
            flipAnimTimer = 1.0f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.2f));
        }
    }

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        float finalScale = (screenH * 0.65f) / (float) PANEL_H;
        float baseX = (screenW / 2f) - ((PANEL_W * finalScale) / 2f);
        float baseY = (screenH / 2f) - ((PANEL_H * finalScale) / 2f);
        float scaleAnim = finalScale;
        float currentX = baseX;
        float currentY = baseY;
        float alphaF = 1.0f;
        float revealProgress = 1.0f;
        float wipeProgress = 0.0f;
        float actualFlyDist = 4.0f * finalScale;

        if (closing) {
            exitTimer += dt;
            if (exitTimer >= EXIT_TIME) {
                active = false;
                closing = false;
                return;
            }
            float t = Math.min(1.0f, exitTimer / EXIT_TIME);
            float easeIn = (float) Math.pow(t, 4.0);
            wipeProgress = easeIn;
            currentX = baseX - (easeIn * actualFlyDist * 1.5f);
            alphaF = 1.0f - (float) Math.pow(t, 8.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME);
            float easeOut = (float) (1.0 - Math.pow(1.0 - t, 5));
            revealProgress = easeOut;
            alphaF = easeOut;
            scaleAnim = finalScale * (1.10f - 0.10f * easeOut);
            currentX = baseX - (1.0f - easeOut) * actualFlyDist * 2f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        float drawWidth = PANEL_W * scaleAnim;
        float drawHeight = PANEL_H * scaleAnim;
        float scaleOffsetW = (drawWidth - PANEL_W * finalScale) / 2f;
        float scaleOffsetH = (drawHeight - PANEL_H * finalScale) / 2f;
        currentDrawX = currentX - scaleOffsetW;
        currentDrawY = currentY - scaleOffsetH;
        currentScale = scaleAnim;

        int scX1 = (int) (currentDrawX - 10);
        int scX2 = (int) (currentDrawX + drawWidth + 10);
        int scY1 = (int) (currentDrawY - 10);
        int scY2 = (int) (currentDrawY + drawHeight + 10);

        if (closing) {
            scX2 = (int) (currentDrawX + drawWidth * (1.0f - wipeProgress));
        } else if (enterTimer < ENTER_TIME) {
            scX2 = (int) (currentDrawX + drawWidth * revealProgress);
        }

        if (flipAnimTimer > 0) {
            flipAnimTimer = Math.max(0f, flipAnimTimer - dt * 3.5f);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);

        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (120 * alphaF)));

        g.enableScissor(scX1, scY1, scX2, scY2);
        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        int alphaInt = Math.max(0, Math.min(255, (int) (255 * alphaF)));
        renderPanel(g, mc.font, alphaInt, alphaF, dt, mx, my);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H;
        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        int cyberEdgeWidth = 3;
        int bgAlpha = (int) (0xCC * alphaF);
        int borderAlpha = (int) (0x66 * alphaF);
        int borderRgb = 0xCCCCCC;

        // 背景
        g.fill(cyberEdgeWidth, 0, PW, PH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(cyberEdgeWidth, 0, PW, 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(cyberEdgeWidth, PH - 1, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(PW - 1, 0, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, themeColor, alpha);

        if (alpha < 5) return;

        // 顶部信息条
        int topBarH = 22;
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // STORY ARCHIVE", 16, 6, HudAnimUtil.withAlpha(0x667788, alpha), false);
        g.pose().popPose();
        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        // ================= 翻页内容渲染 (滑动淡入淡出动画) =================
        int contentY = topBarH + 10;

        if (flipAnimTimer > 0) {
            float animProgress = 1.0f - flipAnimTimer;
            float ease = (float) (1.0 - Math.pow(1.0 - animProgress, 3.0));

            int slideDist = 20; // 滑动距离

            // 渲染旧页面 (淡出)
            if (oldPageIndex >= 0 && oldPageIndex < pages.size()) {
                int oldA = (int) (alpha * (1.0f - ease));
                int oldOffsetX = flipDirection == 1 ? (int)(-slideDist * ease) : (int)(slideDist * ease);
                renderPage(g, font, pages.get(oldPageIndex), contentY, oldA, oldOffsetX);
            }

            // 渲染新页面 (淡入)
            if (currentPageIndex >= 0 && currentPageIndex < pages.size()) {
                int newA = (int) (alpha * ease);
                int newOffsetX = flipDirection == 1 ? (int)(slideDist * (1.0f - ease)) : (int)(-slideDist * (1.0f - ease));
                renderPage(g, font, pages.get(currentPageIndex), contentY, newA, newOffsetX);
            }
        } else {
            // 正常渲染当前页
            if (currentPageIndex >= 0 && currentPageIndex < pages.size()) {
                renderPage(g, font, pages.get(currentPageIndex), contentY, alpha, 0);
            }
        }

        // ================= 底部控制栏 =================
        int btnW = 80, btnH = 16;
        int btnY = PH - btnH - 12;
        int prevX = 20;
        int nextX = PW - btnW - 20;

        boolean hasPrev = currentPageIndex > 0;
        boolean hasNext = currentPageIndex < pages.size() - 1;

        boolean hoverPrev = !closing && hasPrev && lx >= prevX && lx <= prevX + btnW && ly >= btnY && ly <= btnY + btnH;
        boolean hoverNext = !closing && hasNext && lx >= nextX && lx <= nextX + btnW && ly >= btnY && ly <= btnY + btnH;

        prevHoverAnim = HudAnimUtil.step(prevHoverAnim, hoverPrev ? 1f : 0f, 15f, dt);
        nextHoverAnim = HudAnimUtil.step(nextHoverAnim, hoverNext ? 1f : 0f, 15f, dt);

        drawCyberButton(g, font, prevX, btnY, btnW, btnH, "<< PREV", prevHoverAnim, !hasPrev, alpha, alphaF);
        drawCyberButton(g, font, nextX, btnY, btnW, btnH, "NEXT >>", nextHoverAnim, !hasNext, alpha, alphaF);

        String pageStr = "PAGE " + (currentPageIndex + 1) + " / " + Math.max(1, pages.size());
        int pageW = font.width(pageStr);
        g.drawString(font, pageStr, PW / 2 - pageW / 2, btnY + 4, HudAnimUtil.withAlpha(0x778899, alpha), false);
    }

    private static void renderPage(GuiGraphics g, Font font, PageData page, int startY, int alpha, int offsetX) {
        if (alpha <= 4) return;
        int currentY = startY;
        for (RenderLine line : page.lines) {
            g.pose().pushPose();
            // 动画偏移应用在 X 轴
            g.pose().translate(line.x + offsetX, currentY, 0);
            g.pose().scale(line.scale, line.scale, 1f);

            g.drawString(font, line.text, 0, 0, HudAnimUtil.withAlpha(line.color, alpha), false);
            g.pose().popPose();

            currentY += line.lineHeight;
        }
    }

    // ================= 数据解析与排版 =================

    private static void buildPages() {
        pages.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        var def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;

        // 严格扣除左右两边的Padding (左右各20)
        int safeMaxWidth = PANEL_W - 40;
        int maxPageHeight = PANEL_H - 22 - 38;

        PageData currentPage = new PageData();
        int currentYSpace = 0;
        int leftPad = 20;

        // 1. 排版 Title
        float titleScale = 1.35f;
        String titleStr = phase.getDisplayName().getString();
        List<String> titleLines = HudRenderUtil.wrapText(titleStr, (int)((safeMaxWidth) / titleScale), mc.font);
        for (String tLine : titleLines) {
            int lh = (int)Math.ceil(mc.font.lineHeight * titleScale) + 6;
            if (currentYSpace + lh > maxPageHeight && currentYSpace > 0) {
                pages.add(currentPage);
                currentPage = new PageData();
                currentYSpace = 0;
            }
            currentPage.lines.add(new RenderLine(tLine, leftPad, themeColor, titleScale, lh));
            currentYSpace += lh;
        }
        currentYSpace += 12;

        // 2. 排版 Description
        if (phase.hasDescription()) {
            float descScale = 0.95f;
            // 先按照 \n 拆分，防止带有手动换行的文本导致宽度计算错乱
            String[] descParagraphs = phase.getDescription().getString().split("\n");
            for (String para : descParagraphs) {
                if (para.trim().isEmpty()) {
                    currentYSpace += (int)(mc.font.lineHeight * descScale);
                    continue;
                }
                List<String> descLines = HudRenderUtil.wrapText(para, (int)((safeMaxWidth) / descScale), mc.font);
                for (String dLine : descLines) {
                    int lh = (int)Math.ceil(mc.font.lineHeight * descScale) + 5;
                    if (currentYSpace + lh > maxPageHeight && currentYSpace > 0) {
                        pages.add(currentPage);
                        currentPage = new PageData();
                        currentYSpace = 0;
                    }
                    currentPage.lines.add(new RenderLine(dLine, leftPad, 0x99AABB, descScale, lh));
                    currentYSpace += lh;
                }
            }
            currentYSpace += 16;
        }

        // 3. 排版 Story正文
        float storyScale = 1.0f;
        String storyRaw = phase.getStory() != null ? phase.getStory().getString() : "";
        if (!storyRaw.isEmpty()) {
            // 最关键的一步：先强制根据 \n 拆分长文本为多个自然段
            String[] storyParagraphs = storyRaw.split("\n");

            for (String para : storyParagraphs) {
                // 如果是纯空行，则只增加行高，跳过渲染
                if (para.trim().isEmpty()) {
                    currentYSpace += mc.font.lineHeight;
                    continue;
                }

                // 对每一个被拆分出来的自然段进行安全宽度换行
                List<String> storyLines = HudRenderUtil.wrapText(para, (int)(safeMaxWidth / storyScale), mc.font);
                for (String sLine : storyLines) {
                    int lh = mc.font.lineHeight + 6;
                    if (currentYSpace + lh > maxPageHeight && currentYSpace > 0) {
                        pages.add(currentPage);
                        currentPage = new PageData();
                        currentYSpace = 0;
                    }
                    currentPage.lines.add(new RenderLine(sLine, leftPad, 0xE0E0E0, storyScale, lh));
                    currentYSpace += lh;
                }
            }
        }

        if (!currentPage.lines.isEmpty() || pages.isEmpty()) {
            pages.add(currentPage);
        }
    }

    // ================= UI 辅助组件 =================

    private static void drawCyberButton(GuiGraphics g, Font font, int x, int y, int w, int h, String text, float hoverAnim, boolean disabled, int alpha, float alphaF) {
        int currentColor = disabled ? 0x444444 : HudAnimUtil.lerpColor(0x777777, themeColor, hoverAnim);
        int finalBtnBg = HudAnimUtil.withAlpha(0x000000, (int) ((0x44 + 0x44 * hoverAnim) * alphaF));
        int finalBtnBorder = HudAnimUtil.withAlpha(currentColor, disabled ? (int) (100 * alphaF) : alpha);

        g.fill(x, y, x + w, y + h, finalBtnBg);
        g.fill(x, y, x + w, y + 1, finalBtnBorder);
        g.fill(x, y + h - 1, x + w, y + h, finalBtnBorder);
        g.fill(x, y, x + 1, y + h, finalBtnBorder);
        g.fill(x + w - 1, y, x + w, y + h, finalBtnBorder);

        g.pose().pushPose();
        float btnTextScale = disabled ? 0.85f : 0.85f + (0.05f * hoverAnim);
        g.pose().translate(x + w / 2f, y + h / 2f - (font.lineHeight * btnTextScale) / 2f + 1, 0);
        g.pose().scale(btnTextScale, btnTextScale, 1f);
        g.drawCenteredString(font, text, 0, 0, HudAnimUtil.withAlpha(disabled ? 0x888888 : 0xFFFFFF, alpha));
        g.pose().popPose();
    }

    private static class PageData {
        List<RenderLine> lines = new ArrayList<>();
    }

    private static class RenderLine {
        String text;
        int x;
        int color;
        float scale;
        int lineHeight;

        RenderLine(String text, int x, int color, float scale, int lineHeight) {
            this.text = text;
            this.x = x;
            this.color = color;
            this.scale = scale;
            this.lineHeight = lineHeight;
        }
    }
}