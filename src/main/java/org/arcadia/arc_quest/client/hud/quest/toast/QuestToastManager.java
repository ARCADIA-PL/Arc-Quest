package org.arcadia.arc_quest.client.hud.quest.toast;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.trackingmenu.QuestTrackingMenuScreen;
import org.arcadia.arc_quest.config.ArcQuestToastConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Client notification state. The single left-side overlay is the only rendering owner. */
public final class QuestToastManager {
    public static final long ENTER_MILLIS = ToastScheduler.ENTER_MILLIS;
    public static final long HOLD_MILLIS = ToastScheduler.HOLD_MILLIS;
    public static final long EXIT_MILLIS = ToastScheduler.EXIT_MILLIS;

    private static final ToastScheduler<ToastType, Text> SCHEDULER = new ToastScheduler<>();
    // Preserve authoritative pending snapshots, including disabled types, for re-enablement.
    private static final Map<String, List<PendingNotice>> pendingSnapshots = new LinkedHashMap<>();
    private static long anonymousSequence;
    private static long cachedVersion = -1;
    private static Component cachedTitle = Component.empty();
    private static Component cachedDetail = Component.empty();

    private QuestToastManager() {}

    public record PendingNotice(ToastType type, String phaseId, Component title, Component detail) {}
    public record DisplayToast(ToastType type, Component title, Component detail, int themeColor,
                               long elapsedMillis, boolean persistent, long version) {}
    private record Text(Component title, Component detail) {}

    /** Legacy callers supply no stable identity; never infer one from translated display text. */
    public static void show(ToastType type, String questName) {
        if (questName == null || questName.isBlank()) return;
        show(type, Component.literal(questName.trim()));
    }

    public static void show(ToastType type, Component questName) {
        if (type == null || questName == null || questName.getString().isBlank()) return;
        show(type, "legacy#" + ++anonymousSequence, "", questName, Component.empty());
    }

    public static void show(ToastType type, String questId, String subjectId, Component title, Component detail) {
        if (type == null || questId == null || questId.isBlank()) return;
        Text text = text(title, detail);
        String subject = subjectId == null ? "" : subjectId;
        if (type.terminal()) pendingSnapshots.remove(questId);
        boolean accepted = SCHEDULER.submit(new ToastScheduler.Notice<>(type, questId, subject, text), type.isEnabled());
        if (accepted && type.persistent()) {
            List<PendingNotice> notices = new ArrayList<>(pendingSnapshots.getOrDefault(questId, List.of()));
            notices.removeIf(notice -> notice.type() == type && Objects.equals(notice.phaseId(), subject));
            notices.add(new PendingNotice(type, subject, text.title(), text.detail()));
            pendingSnapshots.put(questId, List.copyOf(notices));
        }
    }

    /** Authoritative ACTIVE quest snapshot; use clearPendingForQuest for an inactive quest. */
    public static void replacePendingForQuest(String questId, List<PendingNotice> notices) {
        if (questId == null || questId.isBlank()) return;
        List<PendingNotice> normalized = normalize(notices);
        pendingSnapshots.put(questId, normalized);
        SCHEDULER.replacePendingForQuest(questId, enabledNotices(questId, normalized));
    }

    public static void replaceAllPending(Map<String, List<PendingNotice>> snapshots) {
        Map<String, List<PendingNotice>> normalized = new LinkedHashMap<>();
        if (snapshots != null) snapshots.forEach((questId, notices) -> {
            if (questId == null || questId.isBlank()) return;
            List<PendingNotice> entries = normalize(notices);
            normalized.put(questId, entries);
        });
        pendingSnapshots.clear();
        pendingSnapshots.putAll(normalized);
        SCHEDULER.retainActiveQuests(normalized.keySet());
        rebuildEnabledPending();
    }

    /** A null phase selects every pending phase of this type in the quest. */
    public static void dismissPending(String questId, String phaseId, ToastType type) {
        if (questId == null || type == null) return;
        List<PendingNotice> notices = new ArrayList<>(pendingSnapshots.getOrDefault(questId, List.of()));
        notices.removeIf(notice -> notice.type() == type
                && (phaseId == null || Objects.equals(notice.phaseId(), phaseId)));
        if (notices.isEmpty()) pendingSnapshots.remove(questId);
        else pendingSnapshots.put(questId, List.copyOf(notices));
        SCHEDULER.dismissPending(questId, phaseId, type);
    }

    public static void clearPendingForQuest(String questId) {
        if (questId == null) return;
        pendingSnapshots.remove(questId);
        SCHEDULER.clearPendingForQuest(questId);
    }

    @Nullable
    public static String firstPendingQuestId(ToastType type) {
        return type == null ? null : SCHEDULER.firstPendingQuestId(type);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        SCHEDULER.tick(Util.getMillis(), canDisplay(minecraft));
    }

    static boolean canDisplay(Minecraft minecraft) {
        return minecraft.player != null && !minecraft.options.hideGui
                && !(minecraft.screen instanceof QuestJournalScreen)
                && !(minecraft.screen instanceof QuestTrackingMenuScreen)
                && !(minecraft.screen instanceof DialogueScreen)
                && !QuestSplashRenderer.isActive();
    }

    @Nullable
    public static DisplayToast currentDisplay() {
        ToastScheduler.Display<ToastType, Text> display = SCHEDULER.current();
        if (display == null) return null;
        if (cachedVersion != display.version()) {
            cachedVersion = display.version();
            cachedTitle = display.mergedCount() > 1
                    ? Component.translatable("arc_quest.toast.merged_title", display.payload().title(), display.mergedCount() - 1)
                    : display.payload().title();
            cachedDetail = display.payload().detail();
        }
        return new DisplayToast(display.type(), cachedTitle, cachedDetail, display.type().accentColor,
                display.elapsedMillis(), display.persistent(), display.version());
    }

    public static void refreshConfiguration() {
        SCHEDULER.retainTypes(ToastType::isEnabled);
        rebuildEnabledPending();
    }

    public static void clear() {
        SCHEDULER.clear();
        pendingSnapshots.clear();
        cachedVersion = -1;
        cachedTitle = Component.empty();
        cachedDetail = Component.empty();
    }

    /** Compatibility for old tracker integrations: notifications no longer occupy the top right. */
    public static int getPushDownOffset() { return 0; }

    private static void rebuildEnabledPending() {
        Map<String, List<ToastScheduler.Notice<ToastType, Text>>> enabled = new LinkedHashMap<>();
        pendingSnapshots.forEach((questId, notices) -> enabled.put(questId, enabledNotices(questId, notices)));
        SCHEDULER.replaceAllPending(enabled);
    }

    private static List<ToastScheduler.Notice<ToastType, Text>> enabledNotices(String questId, List<PendingNotice> notices) {
        return notices.stream().filter(notice -> notice.type().isEnabled())
                .map(notice -> new ToastScheduler.Notice<>(notice.type(), questId, notice.phaseId(),
                        text(notice.title(), notice.detail()))).toList();
    }

    private static List<PendingNotice> normalize(List<PendingNotice> notices) {
        if (notices == null || notices.isEmpty()) return List.of();
        List<PendingNotice> result = new ArrayList<>();
        for (PendingNotice notice : notices) {
            if (notice == null || notice.type() == null || !notice.type().persistent()) continue;
            String phase = notice.phaseId() == null ? "" : notice.phaseId();
            Text text = text(notice.title(), notice.detail());
            result.removeIf(previous -> previous.type() == notice.type() && previous.phaseId().equals(phase));
            result.add(new PendingNotice(notice.type(), phase, text.title(), text.detail()));
        }
        return List.copyOf(result);
    }

    private static Text text(Component title, Component detail) {
        return new Text(title == null ? Component.empty() : title.copy(),
                detail == null ? Component.empty() : detail.copy());
    }

    public enum ToastType implements ToastScheduler.Kind {
        QUEST_ACCEPTED(0x4FC3F7, "arc_quest.toast.prefix.quest_accepted"),
        QUEST_COMPLETED(0x66FF66, "arc_quest.toast.prefix.quest_completed"),
        QUEST_FAILED(0xFF6666, "arc_quest.toast.prefix.quest_failed"),
        COLLECTION_ENTRY_DISCOVERED(0xA98BFF, "arc_quest.toast.prefix.collection_entry_discovered"),
        COLLECTION_ENTRY_COMPLETED(0x7CFFB2, "arc_quest.toast.prefix.collection_entry_completed"),
        COLLECTION_REWARD_UNLOCKED(0xFFD166, "arc_quest.toast.prefix.collection_reward_unlocked"),
        COLLECTION_REWARD_CLAIMED(0xFFE6A3, "arc_quest.toast.prefix.collection_reward_claimed"),
        PHASE_ADVANCED(0xFFCC44, "arc_quest.toast.prefix.phase_advanced"),
        OBJECTIVE_COMPLETE(0x88DDFF, "arc_quest.toast.prefix.objective_complete"),
        PHASE_ADDED(0x4FC3F7, "arc_quest.toast.prefix.phase_added"),
        PHASE_SWITCHED(0xFFCC44, "arc_quest.toast.prefix.phase_switched"),
        PHASE_COMPLETED(0x66FF66, "arc_quest.toast.prefix.phase_completed"),
        PHASE_PENDING_CONFIRM(0xFFD166, "arc_quest.toast.prefix.phase_pending_confirm"),
        BRANCH_CHOICE(0xFFCC44, "arc_quest.toast.prefix.branch_choice");

        public final int accentColor;
        public final String translationKey;
        ToastType(int color, String translationKey) { accentColor = color; this.translationKey = translationKey; }
        public String getLocalizedPrefix() { return Component.translatable(translationKey).getString(); }

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
                case PHASE_ADDED -> ArcQuestToastConfig.PHASE_ADVANCED.get() && ArcQuestToastConfig.PHASE_ADDED.get();
                case PHASE_SWITCHED -> ArcQuestToastConfig.PHASE_ADVANCED.get() && ArcQuestToastConfig.PHASE_SWITCHED.get();
                case PHASE_COMPLETED -> ArcQuestToastConfig.PHASE_ADVANCED.get() && ArcQuestToastConfig.PHASE_COMPLETED.get();
                case PHASE_PENDING_CONFIRM -> ArcQuestToastConfig.PHASE_ADVANCED.get() && ArcQuestToastConfig.PHASE_PENDING_CONFIRM.get();
                case BRANCH_CHOICE -> ArcQuestToastConfig.BRANCH_CHOICE.get();
            };
        }

        @Override public boolean persistent() { return this == PHASE_PENDING_CONFIRM || this == BRANCH_CHOICE; }
        @Override public boolean terminal() { return this == QUEST_COMPLETED || this == QUEST_FAILED; }
        @Override public boolean restartsQuest() { return this == QUEST_ACCEPTED; }
        @Override public boolean mergeable() {
            return switch (this) {
                case PHASE_ADVANCED, PHASE_ADDED, PHASE_SWITCHED, PHASE_COMPLETED, OBJECTIVE_COMPLETE,
                        COLLECTION_ENTRY_DISCOVERED, COLLECTION_ENTRY_COMPLETED,
                        COLLECTION_REWARD_UNLOCKED, COLLECTION_REWARD_CLAIMED -> true;
                default -> false;
            };
        }
        @Override public boolean supersededByTerminal() {
            return switch (this) {
                case QUEST_ACCEPTED, PHASE_ADVANCED, PHASE_ADDED, PHASE_SWITCHED, PHASE_COMPLETED,
                        OBJECTIVE_COMPLETE, PHASE_PENDING_CONFIRM, BRANCH_CHOICE -> true;
                default -> false;
            };
        }
    }
}
