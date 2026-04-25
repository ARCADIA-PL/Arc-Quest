package org.com.arc_quest.client.gui.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.client.gui.dialogue.DialogueScreen;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class QuestToastManager {

    private static final int MAX_SLOTS = 3;
    private static final int TOAST_GAP = 4;
    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_TOP = 8;

    private static final long DUPLICATE_WINDOW_MS = 1200L;
    private static final long RECENT_SHOWN_WINDOW_MS = 1500L;

    private static final Deque<PendingToast> pendingQueue = new ArrayDeque<>();
    private static final QuestNotificationToast[] activeSlots = new QuestNotificationToast[MAX_SLOTS];
    private static final String[] activeKeys = new String[MAX_SLOTS];

    private static final Map<String, Long> recentShownAt = new HashMap<>();

    private static String lastQueuedKey = null;
    private static long lastQueuedAt = 0L;

    private QuestToastManager() {}

    public static void show(ToastType type, String questName) {
        if (type == null || questName == null) return;

        String text = questName.trim();
        if (text.isEmpty()) return;

        long now = System.currentTimeMillis();
        String key = buildKey(type, text);

        if (key.equals(lastQueuedKey) && now - lastQueuedAt < DUPLICATE_WINDOW_MS) return;

        for (String activeKey : activeKeys) {
            if (key.equals(activeKey)) {
                Long shownAt = recentShownAt.get(key);
                if (shownAt != null && now - shownAt < RECENT_SHOWN_WINDOW_MS) return;
            }
        }

        Long shownAt = recentShownAt.get(key);
        if (shownAt != null && now - shownAt < RECENT_SHOWN_WINDOW_MS) return;

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
        Minecraft mc = Minecraft.getInstance();

        // 核心修复：如果 Splash 在播放，或者日志、对话在看，全盘冻结！
        boolean isJournalVisible = mc.screen instanceof QuestJournalScreen;
        boolean isFrozen = QuestSplashRenderer.isActive() ||
                isJournalVisible ||
                mc.screen instanceof DialogueScreen;

        // 1. 让存活的 Toast 更新冻结时间戳
        for (int i = 0; i < activeSlots.length; i++) {
            if (activeSlots[i] != null) {
                activeSlots[i].tick(isFrozen);
                if (activeSlots[i].isExpired()) {
                    activeSlots[i] = null;
                    activeKeys[i] = null;
                }
            }
        }

        // 2. 只有在未冻结的状态下，才允许新 Toast 出队进入屏幕！
        if (!isFrozen) {
            for (int i = 0; i < activeSlots.length; i++) {
                if (activeSlots[i] == null && !pendingQueue.isEmpty()) {
                    PendingToast p = pendingQueue.pollFirst();
                    activeSlots[i] = new QuestNotificationToast(p.type(), p.text());
                    activeKeys[i] = p.key();
                    recentShownAt.put(p.key(), now);
                }
            }
        }

        pruneRecentShown(now);
    }

    public static int getPushDownOffset() {
        int highestSlotIndex = -1;
        for (int i = 0; i < activeSlots.length; i++) {
            if (activeSlots[i] != null) highestSlotIndex = i;
        }
        if (highestSlotIndex == -1) return 0;
        return (highestSlotIndex + 1) * (QuestNotificationToast.TOAST_HEIGHT + TOAST_GAP);
    }

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int slotY = MARGIN_TOP;
        for (int i = 0; i < activeSlots.length; i++) {
            QuestNotificationToast toast = activeSlots[i];
            if (toast == null) {
                slotY += QuestNotificationToast.TOAST_HEIGHT + TOAST_GAP;
                continue;
            }
            toast.render(guiGraphics, mc.font, screenWidth, slotY, MARGIN_RIGHT);
            slotY += QuestNotificationToast.TOAST_HEIGHT + TOAST_GAP;
        }
    }

    private static String buildKey(ToastType type, String text) { return type.name() + "|" + text; }
    private static void pruneRecentShown(long now) { recentShownAt.entrySet().removeIf(e -> now - e.getValue() > 5000L); }
    private record PendingToast(ToastType type, String text, String key, long queuedAt) {}

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