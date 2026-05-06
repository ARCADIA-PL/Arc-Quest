// file_name: CollectionHistoryPanel.java
package org.arcadia.arc_quest.client.hud.quest.history;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class CollectionHistoryPanel {

    private static final int PANEL_W = 630;
    private static final int PANEL_H = 270;
    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;

    private static boolean active = false;
    private static boolean closing = false;
    private static String questId;
    private static int themeColor = 0x5AD7FF;

    private static long lastRenderMs = 0L;
    private static float enterTimer = 0f;
    private static float exitTimer = 0f;

    private static float currentScale = 1.0f;
    private static float currentDrawX = 0;
    private static float currentDrawY = 0;

    // 滚动系统
    private static float scroll = 0f;
    private static float targetScroll = 0f;
    private static float maxScroll = 0f;
    private static boolean panning = false;
    private static double lastDragY = 0;

    // 数据缓存
    private static final List<CategoryData> categories = new ArrayList<>();
    private static String hoveredPhaseId = null;
    private static int claimableCount = 0;

    private CollectionHistoryPanel() {
    }

    private static void drawFastFrame(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y + thickness, x + thickness, y + h - thickness, color);
        g.fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color);
    }

    public static void trigger(String qid) {
        questId = qid;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(qid, 0x5AD7FF);
        active = true;
        closing = false;
        enterTimer = 0f;
        exitTimer = 0f;
        scroll = 0f;
        targetScroll = 0f;
        maxScroll = 0f;
        panning = false;
        hoveredPhaseId = null;
        lastRenderMs = System.currentTimeMillis();

        buildData();
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
        return false;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || closing) return false;
        float scaledW = PANEL_W * currentScale, scaledH = PANEL_H * currentScale;
        if (mx < currentDrawX || mx > currentDrawX + scaledW || my < currentDrawY || my > currentDrawY + scaledH) {
            close();
            return true;
        }

        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        int listY = 28, listH = PANEL_H - 32;

        if (ly >= listY && ly <= listY + listH) {
            if (button == 0) {
                panning = true;
                lastDragY = ly;
            }
        }
        return true;
    }

    public static boolean mouseDragged(double mx, double my) {
        if (!active || closing || !panning) return false;
        if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            panning = false;
            return true;
        }
        float ly = (float) ((my - currentDrawY) / currentScale);
        targetScroll -= (ly - lastDragY);
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
        lastDragY = ly;
        return true;
    }

    public static boolean mouseReleased(int button) {
        if (button == 0) panning = false;
        return active;
    }

    public static boolean mouseScrolled(double mx, double my, double delta) {
        if (!active || closing) return false;
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        if (lx >= 4 && lx <= PANEL_W - 4 && ly >= 28 && ly <= PANEL_H - 4) {
            targetScroll -= (float) delta * 30f;
            targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
        }
        return true;
    }

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();

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
            currentX = baseX + (wipeProgress * 4.0f * finalScale * 1.5f); // 向右退出
            alphaF = 1.0f - (float) Math.pow(t, 8.0);
        } else {
            enterTimer = Math.min(ENTER_TIME, enterTimer + dt);
            float t = Math.min(1.0f, enterTimer / ENTER_TIME);
            revealProgress = (float) (1.0 - Math.pow(1.0 - t, 5));
            alphaF = revealProgress;
            scaleAnim = finalScale * (1.10f - 0.10f * revealProgress);
            currentX = baseX + (1.0f - revealProgress) * 4.0f * finalScale * 2f; // 从右滑入
        }

        if (revealProgress <= 0.001f || wipeProgress >= 0.999f) return;

        float drawWidth = PANEL_W * scaleAnim, drawHeight = PANEL_H * scaleAnim;
        currentDrawX = currentX - (drawWidth - PANEL_W * finalScale) / 2f;
        currentDrawY = currentY - (drawHeight - PANEL_H * finalScale) / 2f;
        currentScale = scaleAnim;

        int scX1 = (int) (currentDrawX - 10), scX2 = (int) (currentDrawX + drawWidth + 10);
        if (closing) scX1 = (int) (currentDrawX + drawWidth * wipeProgress);
        else if (enterTimer < ENTER_TIME) scX1 = (int) (currentDrawX + drawWidth * (1.0f - revealProgress));

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);
        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (35 * alphaF)));

        if (mc.screen instanceof QuestJournalScreen qjs)
            qjs.enableScissor(g, scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        else g.enableScissor(scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));

        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        renderPanel(g, mc.font, Math.max(0, Math.min(255, (int) (255 * alphaF))), alphaF, dt, mx, my);

        g.pose().popPose();
        if (mc.screen instanceof QuestJournalScreen qjs) g.disableScissor();
        g.pose().popPose();
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H;
        float lx = (float) ((mx - currentDrawX) / currentScale), ly = (float) ((my - currentDrawY) / currentScale);
        int borderAlpha = (int) (0x66 * alphaF), borderRgb = 0xCCCCCC;

        // 1. 底层背景与边框
        g.fill(3, 0, PW, PH, HudAnimUtil.withAlpha(0x05060A, (int) (0xEE * alphaF)));
        drawFastFrame(g, 3, 0, PW - 3, PH, 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, themeColor, alpha);

        // 2. 顶部导航条
        int topBarH = 22;
        g.pose().pushPose();
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, "SYS.ARC_QUEST // COLLECTION ARCHIVE DATA", 16, 6, HudAnimUtil.withAlpha(0x667788, alpha), false);
        g.pose().popPose();

        if (claimableCount > 0) {
            String claimText = "CLAIMABLE REWARDS: " + claimableCount;
            g.pose().pushPose();
            g.pose().scale(0.85f, 0.85f, 1f);
            g.drawString(font, claimText, (int)((PW - 10) / 0.85f) - font.width(claimText), 6, HudAnimUtil.withAlpha(0xFFD166, alpha), true);
            g.pose().popPose();
        }

        g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        // 3. 装饰性网格点阵
        for (int i = 15; i < PW; i += 30)
            g.fill(i, topBarH, i + 1, PH - 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (3 * alphaF)));
        for (int i = topBarH + 15; i < PH; i += 30)
            g.fill(1, i, PW - 1, i + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (3 * alphaF)));

        // 4. 内容区滚动与 Scissor
        scroll += (targetScroll - scroll) * Math.min(1f, dt * 20f);
        int listX = 12, listY = topBarH + 6, listW = PW - 24, listH = PH - topBarH - 12;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestJournalScreen qjs) {
            qjs.enableScissor(g, (int) (currentDrawX + listX * currentScale), (int) (currentDrawY + listY * currentScale),
                    (int) (currentDrawX + (listX + listW) * currentScale), (int) (currentDrawY + (listY + listH) * currentScale));
        }

        hoveredPhaseId = null;
        int currentY = listY - (int) scroll;
        long time = Util.getMillis();

        // 5. 渲染分类与卡片
        int cardW = 195;
        int cardH = 40;
        int spacing = 6;
        int cols = listW / (cardW + spacing);
        if (cols == 0) cols = 1;

        for (CategoryData cat : categories) {
            if (currentY + 20 > listY && currentY < listY + listH) {
                g.drawString(font, "// " + cat.name.toUpperCase(), listX, currentY + 4, HudAnimUtil.withAlpha(themeColor, alpha), false);
                g.fill(listX + font.width("// " + cat.name) + 10, currentY + 9, listX + listW, currentY + 10, HudAnimUtil.withAlpha(0x445566, (int)(100 * alphaF)));
            }
            currentY += 20;

            int col = 0;
            int rowStartY = currentY;
            for (EntryData entry : cat.entries) {
                if (col >= cols) {
                    col = 0;
                    currentY += cardH + spacing;
                    rowStartY = currentY;
                }

                int cx = listX + col * (cardW + spacing);
                int cy = rowStartY;

                if (cy + cardH > listY && cy < listY + listH) {
                    boolean isHovered = (lx >= cx && lx <= cx + cardW && ly >= cy && ly <= cy + cardH);
                    if (isHovered && ly >= listY && ly <= listY + listH) hoveredPhaseId = entry.phaseId;

                    int cardBg = isHovered ? HudAnimUtil.withAlpha(0x1A222B, alpha) : HudAnimUtil.withAlpha(0x0A0D12, alpha);
                    int borderColor = entry.completed ? themeColor : (isHovered ? 0x8899AA : 0x334455);
                    int borderA = (int) ((entry.completed ? (150 + 50 * Math.sin(time / 400.0)) : 100) * alphaF);

                    // 卡片背景与边框
                    g.fill(cx, cy, cx + cardW, cy + cardH, cardBg);
                    drawFastFrame(g, cx, cy, cardW, cardH, 1, HudAnimUtil.withAlpha(borderColor, borderA));

                    // 进度条背景
                    g.fill(cx + 2, cy + cardH - 4, cx + cardW - 2, cy + cardH - 2, HudAnimUtil.withAlpha(0x223344, alpha));

                    // 进度条填充
                    float progress = Math.min(1f, (float) entry.count / Math.max(1, entry.target));
                    int fillW = (int) ((cardW - 4) * progress);
                    if (fillW > 0) {
                        int fillColor = entry.completed ? 0x66FF88 : themeColor;
                        g.fill(cx + 2, cy + cardH - 4, cx + 2 + fillW, cy + cardH - 2, HudAnimUtil.withAlpha(fillColor, alpha));
                    }

                    // 文字
                    int textColor = entry.completed ? 0xFFFFFF : (entry.count > 0 ? 0xDDDDDD : 0x667788);
                    g.pose().pushPose();
                    g.pose().translate(cx + 6, cy + 6, 0);
                    g.pose().scale(0.85f, 0.85f, 1f);
                    g.drawString(font, font.plainSubstrByWidth(entry.name, (int)((cardW - 40) / 0.85f)), 0, 0, HudAnimUtil.withAlpha(textColor, alpha), false);
                    g.pose().popPose();

                    // 数字比例
                    String countStr = entry.count + "/" + entry.target;
                    int countColor = entry.completed ? 0x66FF88 : 0x8899AA;
                    g.pose().pushPose();
                    g.pose().translate(cx + cardW - 4 - font.width(countStr) * 0.75f, cy + 18, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, countStr, 0, 0, HudAnimUtil.withAlpha(countColor, alpha), false);
                    g.pose().popPose();

                    // 状态角标
                    if (entry.completed) {
                        g.fill(cx + cardW - 6, cy + 2, cx + cardW - 2, cy + 6, HudAnimUtil.withAlpha(themeColor, alpha));
                    }
                }
                col++;
            }
            if (col > 0) currentY += cardH + spacing;
            currentY += 8; // 分类底部留白
        }

        if (mc.screen instanceof QuestJournalScreen qjs) g.disableScissor();

        // 6. 极简滚动条计算
        int totalContentH = currentY - (listY - (int)scroll);
        maxScroll = Math.max(0, totalContentH - listH);

        if (maxScroll > 0) {
            int thumbH = Math.max(20, (int) (((float) listH / totalContentH) * listH));
            int thumbY = listY + (int) ((scroll / maxScroll) * (listH - thumbH));
            g.fill(PW - 5, listY, PW - 4, listY + listH, HudAnimUtil.withAlpha(0x000000, (int)(50 * alphaF)));
            g.fill(PW - 5, thumbY, PW - 4, thumbY + thumbH, HudAnimUtil.withAlpha(themeColor, (int)(150 * alphaF)));
        }
    }

    private static void buildData() {
        categories.clear();
        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (def == null) return;

        claimableCount = ClientQuestCache.INSTANCE.getCollectionClaimableRewardCount(questId);
        CollectionQuestConfig config = def.getCollectionConfig();

        if (config != null && config.isShowCategories()) {
            for (CollectionCategoryDefinition catDef : config.getCategories()) {
                String catName = catDef.getDisplayNameText().resolve(null, null).getString();
                if (catName == null || catName.isEmpty()) catName = catDef.getCategoryId();

                CategoryData category = new CategoryData(catName);
                for (String phaseId : def.getPhaseIds()) {
                    PhaseDefinition phase = def.getPhase(phaseId);
                    if (phase == null || !phase.hasCollectionEntryConfig()) continue;

                    CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
                    if (!catDef.getCategoryId().equals(entryConfig.getCategoryId())) continue;

                    category.entries.add(createEntry(phaseId, phase, entryConfig, runtime));
                }
                if (!category.entries.isEmpty()) categories.add(category);
            }
        } else {
            CategoryData defaultCat = new CategoryData("ALL ASSETS");
            for (String phaseId : def.getPhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null || !phase.hasCollectionEntryConfig()) continue;
                defaultCat.entries.add(createEntry(phaseId, phase, phase.getCollectionEntryConfig(), runtime));
            }
            if (!defaultCat.entries.isEmpty()) categories.add(defaultCat);
        }
    }

    private static EntryData createEntry(String phaseId, PhaseDefinition phase, CollectionEntryConfig config, QuestRuntimeData runtime) {
        int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, phaseId);
        int target = Math.max(1, config.getCompletionTarget());
        boolean completed = runtime != null && runtime.isPhaseCompleted(phaseId);
        String name = phase.getDisplayName().getString();
        if (name == null || name.isEmpty()) name = phaseId;
        return new EntryData(phaseId, name, count, target, completed);
    }

    private static class CategoryData {
        String name;
        List<EntryData> entries = new ArrayList<>();
        CategoryData(String name) { this.name = name; }
    }

    private record EntryData(String phaseId, String name, int count, int target, boolean completed) {}
}