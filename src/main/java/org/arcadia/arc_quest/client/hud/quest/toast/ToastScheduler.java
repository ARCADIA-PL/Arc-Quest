package org.arcadia.arc_quest.client.hud.quest.toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Single-slot scheduling with an activity clock; independent of Minecraft and rendering. */
public final class ToastScheduler<K extends Enum<K> & ToastScheduler.Kind, P> {
    public static final long ENTER_MILLIS = 250, HOLD_MILLIS = 3000, EXIT_MILLIS = 200;
    public static final long EVENT_DURATION_MILLIS = ENTER_MILLIS + HOLD_MILLIS + EXIT_MILLIS;
    public static final long AGGREGATION_MILLIS = 200, MERGE_WINDOW_MILLIS = 600, DUPLICATE_MILLIS = 1500;
    public static final long STALE_MILLIS = 30_000;
    public static final long PENDING_ROTATION_MILLIS = 5000, PENDING_MINIMUM_MILLIS = 2000;
    public static final int MAX_QUEUED = 64, MAX_MERGED_SUBJECTS = 64;
    private static final int MAX_RECENT_KEYS = 1024, MAX_TERMINAL_QUESTS = 256, MAX_EVENT_STREAK = 3;

    public interface Kind {
        boolean persistent();
        boolean terminal();
        boolean mergeable();
        boolean supersededByTerminal();
        boolean restartsQuest();
    }

    public record Notice<K, P>(K type, String questId, String subjectId, P payload) {
        public Notice {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(questId, "questId");
            subjectId = subjectId == null ? "" : subjectId;
            Objects.requireNonNull(payload, "payload");
        }
    }

    public record Display<K, P>(K type, String questId, P payload, int mergedCount,
                                long elapsedMillis, boolean persistent, long version) {}
    private record Key<K>(K type, String questId, String subjectId) {}

    private final Deque<Queued> queue = new ArrayDeque<>();
    private final LinkedHashMap<Key<K>, Notice<K, P>> pending = new LinkedHashMap<>();
    private final LinkedHashMap<Key<K>, Long> recent = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> terminalQuests = new LinkedHashMap<>();
    private long activityMillis;
    private long lastTickMillis = -1;
    private boolean previouslyRunning;
    private long version;
    private int eventStreak;
    private Key<K> lastPendingKey;
    private Active active;

    /** Disabled terminal events still invalidate obsolete notifications and pending actions. */
    public boolean submit(Notice<K, P> notice, boolean enabled) {
        K type = notice.type();
        if (type.restartsQuest()) {
            terminalQuests.remove(notice.questId());
            recent.keySet().removeIf(key -> key.questId().equals(notice.questId()));
        }
        if (type.terminal()) {
            terminalQuests.put(notice.questId(), activityMillis);
            trimOldest(terminalQuests, MAX_TERMINAL_QUESTS);
            queue.removeIf(entry -> entry.notice.questId().equals(notice.questId())
                    && entry.notice.type().supersededByTerminal());
            if (active != null && active.event != null
                    && active.event.notice.questId().equals(notice.questId())
                    && active.event.notice.type().supersededByTerminal()) active = null;
            clearPendingForQuest(notice.questId());
        } else if (type.supersededByTerminal() && terminalQuests.containsKey(notice.questId())) return false;
        if (type.persistent()) {
            if (enabled) upsertPending(notice);
            return true;
        }
        if (!enabled) return false;
        Key<K> key = key(notice);
        if (active != null && active.event != null && active.event.keys.contains(key)) return false;
        for (Queued queued : queue) if (queued.keys.contains(key)) return false;
        Long seen = recent.get(key);
        if (seen != null && activityMillis - seen < DUPLICATE_MILLIS) return false;
        recent.put(key, activityMillis);
        trimOldest(recent, MAX_RECENT_KEYS);
        if (type.mergeable()) {
            Iterator<Queued> newestFirst = queue.descendingIterator();
            while (newestFirst.hasNext()) {
                Queued queued = newestFirst.next();
                if (activityMillis - queued.queuedAt > MERGE_WINDOW_MILLIS) break;
                if (queued.notice.type() == type && queued.notice.questId().equals(notice.questId())
                        && queued.keys.size() < MAX_MERGED_SUBJECTS) {
                    queued.keys.add(key);
                    return true;
                }
            }
        }
        queue.addLast(new Queued(notice, key));
        while (queue.size() > MAX_QUEUED) queue.removeFirst();
        return true;
    }

    /** ACTIVE snapshots preserve surviving entries' order, selection, and animation age. */
    public void replacePendingForQuest(String questId, Collection<Notice<K, P>> notices) {
        LinkedHashMap<Key<K>, Notice<K, P>> desired = new LinkedHashMap<>();
        for (Notice<K, P> notice : notices)
            if (notice.type().persistent() && notice.questId().equals(questId)) desired.put(key(notice), notice);
        // An empty authoritative ACTIVE snapshot can also start a new task lifetime.
        if (terminalQuests.remove(questId) != null)
            recent.keySet().removeIf(key -> key.questId().equals(questId));
        pending.keySet().removeIf(key -> key.questId().equals(questId) && !desired.containsKey(key));
        for (Notice<K, P> notice : desired.values()) upsertPending(notice);
        discardMissingPending();
    }

    public void replaceAllPending(Map<String, ? extends Collection<Notice<K, P>>> snapshots) {
        pending.keySet().removeIf(key -> !snapshots.containsKey(key.questId()));
        snapshots.forEach(this::replacePendingForQuest);
        discardMissingPending();
    }

    /** Remove stale progress after authoritative removal/abandonment; keep final outcomes. */
    public void retainActiveQuests(Set<String> questIds) {
        queue.removeIf(entry -> !entry.notice.type().terminal() && !questIds.contains(entry.notice.questId()));
        if (active != null && active.event != null && !active.event.notice.type().terminal()
                && !questIds.contains(active.event.notice.questId())) active = null;
    }

    public void dismissPending(String questId, String subjectId, K type) {
        pending.keySet().removeIf(key -> key.questId().equals(questId) && key.type() == type
                && (subjectId == null || key.subjectId().equals(subjectId)));
        discardMissingPending();
    }

    public void clearPendingForQuest(String questId) {
        pending.keySet().removeIf(key -> key.questId().equals(questId));
        discardMissingPending();
    }

    public String firstPendingQuestId(K type) {
        for (Key<K> key : pending.keySet()) if (key.type() == type) return key.questId();
        return null;
    }

    public void retainTypes(Predicate<K> enabled) {
        queue.removeIf(entry -> !enabled.test(entry.notice.type()));
        recent.keySet().removeIf(key -> !enabled.test(key.type()));
        pending.keySet().removeIf(key -> !enabled.test(key.type()));
        if (active != null && active.event != null && !enabled.test(active.event.notice.type())) active = null;
        discardMissingPending();
    }

    /** Called once per client tick. Resuming never counts the hidden/offline interval. */
    public void tick(long monotonicMillis, boolean running) {
        if (lastTickMillis >= 0 && previouslyRunning && running)
            activityMillis += Math.max(0, monotonicMillis - lastTickMillis);
        lastTickMillis = lastTickMillis < 0 ? monotonicMillis : Math.max(lastTickMillis, monotonicMillis);
        previouslyRunning = running;
        if (!running) return;
        queue.removeIf(entry -> activityMillis - entry.queuedAt >= STALE_MILLIS);
        recent.values().removeIf(seen -> activityMillis - seen >= STALE_MILLIS);
        terminalQuests.values().removeIf(seen -> activityMillis - seen >= STALE_MILLIS);
        if (active != null && active.event != null
                && activityMillis - active.startedAt >= EVENT_DURATION_MILLIS) active = null;
        discardMissingPending();
        Queued ready = queue.peekFirst();
        boolean eventReady = ready != null && activityMillis - ready.queuedAt >= AGGREGATION_MILLIS;
        if (active != null && active.pendingKey != null) {
            if (eventReady && activityMillis >= active.minimumUntil) active = null;
            else if (pending.size() > 1 && activityMillis - active.startedAt >= PENDING_ROTATION_MILLIS)
                showNextPending(false);
        }
        if (active != null) return;
        if (eventReady && !pending.isEmpty() && eventStreak >= MAX_EVENT_STREAK) showNextPending(true);
        else if (eventReady) {
            active = new Active(queue.removeFirst(), null, 0);
            eventStreak++;
        } else if (queue.isEmpty() && !pending.isEmpty()) showNextPending(false);
    }

    public Display<K, P> current() {
        if (active == null) return null;
        Notice<K, P> notice = active.event != null ? active.event.notice : pending.get(active.pendingKey);
        if (notice == null) return null;
        return new Display<>(notice.type(), notice.questId(), notice.payload(),
                active.event == null ? 1 : active.event.keys.size(),
                Math.max(0, activityMillis - active.startedAt), active.pendingKey != null, active.version);
    }

    public int queuedCount() { return queue.size(); }
    public int pendingCount() { return pending.size(); }

    public void clear() {
        queue.clear();
        pending.clear();
        recent.clear();
        terminalQuests.clear();
        active = null;
        lastPendingKey = null;
        eventStreak = 0;
        activityMillis = 0;
        lastTickMillis = -1;
        previouslyRunning = false;
        version++;
    }

    private void upsertPending(Notice<K, P> notice) {
        Key<K> key = key(notice);
        Notice<K, P> previous = pending.put(key, notice);
        if (active != null && key.equals(active.pendingKey) && !Objects.equals(previous, notice))
            active.version = ++version;
    }

    private void discardMissingPending() {
        if (active != null && active.pendingKey != null && !pending.containsKey(active.pendingKey)) active = null;
    }

    private void showNextPending(boolean guaranteedTurn) {
        List<Key<K>> keys = new ArrayList<>(pending.keySet());
        if (keys.isEmpty()) return;
        int previous = keys.indexOf(lastPendingKey);
        Key<K> selected = keys.get((previous + 1) % keys.size());
        lastPendingKey = selected;
        active = new Active(null, selected, guaranteedTurn ? activityMillis + PENDING_MINIMUM_MILLIS : 0);
        eventStreak = 0;
    }

    private Key<K> key(Notice<K, P> notice) { return new Key<>(notice.type(), notice.questId(), notice.subjectId()); }

    private static <K, V> void trimOldest(LinkedHashMap<K, V> map, int capacity) {
        while (map.size() > capacity) map.remove(map.keySet().iterator().next());
    }

    private final class Queued {
        private final Notice<K, P> notice;
        private final long queuedAt = activityMillis;
        private final Set<Key<K>> keys = new LinkedHashSet<>();
        private Queued(Notice<K, P> notice, Key<K> key) { this.notice = notice; keys.add(key); }
    }

    private final class Active {
        private final Queued event;
        private final Key<K> pendingKey;
        private final long startedAt = activityMillis;
        private final long minimumUntil;
        private long version = ++ToastScheduler.this.version;
        private Active(Queued event, Key<K> pendingKey, long minimumUntil) {
            this.event = event; this.pendingKey = pendingKey; this.minimumUntil = minimumUntil;
        }
    }
}
