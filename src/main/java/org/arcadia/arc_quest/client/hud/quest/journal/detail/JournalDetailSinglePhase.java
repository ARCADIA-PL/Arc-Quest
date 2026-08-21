package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalConstants;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalMarqueeTextRenderer;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.story.QuestStoryPanel;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
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
    private final List<JournalTypes.ChoiceButtonRect> currentChoiceButtons = new ArrayList<>();
    private final Map<Integer, Float> offerHoverAnims = new HashMap<>();
    private final List<OfferProgressRect> currentOfferProgressRects = new ArrayList<>();
    private final int[] descHitBox = new int[4];
    private final Map<String, TextLayoutCache> textLayoutCache = new HashMap<>();
    private final Map<String, String> itemNameCache = new HashMap<>();
    private final String objectiveCompletePrefix = Component.translatable("arc_quest.gui.journal.label.objective_complete_prefix").getString();
    private final String objectiveActivePrefix = Component.translatable("arc_quest.gui.journal.label.objective_active_prefix").getString();
    private final String clickToSubmitText = Component.translatable("arc_quest.gui.journal.label.click_to_submit").getString();
    private final String choosePathText = Component.translatable("arc_quest.gui.journal.section.choose_path").getString();
    private float[] detailObjReveal = new float[0];
    private float[] objProgressAnims = new float[0];
    private ResourceLocation intelSceneId = null;
    private int intelBtnLocalY = 0;
    private float intelBtnHoverAnim = 0f;
    private long lastChoiceClickAt = 0L;
    // 新增：描述文本交互相关状态
    private float descHoverAnim = 0f;
    private String currentDescPhaseId = null;

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
        descHoverAnim = 0f;
        currentDescPhaseId = null;
        textLayoutCache.clear();
    }

    private void safeScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        graphics.disableScissor();
        if (x2 > x1 && y2 > y1) screen.enableScissor(graphics, x1, y1, x2, y2);
    }

    public int render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, String phaseId, int x, int scrollAreaY, int scrollAreaW, int scrollAreaH, int mx, int my, float dt, int activeTheme, float dAlpha, int safeA, int localY) {
        PhaseDefinition phase = def.getPhase(phaseId);
        Font font = screen.getFont();
        currentChoiceButtons.clear();
        currentOfferProgressRects.clear();

        g.pose().pushPose();
        g.pose().translate(0, localY, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        Component phaseName = getPhaseDisplayName(phase);
        Component titleText = Component.translatable("arc_quest.gui.journal.section.current_phase", phaseName);

        int maxTitleW = (int) ((scrollAreaW - 10) / 0.8f);
        int nameAbsX = x;
        int nameAbsY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + localY;
        JournalMarqueeTextRenderer.drawComponent(g, font, titleText, 0, 0, maxTitleW,
                HudAnimUtil.withAlpha(activeTheme, safeA), true, nameAbsX, nameAbsY,
                x, scrollAreaY, x + scrollAreaW, scrollAreaY + scrollAreaH, this::safeScissor);

        g.pose().popPose();
        localY += 14;

        if (phase.hasDescription() || phase.hasStory()) {
            boolean hasStory = phase.getStory() != null && !phase.getStory().getString().isEmpty();
            boolean unreadStory = hasStory && !QuestStoryPanel.hasBeenOpened(entry.questId(), phaseId);
            String descriptionText = unreadStory
                    ? Component.translatable("arc_quest.gui.journal.label.unread_phase_story").getString()
                    : phase.getDescription().getString();
            float baseTextScale = unreadStory ? 0.98f : 0.85f;
            int maxW = (int) ((scrollAreaW - 4) / baseTextScale);
            List<String> phaseDescLines = getWrappedLines("desc:" + phaseId + ":" + unreadStory, descriptionText, maxW, font);

            int unscaledLineSpacing = font.lineHeight + 4;
            int visualLineSpacing = (int) (unscaledLineSpacing * baseTextScale);
            int visualTextHeight = (int) (font.lineHeight * baseTextScale);

            int blockH = Math.max(visualTextHeight, (phaseDescLines.size() - 1) * visualLineSpacing + visualTextHeight);

            // 计算全局绝对坐标与碰撞检测
            int absX = x + 12;
            int absY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + localY;
            int hitW = scrollAreaW - 4;
            int hitH = blockH;

            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            boolean isHovered = hasStory && !panelsActive && mx >= absX && mx <= absX + hitW && my >= absY && my <= absY + hitH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;

            descHoverAnim = HudAnimUtil.lerp(descHoverAnim, isHovered ? 1f : 0f, 0.2f, dt);
            if (isHovered) screen.requestPointerCursor();

            if (isHovered) {
                screen.setHoveredCustomTooltip(List.of(
                        Component.translatable("arc_quest.gui.journal.label.story_archive").withStyle(Style.EMPTY.withColor(activeTheme).withBold(true)),
                        Component.translatable("arc_quest.gui.journal.label.read_story").withStyle(Style.EMPTY.withColor(0xAAAAAA))
                ));
            }

            descHitBox[0] = absX;
            descHitBox[1] = absY;
            descHitBox[2] = hitW;
            descHitBox[3] = hitH;
            currentDescPhaseId = hasStory ? phaseId : null;

            float pulse = 0.5f - 0.5f * (float) Math.cos((Util.getMillis() % 2000L) / 2000f * Math.PI * 2.0);
            float breathScale = unreadStory ? baseTextScale * 0.018f * pulse : 0f;
            float currentScale = baseTextScale + breathScale + (0.045f * HudAnimUtil.easeOutCubic(descHoverAnim));

            g.pose().pushPose();
            // 设定枢轴点，使得放大时以中心左侧为原点，避免跳变
            float pivotX = 0;
            float pivotY = localY + blockH / 2f;
            g.pose().translate(pivotX, pivotY, 0);
            g.pose().scale(currentScale / baseTextScale, currentScale / baseTextScale, 1f);
            g.pose().translate(-pivotX, -pivotY, 0);

            g.pose().translate(0, localY, 0);
            g.pose().scale(baseTextScale, baseTextScale, 1f);

            int descColor = HudAnimUtil.lerpColor(unreadStory ? 0xDCE5EE : 0xAAAAAA,
                    activeTheme, HudAnimUtil.easeOutCubic(descHoverAnim));

            for (int i = 0; i < phaseDescLines.size(); i++) {
                g.drawString(font, phaseDescLines.get(i), 0, i * unscaledLineSpacing, HudAnimUtil.withAlpha(descColor, safeA), false);
            }
            g.pose().popPose();

            if (unreadStory && !phaseDescLines.isEmpty()) {
                float diamondScale = 0.90f + 0.07f * pulse + 0.06f * descHoverAnim;
                int diamondAlpha = (int) ((145 + 110 * pulse) * dAlpha);
                int diamondColor = isHovered ? 0xFF3030 : 0xD93A4A;
                float diamondX = Math.min(scrollAreaW - 8f,
                        font.width(phaseDescLines.get(0)) * currentScale + 8f);
                float diamondY = localY + blockH / 2f;
                g.pose().pushPose();
                g.pose().translate(diamondX, diamondY, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(45f));
                g.pose().scale(diamondScale, diamondScale, 1f);
                g.fill(-2, -2, 2, 2, HudAnimUtil.withAlpha(diamondColor, diamondAlpha));
                g.pose().popPose();
            }

            localY += blockH + 6;
        } else {
            currentDescPhaseId = null;
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
            if (oA <= 4) {
                localY += 22;
                continue;
            }

            int objX = (int) ((1f - HudAnimUtil.easeOutCubic(Math.min(1f, detailObjReveal[i]))) * 25f);
            int progress = runtime.getObjectiveProgress(phaseId, i);
            int required = phase.getObjectives().get(i).getRequiredCount();
            boolean complete = progress >= required;

            String objText = getObjectiveText(phase.getObjectives().get(i), complete);

            int textStartY = localY;
            List<String> originalWrappedLines = getWrappedLines("obj:" + phaseId + ":" + i + ":" + complete, objText, scrollAreaW - 40 - objX, font);
            int textBlockHeight = originalWrappedLines.size() * (font.lineHeight + 1);
            int barW = Math.min(scrollAreaW - 40 - objX, 325);

            boolean isOffer = ObjectiveType.OFFER.equals(phase.getObjectives().get(i).getType()) && progress < required;
            boolean canSubmit = isOffer && screen.getCurrentTab() == JournalTypes.Tab.ACTIVE;
            float hoverAnim = offerHoverAnims.getOrDefault(i, 0f);
            boolean isHovered = false;

            int hitX = objX + 2;
            int hitY = textStartY + 1;
            int hitW = Math.max(1, barW - 4);
            int hitH = textBlockHeight + 4;

            if (canSubmit) {
                boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
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

            List<String> renderLines = originalWrappedLines;
            if (canSubmit && isHovered) {
                ObjectiveEntry currentObj = phase.getObjectives().get(i);
                String submitBase = clickToSubmitText;
                String targetName = "";

                if (currentObj.hasTargetTag() && currentObj.getTargetTagTranslationKey() != null) {
                    targetName = Component.translatable(currentObj.getTargetTagTranslationKey()).getString();
                } else {
                    Item targetItem = BuiltInRegistries.ITEM.get(currentObj.getTargetId());
                    if (targetItem != null && targetItem != Items.AIR) {
                        targetName = getItemName(currentObj.getTargetId());
                    }
                }

                String finalText = targetName.isEmpty() ? submitBase : submitBase + " - " + targetName;
                renderLines = getWrappedLines("submit:" + phaseId + ":" + i + ":" + targetName, finalText, scrollAreaW - 40 - objX, font);
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

            int drawY = textStartY;
            if (canSubmit && isHovered) {
                drawY += (textBlockHeight - renderLines.size() * (font.lineHeight + 1)) / 2;
            }

            for (String line : renderLines) {
                String cleanLine = line.replace("§7", "").replace("§a", "").replace("§f", "");
                int baseColor = complete ? 0x88FF88 : 0xDDDDDD;
                if (canSubmit) baseColor = HudAnimUtil.lerpColor(baseColor, activeTheme, hoverAnim);
                g.drawString(font, cleanLine, objX, drawY, HudAnimUtil.withAlpha(baseColor, oA), false);
                drawY += font.lineHeight + 1;
            }
            g.pose().popPose();

            localY += textBlockHeight;

            float targetRatio = required > 0 ? Math.max(0f, Math.min(1f, (float) progress / required)) : 0f;
            objProgressAnims[i] = HudAnimUtil.lerp(objProgressAnims[i], targetRatio, 0.15f, dt);
            int fillW = (int) (barW * objProgressAnims[i]);

            RenderSystem.enableBlend();
            int emptyBgColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * oAlpha));
            g.fill(objX, localY, objX + barW, localY + 2, emptyBgColor);

            if (fillW > 0) {
                int fillColor = HudAnimUtil.withAlpha(complete ? 0x66FF66 : activeTheme, (int) (0xCC * oAlpha));
                g.fill(objX, localY, objX + fillW, localY + 2, fillColor);
                int brightColor = HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * oAlpha));
                g.fill(objX + fillW - 2, localY - 1, objX + fillW, localY + 3, brightColor);
            }

            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive()
                    || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            int absBarX = x + 12 + objX;
            int absBarY = scrollAreaY + 12 - (int) parent.getDetailScrollOffset() + localY;
            boolean progressBarHovered = !panelsActive
                    && mx >= absBarX && mx < absBarX + barW
                    && my >= absBarY - 2 && my < absBarY + 4
                    && my >= scrollAreaY && my < scrollAreaY + scrollAreaH;
            CollectObjectiveTooltip.request(screen, phase.getObjectives().get(i), progressBarHovered);

            if (canSubmit) {
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

            if (!phase.getObjectives().get(i).isBooleanProgress()) {
                g.pose().pushPose();
                g.pose().translate(objX + barW + 4, localY - 1, 0);
                g.pose().scale(0.7f, 0.7f, 1f);
                g.drawString(font, progress + " / " + required, 0, 0, HudAnimUtil.withAlpha(pColor, oA), false);
                g.pose().popPose();
            }

            localY += 12;
        }
        localY += 6;

        if (intelSceneId != null) {
            intelBtnLocalY = localY;
            int intelBtnAbsX = x + 12, intelBtnAbsY = (int) Math.round(scrollAreaY + 12 - parent.getDetailScrollOffset() + localY);
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            boolean btnHovered = !panelsActive && mx >= intelBtnAbsX && mx <= intelBtnAbsX + JournalConstants.INTEL_BTN_W && my >= intelBtnAbsY && my <= intelBtnAbsY + JournalConstants.INTEL_BTN_H && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;
            intelBtnHoverAnim = HudAnimUtil.step(intelBtnHoverAnim, btnHovered ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, 0, localY, JournalConstants.INTEL_BTN_W, JournalConstants.INTEL_BTN_H, "PHASE INTEL", activeTheme, HudAnimUtil.easeOutCubic(intelBtnHoverAnim), btnHovered);
            localY += JournalConstants.INTEL_BTN_H + 12;
        }

        if (JournalDetailPanel.shouldShowBranchChoices(def, runtime, phaseId)) {
            localY += 8;
            g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (80 * dAlpha)));
            localY += 10;
            g.pose().pushPose();
            g.pose().translate(0, localY, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, choosePathText, 0, 0, HudAnimUtil.withAlpha(0xFFCC66, safeA), false);
            g.pose().popPose();
            localY += 14;

            List<ChoiceOption> choices = phase.getChoices();
            for (int i = 0; i < choices.size(); i++) {
                ChoiceOption choice = choices.get(i);
                if (choice.getVisibleCondition() != null && !choice.getVisibleCondition().testClient(ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(), ClientQuestCache.INSTANCE.getAllFlags(), ClientQuestCache.INSTANCE.getAllVariables()))
                    continue;

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
                g.pose().pushPose();
                g.pose().translate(8, localY + (choiceBtnH - font.lineHeight * textScale) / 2f + 1, 0);
                g.pose().scale(textScale, textScale, 1f);

                String choiceText = (i + 1) + ". " + getChoiceText(choice);
                int maxChoiceW = (int) ((choiceBtnW - 16) / textScale);
                int stringAbsX = absX + 8;
                int stringAbsY = absY + (int) ((choiceBtnH - font.lineHeight * textScale) / 2f + 1);

                JournalMarqueeTextRenderer.drawString(g, font, choiceText, 0, 0, maxChoiceW,
                        HudAnimUtil.withAlpha(textColor, safeA), false, stringAbsX, stringAbsY,
                        x, scrollAreaY, x + scrollAreaW, scrollAreaY + scrollAreaH, this::safeScissor);

                g.pose().popPose();
                localY += choiceBtnH + 5;
            }
        }
        return localY;
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaY = y, scrollAreaH = h - 40;

        // 剧情档案拦截
        if (currentDescPhaseId != null && mx >= descHitBox[0] && mx <= descHitBox[0] + descHitBox[2] && my >= descHitBox[1] && my <= descHitBox[1] + descHitBox[3] && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
            if (!panelsActive) {
                String qid = screen.getCurrentEntries().get(screen.getSelectedIndex()).questId();
                QuestStoryPanel.trigger(qid, currentDescPhaseId);
                screen.playClick();
                return true;
            }
        }

        if (intelSceneId != null) {
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
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
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
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
            boolean panelsActive = QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestHistoryPanel.isActive() || QuestStoryPanel.isActive();
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

    private Component getPhaseDisplayName(PhaseDefinition phase) {
        Component name = phase.getDisplayName();
        return name != null && !name.getString().isEmpty() ? name : Component.literal(phase.getPhaseId());
    }

    private String getObjectiveText(ObjectiveEntry objective, boolean complete) {
        String key = "objective:" + complete + ":" + objective.getDisplayText().getString();
        return textLayoutCache.computeIfAbsent(key, k -> {
            TextLayoutCache cache = new TextLayoutCache();
            cache.text = (complete ? objectiveCompletePrefix : objectiveActivePrefix) + objective.getDisplayText().getString();
            return cache;
        }).text;
    }

    private String getChoiceText(ChoiceOption choice) {
        String key = "choice:" + choice.getDisplayText().getString();
        return textLayoutCache.computeIfAbsent(key, k -> {
            TextLayoutCache cache = new TextLayoutCache();
            cache.text = choice.getDisplayText().getString();
            return cache;
        }).text;
    }

    private List<String> getWrappedLines(String key, String text, int width, Font font) {
        TextLayoutCache cache = textLayoutCache.computeIfAbsent(key, k -> new TextLayoutCache());
        if (cache.lines == null || cache.width != width || !text.equals(cache.text)) {
            cache.text = text;
            cache.width = width;
            cache.lines = HudRenderUtil.wrapText(text, width, font);
        }
        return cache.lines;
    }

    private String getItemName(ResourceLocation itemId) {
        String key = itemId.toString();
        return itemNameCache.computeIfAbsent(key, k -> {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) return "";
            return new ItemStack(item).getHoverName().getString();
        });
    }

    private static class TextLayoutCache {
        String text;
        int width = -1;
        List<String> lines;
    }

    private record OfferProgressRect(int x, int y, int w, int h, String phaseId, int objectiveIndex) {
    }
}
