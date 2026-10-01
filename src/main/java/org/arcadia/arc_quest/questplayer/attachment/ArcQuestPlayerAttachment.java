package org.arcadia.arc_quest.questplayer.attachment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ArcQuestPlayerAttachment {

    public static final String RECEIPTS_KEY = "DeliveredDrawReceipts";
    private CompoundTag snapshot;
    private Set<UUID> deliveredDraws = new HashSet<>();

    private ArcQuestPlayerAttachment(long revision, long writtenAt, CompoundTag snapshot) {
        replaceSnapshot(ArcQuestPlayerPersistenceMetadata.stamp(snapshot, revision, writtenAt));
    }

    public static ArcQuestPlayerAttachment empty() {
        return new ArcQuestPlayerAttachment(0L, 0L, new CompoundTag());
    }

    public static ArcQuestPlayerAttachment fromSnapshot(CompoundTag snapshot) {
        return new ArcQuestPlayerAttachment(
                ArcQuestPlayerPersistenceMetadata.revision(snapshot),
                ArcQuestPlayerPersistenceMetadata.writtenAt(snapshot),
                snapshot);
    }

    public static ArcQuestPlayerAttachment of(long revision, long writtenAt, CompoundTag snapshot) {
        return new ArcQuestPlayerAttachment(revision, writtenAt, snapshot);
    }

    public synchronized long revision() {
        return ArcQuestPlayerPersistenceMetadata.revision(snapshot);
    }

    public synchronized long writtenAt() {
        return ArcQuestPlayerPersistenceMetadata.writtenAt(snapshot);
    }

    public synchronized CompoundTag snapshot() {
        return snapshot.copy();
    }

    public synchronized void replaceSnapshot(CompoundTag snapshot) {
        this.snapshot = snapshot == null ? new CompoundTag() : snapshot.copy();
        // Checkpoints/imports cannot create proof that inventory was saved.
        this.snapshot.remove(RECEIPTS_KEY);
    }

    public synchronized void clear() { snapshot = new CompoundTag(); }

    public synchronized boolean isEmpty() {
        return ArcQuestPlayerPersistenceMetadata.isPayloadEmpty(snapshot);
    }

    public synchronized boolean hasDeliveryReceipts() { return !deliveredDraws.isEmpty(); }

    public synchronized CompoundTag serializeDeliveryReceipts() {
        CompoundTag receipts = new CompoundTag();
        deliveredDraws.forEach(id -> receipts.putBoolean(id.toString(), true));
        return receipts;
    }

    public synchronized void readDeliveryReceipts(CompoundTag root) {
        if (root.contains(RECEIPTS_KEY) && !root.contains(RECEIPTS_KEY, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid draw receipt container");
        }
        CompoundTag receipts = root.getCompound(RECEIPTS_KEY);
        if (receipts.size() > 4096) throw new IllegalArgumentException("Too many draw delivery receipts");
        Set<UUID> parsed = new HashSet<>();
        for (String key : receipts.getAllKeys()) {
            UUID id = UUID.fromString(key);
            if (!id.toString().equals(key) || !receipts.contains(key, Tag.TAG_BYTE) || receipts.getByte(key) != 1) {
                throw new IllegalArgumentException("Invalid draw delivery receipt");
            }
            parsed.add(id);
        }
        deliveredDraws = parsed;
    }

    public synchronized void recordDeliveredDraw(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        if (!deliveredDraws.contains(transactionId) && deliveredDraws.size() >= 4096) {
            throw new IllegalStateException("Draw receipts require successful player save verification");
        }
        deliveredDraws.add(transactionId);
    }

    public synchronized void acknowledgeDeliveredDraw(UUID transactionId) { deliveredDraws.remove(transactionId); }

    public synchronized void retainDeliveryReceipts(Set<UUID> unresolvedTransactions) {
        deliveredDraws.retainAll(unresolvedTransactions);
    }

    public void copyDeliveryReceiptsFrom(ArcQuestPlayerAttachment source) {
        Set<UUID> copy;
        synchronized (source) { copy = new HashSet<>(source.deliveredDraws); }
        synchronized (this) { deliveredDraws = copy; }
    }
}
