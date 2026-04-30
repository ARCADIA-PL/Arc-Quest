package org.arcadia.arc_quest.client.hud.quest.offer;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SSubmitOfferPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QuestOfferPanel {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 140;

    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
    private static final float CLEAR_TIME = 1.4f; // CLEAR 动画持续时间
    private static final long SUBMIT_COOLDOWN_MS = 100L;
    private static boolean active = false;
    private static boolean closing = false;
    private static boolean cleared = false;
    private static long lastRenderMs = 0L;
    private static float enterTimer = 0f;
    private static float exitTimer = 0f;
    private static float clearTimer = 0f;
    private static String questId;
    private static String phaseId;
    private static int objectiveIndex;
    private static int themeColor = 0x5AD7FF;
    private static OfferVM lastValidVm = null; // 缓存断联前的最后影像
    private static ItemStack hoveredStack = ItemStack.EMPTY;
    private static int iconCycleTicker = 0;
    private static int iconCycleIndex = 0;
    private static long lastSubmitClickMs = 0L;
    private static float submitFeedbackAnim = 0f;
    private static boolean submitFeedbackSuccess = false;
    private static int lastKnownProgress = -1;
    private static long pendingSubmitCheckAt = 0L;

    private static String cachedTagKey = "";
    private static List<ItemStack> cachedTagIcons = List.of();

    private static float submitHoverAnim = 0f;
    private static float itemSlotHoverAnim = 0f;
    private static float currentProgressAnim = 0f;

    private static boolean isDraggingSlider = false;
    private static int sliderValue = 1;
    private static float sliderHoverAnim = 0f;
    private static float visualThumbX = -1f;

    private static float currentScale = 1.0f;
    private static float currentDrawX = 0;
    private static float currentDrawY = 0;

    private static long autoCloseAtMs = 0L;
    private static FinishMode finishMode = FinishMode.NONE;
    private static long finishDecideAtMs = 0L;
    private QuestOfferPanel() {
    }

    public static void trigger(String qid, String pid, int objIndex) {
        questId = qid;
        phaseId = pid;
        objectiveIndex = objIndex;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(qid, 0x5AD7FF);
        active = true;
        closing = false;
        cleared = false;
        enterTimer = 0f;
        exitTimer = 0f;
        clearTimer = 0f;
        lastRenderMs = System.currentTimeMillis();
        iconCycleTicker = 0;
        iconCycleIndex = 0;
        hoveredStack = ItemStack.EMPTY;
        submitFeedbackAnim = 0f;
        submitFeedbackSuccess = false;
        lastKnownProgress = -1;
        pendingSubmitCheckAt = 0L;
        cachedTagKey = "";
        cachedTagIcons = List.of();
        submitHoverAnim = 0f;
        itemSlotHoverAnim = 0f;
        isDraggingSlider = false;
        sliderValue = 1;
        sliderHoverAnim = 0f;
        visualThumbX = -1f;
        finishMode = FinishMode.NONE;
        finishDecideAtMs = 0L;
        autoCloseAtMs = 0L;

        OfferVM initialVm = resolveOfferViewModel();
        lastValidVm = initialVm;
        currentProgressAnim = initialVm != null ? initialVm.current : 0f;
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
        if (cleared) return true;
        if (keyCode == 256 || keyCode == 69) {
            close();
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || closing || button != 0) return false;
        if (cleared) return true; // 清算动画期间锁定！

        float scaledW = PANEL_W * currentScale;
        float scaledH = PANEL_H * currentScale;

        if (mx < currentDrawX || mx > currentDrawX + scaledW || my < currentDrawY || my > currentDrawY + scaledH) {
            close();
            return true;
        }

        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        OfferVM vm = resolveOfferViewModel();
        if (vm == null) return true;

        int remain = Math.max(0, vm.required - vm.current);
        int canSubmit = Math.max(0, vm.canSubmitNow);
        int maxSelectable = Math.max(0, Math.min(50, Math.min(remain, canSubmit)));

        int sliderW = 160;
        int sliderX = PANEL_W / 2 - sliderW / 2;
        int sliderY = 95;
        if (maxSelectable > 1 && lx >= sliderX - 5 && lx <= sliderX + sliderW + 5 && ly >= sliderY - 6 && ly <= sliderY + 8) {
            isDraggingSlider = true;
            return true;
        }

        int btnW = 140;
        int btnH = 16;
        int btnX = PANEL_W / 2 - btnW / 2;
        int btnY = PANEL_H - btnH - 10;

        if (lx >= btnX && lx <= btnX + btnW && ly >= btnY && ly <= btnY + btnH) {
            if (sliderValue <= 0 || maxSelectable <= 0) {
                submitFeedbackSuccess = false;
                submitFeedbackAnim = 1f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6F));
                return true;
            }
            long now = Util.getMillis();
            if (now - lastSubmitClickMs < SUBMIT_COOLDOWN_MS) return true;
            lastSubmitClickMs = now;
            lastKnownProgress = vm.current;
            pendingSubmitCheckAt = now + 100L;
            ArcQuestNetwork.sendSubmitOffer(C2SSubmitOfferPacket.of(questId, phaseId, objectiveIndex, sliderValue));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return true;
    }

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        float finalScale = (screenH * 0.55f) / (float) PANEL_H;
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

        if (closing) {
            scX2 = (int) (currentDrawX + drawWidth * (1.0f - wipeProgress));
        } else if (enterTimer < ENTER_TIME) {
            scX2 = (int) (currentDrawX + drawWidth * revealProgress);
        }

        hoveredStack = ItemStack.EMPTY;

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);
        g.fill(-1000, -1000, screenW + 1000, screenH + 1000, HudAnimUtil.withAlpha(0x000000, (int) (100 * alphaF)));
        g.enableScissor(scX1, (int) (currentDrawY - 10), scX2, (int) (currentDrawY + drawHeight + 10));
        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(scaleAnim, scaleAnim, 1f);

        int alphaInt = Math.max(0, Math.min(255, (int) (255 * alphaF)));
        renderPanel(g, mc.font, alphaInt, alphaF, dt, mx, my);

        g.pose().popPose();
        g.disableScissor();
        g.pose().popPose();

        if (!hoveredStack.isEmpty() && !closing && !cleared) {
            Minecraft mcForTip = Minecraft.getInstance();
            if (mcForTip.player != null) {
                List<Component> lines = hoveredStack.getTooltipLines(
                        mcForTip.player,
                        mcForTip.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL
                );
                renderCyberTooltip(g, mcForTip.font, lines, mx, my, themeColor);
            }
        }
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H;
        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        OfferVM vm = resolveOfferViewModel();
        long nowMs = Util.getMillis();

        if (!closing && vm == null && lastValidVm != null) {
            finishMode = FinishMode.CLEARED_CLOSE;
        }

        if (!closing && finishMode == FinishMode.NONE && vm != null && vm.required > 0 && vm.current >= vm.required) {
            finishMode = FinishMode.PENDING_NORMAL;
            finishDecideAtMs = nowMs + 220L;
        }

        if (!closing && finishMode == FinishMode.PENDING_NORMAL && nowMs >= finishDecideAtMs) {
            finishMode = FinishMode.NORMAL_CLOSE;
            autoCloseAtMs = nowMs + 120L;
        }

        if (vm != null) {
            lastValidVm = vm;
        }

        if (finishMode == FinishMode.CLEARED_CLOSE && lastValidVm != null && !cleared && !closing) {
            cleared = true;
            clearTimer = 0f;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
        }

        if (cleared) {
            clearTimer += dt;
            if (clearTimer >= CLEAR_TIME && !closing) {
                close();
            }
        }

        OfferVM renderVm = cleared ? lastValidVm : vm;
        if (renderVm == null) {
            if (active && !closing) close();
            return;
        }

        updateSubmitFeedback(renderVm, dt);

        if (finishMode == FinishMode.NORMAL_CLOSE && !cleared && !closing && nowMs >= autoCloseAtMs) {
            close();
            return;
        }

        int cyberEdgeWidth = 3;
        int bgAlpha = (int) (0x99 * alphaF);
        int borderAlpha = (int) (0x66 * alphaF);
        int borderRgb = 0xCCCCCC;

        int feedbackTargetColor = submitFeedbackSuccess ? 0x33FF66 : 0xFF3333;
        int currentEdgeColor = cleared ? 0x33FF66 : HudAnimUtil.lerpColor(themeColor, feedbackTargetColor, submitFeedbackAnim);

        g.fill(cyberEdgeWidth, 0, PW, PH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(cyberEdgeWidth, 0, PW, 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(cyberEdgeWidth, PH - 1, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(PW - 1, 0, PW, PH, HudAnimUtil.withAlpha(borderRgb, borderAlpha));

        HudRenderUtil.drawCyberneticEdge(g, 0, 0, PH, currentEdgeColor, alpha);

        float contentAlphaMult = cleared ? Math.max(0f, 1f - (clearTimer * 4f)) : 1f;
        int contentAlpha = (int) (alpha * contentAlphaMult);
        float contentAlphaF = alphaF * contentAlphaMult;

        if (contentAlpha > 5) {
            int topBarH = 22;
            g.pose().pushPose();
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(font, "SYS.ARC_QUEST // UPLOAD PROTOCOL", 16, 6, HudAnimUtil.withAlpha(0x667788, contentAlpha), false);
            g.pose().popPose();
            g.fill(10, topBarH - 1, PW - 10, topBarH, HudAnimUtil.withAlpha(borderRgb, (int) (borderAlpha * contentAlphaMult)));

            int contentY = topBarH + 12;
            g.drawString(font, font.plainSubstrByWidth(renderVm.title, PW - 70), 50, contentY + 2, HudAnimUtil.withAlpha(0xFFFFFF, contentAlpha), true);

            String progressText = renderVm.current + " / " + renderVm.required;
            g.pose().pushPose();
            g.pose().scale(0.85f, 0.85f, 1f);
            g.drawString(font, "STATUS: " + progressText, (int) (50 / 0.85f), (int) ((contentY + 14) / 0.85f), HudAnimUtil.withAlpha(0x99AABB, contentAlpha), false);
            g.pose().popPose();

            int iconX = 20, iconY = contentY;
            boolean isHoverSlot = !cleared && lx >= iconX - 2 && lx <= iconX + 20 && ly >= iconY - 2 && ly <= iconY + 20;
            itemSlotHoverAnim = HudAnimUtil.step(itemSlotHoverAnim, isHoverSlot ? 1f : 0f, 15f, dt);

            int slotBorderAlpha = (int) ((0x44 + 0x88 * itemSlotHoverAnim) * contentAlphaF);
            HudAnimUtil.drawFrame(g, iconX - 4, iconY - 4, 24, 24, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * contentAlphaF)), HudAnimUtil.withAlpha(themeColor, slotBorderAlpha));

            iconCycleTicker++;
            if (iconCycleTicker >= 60) {
                iconCycleTicker = 0;
                if (renderVm.iconCandidates.size() > 1)
                    iconCycleIndex = (iconCycleIndex + 1) % renderVm.iconCandidates.size();
            }

            ItemStack icon = renderVm.iconCandidates.isEmpty() ? ItemStack.EMPTY : renderVm.iconCandidates.get(iconCycleIndex % renderVm.iconCandidates.size());
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.renderItem(icon, iconX, iconY);
                g.renderItemDecorations(font, icon, iconX, iconY);
                g.pose().popPose();
                if (isHoverSlot) hoveredStack = icon;
            }

            currentProgressAnim += (renderVm.current - currentProgressAnim) * Math.min(1f, dt * 10f);
            if (cleared) currentProgressAnim = renderVm.required;

            int barX = 20, barY = contentY + 36, barW = PW - 40, barH = 4;
            float ratio = renderVm.required <= 0 ? 0f : Math.max(0f, Math.min(1f, currentProgressAnim / renderVm.required));
            int fillW = Math.max(0, (int) (barW * ratio));

            g.fill(barX, barY, barX + barW, barY + barH, HudAnimUtil.withAlpha(0xFFFFFF, (int) (20 * contentAlphaF)));
            HudAnimUtil.drawFrame(g, barX - 1, barY - 1, barW + 2, barH + 2, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (40 * contentAlphaF)));

            if (fillW > 0) {
                g.fill(barX, barY, barX + fillW, barY + barH, HudAnimUtil.withAlpha(themeColor, contentAlpha));
                g.fill(barX + fillW - 2, barY - 2, barX + fillW + 1, barY + barH + 2, HudAnimUtil.withAlpha(0xFFFFFF, contentAlpha));
            }

            int remain = Math.max(0, renderVm.required - renderVm.current);
            int canSubmit = Math.max(0, renderVm.canSubmitNow);
            int maxSelectable = Math.max(0, Math.min(50, Math.min(remain, canSubmit)));

            if (sliderValue > maxSelectable) sliderValue = Math.max(1, maxSelectable);
            if (sliderValue < 1 && maxSelectable > 0) sliderValue = 1;
            if (maxSelectable == 0) sliderValue = 0;

            int sliderW = 160;
            int sliderX = PW / 2 - sliderW / 2;
            int sliderY = 95;

            if (isDraggingSlider && !cleared) {
                if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                    isDraggingSlider = false;
                } else if (maxSelectable > 1) {
                    float pct = (lx - sliderX) / (float) sliderW;
                    pct = Math.max(0f, Math.min(1f, pct));
                    sliderValue = 1 + Math.round(pct * (maxSelectable - 1));
                }
            }

            boolean sliderHover = !cleared && !isDraggingSlider && maxSelectable > 1 && lx >= sliderX - 5 && lx <= sliderX + sliderW + 5 && ly >= sliderY - 6 && ly <= sliderY + 8;
            sliderHoverAnim = HudAnimUtil.step(sliderHoverAnim, sliderHover ? 1f : 0f, 18f, dt);

            g.pose().pushPose();
            g.pose().scale(0.85f, 0.85f, 1f);
            String qtyText = "QUANTITY // " + (maxSelectable == 0 ? "0" : sliderValue);
            int tW = font.width(qtyText);
            g.drawString(font, qtyText, (int) ((PW / 2f) / 0.85f) - tW / 2, (int) ((sliderY - 12) / 0.85f), HudAnimUtil.withAlpha(0xAAAAAA, contentAlpha), false);
            g.pose().popPose();

            g.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 1, HudAnimUtil.withAlpha(0xFFFFFF, (int) (30 * contentAlphaF)));

            if (maxSelectable > 0) {
                float targetPct = maxSelectable > 1 ? (float) (sliderValue - 1) / (maxSelectable - 1) : 1f;
                float targetThumbX = sliderX + (targetPct * sliderW);
                if (visualThumbX < 0) visualThumbX = targetThumbX;
                visualThumbX += (targetThumbX - visualThumbX) * Math.min(1f, dt * 25f);
                g.fill(sliderX, sliderY, (int) visualThumbX, sliderY + 2, HudAnimUtil.withAlpha(themeColor, contentAlpha));
                int tX = (int) visualThumbX;
                int thumbActiveColor = isDraggingSlider ? 0xFFFFFF : HudAnimUtil.lerpColor(themeColor, 0xFFFFFF, sliderHoverAnim);
                g.fill(tX - 1, sliderY - 2, tX + 1, sliderY + 3, HudAnimUtil.withAlpha(thumbActiveColor, contentAlpha));
            }

            int btnW = 140;
            int btnH = 16;
            int btnX = PW / 2 - btnW / 2;
            int btnY = PANEL_H - btnH - 10;

            boolean disabled = sliderValue <= 0 || maxSelectable <= 0;
            boolean hoverSubmit = !cleared && !disabled && lx >= btnX && lx <= btnX + btnW && ly >= btnY && ly <= btnY + btnH;
            submitHoverAnim = HudAnimUtil.step(submitHoverAnim, hoverSubmit ? 1f : 0f, 15f, dt);

            drawCyberButton(g, font, btnX, btnY, btnW, btnH, "SUBMIT [ " + sliderValue + " ]", submitHoverAnim, disabled, contentAlpha, contentAlphaF);

            if (disabled && remain > 0) {
                String hint = "Insufficient items: need " + remain + ", have " + canSubmit;
                g.pose().pushPose();
                g.pose().scale(0.7f, 0.7f, 1f);
                g.drawString(font, hint, (int) ((btnX + btnW / 2f) / 0.7f) - font.width(hint) / 2, (int) ((sliderY + 8) / 0.7f), HudAnimUtil.withAlpha(0xAA4444, contentAlpha), false);
                g.pose().popPose();
            }
        }

        if (cleared) {
            g.fill(cyberEdgeWidth, 0, PW, PH, HudAnimUtil.withAlpha(0x000000, (int) (80 * Math.min(1f, clearTimer * 3f))));

            float clearScaleBase = Math.min(1f, clearTimer / 0.2f);
            float textScale = 1.8f - 0.5f * (float) Math.pow(clearScaleBase, 3);

            float clearAlphaF = 1f;
            if (clearTimer > CLEAR_TIME - 0.3f) { // 最后0.3秒渐隐
                clearAlphaF = Math.max(0f, (CLEAR_TIME - clearTimer) / 0.3f);
            }
            int tAlpha = (int) (255 * clearAlphaF * alphaF);

            g.pose().pushPose();
            g.pose().translate(PW / 2f, PH / 2f, 100);
            g.pose().scale(textScale, textScale, 1f);

            String clearTxt = "[ // CLEARED // ]";
            int tw = font.width(clearTxt);
            g.drawCenteredString(font, clearTxt, 0, -font.lineHeight / 2, HudAnimUtil.withAlpha(themeColor, tAlpha));
            g.pose().popPose();

            if (clearTimer < 0.6f) {
                float scanLineY = PH * (clearTimer / 0.6f);
                g.fill(cyberEdgeWidth, (int) scanLineY, PW, (int) scanLineY + 1, HudAnimUtil.withAlpha(themeColor, (int) (100 * (1f - clearTimer / 0.6f))));
            }
        }
    }

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

    private static void updateSubmitFeedback(OfferVM vm, float dt) {
        if (submitFeedbackAnim > 0f) submitFeedbackAnim = Math.max(0f, submitFeedbackAnim - dt * 1.25f);
        if (vm == null) return;
        if (pendingSubmitCheckAt > 0L && Util.getMillis() >= pendingSubmitCheckAt) {
            pendingSubmitCheckAt = 0L;
            if (lastKnownProgress >= 0 && vm.current > lastKnownProgress) {
                submitFeedbackSuccess = true;
            } else {
                submitFeedbackSuccess = false;
            }
            submitFeedbackAnim = 1f;
        }
    }

    private static OfferVM resolveOfferViewModel() {
        var data = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (data == null || !data.isPhaseActive(phaseId)) return null;
        var def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return null;
        var phase = def.getPhase(phaseId);
        if (phase == null) return null;
        if (objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) return null;
        ObjectiveEntry obj = phase.getObjectives().get(objectiveIndex);
        if (obj.getType() != ObjectiveType.OFFER) return null;
        int required = Math.max(1, obj.getRequiredCount());
        int current = data.getObjectiveProgress(phaseId, objectiveIndex);
        List<ItemStack> candidates = resolveIconCandidates(obj);
        int canSubmitNow = resolveOfferableCount(obj);
        return new OfferVM(obj.getDisplayText().getString(), required, current, canSubmitNow, candidates);
    }

    private static List<ItemStack> resolveIconCandidates(ObjectiveEntry obj) {
        String targetTag = obj.getExtra("target_tag");
        if (targetTag != null && !targetTag.isEmpty()) {
            if (targetTag.equals(cachedTagKey) && !cachedTagIcons.isEmpty()) return cachedTagIcons;
            try {
                ResourceLocation tagId = ResourceLocation.parse(targetTag);
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                List<ItemStack> list = new ArrayList<>();
                for (Item i : ForgeRegistries.ITEMS.getValues()) {
                    ItemStack st = new ItemStack(i);
                    if (!st.isEmpty() && st.is(tag)) list.add(st);
                }
                if (list.isEmpty()) list = Collections.singletonList(ItemStack.EMPTY);
                cachedTagKey = targetTag;
                return cachedTagIcons = list;
            } catch (Exception ignored) {
                cachedTagKey = targetTag;
                return cachedTagIcons = Collections.singletonList(ItemStack.EMPTY);
            }
        }
        Item item = ForgeRegistries.ITEMS.getValue(obj.getTargetId());
        if (item == null) return Collections.singletonList(ItemStack.EMPTY);
        return Collections.singletonList(new ItemStack(item));
    }

    private static int resolveOfferableCount(ObjectiveEntry obj) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        String targetTag = obj.getExtra("target_tag");
        int total = 0;
        if (targetTag != null && !targetTag.isEmpty()) {
            try {
                ResourceLocation tagId = ResourceLocation.parse(targetTag);
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                for (ItemStack st : mc.player.getInventory().items) {
                    if (!st.isEmpty() && st.is(tag)) total += st.getCount();
                }
                return total;
            } catch (Exception ignored) {
                return 0;
            }
        }
        Item target = ForgeRegistries.ITEMS.getValue(obj.getTargetId());
        if (target == null) return 0;
        for (ItemStack st : mc.player.getInventory().items) {
            if (!st.isEmpty() && st.getItem() == target) total += st.getCount();
        }
        return total;
    }

    private static void renderCyberTooltip(GuiGraphics g, Font font, List<Component> tooltipLines, int mouseX, int mouseY, int theme) {
        if (tooltipLines == null || tooltipLines.isEmpty()) return;

        int padding = 6;
        int cyberEdgeWidth = 3;
        int textMaxWidth = 0;
        for (Component line : tooltipLines) {
            int lw = font.width(line);
            if (lw > textMaxWidth) textMaxWidth = lw;
        }

        int drawW = textMaxWidth + padding * 2 + cyberEdgeWidth + 2;
        int drawH = tooltipLines.size() * font.lineHeight + padding * 2;

        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int drawX = mouseX + 12;
        int drawY = mouseY - 12;
        if (drawX + drawW > sw) drawX = mouseX - drawW - 8;
        if (drawY + drawH > sh) drawY = sh - drawH - 2;
        if (drawY < 2) drawY = 2;

        g.pose().pushPose();
        g.pose().translate(0, 0, 6000);

        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0x000000, 0xD0));
        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + 1, HudAnimUtil.withAlpha(0xCCCCCC, 0x66));
        g.fill(drawX + cyberEdgeWidth, drawY + drawH - 1, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, 0x66));
        g.fill(drawX + drawW - 1, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, 0x66));
        HudRenderUtil.drawCyberneticEdge(g, drawX, drawY, drawH, theme, 0xFF);

        g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);
        int textX = drawX + cyberEdgeWidth + padding + 1;
        int textY = drawY + padding;
        for (Component line : tooltipLines) {
            g.drawString(font, line, textX, textY, HudAnimUtil.withAlpha(0xFFFFFF, 0xFF), true);
            textY += font.lineHeight;
        }
        g.disableScissor();

        g.pose().popPose();
    }

    private enum FinishMode {
        NONE,
        PENDING_NORMAL,
        NORMAL_CLOSE,
        CLEARED_CLOSE
    }

    private record OfferVM(String title, int required, int current, int canSubmitNow, List<ItemStack> iconCandidates) {
    }
}