package org.com.arc_quest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

public final class QuestToastManager {

    private static final Deque<QuestNotificationToast> pendingQueue = new ArrayDeque<>();
    private static final QuestNotificationToast[] activeSlots = new QuestNotificationToast[3];
    private static final int TOAST_GAP = 4;
    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_TOP = 8;

    private QuestToastManager() {}

    public static void show(ToastType type, String questName) { pendingQueue.addLast(new QuestNotificationToast(type, questName)); }
    public static void show(ToastType type, Component questName) { show(type, questName.getString()); }

    public static void clear() {
        pendingQueue.clear();
        for (int i = 0; i < activeSlots.length; i++) activeSlots[i] = null;
    }

    public static void tick() {
        for (int i = 0; i < activeSlots.length; i++) {
            if (activeSlots[i] != null && activeSlots[i].isExpired()) activeSlots[i] = null;
        }
        for (int i = 0; i < activeSlots.length; i++) {
            if (activeSlots[i] == null && !pendingQueue.isEmpty()) activeSlots[i] = pendingQueue.pollFirst();
        }
    }

    // 让追踪面板能查询弹幕到底占了多少高度
    public static int getPushDownOffset() {
        int highestSlotIndex = -1;
        for (int i = 0; i < activeSlots.length; i++) {
            if (activeSlots[i] != null) highestSlotIndex = i;
        }
        if (highestSlotIndex == -1) return 0;
        return (highestSlotIndex + 1) * (QuestNotificationToast.TOAST_HEIGHT + TOAST_GAP);
    }

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight, boolean isFrozen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        tick();

        int slotY = MARGIN_TOP;
        for (int i = 0; i < activeSlots.length; i++) {
            QuestNotificationToast toast = activeSlots[i];
            if (toast == null) {
                slotY += QuestNotificationToast.TOAST_HEIGHT + TOAST_GAP;
                continue;
            }
            toast.render(guiGraphics, mc.font, screenWidth, slotY, MARGIN_RIGHT, isFrozen);
            slotY += QuestNotificationToast.TOAST_HEIGHT + TOAST_GAP;
        }
    }

    public enum ToastType {
        QUEST_ACCEPTED(0x4FC3F7, "✦ QUEST ACCEPTED"),
        QUEST_COMPLETED(0x66FF66, "★ QUEST COMPLETED"),
        QUEST_FAILED(0xFF6666, "✘ QUEST FAILED"),
        PHASE_ADVANCED(0xFFCC44, "▸ PHASE ADVANCED"),
        OBJECTIVE_COMPLETE(0x88DDFF, "✔ OBJECTIVE DONE");

        public final int accentColor; public final String prefix;
        ToastType(int color, String prefix) { this.accentColor = color; this.prefix = prefix; }
    }
}