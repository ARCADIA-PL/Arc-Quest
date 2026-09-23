package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache.TranscriptEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** 客户端主线程所有：一个当前会话、一个等待 OPEN 的会话，以及有界的关闭记录。 */
final class ClientDialogueTranscripts {
    private static final int MAX_RETIRED = 64;
    private static final long PENDING_TTL_NANOS = 30_000_000_000L;
    private final int maxEntries;
    private final int maxCharacters;
    private final LongSupplier clock;
    private final Set<UUID> retired = new LinkedHashSet<>();
    private UUID activeId;
    private History active;
    private UUID pendingId;
    private History pending;
    private long pendingSince;

    ClientDialogueTranscripts() {
        this(DialogueTranscriptCodec.MAX_ENTRIES, 1024 * 1024, System::nanoTime);
    }

    ClientDialogueTranscripts(int maxEntries, int maxCharacters, LongSupplier clock) {
        if (maxEntries <= 0 || maxCharacters <= 0) throw new IllegalArgumentException("Invalid transcript budget");
        this.maxEntries = maxEntries;
        this.maxCharacters = maxCharacters;
        this.clock = Objects.requireNonNull(clock);
    }

    boolean isRetired(UUID id) {
        return retired.contains(id);
    }

    void activate(UUID id) {
        expirePending();
        if (Objects.equals(activeId, id)) return;
        if (activeId != null) retire(activeId);
        activeId = id;
        active = Objects.equals(pendingId, id) ? pending : new History();
        clearPending();
    }

    void replace(UUID id, List<S2CDialogueTranscriptDeltaPacket.Entry> entries) {
        if (id == null || retired.contains(id)) return;
        History replacement = new History();
        for (int index = Math.max(0, entries.size() - maxEntries); index < entries.size(); index++) {
            var entry = entries.get(index);
            replacement.append(freeze(new TranscriptEntry(entry.clientMs(), entry.role(), entry.speaker(), entry.text(),
                    entry.nodeId(), entry.sayId(), entry.choiceId(),
                    entry.choiceIndexOrNeg1() >= 0 ? entry.choiceIndexOrNeg1() : null)));
        }
        expirePending();
        if (id.equals(activeId)) active = replacement;
        else {
            pendingId = id;
            pending = replacement;
            pendingSince = clock.getAsLong();
        }
    }

    void append(UUID id, TranscriptEntry entry) {
        if (id == null || retired.contains(id)) return;
        WeightedEntry copy = freeze(entry);
        expirePending();
        if (id.equals(activeId)) active.append(copy);
        else {
            if (!id.equals(pendingId)) {
                pendingId = id;
                pending = new History();
                pendingSince = clock.getAsLong();
            }
            pending.append(copy);
        }
    }

    List<TranscriptEntry> current(UUID id) {
        expirePending();
        return id != null && id.equals(activeId) ? active.snapshot() : List.of();
    }

    void close(UUID id) {
        if (id == null) return;
        retire(id);
        if (id.equals(activeId)) {
            activeId = null;
            active = null;
        }
        if (id.equals(pendingId)) clearPending();
    }

    void clear() {
        activeId = null;
        active = null;
        retired.clear();
        clearPending();
    }

    private void retire(UUID id) {
        retired.add(id);
        while (retired.size() > MAX_RETIRED) retired.remove(retired.iterator().next());
    }

    private void expirePending() {
        if (pendingId != null && clock.getAsLong() - pendingSince >= PENDING_TTL_NANOS) clearPending();
    }

    private void clearPending() {
        pendingId = null;
        pending = null;
    }

    private WeightedEntry freeze(TranscriptEntry entry) {
        String speaker = Component.Serializer.toJson(entry.speaker() == null ? Component.empty() : entry.speaker());
        String text = Component.Serializer.toJson(entry.text() == null ? Component.empty() : entry.text());
        long characters = 64L + speaker.length() + text.length() + length(entry.role())
                + length(entry.nodeId()) + length(entry.sayId()) + length(entry.choiceId());
        if (characters > maxCharacters) throw new IllegalArgumentException("Dialogue entry exceeds history text budget");
        return new WeightedEntry(new TranscriptEntry(entry.clientMs(), entry.role(),
                Component.Serializer.fromJson(speaker), Component.Serializer.fromJson(text),
                entry.nodeId(), entry.sayId(), entry.choiceId(), entry.choiceIndex()), (int) characters);
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private record WeightedEntry(TranscriptEntry entry, int characters) {
    }

    private final class History {
        private final Deque<WeightedEntry> entries = new ArrayDeque<>();
        private int characters;
        private List<TranscriptEntry> snapshot;

        void append(WeightedEntry entry) {
            while (!entries.isEmpty() && (entries.size() >= maxEntries
                    || characters > maxCharacters - entry.characters())) {
                characters -= entries.removeFirst().characters();
            }
            entries.addLast(entry);
            characters += entry.characters();
            snapshot = null;
        }

        List<TranscriptEntry> snapshot() {
            if (snapshot == null) snapshot = entries.stream().map(WeightedEntry::entry).toList();
            return snapshot;
        }
    }
}
