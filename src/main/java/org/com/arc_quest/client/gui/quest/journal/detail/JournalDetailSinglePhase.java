package org.com.arc_quest.client.gui.quest.journal.detail;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.gui.QuestHudOverlay;
import org.com.arc_quest.client.gui.quest.QuestRewardRenderer;
import org.com.arc_quest.client.gui.quest.journal.JournalConstants;
import org.com.arc_quest.client.gui.quest.journal.JournalTypes;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.gui.render.QuestIntelPanel;
import org.com.arc_quest.quest.api.ChoiceOption;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.com.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;

public class JournalDetailSinglePhase {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;

    private float[] detailObjReveal = new float[0];
    // 全新：用于存储每个目标进度条的平滑动画值
    private float[] objProgressAnims = new float[0];

    private final List<JournalTypes.ChoiceButtonRect> currentChoiceButtons = new ArrayList<>();

    private ResourceLocation intelSceneId = null;
    private int intelBtnLocalY = 0;
    private float intelBtnHoverAnim = 0f;
    private long lastChoiceClickAt = 0L;

    public JournalDetailSinglePhase(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    public void reset() {
        detailObjReveal = new float[0];
        objProgressAnims = new float[0]; // 重置时清空动画状态
        currentChoiceButtons.clear();
        intelSceneId = null;
    }

    public int render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, String phaseId, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, float dt, int activeTheme, float dAlpha, int safeA, int localY) {
        PhaseDefinition phase = def.getPhase(phaseId);
        Font font = screen.getFont();
        currentChoiceButtons.clear();

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

        // 动态初始化/扩容动画数组
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
            List<String> wrappedObjLines = HudRenderUtil.wrapText(objText, scrollAreaW - 40 - objX, font);
            for (String line : wrappedObjLines) {
                g.drawString(font, line, objX, localY, HudAnimUtil.withAlpha(complete ? 0x88FF88 : 0xDDDDDD, oA), true);
                localY += font.lineHeight + 1;
            }

            // ==========================================
            // 全新：进度条丝滑插值运算与极简机能风绘制
            // ==========================================
            int barW = scrollAreaW - 40 - objX;
            float targetRatio = required > 0 ? Math.max(0f, Math.min(1f, (float) progress / required)) : 0f;

            // 使用 dt 进行平滑插值追踪目标进度 (0.15f 为平滑速率)
            objProgressAnims[i] = HudAnimUtil.lerp(objProgressAnims[i], targetRatio, 0.15f, dt);

            // 基于平滑动画计算填充宽度
            int fillW = (int) (barW * objProgressAnims[i]);

            RenderSystem.enableBlend();

            // 极简暗槽背景
            int emptyBgColor = HudAnimUtil.withAlpha(0xFFFFFF, (int)(0x22 * oAlpha));
            g.fill(objX, localY, objX + barW, localY + 2, emptyBgColor);

            if (fillW > 0) {
                // 主题色填充槽
                int fillColor = HudAnimUtil.withAlpha(complete ? 0x66FF66 : activeTheme, (int)(0xCC * oAlpha));
                g.fill(objX, localY, objX + fillW, localY + 2, fillColor);
                // 锐利的光点探针 (上下延伸出 1px)
                int brightColor = HudAnimUtil.withAlpha(0xFFFFFF, (int)(255 * oAlpha));
                g.fill(objX + fillW - 2, localY - 1, objX + fillW, localY + 3, brightColor);
            }
            // ==========================================

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            g.pose().pushPose(); g.pose().translate(objX + barW + 4, localY - 1, 0); g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, progress + " / " + required, 0, 0, HudAnimUtil.withAlpha(0x999999, oA), false);
            g.pose().popPose();
            localY += 12;
        }
        localY += 6;

        if (intelSceneId != null) {
            intelBtnLocalY = localY;
            int intelBtnAbsX = x + 12, intelBtnAbsY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + localY);
            boolean btnHovered = mx >= intelBtnAbsX && mx <= intelBtnAbsX + JournalConstants.INTEL_BTN_W && my >= intelBtnAbsY && my <= intelBtnAbsY + JournalConstants.INTEL_BTN_H && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
            intelBtnHoverAnim = HudAnimUtil.step(intelBtnHoverAnim, btnHovered ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, 0, localY, JournalConstants.INTEL_BTN_W, JournalConstants.INTEL_BTN_H, "PHASE INTEL", activeTheme, HudAnimUtil.easeOutCubic(intelBtnHoverAnim), btnHovered);
            localY += JournalConstants.INTEL_BTN_H + 12;
        }

        if (!phase.getPhaseRewards().isEmpty()) {
            g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (50 * dAlpha)));
            localY += 6;
            g.pose().pushPose(); g.pose().translate(0, localY, 0); g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, Component.translatable("arc_quest.gui.journal.section.phase_rewards").getString(), 0, 0, HudAnimUtil.withAlpha(0xFFCC66, safeA), true);
            g.pose().popPose();
            localY += 11;
            g.pose().pushPose(); g.pose().translate(6, localY, 0); g.pose().scale(0.85f, 0.85f, 1f);
            int phaseRewardH = QuestRewardRenderer.render(g, phase.getPhaseRewards(), (int) ((scrollAreaW - 24) / 0.85f), safeA);
            g.pose().popPose();
            localY += (int) (phaseRewardH * 0.85f) + 4;
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
            int intelBtnAbsX = x + 12, intelBtnAbsY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + intelBtnLocalY);
            if (mx >= intelBtnAbsX && mx <= intelBtnAbsX + JournalConstants.INTEL_BTN_W && my >= intelBtnAbsY && my <= intelBtnAbsY + JournalConstants.INTEL_BTN_H && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
                screen.playClick();
                QuestIntelPanel.trigger(intelSceneId, screen.getCurrentThemeColor(), x, y, w, h);
                return true;
            }
        }
        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && !currentChoiceButtons.isEmpty()) {
            for (JournalTypes.ChoiceButtonRect rect : currentChoiceButtons) {
                if (mx >= rect.x && mx <= rect.x + rect.w && my >= rect.y && my <= rect.y + rect.h && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
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
}