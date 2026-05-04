package org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer;

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
import org.arcadia.arc_quest.mutil.animation.ArcPanelTransition;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.input.ArcCyberButtonElement;
import org.arcadia.arc_quest.mutil.input.ArcCyberSliderElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorUtil;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;
import org.arcadia.arc_quest.mutil.theme.ArcTooltipRenderer;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SSubmitOfferPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.network.S2COfferSubmitResultPacket;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArcQuestOfferPanelElement extends ArcGuiElement {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 140;

    private static final float ENTER_TIME = 0.7f;
    private static final float EXIT_TIME = 0.5f;
    private static final float CLEAR_TIME = 1.4f;
    private static final ArcPanelTransition TRANSITION = new ArcPanelTransition(PANEL_W, PANEL_H, ENTER_TIME, EXIT_TIME);
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
    private static OfferVM lastValidVm = null;
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
    private static ObjectiveEntry cachedObjective = null;
    private static String cachedStaticKey = "";
    private static String cachedTitle = "";
    private static String cachedTrimmedTitle = "";
    private static int cachedTrimmedTitleWidth = -1;
    private static int cachedRequired = 1;
    private static List<ItemStack> cachedIconCandidates = List.of();
    private static ArcTooltipRenderer.CyberLayout cachedTooltipLayout = null;
    private static String cachedTooltipKey = "";
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
    private static S2COfferSubmitResultPacket.CloseMode serverCloseMode = S2COfferSubmitResultPacket.CloseMode.NONE;

    public ArcQuestOfferPanelElement() {
        super(0, 0, PANEL_W, PANEL_H);
    }

    public static void trigger(String qid, String pid, int objIndex) {
        questId = qid;
        phaseId = pid;
        objectiveIndex = objIndex;
        themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(qid, 0x5AD7FF);
        active = true;
        closing = false;
        cleared = false;
        TRANSITION.reset();
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
        cachedObjective = null;
        cachedStaticKey = "";
        cachedTitle = "";
        cachedTrimmedTitle = "";
        cachedTrimmedTitleWidth = -1;
        cachedRequired = 1;
        cachedIconCandidates = List.of();
        cachedTooltipLayout = null;
        cachedTooltipKey = "";
        submitHoverAnim = 0f;
        itemSlotHoverAnim = 0f;
        isDraggingSlider = false;
        sliderValue = 1;
        sliderHoverAnim = 0f;
        visualThumbX = -1f;
        serverCloseMode = S2COfferSubmitResultPacket.CloseMode.NONE;

        initStaticOfferCache();
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
        TRANSITION.close();
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
        if (!active || closing || button != 0 || cleared) return false;

        float scaledW = PANEL_W * currentScale, scaledH = PANEL_H * currentScale;
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

        int sliderW = 160, sliderX = PANEL_W / 2 - sliderW / 2, sliderY = 95;
        if (maxSelectable > 1 && ArcCyberSliderElement.containsLocal(lx, ly, sliderX - 5, sliderY - 6, sliderW + 10, 14)) {
            isDraggingSlider = true;
            return true;
        }

        int btnW = 140, btnH = 16, btnX = PANEL_W / 2 - btnW / 2, btnY = PANEL_H - btnH - 10;
        if (ArcCyberButtonElement.containsLocal(lx, ly, btnX, btnY, btnW, btnH)) {
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

    @Override
    public void draw(GuiGraphics g, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        int mx = context.mouseX(), my = context.mouseY();
        int screenW = context.screenWidth(), screenH = context.screenHeight();
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastRenderMs) / 1000f, 0.1f);
        lastRenderMs = now;

        float finalScale = (screenH * 0.55f) / (float) PANEL_H;
        ArcPanelTransition.Frame frame = TRANSITION.update(dt, screenW, screenH, finalScale);
        if (TRANSITION.isFinished()) {
            active = false;
            closing = false;
            return;
        }
        if (!frame.visible()) return;

        currentDrawX = frame.drawX();
        currentDrawY = frame.drawY();
        currentScale = frame.scale();
        float alphaF = frame.alpha();

        hoveredStack = ItemStack.EMPTY;

        g.pose().pushPose();
        g.pose().translate(0, 0, 4500);
        ArcDrawUtil.fillFullscreenDim(g, screenW, screenH, (int) (100 * alphaF));
        ArcScissorUtil.enableGui(g, frame.scissorX1(), frame.scissorY1(), frame.scissorX2(), frame.scissorY2());
        g.pose().pushPose();
        g.pose().translate(currentDrawX, currentDrawY, 0);
        g.pose().scale(currentScale, currentScale, 1f);

        renderPanel(g, mc.font, ArcDrawUtil.clampAlpha((int) (255 * alphaF)), alphaF, dt, mx, my);

        g.pose().popPose();
        ArcScissorUtil.disable(g);
        g.pose().popPose();
        if (!hoveredStack.isEmpty() && !closing && !cleared) {
            Minecraft mcForTip = Minecraft.getInstance();
            if (mcForTip.player != null) {
                ArcTooltipRenderer.CyberLayout layout = getTooltipLayout(hoveredStack, mcForTip, mcForTip.options.advancedItemTooltips);
                ArcTooltipRenderer.renderCyber(g, mcForTip.font, layout, mx, my, context.screenWidth(), context.screenHeight(), themeColor, 6000);
            }
        }
    }

    private static void renderPanel(GuiGraphics g, Font font, int alpha, float alphaF, float dt, int mx, int my) {
        int PW = PANEL_W, PH = PANEL_H;
        float lx = (float) ((mx - currentDrawX) / currentScale);
        float ly = (float) ((my - currentDrawY) / currentScale);

        OfferVM vm = resolveOfferViewModel();
        if (vm != null) lastValidVm = vm;

        if (!closing) {
            if (serverCloseMode == S2COfferSubmitResultPacket.CloseMode.CLEARED_CLOSE) {
                if (!cleared && lastValidVm != null) {
                    cleared = true;
                    clearTimer = 0f;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
                }
                serverCloseMode = S2COfferSubmitResultPacket.CloseMode.NONE;
            } else if (serverCloseMode == S2COfferSubmitResultPacket.CloseMode.NORMAL_CLOSE) {
                close();
                serverCloseMode = S2COfferSubmitResultPacket.CloseMode.NONE;
                return;
            }
        }

        if (cleared) {
            clearTimer += dt;
            if (clearTimer >= CLEAR_TIME && !closing) close();
        }

        OfferVM renderVm = cleared ? lastValidVm : vm;
        if (renderVm == null) {
            if (active && !closing) close();
            return;
        }

        updateSubmitFeedback(renderVm, dt);

        // =========================================================================
        // PASS 1: 纯 2D 通道 (背景、遮罩、文本、滑块、按钮)
        // =========================================================================
        int bgAlpha = (int) (0x99 * alphaF), borderAlpha = (int) (0x66 * alphaF), borderRgb = 0xCCCCCC;
        int feedbackTargetColor = submitFeedbackSuccess ? 0x33FF66 : 0xFF3333;
        int currentEdgeColor = cleared ? 0x33FF66 : HudAnimUtil.lerpColor(themeColor, feedbackTargetColor, submitFeedbackAnim);

        ArcPanelChrome.drawQuestPanel(g, 0, 0, PW - 3, PH, 0x000000, bgAlpha, borderRgb, borderAlpha, currentEdgeColor, alpha);

        float contentAlphaMult = cleared ? Math.max(0f, 1f - (clearTimer * 4f)) : 1f;
        int contentAlpha = (int) (alpha * contentAlphaMult);
        float contentAlphaF = alphaF * contentAlphaMult;

        int iconX = 20, iconY = 34; // 提前声明用于PASS 2
        ItemStack iconToRender = ItemStack.EMPTY;

        if (contentAlpha > 5) {
            int topBarH = 22;
            ArcPanelChrome.drawHeader(g, font, "SYS.ARC_QUEST // UPLOAD PROTOCOL", 0.8f, 16, 6, ArcPanelChrome.DEFAULT_HEADER_RGB, contentAlpha);
            ArcPanelChrome.drawTopDivider(g, PW, topBarH, borderRgb, (int) (borderAlpha * contentAlphaMult));

            int contentY = topBarH + 12;
            g.drawString(font, getTrimmedTitle(font, renderVm.title, PW - 70), 50, contentY + 2, HudAnimUtil.withAlpha(0xFFFFFF, contentAlpha), false);

            g.pose().pushPose();
            g.pose().scale(0.85f, 0.85f, 1f);
            g.drawString(font, "STATUS: " + renderVm.current + " / " + renderVm.required, (int) (50 / 0.85f), (int) ((contentY + 14) / 0.85f), HudAnimUtil.withAlpha(0x99AABB, contentAlpha), false);
            g.pose().popPose();

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
            iconToRender = renderVm.iconCandidates.isEmpty() ? ItemStack.EMPTY : renderVm.iconCandidates.get(iconCycleIndex % renderVm.iconCandidates.size());
            if (!iconToRender.isEmpty() && isHoverSlot) hoveredStack = iconToRender;

            currentProgressAnim += (renderVm.current - currentProgressAnim) * Math.min(1f, dt * 10f);
            if (cleared) currentProgressAnim = renderVm.required;

            int barX = 20, barY = contentY + 36, barW = PW - 40, barH = 4;
            float ratio = renderVm.required <= 0 ? 0f : Math.max(0f, Math.min(1f, currentProgressAnim / renderVm.required));
            ArcCyberSliderElement.renderProgressFrame(g, barX, barY, barW, barH, ratio, contentAlpha, contentAlphaF, themeColor);
            int remain = Math.max(0, renderVm.required - renderVm.current);
            int canSubmit = Math.max(0, renderVm.canSubmitNow);
            int maxSelectable = Math.max(0, Math.min(50, Math.min(remain, canSubmit)));

            if (sliderValue > maxSelectable) sliderValue = Math.max(1, maxSelectable);
            if (sliderValue < 1 && maxSelectable > 0) sliderValue = 1;
            if (maxSelectable == 0) sliderValue = 0;

            int sliderW = 160, sliderX = PW / 2 - sliderW / 2, sliderY = 95;

            if (isDraggingSlider && !cleared) {
                if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                    isDraggingSlider = false;
                } else if (maxSelectable > 1) {
                    sliderValue = ArcCyberSliderElement.valueFromLocal(lx, sliderX, sliderW, 1, maxSelectable);
                }
            }

            boolean sliderHover = !cleared && !isDraggingSlider && maxSelectable > 1 && ArcCyberSliderElement.containsLocal(lx, ly, sliderX - 5, sliderY - 6, sliderW + 10, 14);
            sliderHoverAnim = HudAnimUtil.step(sliderHoverAnim, sliderHover ? 1f : 0f, 18f, dt);

            g.pose().pushPose();
            g.pose().scale(0.85f, 0.85f, 1f);
            String qtyText = "QUANTITY // " + (maxSelectable == 0 ? "0" : sliderValue);
            g.drawString(font, qtyText, (int) ((PW / 2f) / 0.85f) - font.width(qtyText) / 2, (int) ((sliderY - 12) / 0.85f), HudAnimUtil.withAlpha(0xAAAAAA, contentAlpha), false);
            g.pose().popPose();
            if (maxSelectable > 0) {
                float targetThumbX = ArcCyberSliderElement.targetThumbX(sliderX, sliderW, sliderValue, 1, maxSelectable);
                visualThumbX = ArcCyberSliderElement.stepVisualThumb(visualThumbX, targetThumbX, dt);
                ArcCyberSliderElement.renderCyber(g, sliderX, sliderY, sliderW, 1, visualThumbX, isDraggingSlider, sliderHoverAnim, contentAlpha, contentAlphaF, themeColor);
            }
            int btnW = 140, btnH = 16, btnX = PW / 2 - btnW / 2, btnY = PANEL_H - btnH - 10;
            boolean disabled = sliderValue <= 0 || maxSelectable <= 0;
            boolean hoverSubmit = !cleared && !disabled && ArcCyberButtonElement.containsLocal(lx, ly, btnX, btnY, btnW, btnH);
            submitHoverAnim = HudAnimUtil.step(submitHoverAnim, hoverSubmit ? 1f : 0f, 15f, dt);

            ArcCyberButtonElement.renderCyber(g, font, btnX, btnY, btnW, btnH, "SUBMIT [ " + sliderValue + " ]", submitHoverAnim, disabled, contentAlpha, contentAlphaF, themeColor);

            if (disabled && remain > 0) {
                String hint = "Insufficient items: need " + remain + ", have " + canSubmit;
                g.pose().pushPose();
                g.pose().scale(0.7f, 0.7f, 1f);
                g.drawString(font, hint, (int) ((btnX + btnW / 2f) / 0.7f) - font.width(hint) / 2, (int) ((sliderY + 8) / 0.7f), HudAnimUtil.withAlpha(0xAA4444, contentAlpha), false);
                g.pose().popPose();
            }
        }

        if (cleared) {
            g.fill(3, 0, PW - 3, PH, HudAnimUtil.withAlpha(0x000000, (int) (80 * Math.min(1f, clearTimer * 3f))));
            float clearScaleBase = Math.min(1f, clearTimer / 0.2f);
            float textScale = 1.8f - 0.5f * (float) Math.pow(clearScaleBase, 3);
            float clearAlphaF = clearTimer > CLEAR_TIME - 0.3f ? Math.max(0f, (CLEAR_TIME - clearTimer) / 0.3f) : 1f;

            g.pose().pushPose();
            g.pose().translate(PW / 2f, PH / 2f, 100);
            g.pose().scale(textScale, textScale, 1f);
            g.drawCenteredString(font, "[ // CLEARED // ]", 0, -font.lineHeight / 2, HudAnimUtil.withAlpha(themeColor, (int) (255 * clearAlphaF * alphaF)));
            g.pose().popPose();

            if (clearTimer < 0.6f) {
                float scanLineY = PH * (clearTimer / 0.6f);
                g.fill(3, (int) scanLineY, PW - 3, (int) scanLineY + 1, HudAnimUtil.withAlpha(themeColor, (int) (100 * (1f - clearTimer / 0.6f))));
            }
        }

        // =========================================================================
        // PASS 2: 纯 3D 通道 (只渲染物品)
        // =========================================================================
        if (contentAlpha > 5 && !iconToRender.isEmpty() && !cleared) {
            g.pose().pushPose();
            g.renderItem(iconToRender, iconX, iconY);
            g.renderItemDecorations(font, iconToRender, iconX, iconY);
            g.pose().popPose();
        }
    }
    private static void updateSubmitFeedback(OfferVM vm, float dt) {
        if (submitFeedbackAnim > 0f) submitFeedbackAnim = Math.max(0f, submitFeedbackAnim - dt * 1.25f);
        if (vm == null) return;
        if (pendingSubmitCheckAt > 0L && Util.getMillis() >= pendingSubmitCheckAt) {
            pendingSubmitCheckAt = 0L;
            submitFeedbackSuccess = lastKnownProgress >= 0 && vm.current > lastKnownProgress;
            submitFeedbackAnim = 1f;
        }
    }

    private static void initStaticOfferCache() {
        cachedStaticKey = questId + "|" + phaseId + "|" + objectiveIndex;
        cachedObjective = null;
        cachedTitle = "";
        cachedTrimmedTitle = "";
        cachedTrimmedTitleWidth = -1;
        cachedRequired = 1;
        cachedIconCandidates = List.of();

        var def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return;
        var phase = def.getPhase(phaseId);
        if (phase == null || objectiveIndex < 0 || objectiveIndex >= phase.getObjectives().size()) return;
        ObjectiveEntry obj = phase.getObjectives().get(objectiveIndex);
        if (obj.getType() != ObjectiveType.OFFER) return;

        cachedObjective = obj;
        cachedTitle = obj.getDisplayText().getString();
        cachedRequired = Math.max(1, obj.getRequiredCount());
        cachedIconCandidates = resolveIconCandidates(obj);
    }

    private static String getTrimmedTitle(Font font, String title, int width) {
        if (cachedTrimmedTitleWidth != width || !title.equals(cachedTitle) || cachedTrimmedTitle.isEmpty()) {
            cachedTitle = title;
            cachedTrimmedTitleWidth = width;
            cachedTrimmedTitle = font.plainSubstrByWidth(title, width);
        }
        return cachedTrimmedTitle;
    }

    private static OfferVM resolveOfferViewModel() {
        var data = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (data == null || !data.isPhaseActive(phaseId)) return null;
        if (cachedObjective == null || !(questId + "|" + phaseId + "|" + objectiveIndex).equals(cachedStaticKey))
            initStaticOfferCache();
        if (cachedObjective == null) return null;
        return new OfferVM(cachedTitle, cachedRequired, data.getObjectiveProgress(phaseId, objectiveIndex), resolveOfferableCount(cachedObjective), cachedIconCandidates);
    }

    private static List<ItemStack> resolveIconCandidates(ObjectiveEntry obj) {
        String targetTag = obj.getTargetTagId();
        if (targetTag != null && !targetTag.isEmpty()) {
            if (targetTag.equals(cachedTagKey) && !cachedTagIcons.isEmpty()) return cachedTagIcons;
            try {
                ResourceLocation tagId = obj.getTargetTagResourceLocation();
                if (tagId == null) {
                    cachedTagKey = targetTag;
                    return cachedTagIcons = Collections.singletonList(ItemStack.EMPTY);
                }
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
        String targetTag = obj.getTargetTagId();
        int total = 0;
        if (targetTag != null && !targetTag.isEmpty()) {
            try {
                ResourceLocation tagId = obj.getTargetTagResourceLocation();
                if (tagId == null) return 0;
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

    private static ArcTooltipRenderer.CyberLayout getTooltipLayout(ItemStack stack, Minecraft mc, boolean advanced) {
        String key = stack.getItem().builtInRegistryHolder().key().location() + "|" + stack.getCount() + "|" + stack.getHoverName().getString() + "|" + advanced;
        if (key.equals(cachedTooltipKey) && cachedTooltipLayout != null) return cachedTooltipLayout;
        List<Component> lines = List.of();
        if (mc.player != null) {
            lines = stack.getTooltipLines(mc.player, advanced ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        }
        ArcTooltipRenderer.CyberLayout layout = ArcTooltipRenderer.measure(mc.font, lines);
        cachedTooltipKey = key;
        cachedTooltipLayout = layout;
        return layout;
    }
    public static void onServerSubmitResult(String qid, String pid, int objIndex, S2COfferSubmitResultPacket.CloseMode mode) {
        if (!active || !questId.equals(qid) || !phaseId.equals(pid) || objectiveIndex != objIndex) return;
        if (mode != null) serverCloseMode = mode;
    }

    private record OfferVM(String title, int required, int current, int canSubmitNow, List<ItemStack> iconCandidates) {
    }
}