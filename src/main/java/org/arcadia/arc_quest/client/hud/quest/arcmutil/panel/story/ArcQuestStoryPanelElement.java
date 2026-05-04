package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.input.ArcCyberButtonElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorUtil;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.text.ArcTextLayoutUtil;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;

public class ArcQuestStoryPanelElement extends ArcGuiElement {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;

    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
    private static final List<ArcTextLayoutUtil.Page> pages = new ArrayList<>();
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

    public ArcQuestStoryPanelElement() {
        super(0, 0, PANEL_W, PANEL_H);
    }
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

        if (currentPageIndex > 0 && ArcCyberButtonElement.containsLocal(lx, ly, 20, btnY, btnW, btnH)) {
            turnPage(-1);
            return true;
        }
        if (currentPageIndex < pages.size() - 1 && ArcCyberButtonElement.containsLocal(lx, ly, PANEL_W - btnW - 20, btnY, btnW, btnH)) {
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

    @Override
    public void draw(GuiGraphics g, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int mx = context.mouseX(), my = context.mouseY();
        int screenW = context.screenWidth(), screenH = context.screenHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        float finalScale = (screenH * 0.65f) / (float) PANEL_H;
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
        ArcDrawUtil.fillFullscreenDim(g, screenW, screenH, (int) (120 * alphaF));

        ArcScissorUtil.enableScreenAware(g, scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        // 此面板全为纯 2D 渲染操作
        renderPanel(g, mc.font, Math.max(0, Math.min(255, (int) (255 * alphaF))), alphaF, dt, mx, my);

        g.pose().popPose();
        ArcScissorUtil.disable(g);
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H, cyberEdgeWidth = 3;
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        ArcPanelChrome.drawQuestPanel(g, 0, 0, PW, PH, 0x000000, (int) (0xCC * alphaF), 0xCCCCCC, (int) (0x66 * alphaF), themeColor, alpha, cyberEdgeWidth);

        if (alpha < 5) return;

        int topBarH = 22;
        g.pose().pushPose();
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // STORY ARCHIVE", 16, 6, HudAnimUtil.withAlpha(0x667788, alpha), false);
        g.pose().popPose();
        ArcPanelChrome.drawTopDivider(g, PW, topBarH, 0xCCCCCC, (int) (0x66 * alphaF));

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
        boolean hoverPrev = !closing && currentPageIndex > 0 && ArcCyberButtonElement.containsLocal(lx, ly, 20, btnY, btnW, btnH);
        boolean hoverNext = !closing && currentPageIndex < pages.size() - 1 && ArcCyberButtonElement.containsLocal(lx, ly, PW - btnW - 20, btnY, btnW, btnH);

        prevHoverAnim = HudAnimUtil.step(prevHoverAnim, hoverPrev ? 1f : 0f, 15f, dt);
        nextHoverAnim = HudAnimUtil.step(nextHoverAnim, hoverNext ? 1f : 0f, 15f, dt);

        ArcCyberButtonElement.renderCyber(g, font, 20, btnY, btnW, btnH, "<< PREV", prevHoverAnim, currentPageIndex <= 0, alpha, alphaF, themeColor);
        ArcCyberButtonElement.renderCyber(g, font, PW - btnW - 20, btnY, btnW, btnH, "NEXT >>", nextHoverAnim, currentPageIndex >= pages.size() - 1, alpha, alphaF, themeColor);

        String pageStr = "PAGE " + (currentPageIndex + 1) + " / " + Math.max(1, pages.size());
        g.drawString(font, pageStr, PW / 2 - font.width(pageStr) / 2, btnY + 4, HudAnimUtil.withAlpha(0x778899, alpha), false);
    }

    private static void renderPage(GuiGraphics g, Font font, ArcTextLayoutUtil.Page page, int startY, int alpha, int offsetX) {
        if (alpha <= 4) return;
        int currentY = startY;
        for (ArcTextLayoutUtil.StyledLine line : page.lines()) {
            g.pose().pushPose();
            g.pose().translate(line.x() + offsetX, currentY, 0);
            g.pose().scale(line.scale(), line.scale(), 1f);
            g.drawString(font, line.text(), 0, 0, HudAnimUtil.withAlpha(line.color(), alpha), false);
            g.pose().popPose();
            currentY += line.lineHeight();
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

        int safeMaxWidth = PANEL_W - 40;
        int maxPageHeight = PANEL_H - 22 - 38;
        pages.addAll(ArcTextLayoutUtil.paginateStory(mc.font, phase.getDisplayName().getString(), phase.hasDescription() ? phase.getDescription() : null, phase.getStory(), safeMaxWidth, maxPageHeight, 20, themeColor));
    }}