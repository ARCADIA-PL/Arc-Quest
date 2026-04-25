package org.com.arc_quest.client.gui.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class QuestToastManager {

    private static final int MAX_SLOTS = 3;
    private static final int TOAST_GAP = 4;
    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_TOP = 8;

    // 去重与节流窗口（毫秒）
    private static final long DUPLICATE_WINDOW_MS = 1200L;
    private static final long RECENT_SHOWN_WINDOW_MS = 1500L;

    private static final Deque<PendingToast> pendingQueue = new ArrayDeque<>();
    private static final QuestNotificationToast[] activeSlots = new QuestNotificationToast[MAX_SLOTS];
    private static final String[] activeKeys = new String[MAX_SLOTS];

    private static final Map<String, Long> recentShownAt = new HashMap<>();

    private static String lastQueuedKey = null;
    private static long lastQueuedAt = 0L;

    private QuestToastManager() {
    }

    public static void show(ToastType type, String questName) {
        if (type == null || questName == null) return;

        String text = questName.trim();
        if (text.isEmpty()) return;

        long now = System.currentTimeMillis();
        String key = buildKey(type, text);

        // 1) 与最近入队相同，短窗口内跳过
        if (key.equals(lastQueuedKey) && now - lastQueuedAt < DUPLICATE_WINDOW_MS) {
            return;
        }

        // 2) 已在 active 中，短窗口内跳过
        for (String activeKey : activeKeys) {
            if (key.equals(activeKey)) {
                Long shownAt = recentShownAt.get(key);
                if (shownAt != null && now - shownAt < RECENT_SHOWN_WINDOW_MS) {
                    return;
                }
            }
        }

        // 3) 近期刚显示过，且队列里又来同一条，跳过
        Long shownAt = recentShownAt.get(key);
        if (shownAt != null && now - shownAt < RECENT_SHOWN_WINDOW_MS) {
            return;
        }

        pendingQueue.addLast(new PendingToast(type, text, key, now));
        lastQueuedKey = key;
        lastQueuedAt = now;

        pruneRecentShown(now);
    }

    public static void show(ToastType type, Component questName) {
        if (questName == null) return;
        show(type, questName.getString());
    }

    public static void clear() {
        pendingQueue.clear();
        for (int i = 0; i < activeSlots.length; i++) {
            activeSlots[i] = null;
            activeKeys[i] = null;
        }
        recentShownAt.clear();
        lastQueuedKey = null;
        lastQueuedAt = 0L;
    }

    public static void tick() {
        long now = System.currentTimeMillis();

        // 回收过期 active
        for (int i = 0; i < activeSlots.length; i++) {
            if (activeSlots[i] != null && activeSlots[i].isExpired()) {
                activeSlots[i] = null;
                activeKeys[i] = null;
            }
        }

        // 填充空槽
        for (int i = 0; i < activeSlots.length; i++) {
            if (activeSlots[i] == null && !pendingQueue.isEmpty()) {
                PendingToast p = pendingQueue.pollFirst();
                activeSlots[i] = new QuestNotificationToast(p.type(), p.text());
                activeKeys[i] = p.key();
                recentShownAt.put(p.key(), now);
            }
        }

        pruneRecentShown(now);
    }

    // 让追踪面板查询右上角占位高度
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

    private static String buildKey(ToastType type, String text) {
        return type.name() + "|" + text;
    }

    private static void pruneRecentShown(long now) {
        recentShownAt.entrySet().removeIf(e -> now - e.getValue() > 5000L);
    }

    private record PendingToast(ToastType type, String text, String key, long queuedAt) {
    }

    public enum ToastType {
        QUEST_ACCEPTED(0x4FC3F7, "✦ QUEST ACCEPTED"),
        QUEST_COMPLETED(0x66FF66, "★ QUEST COMPLETED"),
        QUEST_FAILED(0xFF6666, "✘ QUEST FAILED"),
        PHASE_ADVANCED(0xFFCC44, "▸ PHASE ADVANCED"),
        OBJECTIVE_COMPLETE(0x88DDFF, "✔ OBJECTIVE DONE");

        public final int accentColor;
        public final String prefix;

        ToastType(int color, String prefix) {
            this.accentColor = color;
            this.prefix = prefix;
        }
    }
}