package org.com.arc_quest.client.gui.quest.journal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.gui.QuestHudOverlay;
import org.com.arc_quest.client.gui.render.QuestIconRenderer;
import org.com.arc_quest.client.gui.render.QuestIntelPanel;
import org.com.arc_quest.quest.api.*;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.com.arc_quest.quest.reward.ItemReward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JournalDetailPanel {
    private final QuestJournalScreen screen;

    private double detailScrollOffset = 0;
    private double detailTargetScroll = 0;
    private boolean isDraggingDetailScrollbar = false;
    private double dragDetailYOffset = 0;
    private int detailContentHeight = 0;

    private float detailReveal = 0f;
    private float trackBtnHover = 0f;
    private float abandonBtnHover = 0f;
    private float failedRestartBtnHover = 0f;
    private float chapterShopBtnHover = 0f;
    private float intelBtnHoverAnim = 0f;

    private final Map<String, Float> phaseCardReveal = new HashMap<>();
    private final Map<String, Float> phaseCardHoverAnims = new HashMap<>();

    private final Map<String, Double> phaseObjScrollOffsets = new HashMap<>();
    private final Map<String, Double> phaseObjTargetScrolls = new HashMap<>();
    private record ObjScrollArea(int absX, int absY, int w, int h, String phaseId, int maxScroll) {}
    private final List<ObjScrollArea> objScrollAreas = new ArrayList<>();

    private String selectedPhaseId = null;
    private final List<JournalTypes.ChoiceButtonRect> currentChoiceButtons = new ArrayList<>();
    private final List<JournalTypes.PhaseTagRect> currentPhaseTags = new ArrayList<>();

    private ResourceLocation intelSceneId = null;
    private int intelBtnLocalY = 0;
    private long lastChoiceClickAt = 0L;

    public JournalDetailPanel(QuestJournalScreen screen) {
        this.screen = screen;
    }

    public void resetState() {
        detailReveal = 0f;
        detailTargetScroll = 0;
        detailScrollOffset = 0;
        currentChoiceButtons.clear();
        currentPhaseTags.clear();
        selectedPhaseId = null;
        phaseCardReveal.clear();
        phaseCardHoverAnims.clear();
        phaseObjScrollOffsets.clear();
        phaseObjTargetScrolls.clear();
        objScrollAreas.clear();
    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        clampScroll(h - 40);
        detailScrollOffset += Math.abs(detailTargetScroll - detailScrollOffset) > 0.5 ? (detailTargetScroll - detailScrollOffset) * Math.min(1.0, dt * 14.0) : (detailTargetScroll - detailScrollOffset);

        int selectedIndex = screen.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= screen.getCurrentEntries().size()) {
            renderEmptyDetail(g, x, y, w, h);
            return;
        }

        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(selectedIndex);
        QuestDefinition def = entry.def();
        if (def == null) return;

        int activeTheme = ClientQuestCache.INSTANCE.getQuestThemeColor(entry.questId(), theme);
        screen.setCurrentThemeColor(activeTheme);

        detailReveal = HudAnimUtil.lerp(detailReveal, 1f, 0.15f, dt);
        float dAlpha = screen.getEffectiveAlpha() * HudAnimUtil.easeOutCubic(Math.min(1f, detailReveal));
        int safeA = (int) (255 * dAlpha);
        if (safeA <= 8) return;

        int scrollAreaY = y;
        int scrollAreaH = h - 40;
        int scrollAreaW = w - 8;
        Font font = screen.getFont();

        objScrollAreas.clear();

        g.enableScissor(x, scrollAreaY, x + w - 8, scrollAreaY + scrollAreaH);

        def.getSplashConfig(SplashType.QUEST_DETAIL).ifPresent(asset -> {
            RenderSystem.enableBlend();
            float watermarkAlpha = 0.15f * dAlpha;
            int rw = (int) (w * 0.7f);
            int rh = rw;
            int rx = x + w / 2 - rw / 2 + (int) ((1f - detailReveal) * 50f);
            int ry = scrollAreaY + scrollAreaH / 2 - rh / 2;

            RenderSystem.setShaderColor(1f, 1f, 1f, watermarkAlpha);
            QuestIconRenderer.renderIcon(g, asset, rx, ry, rw, rh);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        });

        g.pose().pushPose();
        g.pose().translate(x + 12, scrollAreaY + 12 - detailScrollOffset, 0);

        int localY = 0;
        int titleIconOffset = 0;

        if (def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).isPresent()) {
            int finalLocalY = localY;
            def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).ifPresent(icon -> {
                RenderSystem.setShaderColor(1f, 1f, 1f, dAlpha);
                QuestIconRenderer.renderIcon(g, icon, 0, finalLocalY - 2, 16, 16);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            });
            titleIconOffset = 22;
        }

        g.pose().pushPose();
        g.pose().translate(titleIconOffset, localY, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(font, def.getDisplayName().getString(), 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
        g.pose().popPose();
        localY += 18;

        if (!def.getDescription().getString().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, localY, 0);
            g.pose().scale(0.85f, 0.85f, 1f);
            List<String> descLines = HudRenderUtil.wrapText(def.getDescription().getString(), (int) ((scrollAreaW - 24) / 0.85f), font);
            for (String line : descLines) {
                g.drawString(font, line, 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), false);
                g.pose().translate(0, font.lineHeight + 1, 0);
            }
            g.pose().popPose();
            localY += descLines.size() * (int) (font.lineHeight * 0.85f + 1) + 8;
        }

        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (120 * dAlpha)));
        localY += 10;

        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());

        if (entry.state() == QuestState.ACTIVE && runtime != null) {
            currentChoiceButtons.clear();
            currentPhaseTags.clear();

            List<String> activePhaseIds = new ArrayList<>();
            for (String pid : def.getPhaseIds()) if (runtime.isPhaseActive(pid)) activePhaseIds.add(pid);
            if (activePhaseIds.isEmpty()) activePhaseIds.addAll(runtime.getActivePhaseIds());

            if (activePhaseIds.isEmpty()) {
                g.drawString(font, "No active phase.", 0, localY, HudAnimUtil.withAlpha(0x888888, safeA), false);
                localY += 16;
            } else {
                g.pose().pushPose();
                g.pose().translate(0, localY, 0);
                g.pose().scale(0.8f, 0.8f, 1f);
                g.drawString(font, "ACTIVE LANES", 0, 0, HudAnimUtil.withAlpha(activeTheme, safeA), true);
                g.pose().popPose();
                localY += 14;

                int cardAreaW = scrollAreaW - 24;
                int gap = 8;
                int colW = (cardAreaW - gap) / 2;

                int MAX_VISIBLE_OBJS = 2;
                int OBJ_LINE_H = 14;
                int FIXED_OBJ_VIEW_H = MAX_VISIBLE_OBJS * OBJ_LINE_H;

                Map<String, Integer> rowAssignedHeights = new HashMap<>();

                for (int i = 0; i < activePhaseIds.size(); i += 2) {
                    String p1 = activePhaseIds.get(i);
                    PhaseDefinition phase1 = def.getPhase(p1);
                    int c1 = getChoicesHeight(phase1, def, runtime, p1);
                    int h1 = 22 + 12 + 4 + FIXED_OBJ_VIEW_H + c1 + (c1 > 0 ? 4 : 0);

                    int h2 = 0;
                    if (i + 1 < activePhaseIds.size()) {
                        String p2 = activePhaseIds.get(i + 1);
                        PhaseDefinition phase2 = def.getPhase(p2);
                        int c2 = getChoicesHeight(phase2, def, runtime, p2);
                        h2 = 22 + 12 + 4 + FIXED_OBJ_VIEW_H + c2 + (c2 > 0 ? 4 : 0);
                    }

                    int maxH = Math.max(h1, h2);
                    rowAssignedHeights.put(p1, maxH);
                    if (i + 1 < activePhaseIds.size()) {
                        rowAssignedHeights.put(activePhaseIds.get(i + 1), maxH);
                    }
                }

                int currentY = localY;

                for (int idx = 0; idx < activePhaseIds.size(); idx++) {
                    int col = idx % 2;
                    int rawCardX = col * (colW + gap);
                    int rawCardY = currentY;

                    String phaseId = activePhaseIds.get(idx);
                    PhaseDefinition phase = def.getPhase(phaseId);
                    if (phase == null) continue;

                    int cardH = rowAssignedHeights.get(phaseId);
                    int objViewH = FIXED_OBJ_VIEW_H;

                    float reveal = phaseCardReveal.getOrDefault(phaseId, 0f);
                    reveal = HudAnimUtil.lerp(reveal, 1f, 0.12f + (col * 0.02f), dt);
                    phaseCardReveal.put(phaseId, reveal);

                    float cardEase = HudAnimUtil.easeOutCubic(Math.min(1f, reveal));
                    int cardSlideX = (int) ((1f - cardEase) * 18f);
                    int cardSlideY = (int) ((1f - cardEase) * 10f);
                    float cardAlphaMul = cardEase;

                    int cardX = rawCardX + cardSlideX;
                    int cardY = rawCardY + cardSlideY;
                    int cardSafeA = (int) (safeA * cardAlphaMul);

                    int done = 0;
                    int total = phase.getObjectives().size();
                    for (int i = 0; i < total; i++) {
                        ObjectiveEntry objective = phase.getObjectives().get(i);
                        int required = Math.max(1, objective.getRequiredCount());
                        int p = runtime.getObjectiveProgress(phaseId, i);
                        if (p >= required) done++;
                    }

                    boolean selected = phaseId.equals(resolveSelectedPhaseId(def, runtime));
                    boolean phaseDone = isPhaseObjectivesDone(runtime, phase, phaseId);

                    float powerFactor = (selected && !phaseDone) ? 1.0f : 0.35f;

                    List<ChoiceOption> visibleChoices = new ArrayList<>();
                    if (shouldShowBranchChoices(def, runtime, phaseId)) {
                        for (ChoiceOption choice : phase.getChoices()) {
                            boolean isVisible = choice.getVisibleCondition() == null || choice.getVisibleCondition().testClient(
                                    ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(),
                                    ClientQuestCache.INSTANCE.getAllFlags(),
                                    ClientQuestCache.INSTANCE.getAllVariables()
                            );
                            if (isVisible) visibleChoices.add(choice);
                        }
                    }

                    int objContentH = total * OBJ_LINE_H;
                    int maxInnerScroll = Math.max(0, objContentH - objViewH);

                    int absCardX = x + 12 + cardX;
                    int absCardY = (int) Math.round(scrollAreaY + 12 - detailScrollOffset + cardY);
                    boolean cardHovered = mx >= absCardX && mx <= absCardX + colW
                            && my >= absCardY && my <= absCardY + cardH
                            && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;

                    // == 优化点1：滚轮判定区扩大到整张卡片 ==
                    if (maxInnerScroll > 0) {
                        objScrollAreas.add(new ObjScrollArea(absCardX, absCardY, colW, cardH, phaseId, maxInnerScroll));
                    }

                    float hoverAnim = phaseCardHoverAnims.getOrDefault(phaseId, 0f);
                    hoverAnim = HudAnimUtil.lerp(hoverAnim, cardHovered ? 1f : 0f, 0.2f, dt);
                    phaseCardHoverAnims.put(phaseId, hoverAnim);
                    float hoverEase = HudAnimUtil.easeOutCubic(hoverAnim);

                    int contentShiftX = 0;
                    int cyberEdgeWidth = 3;

                    int baseBgAlpha = 0x44;
                    int hoverBgAlphaOffset = (int) (0x22 * hoverEase);
                    int bgAlpha = (int) ((baseBgAlpha + (selected ? 0x11 : hoverBgAlphaOffset)) * dAlpha * cardAlphaMul);

                    int staticBorderColor = 0xFFFFFF;
                    int staticBorderAlpha = (int) ((0x1A + 0x22 * hoverEase) * dAlpha * cardAlphaMul);

                    int finalEdgeColor;
                    int edgeAlpha;
                    if (selected) {
                        finalEdgeColor = activeTheme;
                        edgeAlpha = (int) (255 * dAlpha * cardAlphaMul);
                    } else {
                        finalEdgeColor = HudAnimUtil.lerpColor(0x555555, 0xDDDDDD, hoverEase);
                        edgeAlpha = (int) ((100 + 100 * hoverEase) * powerFactor * dAlpha * cardAlphaMul);
                    }

                    g.fill(cardX + cyberEdgeWidth, cardY, cardX + colW, cardY + cardH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
                    g.fill(cardX + cyberEdgeWidth, cardY, cardX + colW, cardY + 1, HudAnimUtil.withAlpha(staticBorderColor, staticBorderAlpha));
                    g.fill(cardX + cyberEdgeWidth, cardY + cardH - 1, cardX + colW, cardY + cardH, HudAnimUtil.withAlpha(staticBorderColor, staticBorderAlpha));
                    g.fill(cardX + colW - 1, cardY, cardX + colW, cardY + cardH, HudAnimUtil.withAlpha(staticBorderColor, staticBorderAlpha));
                    HudRenderUtil.drawCyberneticEdge(g, cardX, cardY, cardH, finalEdgeColor, edgeAlpha);

                    currentPhaseTags.add(new JournalTypes.PhaseTagRect(absCardX, absCardY, colW, cardH, phaseId));

                    String phaseName = phase.getDisplayName() != null && !phase.getDisplayName().getString().isEmpty() ? phase.getDisplayName().getString() : phase.getPhaseId();
                    phaseName = font.plainSubstrByWidth(phaseName, colW - 60);
                    g.drawString(font, phaseName, cardX + 8 + contentShiftX, cardY + 6, HudAnimUtil.withAlpha(selected ? 0xFFFFFF : 0xDDDDDD, cardSafeA), true);

                    String statusLabel = phaseDone ? "COMPLETED" : (selected ? "TRACKING" : "STANDBY");
                    int statusColor = phaseDone ? 0x66FF66 : (selected ? activeTheme : 0x777777);
                    g.pose().pushPose();
                    g.pose().translate(cardX + colW - 6 - font.width(statusLabel)*0.7f, cardY + 8, 0);
                    g.pose().scale(0.7f, 0.7f, 1f);
                    g.drawString(font, statusLabel, 0, 0, HudAnimUtil.withAlpha(statusColor, cardSafeA), false);
                    g.pose().popPose();

                    int cy = cardY + 22;

                    int laneBarW = colW - 16;
                    int barX = cardX + 8 + contentShiftX;

                    int baseThemeColor = phaseDone ? 0x66FF66 : activeTheme;
                    int dimmedThemeColor = HudAnimUtil.lerpColor(0x000000, baseThemeColor, powerFactor);
                    int emptyBgColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * powerFactor * dAlpha * cardAlphaMul));
                    int fillColor = HudAnimUtil.withAlpha(dimmedThemeColor, (int) (0xCC * powerFactor * dAlpha * cardAlphaMul));
                    int brightColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * powerFactor * dAlpha * cardAlphaMul));

                    // == 优化点3：段落化分体多目标进度条引擎 ==
                    if (total <= 1) {
                        float singleRatio = 0f;
                        if (total == 1) {
                            ObjectiveEntry ob = phase.getObjectives().get(0);
                            int req = Math.max(1, ob.getRequiredCount());
                            int p = Math.max(0, Math.min(runtime.getObjectiveProgress(phaseId, 0), req));
                            singleRatio = (float) p / req;
                        }
                        int laneFillW = (int) (laneBarW * singleRatio);
                        g.fill(barX, cy, barX + laneBarW, cy + 2, emptyBgColor);
                        if (laneFillW > 0) {
                            g.fill(barX, cy, barX + laneFillW, cy + 2, fillColor);
                            g.fill(barX + laneFillW - 2, cy - 1, barX + laneFillW, cy + 3, brightColor);
                        }
                    } else {
                        int gapX = 2;
                        int totalGaps = total - 1;
                        float segW = (float)(laneBarW - totalGaps * gapX) / total;
                        float cx = barX;
                        for (int i = 0; i < total; i++) {
                            ObjectiveEntry objective = phase.getObjectives().get(i);
                            int required = Math.max(1, objective.getRequiredCount());
                            int p = runtime.getObjectiveProgress(phaseId, i);
                            int clamped = Math.max(0, Math.min(p, required));
                            float ratio = (float) clamped / required;
                            int sFill = (int)(segW * ratio);

                            g.fill((int)cx, cy, (int)(cx + segW), cy + 2, emptyBgColor);
                            if (sFill > 0) {
                                g.fill((int)cx, cy, (int)(cx + sFill), cy + 2, fillColor);
                                if (ratio >= 1.0f) {
                                    g.fill((int)(cx + segW) - 2, cy - 1, (int)(cx + segW), cy + 3, brightColor);
                                } else {
                                    g.fill((int)(cx + sFill) - 2, cy - 1, (int)(cx + sFill), cy + 3, brightColor);
                                }
                            }
                            cx += segW + gapX;
                        }
                    }
                    // ========================================

                    cy += 12;

                    double currentInnerScroll = phaseObjScrollOffsets.getOrDefault(phaseId, 0.0);
                    double targetInnerScroll = phaseObjTargetScrolls.getOrDefault(phaseId, 0.0);
                    currentInnerScroll += (targetInnerScroll - currentInnerScroll) * Math.min(1.0, dt * 15.0);
                    phaseObjScrollOffsets.put(phaseId, currentInnerScroll);

                    int sX = x + 12 + cardX;
                    int sY = (int) Math.round(scrollAreaY + 12 - detailScrollOffset + cy);
                    int sW = colW;
                    int sH = objViewH;
                    int intX1 = Math.max(x, sX);
                    int intY1 = Math.max(scrollAreaY, sY);
                    int intX2 = Math.min(x + w - 8, sX + sW);
                    int intY2 = Math.min(scrollAreaY + scrollAreaH, sY + sH);

                    if (intX2 > intX1 && intY2 > intY1 && total > 0) {
                        g.disableScissor();
                        g.enableScissor(intX1, intY1, intX2, intY2);

                        g.pose().pushPose();
                        g.pose().translate(0, -currentInnerScroll, 0);

                        int objY = cy;
                        for (int i = 0; i < total; i++) {
                            ObjectiveEntry obj = phase.getObjectives().get(i);
                            int progress = runtime.getObjectiveProgress(phaseId, i);
                            int required = obj.getRequiredCount();
                            boolean complete = progress >= required;

                            int extraMargin = maxInnerScroll > 0 ? 8 : 0;
                            String pr = progress + "/" + required;
                            int prWidth = font.width(pr);

                            int availableWidth = colW - 16 - contentShiftX - extraMargin - prWidth - 6;

                            String line = (complete ? "§a✔ " : "§7○ ") + obj.getDisplayText().getString();
                            line = font.plainSubstrByWidth(line, Math.max(5, availableWidth));

                            int objColor = complete ? 0x88FF88 : 0xCCCCCC;
                            int dimmedObjColor = HudAnimUtil.lerpColor(0x000000, objColor, Math.max(0.6f, powerFactor));

                            g.drawString(font, line, cardX + 8 + contentShiftX, objY, HudAnimUtil.withAlpha(dimmedObjColor, cardSafeA), false);
                            g.drawString(font, pr, cardX + colW - 8 - extraMargin - prWidth, objY, HudAnimUtil.withAlpha(0x888888, cardSafeA), false);

                            objY += OBJ_LINE_H;
                        }

                        g.pose().popPose();

                        g.disableScissor();
                        g.enableScissor(x, scrollAreaY, x + w - 8, scrollAreaY + scrollAreaH);
                    }

                    if (maxInnerScroll > 0) {
                        int gradientW = colW - 8; // 优化点2：略微收窄避开滚动条
                        if (currentInnerScroll > 1.0) {
                            g.fillGradient(cardX + cyberEdgeWidth, cy, cardX + gradientW, cy + 6,
                                    HudAnimUtil.withAlpha(0x000000, (int)(0xAA * dAlpha * cardAlphaMul)),
                                    HudAnimUtil.withAlpha(0x000000, 0));
                        }
                        if (currentInnerScroll < maxInnerScroll - 1.0) {
                            // 优化点2：底部阴影严格贴合视口底部
                            g.fillGradient(cardX + cyberEdgeWidth, cy + objViewH - 6, cardX + gradientW, cy + objViewH,
                                    HudAnimUtil.withAlpha(0x000000, 0),
                                    HudAnimUtil.withAlpha(0x000000, (int)(0xAA * dAlpha * cardAlphaMul)));
                        }

                        int trackX = cardX + colW - 6;
                        int trackY = cy;
                        g.fill(trackX, trackY, trackX + 2, trackY + objViewH, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x11 * dAlpha * cardAlphaMul)));

                        int thumbH = Math.max(8, (int)(((float)objViewH / objContentH) * objViewH));
                        int thumbY = trackY + (int)((currentInnerScroll / maxInnerScroll) * (objViewH - thumbH));
                        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, HudAnimUtil.withAlpha(activeTheme, (int)(0xAA * dAlpha * cardAlphaMul)));
                    }

                    cy += objViewH + 4;

                    if (phase.hasChoices() && !shouldShowBranchChoices(def, runtime, phaseId)) {
                        g.drawString(font, "Choices locked", cardX + 8 + contentShiftX, cy, HudAnimUtil.withAlpha(0x888888, cardSafeA), false);
                        cy += 14;
                    }

                    if (!visibleChoices.isEmpty()) {
                        cy += 4;
                        for (int i = 0; i < visibleChoices.size(); i++) {
                            ChoiceOption choice = visibleChoices.get(i);
                            int btnX = cardX + 8 + contentShiftX, btnY = cy, btnW = colW - 16, btnH = 20;
                            int absBtnX = x + 12 + btnX, absBtnY = (int) Math.round(scrollAreaY + 12 - detailScrollOffset + btnY);

                            boolean btnHover = mx >= absBtnX && mx <= absBtnX + btnW && my >= absBtnY && my <= absBtnY + btnH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;

                            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((btnHover ? 0x22 : 0x12) * dAlpha * cardAlphaMul)));
                            g.fill(btnX, btnY, btnX + btnW, btnY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardAlphaMul)));
                            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardAlphaMul)));
                            g.fill(btnX, btnY, btnX + 1, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardAlphaMul)));
                            g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardAlphaMul)));

                            String cText = font.plainSubstrByWidth((i + 1) + ". " + choice.getDisplayText().getString(), btnW - 12);
                            g.drawString(font, cText, btnX + 6, btnY + 6, HudAnimUtil.withAlpha(btnHover ? activeTheme : 0xDDDDDD, cardSafeA), false);

                            currentChoiceButtons.add(new JournalTypes.ChoiceButtonRect(absBtnX, absBtnY, btnW, btnH, phase.getChoices().indexOf(choice), phaseId));
                            cy += 24;
                        }
                    }

                    if (col == 1 || idx == activePhaseIds.size() - 1) {
                        currentY += cardH + gap;
                    }
                }

                localY = currentY + 6;

                g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (80 * dAlpha)));
                localY += 10;

                g.pose().pushPose();
                g.pose().translate(0, localY, 0);
                g.pose().scale(0.8f, 0.8f, 1f);
                g.drawString(font, Component.translatable("arc_quest.gui.journal.section.completed_phases").getString(), 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), true);
                g.pose().popPose();
                localY += 14;

                int completedCount = 0;
                for (String phaseId : runtime.getCompletedPhaseIds()) {
                    String completedPhaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(entry.questId(), phaseId);
                    g.pose().pushPose(); g.pose().translate(8, localY, 0); g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, "§a>" + completedPhaseName, 0, 0, HudAnimUtil.withAlpha(0x88FF88, (int) (200 * dAlpha)), false);
                    g.pose().popPose();
                    localY += 12;
                    completedCount++;
                }
                if (completedCount == 0) {
                    g.pose().pushPose(); g.pose().translate(8, localY, 0); g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, Component.translatable("arc_quest.gui.journal.label.no_phases_completed").getString(), 0, 0, HudAnimUtil.withAlpha(0x666666, safeA), false);
                    g.pose().popPose();
                    localY += 12;
                }

                String intelPhaseId = resolveSelectedPhaseId(def, runtime);
                PhaseDefinition intelPhase = intelPhaseId == null ? null : def.getPhase(intelPhaseId);
                intelSceneId = intelPhase != null ? intelPhase.getIntelSceneId() : null;
                if (intelSceneId != null) {
                    localY += 8;
                    intelBtnLocalY = localY;
                    int intelBtnAbsX = x + 12;
                    int intelBtnAbsY = (int) Math.round(scrollAreaY + 12 - detailScrollOffset + localY);
                    boolean btnHovered = mx >= intelBtnAbsX && mx <= intelBtnAbsX + JournalConstants.INTEL_BTN_W
                            && my >= intelBtnAbsY && my <= intelBtnAbsY + JournalConstants.INTEL_BTN_H
                            && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;

                    intelBtnHoverAnim = HudAnimUtil.step(intelBtnHoverAnim, btnHovered ? 1f : 0f, 8f, dt);
                    drawButton(g, 0, localY, JournalConstants.INTEL_BTN_W, JournalConstants.INTEL_BTN_H, "PHASE INTEL", activeTheme, HudAnimUtil.easeOutCubic(intelBtnHoverAnim), btnHovered);
                    localY += JournalConstants.INTEL_BTN_H + 8;
                }

                if (!def.getCompletionRewards().isEmpty()) {
                    localY += 8;
                    int boxW = scrollAreaW - 24;
                    int bA = (int) (255 * dAlpha);

                    int tempX = 12, rows = 1;
                    for (IReward r : def.getCompletionRewards()) {
                        int rWidth = (r instanceof ItemReward) ? 28 : (int) (font.width(">" + r.describe()) * 0.75f) + 12;
                        if (tempX + rWidth > boxW - 16 && tempX > 12) { tempX = 12; rows++; }
                        tempX += rWidth;
                    }
                    int boxH = 24 + rows * 28;

                    HudAnimUtil.drawFrame(g, 0, localY, boxW, boxH, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * dAlpha)), HudAnimUtil.withAlpha(activeTheme, (int) (0x66 * dAlpha)));

                    g.pose().pushPose(); g.pose().translate(8, localY + 6, 0); g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, Component.translatable("arc_quest.gui.journal.section.chapter_rewards").getString(), 0, 0, HudAnimUtil.withAlpha(0xFFDD88, safeA), true);
                    g.pose().popPose();

                    int startX = 12, startY = localY + 22;
                    for (IReward r : def.getCompletionRewards()) {
                        int rWidth = (r instanceof ItemReward) ? 28 : (int) (font.width(">" + r.describe()) * 0.75f) + 12;
                        if (startX + rWidth > boxW - 16 && startX > 12) { startX = 12; startY += 28; }

                        if (r instanceof ItemReward ir) {
                            ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
                            HudAnimUtil.drawFrame(g, startX - 2, startY - 2, 20, 20, HudAnimUtil.withAlpha(0x000000, (int) (0x33 * dAlpha)), HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * dAlpha)));
                            g.renderItem(stack, startX, startY);
                            g.pose().pushPose(); g.pose().translate(0, 0, 200); g.renderItemDecorations(font, stack, startX, startY); g.pose().popPose();

                            int absX = x + 12 + startX, absY = scrollAreaY + 12 - (int) detailScrollOffset + startY;
                            if (mx >= absX && mx <= absX + 16 && my >= absY && my <= absY + 16 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                                g.fill(startX - 1, startY - 1, startX + 17, startY + 17, HudAnimUtil.withAlpha(0xFFFFFF, (int) (bA * 0.25f)));
                                screen.setHoveredRewardTooltip(stack);
                            }
                        } else {
                            g.pose().pushPose(); g.pose().translate(startX, startY + 4, 0); g.pose().scale(0.75f, 0.75f, 1f);
                            g.drawString(font, ">" + r.describe(), 0, 0, HudAnimUtil.withAlpha(0x88AAFF, safeA), false);
                            g.pose().popPose();
                        }
                        startX += rWidth;
                    }
                    localY += boxH + 8;
                }
            }
        } else if (entry.state() == QuestState.COMPLETED) {
            g.drawString(font, Component.translatable("arc_quest.gui.journal.label.quest_completed").getString(), 0, localY, HudAnimUtil.withAlpha(0x88FF88, safeA), true);
            localY += 16;
        } else if (entry.state() == QuestState.FAILED) {
            g.drawString(font, Component.translatable("arc_quest.gui.journal.label.quest_failed").getString(), 0, localY, HudAnimUtil.withAlpha(0xFF6666, safeA), true);
            localY += 16;
        }

        detailContentHeight = localY + 12;
        g.pose().popPose();
        g.disableScissor();

        renderScrollbar(g, x + w - 6, scrollAreaY + 2, scrollAreaH - 4, detailContentHeight, Math.max(0, detailContentHeight - scrollAreaH));

        int btnH = 20, btnY = y + h - btnH - 8;

        if (def.hasChapterShop() && (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE || (screen.getCurrentTab() == JournalTypes.Tab.COMPLETED && def.isChapterShopPersistent()))) {
            int shopBtnW = Math.min(110, w - 16), shopBtnX = x + w - shopBtnW - 8, shopBtnY = btnY - btnH - 6;
            boolean shopHover = mx >= shopBtnX && mx <= shopBtnX + shopBtnW && my >= shopBtnY && my <= shopBtnY + btnH;
            chapterShopBtnHover = HudAnimUtil.step(chapterShopBtnHover, shopHover ? 1f : 0f, 8f, dt);
            drawButton(g, shopBtnX, shopBtnY, shopBtnW, btnH, Component.translatable("arc_quest.gui.journal.button.chapter_shop").getString(), activeTheme, HudAnimUtil.easeOutCubic(chapterShopBtnHover), shopHover);
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && runtime != null) {
            int btnW = Math.min(90, (w - 24) / 2), trackX = x + w - btnW - 8, abanX = trackX - 8 - btnW;

            boolean tHover = mx >= trackX && mx <= trackX + btnW && my >= btnY && my <= btnY + btnH;
            trackBtnHover = HudAnimUtil.step(trackBtnHover, tHover ? 1f : 0f, 8f, dt);
            drawButton(g, trackX, btnY, btnW, btnH, entry.questId().equals(QuestHudOverlay.INSTANCE.getTrackedQuestId()) ? Component.translatable("arc_quest.gui.journal.button.tracked").getString() : Component.translatable("arc_quest.gui.journal.button.track").getString(), activeTheme, HudAnimUtil.easeOutCubic(trackBtnHover), tHover);

            boolean aHover = mx >= abanX && mx <= abanX + btnW && my >= btnY && my <= btnY + btnH;
            abandonBtnHover = HudAnimUtil.step(abandonBtnHover, aHover ? 1f : 0f, 8f, dt);
            drawButton(g, abanX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.abandon").getString(), 0xFF4444, HudAnimUtil.easeOutCubic(abandonBtnHover), aHover);
        } else if (screen.getCurrentTab() == JournalTypes.Tab.FAILED && entry.state() == QuestState.FAILED) {
            int restartBtnW = Math.min(120, w - 16), restartBtnX = x + w - restartBtnW - 8;
            boolean rHover = mx >= restartBtnX && mx <= restartBtnX + restartBtnW && my >= btnY && my <= btnY + btnH;
            failedRestartBtnHover = HudAnimUtil.step(failedRestartBtnHover, rHover ? 1f : 0f, 8f, dt);
            drawButton(g, restartBtnX, btnY, restartBtnW, btnH, Component.translatable("arc_quest.gui.journal.button.restart").getString(), activeTheme, HudAnimUtil.easeOutCubic(failedRestartBtnHover), rHover);
        }
    }

    private void renderEmptyDetail(GuiGraphics g, int x, int y, int w, int h) {
        if (screen.getEffectiveAlpha() > 0.05f) {
            g.drawCenteredString(screen.getFont(), Component.translatable("arc_quest.gui.journal.label.select_quest").getString(), x + w / 2, y + h / 2, HudAnimUtil.withAlpha(0x666666, (int) (120 * screen.getEffectiveAlpha())));
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH));
        int thumbY = y + (int) ((detailScrollOffset / maxScroll) * (viewH - thumbH));
        g.fill(x, y, x + 4, y + viewH, HudAnimUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isDraggingDetailScrollbar ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, int themeColor, float hoverEase, boolean hovered) {
        int bgAlpha = (int) ((0x33 + 0x44 * hoverEase) * screen.getEffectiveAlpha());
        int borderAlpha = (int) ((0x66 + 0x99 * hoverEase) * screen.getEffectiveAlpha());
        int borderRgb = hovered ? (themeColor & 0xFFFFFF) : 0xCCCCCC;

        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(x, y, x + w, y + 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y + h - 1, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y, x + 1, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x + w - 1, y, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        if (screen.getEffectiveAlpha() > 0.05f) {
            int textW = screen.getFont().width(text);
            float baseScale = 0.85f;
            if (textW * baseScale > w - 4) baseScale = Math.max(0.5f, (w - 6) / (float) textW);

            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h / 2f - (screen.getFont().lineHeight * baseScale) / 2f + 1, 0);
            g.pose().scale(baseScale, baseScale, 1f);
            g.drawCenteredString(screen.getFont(), text, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * screen.getEffectiveAlpha())));
            g.pose().popPose();
        }
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaH = h - 40;
        int maxDetailScroll = Math.max(0, detailContentHeight - scrollAreaH);
        int detailScrollbarX = x + w - 6;

        if (maxDetailScroll > 0 && mx >= detailScrollbarX && mx <= detailScrollbarX + 6 && my >= y && my <= y + scrollAreaH) {
            isDraggingDetailScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) scrollAreaH / detailContentHeight) * scrollAreaH));
            int thumbY = y + (int) ((detailScrollOffset / maxDetailScroll) * (scrollAreaH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragDetailYOffset = my - thumbY;
            else { dragDetailYOffset = thumbH / 2.0; updateScrollFromMouse(my, y, scrollAreaH, maxDetailScroll); }
            return true;
        }

        if (intelSceneId != null) {
            int intelBtnAbsX = x + 12;
            int intelBtnAbsY = (int) Math.round(y + 12 - detailScrollOffset + intelBtnLocalY);
            if (mx >= intelBtnAbsX && mx <= intelBtnAbsX + JournalConstants.INTEL_BTN_W && my >= intelBtnAbsY && my <= intelBtnAbsY + JournalConstants.INTEL_BTN_H && intelBtnAbsY >= y && intelBtnAbsY < y + scrollAreaH) {
                screen.playClick();
                QuestIntelPanel.trigger(intelSceneId, screen.getCurrentThemeColor(), x, y, w, h);
                return true;
            }
        }

        int btnH = 20, btnY = y + h - btnH - 8;
        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && screen.getSelectedIndex() >= 0) {
            int btnW = Math.min(90, (w - 24) / 2), trackX = x + w - btnW - 8, abanX = trackX - 8 - btnW;
            if (mx >= trackX && mx <= trackX + btnW && my >= btnY && my <= btnY + btnH) {
                QuestHudOverlay.INSTANCE.setTrackedQuest(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId());
                screen.playClick();
                return true;
            }
            if (mx >= abanX && mx <= abanX + btnW && my >= btnY && my <= btnY + btnH) {
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.abandon(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId()));
                screen.playClick();
                return true;
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.FAILED && screen.getSelectedIndex() >= 0) {
            int restartBtnW = Math.min(120, w - 16), restartBtnX = x + w - restartBtnW - 8;
            if (mx >= restartBtnX && mx <= restartBtnX + restartBtnW && my >= btnY && my <= btnY + btnH) {
                String qid = screen.getCurrentEntries().get(screen.getSelectedIndex()).questId();
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.abandon(qid));
                screen.executeNetworkAction(() -> ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.accept(qid)));
                screen.playClick();
                return true;
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentPhaseTags.isEmpty()) {
            for (JournalTypes.PhaseTagRect rect : currentPhaseTags) {
                if (mx >= rect.x && mx <= rect.x + rect.w && my >= rect.y && my <= rect.y + rect.h) {
                    selectedPhaseId = rect.phaseId;
                    QuestHudOverlay.INSTANCE.setTrackedFocus(
                            screen.getCurrentEntries().get(screen.getSelectedIndex()).questId(),
                            rect.phaseId
                    );
                    currentChoiceButtons.clear();
                    screen.playClick();
                    return true;
                }
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentChoiceButtons.isEmpty()) {
            for (JournalTypes.ChoiceButtonRect rect : currentChoiceButtons) {
                if (mx >= rect.x && mx <= rect.x + rect.w && my >= rect.y && my <= rect.y + rect.h && my >= y && my <= y + scrollAreaH) {
                    long nowMs = Util.getMillis();
                    if (nowMs - lastChoiceClickAt < JournalConstants.CHOICE_CLICK_COOLDOWN_MS) return true;
                    lastChoiceClickAt = nowMs;

                    ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.choose(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId(), rect.phaseId, rect.choiceIndex));
                    QuestHudOverlay.INSTANCE.clearBranchChoiceToast();
                    screen.playClick();
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseDragged(double mx, double my, int y, int h) {
        if (isDraggingDetailScrollbar) {
            updateScrollFromMouse(my, y, h - 40, Math.max(0, detailContentHeight - (h - 40)));
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int button) {
        if (button == 0) isDraggingDetailScrollbar = false;
        return isDraggingDetailScrollbar;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        for (ObjScrollArea area : objScrollAreas) {
            if (mx >= area.absX && mx <= area.absX + area.w && my >= area.absY && my <= area.absY + area.h) {
                double target = phaseObjTargetScrolls.getOrDefault(area.phaseId, 0.0);
                target -= delta * 14.0;
                target = Math.max(0.0, Math.min(target, area.maxScroll));
                phaseObjTargetScrolls.put(area.phaseId, target);
                return true;
            }
        }

        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            detailTargetScroll -= delta * 25.0;
            clampScroll(h - 40);
            return true;
        }
        return false;
    }

    public void clampScroll(int scrollAreaH) {
        detailTargetScroll = Math.max(0, Math.min(detailTargetScroll, Math.max(0, detailContentHeight - scrollAreaH)));
    }

    private void updateScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / detailContentHeight) * viewH));
        detailTargetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragDetailYOffset) / (viewH - thumbH))) * maxScroll;
    }

    private int getChoicesHeight(PhaseDefinition phase, QuestDefinition def, QuestRuntimeData runtime, String phaseId) {
        int choicesH = 0;
        boolean showChoices = shouldShowBranchChoices(def, runtime, phaseId);
        if (showChoices) {
            int visibleCount = 0;
            for (ChoiceOption choice : phase.getChoices()) {
                if (choice.getVisibleCondition() == null || choice.getVisibleCondition().testClient(
                        ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(),
                        ClientQuestCache.INSTANCE.getAllFlags(),
                        ClientQuestCache.INSTANCE.getAllVariables())) {
                    visibleCount++;
                }
            }
            if (visibleCount > 0) choicesH = visibleCount * 24;
        }
        int lockedChoicesH = (phase.hasChoices() && !showChoices) ? 14 : 0;
        return choicesH + lockedChoicesH;
    }

    private boolean shouldShowBranchChoices(QuestDefinition def, QuestRuntimeData runtime, String phaseId) {
        if (def == null || runtime == null || phaseId == null || phaseId.isEmpty()) return false;
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null || !phase.hasChoices()) return false;
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++) {
            if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        }
        return true;
    }

    private String resolveSelectedPhaseId(QuestDefinition def, QuestRuntimeData runtime) {
        if (def == null || runtime == null) return null;

        if (selectedPhaseId != null
                && !selectedPhaseId.isEmpty()
                && runtime.isPhaseActive(selectedPhaseId)
                && def.getPhase(selectedPhaseId) != null) {
            return selectedPhaseId;
        }

        String trackerQuest = QuestHudOverlay.INSTANCE.getTrackedQuestId();
        String trackerPhase = QuestHudOverlay.INSTANCE.getTrackedPhaseId();
        if (trackerQuest != null
                && trackerQuest.equals(runtime.getQuestId())
                && trackerPhase != null
                && !trackerPhase.isEmpty()
                && runtime.isPhaseActive(trackerPhase)
                && def.getPhase(trackerPhase) != null) {
            selectedPhaseId = trackerPhase;
            return selectedPhaseId;
        }

        String current = runtime.getCurrentPhaseId();
        if (current != null
                && !current.isEmpty()
                && runtime.isPhaseActive(current)
                && def.getPhase(current) != null) {
            selectedPhaseId = current;
            return selectedPhaseId;
        }

        for (String pid : runtime.getActivePhaseIds()) {
            if (def.getPhase(pid) != null) {
                selectedPhaseId = pid;
                return selectedPhaseId;
            }
        }

        return selectedPhaseId = null;
    }

    private boolean isPhaseObjectivesDone(QuestRuntimeData runtime, PhaseDefinition phase, String phaseId) {
        int[] progress = runtime.getAllProgress(phaseId);
        for (int i = 0; i < phase.getObjectives().size(); i++) if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) return false;
        return true;
    }
}