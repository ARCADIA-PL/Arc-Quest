package org.com.arc_quest.client.gui.quest.offer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.quest.api.ObjectiveEntry;
import org.com.arc_quest.quest.api.ObjectiveType;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.C2SSubmitOfferPacket;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.com.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QuestOfferPanel {

    public interface TooltipBridge {
        void renderItemTooltip(GuiGraphics g, ItemStack stack, int mx, int my);
    }

    private static boolean active = false;
    private static boolean closing = false;
    private static long lastRenderMs = 0L;
    private static float dt = 0f;
    private static float anim = 0f; // 0~1
    private static float suspendAlpha = 1f;

    private static String questId;
    private static String phaseId;
    private static int objectiveIndex;

    private static ItemStack hoveredStack = ItemStack.EMPTY;
    private static int iconCycleTicker = 0;
    private static int iconCycleIndex = 0;

    private static long lastSubmitClickMs = 0L;
    private static final long SUBMIT_COOLDOWN_MS = 180L;

    private static TooltipBridge tooltipBridge;

    private static float submitFeedbackAnim = 0f;
    private static boolean submitFeedbackSuccess = false;
    private static String submitFeedbackText = "";
    private static int lastKnownProgress = -1;
    private static long pendingSubmitCheckAt = 0L;

    private static String cachedTagKey = "";
    private static List<ItemStack> cachedTagIcons = List.of();

    private QuestOfferPanel() {}

    public static void trigger(String qid, String pid, int objIndex, TooltipBridge bridge) {
        questId = qid;
        phaseId = pid;
        objectiveIndex = objIndex;
        tooltipBridge = bridge;

        active = true;
        closing = false;
        anim = 0f;
        suspendAlpha = 1f;
        lastRenderMs = 0L;
        iconCycleTicker = 0;
        iconCycleIndex = 0;
        hoveredStack = ItemStack.EMPTY;
        submitFeedbackAnim = 0f;
        submitFeedbackSuccess = false;
        submitFeedbackText = "";
        lastKnownProgress = -1;
        pendingSubmitCheckAt = 0L;
        cachedTagKey = "";
        cachedTagIcons = List.of();
    }

    public static boolean isActive() {
        return active;
    }

    public static void close() {
        if (!active) return;
        closing = true;
    }

    public static boolean keyPressed(int keyCode) {
        if (!active) return false;
        if (keyCode == 256 || keyCode == 69) { // ESC / E
            close();
            return true;
        }
        return false;
    }

    public static boolean mouseClicked(double mx, double my, int button) {
        if (!active || button != 0) return false;

        Layout l = computeLayout();
        if (l == null) return true;

        if (!inside(mx, my, l.panelX, l.panelY, l.panelW, l.panelH)) {
            close();
            return true;
        }

        if (inside(mx, my, l.submitBtnX, l.submitBtnY, l.submitBtnW, l.submitBtnH)) {
            OfferVM vm = resolveOfferViewModel();
            if (vm == null) return true;

            int remain = Math.max(0, vm.required - vm.current);
            int canSubmit = Math.max(0, vm.canSubmitNow);
            int amount = Math.min(1, Math.min(remain, canSubmit));
            if (amount <= 0) {
                submitFeedbackSuccess = false;
                submitFeedbackText = Component.translatable("arc_quest.gui.offer.submit_disabled").getString();
                submitFeedbackAnim = 1f;
                return true;
            }

            long now = Util.getMillis();
            if (now - lastSubmitClickMs < SUBMIT_COOLDOWN_MS) return true;
            lastSubmitClickMs = now;

            lastKnownProgress = vm.current;
            pendingSubmitCheckAt = now + 220L;

            ArcQuestNetwork.sendSubmitOffer(C2SSubmitOfferPacket.of(questId, phaseId, objectiveIndex, amount));
            return true;
        }

        return true;
    }

    public static void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (!active) return;

        long now = Util.getMillis();
        if (lastRenderMs == 0L) lastRenderMs = now;
        dt = Math.min(0.1f, (now - lastRenderMs) / 1000f);
        lastRenderMs = now;

        suspendAlpha = Math.min(1f, suspendAlpha + dt * 3f);

        if (!closing) {
            anim = Math.min(1f, anim + dt / 0.16f);
        } else {
            anim = Math.max(0f, anim - dt / 0.12f);
            if (anim <= 0.001f) {
                active = false;
                closing = false;
                return;
            }
        }

        float ease = closing ? HudAnimUtil.easeInCubic(anim) : HudAnimUtil.easeOutCubic(anim);
        float alpha = ease * HudAnimUtil.easeOutCubic(suspendAlpha);

        Layout l = computeLayout();
        if (l == null) return;

        hoveredStack = ItemStack.EMPTY;

        int dimA = (int) (145 * alpha);
        g.fill(0, 0, l.screenW, l.screenH, dimA << 24);

        int panelBg = HudAnimUtil.withAlpha(0x101319, (int) (225 * alpha));
        int border = HudAnimUtil.withAlpha(0x5AD7FF, (int) (220 * alpha));
        g.fill(l.panelX, l.panelY, l.panelX + l.panelW, l.panelY + l.panelH, panelBg);
        drawFrame(g, l.panelX, l.panelY, l.panelW, l.panelH, border);

        Font font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("arc_quest.gui.offer.title"), l.panelX + 14, l.panelY + 10, HudAnimUtil.withAlpha(0xDDF6FF, (int)(255 * alpha)), false);

        OfferVM vm = resolveOfferViewModel();
        updateSubmitFeedback(vm);
        if (vm == null) {
            g.drawString(font, Component.translatable("arc_quest.gui.offer.invalid"), l.panelX + 14, l.panelY + 34, HudAnimUtil.withAlpha(0xFF7777, (int)(255 * alpha)), false);
            return;
        }

        renderOfferCard(g, font, l, vm, mx, my, alpha);
        updateSubmitFeedback(vm);

        if (!hoveredStack.isEmpty() && tooltipBridge != null) {
            tooltipBridge.renderItemTooltip(g, hoveredStack, mx, my);
        }
    }

    private static void updateSubmitFeedback(OfferVM vm) {
        if (submitFeedbackAnim > 0f) {
            submitFeedbackAnim = Math.max(0f, submitFeedbackAnim - dt * 2.2f);
        }

        if (vm == null) return;

        if (pendingSubmitCheckAt > 0L && Util.getMillis() >= pendingSubmitCheckAt) {
            pendingSubmitCheckAt = 0L;

            if (lastKnownProgress >= 0 && vm.current > lastKnownProgress) {
                submitFeedbackSuccess = true;
                submitFeedbackText = Component.translatable("arc_quest.gui.offer.submit_ok").getString();
            } else {
                submitFeedbackSuccess = false;
                submitFeedbackText = Component.translatable("arc_quest.gui.offer.submit_fail").getString();
            }
            submitFeedbackAnim = 1f;
        }
    }

    private static void renderOfferCard(GuiGraphics g, Font font, Layout l, OfferVM vm, int mx, int my, float alpha) {
        int cardX = l.panelX + 14;
        int cardY = l.panelY + 34;
        int cardW = l.panelW - 28;
        int cardH = l.panelH - 52;

        int cardBg = HudAnimUtil.withAlpha(0x171C24, (int)(220 * alpha));
        int cardBorder = HudAnimUtil.withAlpha(0x3E4D63, (int)(190 * alpha));
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, cardBg);
        drawFrame(g, cardX, cardY, cardW, cardH, cardBorder);

        iconCycleTicker++;
        if (iconCycleTicker >= 5) {
            iconCycleTicker = 0;
            if (vm.iconCandidates.size() > 1) {
                iconCycleIndex = (iconCycleIndex + 1) % vm.iconCandidates.size();
            }
        }

        ItemStack icon = vm.iconCandidates.isEmpty() ? ItemStack.EMPTY : vm.iconCandidates.get(iconCycleIndex % vm.iconCandidates.size());

        int iconX = cardX + 10;
        int iconY = cardY + 12;
        if (!icon.isEmpty()) {
            g.renderItem(icon, iconX, iconY);
            g.renderItemDecorations(font, icon, iconX, iconY);
            if (inside(mx, my, iconX, iconY, 16, 16)) {
                hoveredStack = icon;
            }
        }

        g.drawString(font, vm.title, cardX + 32, cardY + 12, 0xEAF7FF, false);

        String progressText = vm.current + " / " + vm.required;
        g.drawString(font, progressText, cardX + 32, cardY + 28, 0x9FC5D6, false);

        int barX = cardX + 10;
        int barY = cardY + 52;
        int barW = cardW - 20;
        int barH = 4;
        float ratio = vm.required <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) vm.current / vm.required));
        int fillW = Math.max(0, (int)(barW * ratio));

        g.fill(barX, barY, barX + barW, barY + barH, HudAnimUtil.withAlpha(0xFFFFFF, 30));
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + barH, HudAnimUtil.withAlpha(0x54D7FF, 220));
            g.fill(barX + fillW - 1, barY - 1, barX + fillW + 1, barY + barH + 1, HudAnimUtil.withAlpha(0xE8FAFF, 230));
        }

        int remain = Math.max(0, vm.required - vm.current);
        int canSubmit = Math.max(0, vm.canSubmitNow);
        int amount = Math.min(1, Math.min(remain, canSubmit));
        boolean disabled = amount <= 0;

        l.submitBtnX = cardX + 10;
        l.submitBtnY = cardY + cardH - 30;
        l.submitBtnW = cardW - 20;
        l.submitBtnH = 20;

        boolean hover = inside(l.mouseX, l.mouseY, l.submitBtnX, l.submitBtnY, l.submitBtnW, l.submitBtnH);
        int btnBg = disabled
                ? HudAnimUtil.withAlpha(0x3A3F47, 170)
                : HudAnimUtil.withAlpha(hover ? 0x1FA7C8 : 0x1686A0, 210);
        int btnText = disabled ? 0x89919C : 0xF1FBFF;

        g.fill(l.submitBtnX, l.submitBtnY, l.submitBtnX + l.submitBtnW, l.submitBtnY + l.submitBtnH, btnBg);
        drawFrame(g, l.submitBtnX, l.submitBtnY, l.submitBtnW, l.submitBtnH, HudAnimUtil.withAlpha(0xD5F6FF, disabled ? 70 : 170));

        String label = disabled
                ? Component.translatable("arc_quest.gui.offer.submit_disabled").getString()
                : Component.translatable("arc_quest.gui.offer.submit_one").getString();
        int tw = font.width(label);
        g.drawString(font, label, l.submitBtnX + (l.submitBtnW - tw) / 2, l.submitBtnY + 6, btnText, false);

        if (disabled) {
            String hint = Component.translatable("arc_quest.gui.offer.hint_need_more", remain, canSubmit).getString();
            g.drawString(font, hint, cardX + 10, l.submitBtnY - 12, 0x92A8B6, false);
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

        return new OfferVM(
                obj.getDisplayText().getString(),
                required,
                current,
                canSubmitNow,
                candidates
        );
    }

    private static List<ItemStack> resolveIconCandidates(ObjectiveEntry obj) {
        String targetTag = obj.getExtra("target_tag");
        if (targetTag != null && !targetTag.isEmpty()) {
            if (targetTag.equals(cachedTagKey) && !cachedTagIcons.isEmpty()) {
                return cachedTagIcons;
            }

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
                cachedTagIcons = list;
                return list;
            } catch (Exception ignored) {
                cachedTagKey = targetTag;
                cachedTagIcons = Collections.singletonList(ItemStack.EMPTY);
                return cachedTagIcons;
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

    private static Layout computeLayout() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return null;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Layout l = new Layout();
        l.screenW = sw;
        l.screenH = sh;
        l.panelW = Math.min(360, sw - 24);
        l.panelH = Math.min(190, sh - 30);
        l.panelX = (sw - l.panelW) / 2;
        l.panelY = (sh - l.panelH) / 2;
        l.mouseX = Minecraft.getInstance().mouseHandler.xpos() * sw / mc.getWindow().getScreenWidth();
        l.mouseY = Minecraft.getInstance().mouseHandler.ypos() * sh / mc.getWindow().getScreenHeight();
        return l;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static final class Layout {
        int screenW, screenH;
        int panelX, panelY, panelW, panelH;
        int submitBtnX, submitBtnY, submitBtnW, submitBtnH;
        double mouseX, mouseY;
    }

    private record OfferVM(
            String title,
            int required,
            int current,
            int canSubmitNow,
            List<ItemStack> iconCandidates
    ) {}
}