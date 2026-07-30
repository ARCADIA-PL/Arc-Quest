// file_name: QuestStoryPanel.java
package org.arcadia.arc_quest.client.hud.quest.story;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;

public final class QuestStoryPanel {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;

    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
    private static final List<PageData> pages = new ArrayList<>();
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
    private static int currentPageIndex = 0;

    private static float flipAnimTimer = 0f;
    private static int flipDirection = 0;
    private static int oldPageIndex = 0;

    private static float prevHoverAnim = 0f;
    private static float nextHoverAnim = 0f;
    private QuestStoryPanel() {
    }

    private static void drawFastFrame(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y + thickness, x + thickness, y + h - thickness, color);
        g.fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color);
    }

    public static void trigger(String qid, String pid) {
        questId = qid;
        phaseId = pid;
        if (ClientQuestCache.INSTANCE.markPhaseStoryRead(qid, pid)) {
            ArcQuestNetwork.markPhaseStoryRead(qid, pid);
        }
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

    public static boolean hasBeenOpened(String qid, String pid) {
        return qid != null && pid != null && ClientQuestCache.INSTANCE.isPhaseStoryRead(qid, pid);
    }

    public static void clearQuest(String qid) {
        if (qid == null || qid.isBlank()) return;
        ClientQuestCache.INSTANCE.clearReadPhaseStories(qid);
        if (qid.equals(questId)) {
            active = false;
            closing = false;
        }
    }

    public static void clearClientSession() {
        active = false;
        closing = false;
    }

    public static void close() {
        if (!active || closing) return;
        closing = true;
        exitTimer = 0f;
    }

    public static boolean keyPressed(int keyCode) {
        if (!active || closing) return false;
        if (keyCode == 256 || keyCode == 69) {
            close();
            return true;
        }
        if (keyCode == 263 || keyCode == 65) {
            turnPage(-1);
            return true;
        }
        if (keyCode == 262 || keyCode == 68) {
            turnPage(1);
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || closing || button != 0) return false;
        float scaledW = PANEL_W * currentScale, scaledH = PANEL_H * currentScale;

        if (mx < currentDrawX || mx > currentDrawX + scaledW || my < currentDrawY || my > currentDrawY + scaledH) {
            close();
            return true;
        }

        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        int btnW = 80, btnH = 16, btnY = PANEL_H - btnH - 12;

        if (currentPageIndex > 0 && lx >= 20 && lx <= 20 + btnW && ly >= btnY && ly <= btnY + btnH) {
            turnPage(-1);
            return true;
        }
        if (currentPageIndex < pages.size() - 1 && lx >= PANEL_W - btnW - 20 && lx <= PANEL_W - 20 && ly >= btnY && ly <= btnY + btnH) {
            turnPage(1);
            return true;
        }
        return true;
    }

    public static boolean mouseDragged(double mx, double my) {
        return active;
    }

    public static boolean mouseReleased(int button) {
        return active;
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

    public static void render(GuiGraphics g, int baseScreenW, int baseScreenH, int mx, int my, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (mc.screen instanceof QuestJournalScreen qjs) {
            screenW = qjs.getScaledWidth();
            screenH = qjs.getScaledHeight();
        }

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        float finalScale = Math.min((screenW * 0.90f) / (float) PANEL_W, (screenH * 0.65f) / (float) PANEL_H);
        float baseX = (screenW / 2f) - ((PANEL_W * finalScale) / 2f), baseY = (screenH / 2f) - ((PANEL_H * finalScale) / 2f);
        float scaleAnim = finalScale, currentX = baseX, currentY = baseY, alphaF = 1.0f, revealProgress = 1.0f, wipeProgress = 0.0f;

        if (closing) {
            exitTimer += dt;
            if (exitTimer >= EXIT_TIME) {
                active = false;
                closing = false;
                return;
            }
            float t = Math.min(1.0f, exitTimer / EXIT_TIME);
            wipeProgress = (float) Math.pow(t, 4.0);
            currentX = baseX - (wipeProgress * 4.0f * finalScale * 1.5f);
            alphaF = 1.0f - (float) Math.pow(t, 8.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME);
            revealProgress = (float) (1.0 - Math.pow(1.0 - t, 5));
            alphaF = revealProgress;
            scaleAnim = finalScale * (1.10f - 0.10f * revealProgress);
            currentX = baseX - (1.0f - revealProgress) * 4.0f * finalScale * 2f;
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        float drawWidth = PANEL_W * scaleAnim, drawHeight = PANEL_H * scaleAnim;
        currentDrawX = currentX - (drawWidth - PANEL_W * finalScale) / 2f;
        currentDrawY = currentY - (drawHeight - PANEL_H * finalScale) / 2f;
        currentScale = scaleAnim;

        int scX1 = (int) (currentDrawX - 10), scX2 = (int) (currentDrawX + drawWidth + 10);
        if (closing) scX2 = (int) (currentDrawX + drawWidth * (1.0f - wipeProgress));
        else if (enterTimer < ENTER_TIME) scX2 = (int) (currentDrawX + drawWidth * revealProgress);

        if (flipAnimTimer > 0) flipAnimTimer = Math.max(0f, flipAnimTimer - dt * 3.5f);

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);
        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (120 * alphaF)));

        if (mc.screen instanceof QuestJournalScreen qjs) {
            qjs.enableScissor(g, scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        } else {
            g.enableScissor(scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        }

        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        renderPanel(g, mc.font, Math.max(0, Math.min(255, (int) (255 * alphaF))), alphaF, dt, mx, my);

        g.pose().popPose();

        if (mc.screen instanceof QuestJournalScreen qjs) {
            g.disableScissor();
        } else {
            g.disableScissor();
        }
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H, cyberEdgeWidth = 3;
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);

        g.fill(cyberEdgeWidth, 0, PW, PH, HudAnimUtil.withAlpha(0x000000, (int) (0xCC * alphaF)));
        drawFastFrame(g, cyberEdgeWidth, 0, PW - cyberEdgeWidth, PH, 1, HudAnimUtil.withAlpha(0xCCCCCC, (int) (0x66 * alphaF)));
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, themeColor, alpha);

        if (alpha < 5) return;

        int topBarH = 22;
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, Component.translatable("arc_quest.gui.quest_story.header").getString(), 16, 6, HudAnimUtil.withAlpha(0x667788, alpha), false);
        g.pose().popPose();
        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(0xCCCCCC, (int) (0x66 * alphaF)));

        int contentY = topBarH + 10;

        if (flipAnimTimer > 0) {
            float ease = (float) (1.0 - Math.pow(flipAnimTimer, 3.0));
            int slideDist = 20;

            if (oldPageIndex >= 0 && oldPageIndex < pages.size()) {
                renderPage(g, font, pages.get(oldPageIndex), contentY, (int) (alpha * (1.0f - ease)), flipDirection == 1 ? (int) (-slideDist * ease) : (int) (slideDist * ease));
            }
            if (currentPageIndex >= 0 && currentPageIndex < pages.size()) {
                renderPage(g, font, pages.get(currentPageIndex), contentY, (int) (alpha * ease), flipDirection == 1 ? (int) (slideDist * (1.0f - ease)) : (int) (-slideDist * (1.0f - ease)));
            }
        } else if (currentPageIndex >= 0 && currentPageIndex < pages.size()) {
            renderPage(g, font, pages.get(currentPageIndex), contentY, alpha, 0);
        }

        int btnW = 80, btnH = 16, btnY = PH - btnH - 12;
        boolean hoverPrev = !closing && currentPageIndex > 0 && lx >= 20 && lx <= 20 + btnW && ly >= btnY && ly <= btnY + btnH;
        boolean hoverNext = !closing && currentPageIndex < pages.size() - 1 && lx >= PW - btnW - 20 && lx <= PW - 20 && ly >= btnY && ly <= btnY + btnH;

        prevHoverAnim = HudAnimUtil.step(prevHoverAnim, hoverPrev ? 1f : 0f, 15f, dt);
        nextHoverAnim = HudAnimUtil.step(nextHoverAnim, hoverNext ? 1f : 0f, 15f, dt);

        drawCyberButton(g, font, 20, btnY, btnW, btnH, Component.translatable("arc_quest.gui.quest_story.prev").getString(), prevHoverAnim, currentPageIndex <= 0, alpha, alphaF);
        drawCyberButton(g, font, PW - btnW - 20, btnY, btnW, btnH, Component.translatable("arc_quest.gui.quest_story.next").getString(), nextHoverAnim, currentPageIndex >= pages.size() - 1, alpha, alphaF);

        String pageStr = Component.translatable("arc_quest.gui.quest_story.page", currentPageIndex + 1, Math.max(1, pages.size())).getString();
        g.drawString(font, pageStr, PW / 2 - font.width(pageStr) / 2, btnY + 4, HudAnimUtil.withAlpha(0x778899, alpha), false);
    }

    private static void renderPage(GuiGraphics g, Font font, PageData page, int startY, int alpha, int offsetX) {
        if (alpha <= 4) return;
        int currentY = startY;
        for (RenderLine line : page.lines) {
            g.pose().pushPose();
            g.pose().translate(line.x + offsetX, currentY, 0);
            g.pose().scale(line.scale, line.scale, 1f);
            g.drawString(font, line.text, 0, 0, HudAnimUtil.withAlpha(line.color, alpha), false);
            g.pose().popPose();
            currentY += line.lineHeight;
        }
    }

    private static void buildPages() {
        pages.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        var def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) return;

        int safeMaxWidth = PANEL_W - 40, maxPageHeight = PANEL_H - 22 - 38, currentYSpace = 0, leftPad = 20;
        PageData currentPage = new PageData();

        for (FormattedCharSequence tLine : mc.font.split(phase.getDisplayName(), (int) (safeMaxWidth / 1.35f))) {
            int lh = (int) Math.ceil(mc.font.lineHeight * 1.35f) + 6;
            if (currentYSpace + lh > maxPageHeight && currentYSpace > 0) {
                pages.add(currentPage);
                currentPage = new PageData();
                currentYSpace = 0;
            }
            currentPage.lines.add(new RenderLine(tLine, leftPad, themeColor, 1.35f, lh));
            currentYSpace += lh;
        }
        currentYSpace += 12;

        if (phase.hasDescription()) {
            for (String para : phase.getDescription().getString().split("\n")) {
                if (para.trim().isEmpty()) {
                    currentYSpace += (int) (mc.font.lineHeight * 0.95f);
                    continue;
                }
                for (String dLine : HudRenderUtil.wrapText(para, (int) (safeMaxWidth / 0.95f), mc.font)) {
                    int lh = (int) Math.ceil(mc.font.lineHeight * 0.95f) + 5;
                    if (currentYSpace + lh > maxPageHeight && currentYSpace > 0) {
                        pages.add(currentPage);
                        currentPage = new PageData();
                        currentYSpace = 0;
                    }
                    currentPage.lines.add(new RenderLine(dLine, leftPad, 0x99AABB, 0.95f, lh));
                    currentYSpace += lh;
                }
            }
            currentYSpace += 16;
        }

        String storyRaw = phase.getStory() != null ? phase.getStory().getString() : "";
        if (!storyRaw.isEmpty()) {
            for (String para : storyRaw.split("\n")) {
                if (para.trim().isEmpty()) {
                    currentYSpace += mc.font.lineHeight;
                    continue;
                }
                for (String sLine : HudRenderUtil.wrapText(para, safeMaxWidth, mc.font)) {
                    int lh = mc.font.lineHeight + 6;
                    if (currentYSpace + lh > maxPageHeight && currentYSpace > 0) {
                        pages.add(currentPage);
                        currentPage = new PageData();
                        currentYSpace = 0;
                    }
                    currentPage.lines.add(new RenderLine(sLine, leftPad, 0xE0E0E0, 1.0f, lh));
                    currentYSpace += lh;
                }
            }
        }
        if (!currentPage.lines.isEmpty() || pages.isEmpty()) pages.add(currentPage);
    }

    private static void drawCyberButton(GuiGraphics g, Font font, int x, int y, int w, int h, String text, float hoverAnim, boolean disabled, int alpha, float alphaF) {
        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x000000, (int) ((0x44 + 0x44 * hoverAnim) * alphaF)));
        drawFastFrame(g, x, y, w, h, 1, HudAnimUtil.withAlpha(disabled ? 0x444444 : HudAnimUtil.lerpColor(0x777777, themeColor, hoverAnim), disabled ? (int) (100 * alphaF) : alpha));

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
        FormattedCharSequence text;
        int x, color, lineHeight;
        float scale;

        RenderLine(String t, int x, int c, float s, int lh) {
            this(Component.literal(t).getVisualOrderText(), x, c, s, lh);
        }

        RenderLine(FormattedCharSequence t, int x, int c, float s, int lh) {
            text = t;
            this.x = x;
            color = c;
            scale = s;
            lineHeight = lh;
        }
    }

}
