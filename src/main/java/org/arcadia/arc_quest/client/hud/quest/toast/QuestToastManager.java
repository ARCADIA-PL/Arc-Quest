package org.arcadia.arc_quest.client.hud.quest.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.trackingmenu.QuestTrackingMenuScreen;
import org.arcadia.arc_quest.config.ArcQuestToastConfig;

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

    private QuestToastManager() {
    }

    public static void show(ToastType type, String questName) {
        if (type == null || !type.isEnabled() || questName == null) return;

        String text = questName.trim();
        if (text.isEmpty()) return;

        enqueue(type, Component.literal(text), text);
    }

    public static void show(ToastType type, Component questName) {
        if (type == null || !type.isEnabled() || questName == null) return;

        String plainText = questName.getString().trim();
        if (plainText.isEmpty()) return;

        enqueue(type, questName.copy(), plainText);
    }

    private static void enqueue(ToastType type, Component text, String plainText) {

        if (type == ToastType.QUEST_FAILED) {
            cancelAcceptedToastForQuest(plainText);
        }

        long now = System.currentTimeMillis();
        String key = buildKey(type, plainText);

        if (key.equals(lastQueuedKey) && now - lastQueuedAt < DUPLICATE_WINDOW_MS) {
            return;
        }

        for (String activeKey : activeKeys) {
            if (key.equals(activeKey)) {
                Long shownAt = recentShownAt.get(key);
                if (shownAt != null && now - shownAt < RECENT_SHOWN_WINDOW_MS) {
                    return;
                }
            }
        }

        Long shownAt = recentShownAt.get(key);
        if (shownAt != null && now - shownAt < RECENT_SHOWN_WINDOW_MS) {
            return;
        }

        pendingQueue.addLast(new PendingToast(type, text, key, now));
        lastQueuedKey = key;
        lastQueuedAt = now;

        pruneRecentShown(now);
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
                mc.screen instanceof QuestTrackingMenuScreen ||
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

    public static void refreshConfiguration() {
        removeDisabledToasts();
        recentShownAt.clear();
        lastQueuedKey = null;
        lastQueuedAt = 0L;
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

    private static String buildKey(ToastType type, String text) {
        return type.name() + "|" + text;
    }

    private static void cancelAcceptedToastForQuest(String questName) {
        String acceptedKey = buildKey(ToastType.QUEST_ACCEPTED, questName);

        pendingQueue.removeIf(p -> p.type() == ToastType.QUEST_ACCEPTED && questName.equals(p.text().getString()));

        for (int i = 0; i < activeSlots.length; i++) {
            if (acceptedKey.equals(activeKeys[i])) {
                activeSlots[i] = null;
                activeKeys[i] = null;
            }
        }

        recentShownAt.remove(acceptedKey);
        if (acceptedKey.equals(lastQueuedKey)) {
            lastQueuedKey = null;
            lastQueuedAt = 0L;
        }
    }

    private static void pruneRecentShown(long now) {
        recentShownAt.entrySet().removeIf(e -> now - e.getValue() > 5000L);
    }

    private static void removeDisabledToasts() {
        pendingQueue.removeIf(pending -> !pending.type().isEnabled());
        for (int index = 0; index < activeSlots.length; index++) {
            QuestNotificationToast toast = activeSlots[index];
            if (toast != null && !toast.getType().isEnabled()) {
                activeSlots[index] = null;
                activeKeys[index] = null;
            }
        }
    }

    public enum ToastType {
        QUEST_ACCEPTED(0x4FC3F7, "arc_quest.toast.prefix.quest_accepted"),
        QUEST_COMPLETED(0x66FF66, "arc_quest.toast.prefix.quest_completed"),
        QUEST_FAILED(0xFF6666, "arc_quest.toast.prefix.quest_failed"),
        COLLECTION_ENTRY_DISCOVERED(0xA98BFF, "arc_quest.toast.prefix.collection_entry_discovered"),
        COLLECTION_ENTRY_COMPLETED(0x7CFFB2, "arc_quest.toast.prefix.collection_entry_completed"),
        COLLECTION_REWARD_UNLOCKED(0xFFD166, "arc_quest.toast.prefix.collection_reward_unlocked"),
        COLLECTION_REWARD_CLAIMED(0xFFE6A3, "arc_quest.toast.prefix.collection_reward_claimed"),
        PHASE_ADVANCED(0xFFCC44, "arc_quest.toast.prefix.phase_advanced"),
        OBJECTIVE_COMPLETE(0x88DDFF, "arc_quest.toast.prefix.objective_complete");

        public final int accentColor;
        public final String translationKey;

        ToastType(int color, String translationKey) {
            accentColor = color;
            this.translationKey = translationKey;
        }

        /**
         * 获取翻译后的前缀文本。
         */
        public String getLocalizedPrefix() {
            return Minecraft.getInstance().player != null
                    ? Component.translatable(translationKey).getString()
                    : translationKey;
        }

        public boolean isEnabled() {
            return switch (this) {
                case QUEST_ACCEPTED -> ArcQuestToastConfig.QUEST_ACCEPTED.get();
                case QUEST_COMPLETED -> ArcQuestToastConfig.QUEST_COMPLETED.get();
                case QUEST_FAILED -> ArcQuestToastConfig.QUEST_FAILED.get();
                case COLLECTION_ENTRY_DISCOVERED -> ArcQuestToastConfig.COLLECTION_ENTRY_DISCOVERED.get();
                case COLLECTION_ENTRY_COMPLETED -> ArcQuestToastConfig.COLLECTION_ENTRY_COMPLETED.get();
                case COLLECTION_REWARD_UNLOCKED -> ArcQuestToastConfig.COLLECTION_REWARD_UNLOCKED.get();
                case COLLECTION_REWARD_CLAIMED -> ArcQuestToastConfig.COLLECTION_REWARD_CLAIMED.get();
                case PHASE_ADVANCED -> ArcQuestToastConfig.PHASE_ADVANCED.get();
                case OBJECTIVE_COMPLETE -> ArcQuestToastConfig.OBJECTIVE_COMPLETE.get();
            };
        }
    }

    private record PendingToast(ToastType type, Component text, String key, long queuedAt) {
    }
}
