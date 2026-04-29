package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalConstants;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.quest.api.ChoiceOption;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JournalDetailSinglePhase {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;

    private float[] detailObjReveal = new float[0];
    private float[] objProgressAnims = new float[0];
    private final List<JournalTypes.ChoiceButtonRect> currentChoiceButtons = new ArrayList<>();

    private ResourceLocation intelSceneId = null;
    private int intelBtnLocalY = 0;
    private float intelBtnHoverAnim = 0f;
    private long lastChoiceClickAt = 0L;

    private final Map<Integer, Float> offerHoverAnims = new HashMap<>();

    private record OfferProgressRect(int x, int y, int w, int h, String phaseId, int objectiveIndex) {}
    private final List<OfferProgressRect> currentOfferProgressRects = new ArrayList<>();

    public JournalDetailSinglePhase(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    public void reset() {
        detailObjReveal = new float[0];
        objProgressAnims = new float[0];
        currentChoiceButtons.clear();
        intelSceneId = null;
        currentOfferProgressRects.clear();
        offerHoverAnims.clear();
    }

    public int render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, String phaseId, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, float dt, int activeTheme, float dAlpha, int safeA, int localY) {
        PhaseDefinition phase = def.getPhase(phaseId);
        Font font = screen.getFont();
        currentChoiceButtons.clear();
        currentOfferProgressRects.clear();

        g.pose().pushPose(); g.pose().translate(0, localY, 0); g.pose().scale(0.8f, 0.8f, 1f);
        String phaseName = phase.getDisplayName() != null && !phase.getDisplayName().getString().isEmpty() ? phase.getDisplayName().getString() : phase.getPhaseId();
        g.drawString(font, Component.translatable("arc_quest.gui.journal.section.current_phase", phaseName).getString(), 0, 0, HudAnimUtil.withAlpha(activeTheme, safeA), true);
        g.pose().popPose();
        localY += 14;

        if (phase.hasDescription()) {
            g.pose().pushPose(); g.pose().translate(4, localY, 0); g.pose().scale(0.8f, 0.8f, 1f);
            List<String> phaseDescLines = HudRenderUtil.wrapText(phase.getDescription().getString(), (int) ((scrollAreaW - 28) / 0.8f), font);
            for (String line : phaseDescLines) {
                g.drawString(font, line, 0, 0, HudAnimUtil.withAlpha(0x99BBFF, safeA), false);
                g.pose().translate(0, font.lineHeight + 1, 0);
            }
            g.pose().popPose();
            localY += phaseDescLines.size() * (font.lineHeight + 1) + 6;
        }

        intelSceneId = phase.getIntelSceneId();
        int objCount = phase.getObjectives().size();

        if (detailObjReveal.length != objCount) {
            detailObjReveal = new float[objCount];
            objProgressAnims = new float[objCount];
        }

        for (int i = 0; i < objCount; i++) {
            detailObjReveal[i] = HudAnimUtil.lerp(detailObjReveal[i], 1f, 0.1f + i * 0.03f, dt);
            float oAlpha = dAlpha * HudAnimUtil.easeOutCubic(Math.min(1f, detailObjReveal[i]));
            int oA = (int) (255 * oAlpha);
            if (oA <= 4) { localY += 22; continue; }

            int objX = (int) ((1f - HudAnimUtil.easeOutCubic(Math.min(1f, detailObjReveal[i]))) * 25f);
            int progress = runtime.getObjectiveProgress(phaseId, i);
            int required = phase.getObjectives().get(i).getRequiredCount();
            boolean complete = progress >= required;

            String objText = (complete ? Component.translatable("arc_quest.gui.journal.label.objective_complete_prefix").getString() : Component.translatable("arc_quest.gui.journal.label.objective_active_prefix").getString()) + phase.getObjectives().get(i).getDisplayText().getString();

            int textStartY = localY;
            List<String> wrappedObjLines = HudRenderUtil.wrapText(objText, scrollAreaW - 40 - objX, font);
            int textBlockHeight = wrappedObjLines.size() * (font.lineHeight + 1);
            int barW = Math.min(scrollAreaW - 40 - objX, 325);

            boolean isOffer = phase.getObjectives().get(i).getType() == ObjectiveType.OFFER && progress < required;
            boolean canSubmit = isOffer && screen.getCurrentTab() == JournalTypes.Tab.ACTIVE;
            float hoverAnim = offerHoverAnims.getOrDefault(i, 0f);
            boolean isHovered = false;

            int hitX = objX + 2;
            int hitY = textStartY + 1;
            int hitW = Math.max(1, barW - 4);
            int hitH = textBlockHeight + 4;

            if (canSubmit) {
                boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive();
                if (!panelsActive) {
                    int absX = x + 12 + hitX;
                    int absY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + hitY;
                    isHovered = mx >= absX && mx < absX + hitW && my >= absY && my < absY + hitH && my >= scrollAreaY && my < scrollAreaY + scrollAreaH;
                    hoverAnim = HudAnimUtil.lerp(hoverAnim, isHovered ? 1f : 0f, 0.2f, dt);
                    offerHoverAnims.put(i, hoverAnim);
                    currentOfferProgressRects.add(new OfferProgressRect(absX, absY, hitW, hitH, phaseId, i));
                } else {
                    offerHoverAnims.put(i, 0f);
                    hoverAnim = 0f;
                }
            }

            g.pose().pushPose();
            if (canSubmit && hoverAnim > 0.01f) {
                float scale = 1.0f + 0.05f * hoverAnim;
                float pivotX = objX;
                float pivotY = textStartY + textBlockHeight / 2.0f;
                g.pose().translate(pivotX, pivotY, 0);
                g.pose().scale(scale, scale, 1f);
                g.pose().translate(-pivotX, -pivotY, 0);
            }

            for (String line : wrappedObjLines) {
                String cleanLine = line.replace("§7", "").replace("§a", "").replace("§f", "");
                int baseColor = complete ? 0x88FF88 : 0xDDDDDD;
                if (canSubmit) baseColor = HudAnimUtil.lerpColor(baseColor, activeTheme, hoverAnim);
                g.drawString(font, cleanLine, objX, localY, HudAnimUtil.withAlpha(baseColor, oA), true);
                localY += font.lineHeight + 1;
            }
            g.pose().popPose();

            float targetRatio = required > 0 ? Math.max(0f, Math.min(1f, (float) progress / required)) : 0f;
            objProgressAnims[i] = HudAnimUtil.lerp(objProgressAnims[i], targetRatio, 0.15f, dt);
            int fillW = (int) (barW * objProgressAnims[i]);

            RenderSystem.enableBlend();
            int emptyBgColor = HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x22 * oAlpha));
            g.fill(objX, localY, objX + barW, localY + 2, emptyBgColor);

            if (fillW > 0) {
                int fillColor = HudAnimUtil.withAlpha(complete ? 0x66FF66 : activeTheme, (int)(0xCC * oAlpha));
                g.fill(objX, localY, objX + fillW, localY + 2, fillColor);
                int brightColor = HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * oAlpha));
                g.fill(objX + fillW - 2, localY - 1, objX + fillW, localY + 3, brightColor);
            }

            if (canSubmit) {
                boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive();
                if (!panelsActive) {
                    float breath = (float) (Math.sin(Util.getMillis() / 250.0) * 0.5f + 0.5f);
                    int glowColor = HudAnimUtil.withAlpha(activeTheme, (int) (60 * breath * oAlpha));
                    int hoverGlow = HudAnimUtil.withAlpha(0xFFFFFF, (int) (40 * hoverAnim * oAlpha));
                    g.fill(objX, localY, objX + barW, localY + 2, glowColor);
                    if (hoverAnim > 0.01f) {
                        g.fill(objX, localY, objX + barW, localY + 2, hoverGlow);
                    }
                }
            }

            int pColor = canSubmit ? HudAnimUtil.lerpColor(0x999999, 0xFFFFFF, hoverAnim) : 0x999999;

            g.pose().pushPose();
            g.pose().translate(objX + barW + 4, localY - 1, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, progress + " / " + required, 0, 0, HudAnimUtil.withAlpha(pColor, oA), false);
            g.pose().popPose();

            localY += 12;
        }
        localY += 6;

        if (intelSceneId != null) {
            intelBtnLocalY = localY;
            int intelBtnAbsX = x + 12, intelBtnAbsY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + localY);
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive();
            boolean btnHovered = !panelsActive && mx >= intelBtnAbsX && mx <= intelBtnAbsX + JournalConstants.INTEL_BTN_W && my >= intelBtnAbsY && my <= intelBtnAbsY + JournalConstants.INTEL_BTN_H && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
            intelBtnHoverAnim = HudAnimUtil.step(intelBtnHoverAnim, btnHovered ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, 0, localY, JournalConstants.INTEL_BTN_W, JournalConstants.INTEL_BTN_H, "PHASE INTEL", activeTheme, HudAnimUtil.easeOutCubic(intelBtnHoverAnim), btnHovered);
            localY += JournalConstants.INTEL_BTN_H + 12;
        }

        if (JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId)) {
            localY += 8;
            g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (80 * dAlpha)));
            localY += 10;
            g.pose().pushPose(); g.pose().translate(0, localY, 0); g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, Component.translatable("arc_quest.gui.journal.section.choose_path").getString(), 0, 0, HudAnimUtil.withAlpha(0xFFCC66, safeA), true);
            g.pose().popPose();
            localY += 14;

            List<ChoiceOption> choices = phase.getChoices();
            for (int i = 0; i < choices.size(); i++) {
                ChoiceOption choice = choices.get(i);
                if (choice.getVisibleCondition() != null && !choice.getVisibleCondition().testClient(ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(), ClientQuestCache.INSTANCE.getAllFlags(), ClientQuestCache.INSTANCE.getAllVariables())) continue;

                int choiceBtnW = scrollAreaW - 24, choiceBtnH = 22;
                int absX = x + 12, absY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + localY;
                currentChoiceButtons.add(new JournalTypes.ChoiceButtonRect(absX, absY, choiceBtnW, choiceBtnH, i, phaseId));

                boolean isHovered = mx >= absX && mx <= absX + choiceBtnW && my >= absY && my <= absY + choiceBtnH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
                int borderColor = isHovered ? activeTheme : 0x666666;
                int textColor = isHovered ? activeTheme : 0xCCCCCC;

                g.fill(0, localY, choiceBtnW, localY + choiceBtnH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isHovered ? 0x22 : 0x11) * dAlpha)));
                g.fill(0, localY, choiceBtnW, localY + 1, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));
                g.fill(0, localY + choiceBtnH - 1, choiceBtnW, localY + choiceBtnH, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));
                g.fill(0, localY, 1, localY + choiceBtnH, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));
                g.fill(choiceBtnW - 1, localY, choiceBtnW, localY + choiceBtnH, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));

                float textScale = 0.8f;
                g.pose().pushPose(); g.pose().translate(8, localY + (choiceBtnH - font.lineHeight * textScale) / 2f + 1, 0); g.pose().scale(textScale, textScale, 1f);
                g.drawString(font, font.plainSubstrByWidth((i + 1) + ". " + choice.getDisplayText().getString(), (int) ((choiceBtnW - 16) / textScale)), 0, 0, HudAnimUtil.withAlpha(textColor, safeA), false);
                g.pose().popPose();
                localY += choiceBtnH + 5;
            }
        }
        return localY;
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaY = y, scrollAreaH = h - 40;
        if (intelSceneId != null) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive();
            if (!panelsActive) {
                int intelBtnAbsX = x + 12, intelBtnAbsY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + intelBtnLocalY);
                if (mx >= intelBtnAbsX && mx < intelBtnAbsX + JournalConstants.INTEL_BTN_W && my >= intelBtnAbsY && my < intelBtnAbsY + JournalConstants.INTEL_BTN_H && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                    screen.playClick();
                    QuestIntelPanel.trigger(intelSceneId, screen.getCurrentThemeColor(), x, y, w, h);
                    return true;
                }
            }
        }
        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentOfferProgressRects.isEmpty()) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive();
            if (!panelsActive) {
                for (OfferProgressRect rect : currentOfferProgressRects) {
                    if (mx >= rect.x && mx < rect.x + rect.w && my >= rect.y && my < rect.y + rect.h && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                        String qid = screen.getCurrentEntries().get(screen.getSelectedIndex()).questId();
                        QuestOfferPanel.trigger(qid, rect.phaseId, rect.objectiveIndex);
                        screen.playClick();
                        return true;
                    }
                }
            }
        }
        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentChoiceButtons.isEmpty()) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive();
            if (!panelsActive) {
                for (JournalTypes.ChoiceButtonRect rect : currentChoiceButtons) {
                    if (mx >= rect.x && mx < rect.x + rect.w && my >= rect.y && my < rect.y + rect.h && my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
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
        }
        return false;
    }
}