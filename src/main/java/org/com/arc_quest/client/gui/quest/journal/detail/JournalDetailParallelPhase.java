package org.com.arc_quest.client.gui.quest.journal.detail;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.gui.QuestHudOverlay;
import org.com.arc_quest.client.gui.quest.journal.JournalConstants;
import org.com.arc_quest.client.gui.quest.journal.JournalTypes;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.gui.render.QuestIntelPanel;
import org.com.arc_quest.quest.api.ChoiceOption;
import org.com.arc_quest.quest.api.ObjectiveEntry;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.com.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JournalDetailParallelPhase {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;

    private double phaseScrollOffset = 0;
    private double phaseTargetScroll = 0;
    private int maxPhaseScroll = 0;
    private double scrollStep = 0;

    private boolean isDraggingPhaseScrollbar = false;
    private double dragPhaseXOffset = 0;
    private record ScrollControls(int leftX, int leftY, int leftW, int leftH,
                                  int rightX, int rightY, int rightW, int rightH,
                                  int trackX, int trackY, int trackW, int thumbX, int thumbW) {}
    private ScrollControls currentScrollControls = null;
    private float leftBtnHover = 0f;
    private float rightBtnHover = 0f;

    private final Map<String, Float> phaseCardReveal = new HashMap<>();
    private final Map<String, Float> phaseCardHoverAnims = new HashMap<>();
    private final Map<String, Double> phaseObjScrollOffsets = new HashMap<>();
    private final Map<String, Double> phaseObjTargetScrolls = new HashMap<>();

    private final Map<String, float[]> phaseObjProgressAnims = new HashMap<>();

    private final Map<String, Float> phaseIntelHoverAnims = new HashMap<>();
    private final Map<String, Float> phaseIntelBtnHoverAnims = new HashMap<>();
    private record IntelBtnRect(int absX, int absY, int w, int h, ResourceLocation sceneId) {}
    private final List<IntelBtnRect> currentIntelBtns = new ArrayList<>();

    private record ObjScrollArea(int absX, int absY, int w, int h, String phaseId, int maxScroll) {}
    private final List<ObjScrollArea> objScrollAreas = new ArrayList<>();

    private String selectedPhaseId = null;
    private final List<JournalTypes.ChoiceButtonRect> currentChoiceButtons = new ArrayList<>();
    private final List<JournalTypes.PhaseTagRect> currentPhaseTags = new ArrayList<>();

    private long lastChoiceClickAt = 0L;

    public JournalDetailParallelPhase(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    public void reset() {
        phaseScrollOffset = 0;
        phaseTargetScroll = 0;
        isDraggingPhaseScrollbar = false;
        currentScrollControls = null;
        phaseCardReveal.clear();
        phaseCardHoverAnims.clear();
        phaseObjScrollOffsets.clear();
        phaseObjTargetScrolls.clear();
        phaseObjProgressAnims.clear();
        phaseIntelHoverAnims.clear();
        phaseIntelBtnHoverAnims.clear();
        objScrollAreas.clear();
        currentChoiceButtons.clear();
        currentPhaseTags.clear();
        currentIntelBtns.clear();
        selectedPhaseId = null;
    }

    public int render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, List<String> activePhaseIds, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, float dt, int activeTheme, float dAlpha, int safeA, int localY) {
        Font font = screen.getFont();
        objScrollAreas.clear();
        currentChoiceButtons.clear();
        currentPhaseTags.clear();
        currentIntelBtns.clear();

        phaseScrollOffset += Math.abs(phaseTargetScroll - phaseScrollOffset) > 0.5 ? (phaseTargetScroll - phaseScrollOffset) * Math.min(1.0, dt * 14.0) : (phaseTargetScroll - phaseScrollOffset);

        g.pose().pushPose(); g.pose().translate(0, localY, 0); g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, "PARALLEL LANES", 0, 0, HudAnimUtil.withAlpha(activeTheme, safeA), true);
        g.pose().popPose();
        localY += 14;

        int cardAreaW = scrollAreaW - 24, gap = 8, colW = (cardAreaW - gap) / 2;
        scrollStep = colW + gap;
        int MAX_VISIBLE_OBJS = 2, OBJ_LINE_H = 14, FIXED_OBJ_VIEW_H = MAX_VISIBLE_OBJS * OBJ_LINE_H;

        int maxCardH = 0;
        for (String pid : activePhaseIds) {
            PhaseDefinition phase = def.getPhase(pid);
            int choicesH = getChoicesHeight(phase, def, runtime, pid);
            maxCardH = Math.max(maxCardH, 22 + 12 + 4 + FIXED_OBJ_VIEW_H + choicesH + (choicesH > 0 ? 4 : 0));
        }

        maxPhaseScroll = Math.max(0, activePhaseIds.size() * (colW + gap) - gap - cardAreaW);
        if (maxPhaseScroll > 0) phaseTargetScroll = Math.max(0, Math.min(phaseTargetScroll, maxPhaseScroll));

        int currentY = localY;
        int clipAbsX1 = x + 12, clipAbsY1 = scrollAreaY, clipAbsX2 = clipAbsX1 + cardAreaW + 4, clipAbsY2 = scrollAreaY + scrollAreaH;

        g.disableScissor();
        g.enableScissor(clipAbsX1, clipAbsY1, clipAbsX2, clipAbsY2);

        for (int idx = 0; idx < activePhaseIds.size(); idx++) {
            int rawCardX = idx * (colW + gap) - (int) phaseScrollOffset;
            if (rawCardX + colW < 0 || rawCardX > cardAreaW) continue;

            String phaseId = activePhaseIds.get(idx);
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;

            float reveal = phaseCardReveal.getOrDefault(phaseId, 0f);
            reveal = HudAnimUtil.lerp(reveal, 1f, 0.12f + (idx * 0.02f), dt);
            phaseCardReveal.put(phaseId, reveal);

            float cardEase = HudAnimUtil.easeOutCubic(Math.min(1f, reveal));
            int cardX = rawCardX + (int) ((1f - cardEase) * 18f);
            int cardY = currentY + (int) ((1f - cardEase) * 10f);
            int cardSafeA = (int) (safeA * cardEase);

            int total = phase.getObjectives().size();
            boolean selected = phaseId.equals(resolveSelectedPhaseId(def, runtime));
            boolean phaseDone = JournalDetailPanel.isPhaseObjectivesDone(runtime, phase, phaseId);
            float powerFactor = (selected && !phaseDone) ? 1.0f : 0.35f;

            List<ChoiceOption> visibleChoices = new ArrayList<>();
            if (JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId)) {
                for (ChoiceOption choice : phase.getChoices()) if (choice.getVisibleCondition() == null || choice.getVisibleCondition().testClient(ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(), ClientQuestCache.INSTANCE.getAllFlags(), ClientQuestCache.INSTANCE.getAllVariables())) visibleChoices.add(choice);
            }

            int objContentH = total * OBJ_LINE_H;
            int maxInnerScroll = Math.max(0, objContentH - FIXED_OBJ_VIEW_H);
            int absCardX = x + 12 + cardX, absCardY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + cardY);
            boolean cardHovered = mx >= absCardX && mx <= absCardX + colW && my >= absCardY && my <= absCardY + maxCardH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH && mx >= clipAbsX1 && mx <= clipAbsX2;

            if (maxInnerScroll > 0) objScrollAreas.add(new ObjScrollArea(absCardX, absCardY, colW, maxCardH, phaseId, maxInnerScroll));

            float hoverAnim = phaseCardHoverAnims.getOrDefault(phaseId, 0f);
            hoverAnim = HudAnimUtil.lerp(hoverAnim, cardHovered ? 1f : 0f, 0.2f, dt);
            phaseCardHoverAnims.put(phaseId, hoverAnim);
            float hoverEase = HudAnimUtil.easeOutCubic(hoverAnim);

            int cyberEdgeWidth = 3, contentShiftX = 0;
            int bgAlpha = (int) ((0x44 + (selected ? 0x11 : (int)(0x22 * hoverEase))) * dAlpha * cardEase);
            int staticBorderAlpha = (int) ((0x1A + 0x22 * hoverEase) * dAlpha * cardEase);
            int finalEdgeColor = selected ? activeTheme : HudAnimUtil.lerpColor(0x555555, 0xDDDDDD, hoverEase);
            int edgeAlpha = selected ? (int) (255 * dAlpha * cardEase) : (int) ((100 + 100 * hoverEase) * powerFactor * dAlpha * cardEase);

            g.fill(cardX + cyberEdgeWidth, cardY, cardX + colW, cardY + maxCardH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
            g.fill(cardX + cyberEdgeWidth, cardY, cardX + colW, cardY + 1, HudAnimUtil.withAlpha(0xFFFFFF, staticBorderAlpha));
            g.fill(cardX + cyberEdgeWidth, cardY + maxCardH - 1, cardX + colW, cardY + maxCardH, HudAnimUtil.withAlpha(0xFFFFFF, staticBorderAlpha));
            g.fill(cardX + colW - 1, cardY, cardX + colW, cardY + maxCardH, HudAnimUtil.withAlpha(0xFFFFFF, staticBorderAlpha));
            HudRenderUtil.drawCyberneticEdge(g, cardX, cardY, maxCardH, finalEdgeColor, edgeAlpha);

            currentPhaseTags.add(new JournalTypes.PhaseTagRect(absCardX, absCardY, colW, maxCardH, phaseId));

            String phaseName = phase.getDisplayName() != null && !phase.getDisplayName().getString().isEmpty() ? phase.getDisplayName().getString() : phase.getPhaseId();
            g.drawString(font, font.plainSubstrByWidth(phaseName, colW - 60), cardX + 8 + contentShiftX, cardY + 6, HudAnimUtil.withAlpha(selected ? 0xFFFFFF : 0xDDDDDD, cardSafeA), true);

            //右上角的状态文字 <-> Intel按钮
            ResourceLocation pIntel = phase.getIntelSceneId();
            boolean hasIntel = pIntel != null;

            float intelAnim = phaseIntelHoverAnims.getOrDefault(phaseId, 0f);
            intelAnim = HudAnimUtil.step(intelAnim, (cardHovered && hasIntel) ? 1f : 0f, 15f, dt);
            phaseIntelHoverAnims.put(phaseId, intelAnim);
            float easeIntel = HudAnimUtil.easeOutQuintic(intelAnim);

            int rightEdgeX = cardX + colW - 6;

            if (easeIntel < 0.99f) {
                String statusLabel = phaseDone ? "COMPLETED" : (selected ? "TRACKING" : "STANDBY");
                int statusColor = phaseDone ? 0x66FF66 : (selected ? activeTheme : 0x777777);
                float sAlpha = 1f - easeIntel;

                g.pose().pushPose();
                g.pose().translate(rightEdgeX - font.width(statusLabel) * 0.7f, cardY + 7, 0); // 原地不动
                g.pose().scale(0.7f, 0.7f, 1f);
                g.drawString(font, statusLabel, 0, 0, HudAnimUtil.withAlpha(statusColor, (int)(sAlpha * cardSafeA)), false);
                g.pose().popPose();
            }

            if (hasIntel && easeIntel > 0.01f) {
                String btnText = "INTEL";
                float baseScale = 0.75f;
                float rawTextW = font.width(btnText);
                float rawTextH = font.lineHeight;
                float textW = rawTextW * baseScale;
                float textH = rawTextH * baseScale;

                int btnW = (int)textW + 8;
                int btnH = 10;
                int btnX = rightEdgeX - btnW;
                int btnY = cardY + 5;

                int absBtnX = x + 12 + btnX;
                int absBtnY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + btnY);
                boolean btnHovered = mx >= absBtnX && mx <= absBtnX + btnW && my >= absBtnY && my <= absBtnY + btnH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH && mx >= clipAbsX1 && mx <= clipAbsX2;

                if (QuestIntelPanel.isActive()) {
                    btnHovered = false;
                }

                float btnSelfHover = phaseIntelBtnHoverAnims.getOrDefault(phaseId, 0f);
                btnSelfHover = HudAnimUtil.step(btnSelfHover, (btnHovered && intelAnim > 0.5f) ? 1f : 0f, 15f, dt);
                phaseIntelBtnHoverAnims.put(phaseId, btnSelfHover);

                int currentColor = HudAnimUtil.lerpColor(activeTheme, 0xFFFFFF, btnSelfHover);
                int finalColor = HudAnimUtil.withAlpha(currentColor, (int)(easeIntel * cardSafeA));

                g.fill(btnX, btnY + 2, btnX + 1, btnY + btnH - 2, finalColor);

                float wave = (float) Math.sin(Util.getMillis() / 600.0); // 低频平滑周期
                float breathScale = baseScale + 0.06f * wave;

                float textCenterX = btnX + 5 + textW / 2f;
                float textCenterY = btnY + btnH / 2f;

                g.pose().pushPose();
                g.pose().translate(textCenterX, textCenterY, 0);
                g.pose().scale(breathScale, breathScale, 1f);

                g.drawString(font, btnText, -rawTextW / 2f, -rawTextH / 2f + 0.5f, finalColor, false);
                g.pose().popPose();

                if (easeIntel > 0.5f) {
                    currentIntelBtns.add(new IntelBtnRect(absBtnX, absBtnY, btnW, btnH, pIntel));
                }
            }
            // =========================================================

            int cy = cardY + 22, laneBarW = colW - 16, barX = cardX + 8 + contentShiftX;
            int dimmedThemeColor = HudAnimUtil.lerpColor(0x000000, phaseDone ? 0x66FF66 : activeTheme, powerFactor);
            int emptyBgColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * powerFactor * dAlpha * cardEase));
            int fillColor = HudAnimUtil.withAlpha(dimmedThemeColor, (int) (0xCC * powerFactor * dAlpha * cardEase));
            int brightColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * powerFactor * dAlpha * cardEase));

            float[] pAnims = phaseObjProgressAnims.computeIfAbsent(phaseId, k -> new float[Math.max(1, total)]);
            if (pAnims.length < total) {
                float[] newAnims = new float[total];
                System.arraycopy(pAnims, 0, newAnims, 0, pAnims.length);
                pAnims = newAnims;
                phaseObjProgressAnims.put(phaseId, pAnims);
            }

            if (total <= 1) {
                float targetRatio = total == 1 ? (float) Math.max(0, Math.min(runtime.getObjectiveProgress(phaseId, 0), Math.max(1, phase.getObjectives().get(0).getRequiredCount()))) / Math.max(1, phase.getObjectives().get(0).getRequiredCount()) : 0f;
                pAnims[0] = HudAnimUtil.lerp(pAnims[0], targetRatio, 0.15f, dt);

                int laneFillW = (int) (laneBarW * pAnims[0]);
                g.fill(barX, cy, barX + laneBarW, cy + 2, emptyBgColor);
                if (laneFillW > 0) {
                    g.fill(barX, cy, barX + laneFillW, cy + 2, fillColor);
                    g.fill(barX + laneFillW - 2, cy - 1, barX + laneFillW, cy + 3, brightColor);
                }
            } else {
                float segW = (float)(laneBarW - (total - 1) * 2) / total, cx = barX;
                for (int i = 0; i < total; i++) {
                    int req = Math.max(1, phase.getObjectives().get(i).getRequiredCount());
                    float targetRatio = (float) Math.max(0, Math.min(runtime.getObjectiveProgress(phaseId, i), req)) / req;
                    pAnims[i] = HudAnimUtil.lerp(pAnims[i], targetRatio, 0.15f, dt);

                    int sFill = (int)(segW * pAnims[i]);
                    g.fill((int)cx, cy, (int)(cx + segW), cy + 2, emptyBgColor);
                    if (sFill > 0) {
                        g.fill((int)cx, cy, (int)(cx + sFill), cy + 2, fillColor);
                        g.fill((int)(cx + sFill) - 2, cy - 1, (int)(cx + sFill), cy + 3, brightColor);
                    }
                    cx += segW + 2;
                }
            }
            // ==========================================

            cy += 12;
            double currentInnerScroll = phaseObjScrollOffsets.getOrDefault(phaseId, 0.0);
            double targetInnerScroll = phaseObjTargetScrolls.getOrDefault(phaseId, 0.0);
            currentInnerScroll += (targetInnerScroll - currentInnerScroll) * Math.min(1.0, dt * 15.0);
            phaseObjScrollOffsets.put(phaseId, currentInnerScroll);

            int intX1 = Math.max(clipAbsX1, x + 12 + cardX);
            int intY1 = Math.max(clipAbsY1, (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + cy));
            int intX2 = Math.min(clipAbsX2, x + 12 + cardX + colW);
            int intY2 = Math.min(clipAbsY2, (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + cy) + FIXED_OBJ_VIEW_H);

            if (intX2 > intX1 && intY2 > intY1 && total > 0) {
                g.disableScissor(); g.enableScissor(intX1, intY1, intX2, intY2);
                g.pose().pushPose(); g.pose().translate(0, -currentInnerScroll, 0);

                int objY = cy;
                for (int i = 0; i < total; i++) {
                    ObjectiveEntry obj = phase.getObjectives().get(i);
                    int progress = runtime.getObjectiveProgress(phaseId, i), required = obj.getRequiredCount();
                    boolean complete = progress >= required;
                    int extraMargin = maxInnerScroll > 0 ? 8 : 0;
                    String pr = progress + "/" + required;
                    String line = font.plainSubstrByWidth((complete ? "§a✔ " : "§7○ ") + obj.getDisplayText().getString(), Math.max(5, colW - 16 - contentShiftX - extraMargin - font.width(pr) - 6));

                    int objColor = complete ? 0x88FF88 : 0xCCCCCC;
                    g.drawString(font, line, cardX + 8 + contentShiftX, objY, HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0x000000, objColor, Math.max(0.6f, powerFactor)), cardSafeA), false);
                    g.drawString(font, pr, cardX + colW - 8 - extraMargin - font.width(pr), objY, HudAnimUtil.withAlpha(0x888888, cardSafeA), false);
                    objY += OBJ_LINE_H;
                }
                g.pose().popPose(); g.disableScissor(); g.enableScissor(clipAbsX1, clipAbsY1, clipAbsX2, clipAbsY2);
            }

            if (maxInnerScroll > 0) {
                int gradientW = colW - 8;
                if (currentInnerScroll > 1.0) g.fillGradient(cardX + cyberEdgeWidth, cy, cardX + gradientW, cy + 6, HudAnimUtil.withAlpha(0x000000, (int)(0xAA * dAlpha * cardEase)), HudAnimUtil.withAlpha(0x000000, 0));
                if (currentInnerScroll < maxInnerScroll - 1.0) g.fillGradient(cardX + cyberEdgeWidth, cy + FIXED_OBJ_VIEW_H - 6, cardX + gradientW, cy + FIXED_OBJ_VIEW_H, HudAnimUtil.withAlpha(0x000000, 0), HudAnimUtil.withAlpha(0x000000, (int)(0xAA * dAlpha * cardEase)));

                int trackX = cardX + colW - 6;
                g.fill(trackX, cy, trackX + 2, cy + FIXED_OBJ_VIEW_H, HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x11 * dAlpha * cardEase)));
                int thumbH = Math.max(8, (int)(((float)FIXED_OBJ_VIEW_H / objContentH) * FIXED_OBJ_VIEW_H));
                g.fill(trackX, cy + (int)((currentInnerScroll / maxInnerScroll) * (FIXED_OBJ_VIEW_H - thumbH)), trackX + 2, cy + (int)((currentInnerScroll / maxInnerScroll) * (FIXED_OBJ_VIEW_H - thumbH)) + thumbH, HudAnimUtil.withAlpha(activeTheme, (int)(0xAA * dAlpha * cardEase)));
            }

            cy += FIXED_OBJ_VIEW_H + 4;
            if (phase.hasChoices() && !JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId)) {
                g.drawString(font, "Choices locked", cardX + 8 + contentShiftX, cy, HudAnimUtil.withAlpha(0x888888, cardSafeA), false);
            }

            if (!visibleChoices.isEmpty()) {
                cy += 4;
                for (int i = 0; i < visibleChoices.size(); i++) {
                    ChoiceOption choice = visibleChoices.get(i);
                    int btnX = cardX + 8 + contentShiftX, btnY = cy, btnW = colW - 16, btnH = 20;
                    int absBtnX = x + 12 + btnX, absBtnY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + btnY);

                    boolean btnHover = mx >= absBtnX && mx <= absBtnX + btnW && my >= absBtnY && my <= absBtnY + btnH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH && mx >= clipAbsX1 && mx <= clipAbsX2;

                    g.fill(btnX, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((btnHover ? 0x22 : 0x12) * dAlpha * cardEase)));
                    g.fill(btnX, btnY, btnX + btnW, btnY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardEase)));
                    g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardEase)));
                    g.fill(btnX, btnY, btnX + 1, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardEase)));
                    g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, HudAnimUtil.withAlpha(activeTheme, (int) (170 * dAlpha * cardEase)));

                    g.drawString(font, font.plainSubstrByWidth((i + 1) + ". " + choice.getDisplayText().getString(), btnW - 12), btnX + 6, btnY + 6, HudAnimUtil.withAlpha(btnHover ? activeTheme : 0xDDDDDD, cardSafeA), false);
                    currentChoiceButtons.add(new JournalTypes.ChoiceButtonRect(absBtnX, absBtnY, btnW, btnH, phase.getChoices().indexOf(choice), phaseId));
                    cy += 24;
                }
            }
        }

        g.disableScissor(); g.enableScissor(x, scrollAreaY, x + scrollAreaW, scrollAreaY + scrollAreaH);
        localY = currentY + maxCardH + 8;

        if (maxPhaseScroll > 0) {
            int ctrlY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + localY);
            int absCtrlY = ctrlY;
            int btnW = 12;
            int trackGap = 8;
            int absTrackW = cardAreaW - (btnW * 2) - (trackGap * 2);
            int absLeftX = x + 12;
            int absRightX = x + 12 + cardAreaW - btnW;
            int absTrackX = absLeftX + btnW + trackGap;

            boolean lHover = mx >= absLeftX && mx <= absLeftX + btnW && my >= absCtrlY - 2 && my <= absCtrlY + 8 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
            leftBtnHover = HudAnimUtil.step(leftBtnHover, lHover ? 1f : 0f, 10f, dt);
            g.drawString(font, "<", 0, localY - 2, HudAnimUtil.withAlpha(activeTheme, (int)((100 + 155*leftBtnHover) * dAlpha)), false);

            boolean rHover = mx >= absRightX && mx <= absRightX + btnW && my >= absCtrlY - 2 && my <= absCtrlY + 8 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
            rightBtnHover = HudAnimUtil.step(rightBtnHover, rHover ? 1f : 0f, 10f, dt);
            g.drawString(font, ">", cardAreaW - btnW + 4, localY - 2, HudAnimUtil.withAlpha(activeTheme, (int)((100 + 155*rightBtnHover) * dAlpha)), false);

            int localTrackX = btnW + trackGap;
            g.fill(localTrackX, localY + 2, localTrackX + absTrackW, localY + 3, HudAnimUtil.withAlpha(0xFFFFFF, (int)(25 * dAlpha)));
            g.fill(localTrackX, localY + 1, localTrackX + 1, localY + 4, HudAnimUtil.withAlpha(0xFFFFFF, (int)(50 * dAlpha)));
            g.fill(localTrackX + absTrackW - 1, localY + 1, localTrackX + absTrackW, localY + 4, HudAnimUtil.withAlpha(0xFFFFFF, (int)(50 * dAlpha)));

            int thumbW = Math.max(12, (int) (((float) absTrackW / (activePhaseIds.size() * (colW + gap) - gap)) * absTrackW));
            int thumbLocalX = localTrackX + (int) ((phaseScrollOffset / maxPhaseScroll) * (absTrackW - thumbW));

            int thumbAlpha = isDraggingPhaseScrollbar ? 255 : 150;
            g.fill(thumbLocalX, localY + 2, thumbLocalX + thumbW, localY + 3, HudAnimUtil.withAlpha(activeTheme, (int)(thumbAlpha * dAlpha)));

            int centerX = thumbLocalX + thumbW / 2;
            int needleOffset = isDraggingPhaseScrollbar ? 2 : 1;
            g.fill(centerX, localY + 2 - needleOffset, centerX + 1, localY + 3 + needleOffset, HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * dAlpha)));
            g.fill(centerX - 2, localY + 2, centerX, localY + 3, HudAnimUtil.withAlpha(activeTheme, (int)(255 * dAlpha)));
            g.fill(centerX + 1, localY + 2, centerX + 3, localY + 3, HudAnimUtil.withAlpha(activeTheme, (int)(255 * dAlpha)));

            currentScrollControls = new ScrollControls(absLeftX, absCtrlY - 2, btnW, 10, absRightX, absCtrlY - 2, btnW, 10, absTrackX, absCtrlY - 2, absTrackW, absTrackX + (int) ((phaseScrollOffset / maxPhaseScroll) * (absTrackW - thumbW)), thumbW);

            localY += 12;
        } else {
            currentScrollControls = null;
        }

        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (80 * dAlpha)));
        localY += 10;

        g.pose().pushPose(); g.pose().translate(0, localY, 0); g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, Component.translatable("arc_quest.gui.journal.section.completed_phases").getString(), 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), true);
        g.pose().popPose();
        localY += 14;

        int completedCount = 0;
        for (String cPhaseId : runtime.getCompletedPhaseIds()) {
            String completedPhaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(entry.questId(), cPhaseId);
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

        return localY;
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaY = y, scrollAreaH = h - 40;

        if (maxPhaseScroll > 0 && currentScrollControls != null) {
            ScrollControls sc = currentScrollControls;
            if (mx >= sc.leftX && mx <= sc.leftX + sc.leftW && my >= sc.leftY && my <= sc.leftY + sc.leftH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                screen.playClick();
                phaseTargetScroll = Math.max(0, phaseTargetScroll - scrollStep);
                return true;
            }
            if (mx >= sc.rightX && mx <= sc.rightX + sc.rightW && my >= sc.rightY && my <= sc.rightY + sc.rightH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                screen.playClick();
                phaseTargetScroll = Math.min(maxPhaseScroll, phaseTargetScroll + scrollStep);
                return true;
            }
            if (mx >= sc.trackX && mx <= sc.trackX + sc.trackW && my >= sc.trackY - 2 && my <= sc.trackY + 12 && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                isDraggingPhaseScrollbar = true;
                if (mx >= sc.thumbX && mx <= sc.thumbX + sc.thumbW) {
                    dragPhaseXOffset = mx - sc.thumbX;
                } else {
                    dragPhaseXOffset = sc.thumbW / 2.0;
                    updatePhaseScrollFromAbsoluteMouse(mx);
                }
                return true;
            }
        }

        // 全新：优先拦截并处理悬浮状态下的 Intel 微型按键点击
        if (!currentIntelBtns.isEmpty()) {
            for (IntelBtnRect rect : currentIntelBtns) {
                if (mx >= rect.absX && mx <= rect.absX + rect.w && my >= rect.absY && my <= rect.absY + rect.h && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                    screen.playClick();
                    QuestIntelPanel.trigger(rect.sceneId, screen.getCurrentThemeColor(), x, y, w, h);
                    return true;
                }
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentPhaseTags.isEmpty()) {
            for (JournalTypes.PhaseTagRect rect : currentPhaseTags) {
                if (mx >= rect.x && mx <= rect.x + rect.w && my >= rect.y && my <= rect.y + rect.h) {
                    selectedPhaseId = rect.phaseId;
                    QuestHudOverlay.INSTANCE.setTrackedFocus(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId(), rect.phaseId);
                    screen.playClick(); return true;
                }
            }
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentChoiceButtons.isEmpty()) {
            for (JournalTypes.ChoiceButtonRect rect : currentChoiceButtons) {
                if (mx >= rect.x && mx <= rect.x + rect.w && my >= rect.y && my <= rect.y + rect.h && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                    long nowMs = Util.getMillis();
                    if (nowMs - lastChoiceClickAt < JournalConstants.CHOICE_CLICK_COOLDOWN_MS) return true;
                    lastChoiceClickAt = nowMs;
                    ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.choose(screen.getCurrentEntries().get(screen.getSelectedIndex()).questId(), rect.phaseId, rect.choiceIndex));
                    QuestHudOverlay.INSTANCE.clearBranchChoiceToast(); screen.playClick(); return true;
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx) {
        if (isDraggingPhaseScrollbar && maxPhaseScroll > 0 && currentScrollControls != null) {
            updatePhaseScrollFromAbsoluteMouse(mx);
            return true;
        }
        return false;
    }

    public void onMouseReleased() { isDraggingPhaseScrollbar = false; }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int scrollAreaY, int scrollAreaH) {
        for (ObjScrollArea area : objScrollAreas) {
            if (mx >= area.absX && mx <= area.absX + area.w && my >= area.absY && my <= area.absY + area.h) {
                double target = phaseObjTargetScrolls.getOrDefault(area.phaseId, 0.0);
                target -= delta * 14.0;
                phaseObjTargetScrolls.put(area.phaseId, Math.max(0.0, Math.min(target, area.maxScroll)));
                return true;
            }
        }
        return false;
    }

    private void updatePhaseScrollFromAbsoluteMouse(double mx) {
        if (maxPhaseScroll <= 0 || currentScrollControls == null) return;
        ScrollControls sc = currentScrollControls;
        double rawPercentage = (mx - dragPhaseXOffset - sc.trackX) / (sc.trackW - sc.thumbW);
        phaseTargetScroll = Math.max(0.0, Math.min(1.0, rawPercentage)) * maxPhaseScroll;
    }

    private int getChoicesHeight(PhaseDefinition phase, QuestDefinition def, QuestRuntimeData runtime, String phaseId) {
        boolean showChoices = JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId);
        if (showChoices) {
            int visibleCount = 0;
            for (ChoiceOption choice : phase.getChoices()) if (choice.getVisibleCondition() == null || choice.getVisibleCondition().testClient(ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(), ClientQuestCache.INSTANCE.getAllFlags(), ClientQuestCache.INSTANCE.getAllVariables())) visibleCount++;
            if (visibleCount > 0) return visibleCount * 24;
        }
        return (phase.hasChoices() && !showChoices) ? 14 : 0;
    }

    private String resolveSelectedPhaseId(QuestDefinition def, QuestRuntimeData runtime) {
        if (def == null || runtime == null) return null;
        if (selectedPhaseId != null && !selectedPhaseId.isEmpty() && runtime.isPhaseActive(selectedPhaseId) && def.getPhase(selectedPhaseId) != null) return selectedPhaseId;
        String tQuest = QuestHudOverlay.INSTANCE.getTrackedQuestId(), tPhase = QuestHudOverlay.INSTANCE.getTrackedPhaseId();
        if (tQuest != null && tQuest.equals(runtime.getQuestId()) && tPhase != null && !tPhase.isEmpty() && runtime.isPhaseActive(tPhase) && def.getPhase(tPhase) != null) return selectedPhaseId = tPhase;
        String current = runtime.getCurrentPhaseId();
        if (current != null && !current.isEmpty() && runtime.isPhaseActive(current) && def.getPhase(current) != null) return selectedPhaseId = current;
        for (String pid : runtime.getActivePhaseIds()) if (def.getPhase(pid) != null) return selectedPhaseId = pid;
        return selectedPhaseId = null;
    }
}