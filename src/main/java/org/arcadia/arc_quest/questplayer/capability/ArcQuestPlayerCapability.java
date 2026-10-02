package org.arcadia.arc_quest.questplayer.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ArcQuestPlayerCapability implements INBTSerializable<CompoundTag> {

    private CompoundTag snapshot = new CompoundTag();
    private static final String RECEIPTS = "DeliveredDrawReceipts";
    private Set<UUID> deliveredDraws = new HashSet<>();
    private static final String COLLECTION_RECEIPTS = "DeliveredCollectionReceipts";
    private Set<UUID> deliveredCollections = new HashSet<>();

    public synchronized CompoundTag snapshot() {
        return snapshot.copy();
    }

    public synchronized void replaceSnapshot(CompoundTag snapshot) {
        this.snapshot = snapshot == null ? new CompoundTag() : snapshot.copy();
        // 操作回执不属于可恢复的进度：检查点和管理员导入不能为旧背包补造回执。
        this.snapshot.remove(RECEIPTS);
        this.snapshot.remove(COLLECTION_RECEIPTS);
    }

    public synchronized void clear() {
        snapshot = new CompoundTag();
    }

    public synchronized boolean isEmpty() {
        return ArcQuestPlayerPersistenceMetadata.isPayloadEmpty(snapshot);
    }

    @Override
    public synchronized CompoundTag serializeNBT() {
        CompoundTag root = snapshot.copy();
        CompoundTag receipts = new CompoundTag();
        deliveredDraws.forEach(id -> receipts.putBoolean(id.toString(), true));
        root.put(RECEIPTS, receipts);
        CompoundTag collections = new CompoundTag();
        deliveredCollections.forEach(id -> collections.putBoolean(id.toString(), true));
        root.put(COLLECTION_RECEIPTS, collections);
        return root;
    }

    @Override
    public synchronized void deserializeNBT(CompoundTag tag) {
        if (tag.contains(RECEIPTS) && !tag.contains(RECEIPTS, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid draw receipt container");
        }
        CompoundTag receipts = tag.getCompound(RECEIPTS);
        if (receipts.size() > 4096) throw new IllegalArgumentException("Too many draw delivery receipts");
        Set<UUID> parsed = new HashSet<>();
        for (String key : receipts.getAllKeys()) {
            UUID id = UUID.fromString(key);
            if (!id.toString().equals(key) || !receipts.contains(key, Tag.TAG_BYTE) || receipts.getByte(key) != 1) {
                throw new IllegalArgumentException("Invalid draw delivery receipt");
            }
            parsed.add(id);
        }
        if (tag.contains(COLLECTION_RECEIPTS) && !tag.contains(COLLECTION_RECEIPTS, Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid collection delivery receipt container");
        CompoundTag collections = tag.getCompound(COLLECTION_RECEIPTS);
        if (collections.size() > 8192) throw new IllegalArgumentException("Too many collection delivery receipts");
        Set<UUID> parsedCollections = new HashSet<>();
        for (String key : collections.getAllKeys()) {
            UUID id = UUID.fromString(key);
            if (!id.toString().equals(key) || !collections.contains(key, Tag.TAG_BYTE) || collections.getByte(key) != 1)
                throw new IllegalArgumentException("Invalid collection delivery receipt");
            parsedCollections.add(id);
        }
        replaceSnapshot(tag);
        deliveredDraws = parsed;
        deliveredCollections = parsedCollections;
    }

    public synchronized void recordDeliveredDraw(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        if (!deliveredDraws.contains(transactionId) && deliveredDraws.size() >= 4096) {
            throw new IllegalStateException("Draw receipts require successful player save verification");
        }
        deliveredDraws.add(transactionId);
    }

    public synchronized void acknowledgeDeliveredDraw(UUID transactionId) {
        deliveredDraws.remove(transactionId);
    }

    public synchronized void retainDeliveryReceipts(Set<UUID> unresolvedTransactions) {
        deliveredDraws.retainAll(unresolvedTransactions);
    }

    public void copyDeliveryReceiptsFrom(ArcQuestPlayerCapability source) {
        Set<UUID> copy;
        Set<UUID> collectionCopy;
        synchronized (source) { copy = new HashSet<>(source.deliveredDraws); collectionCopy = new HashSet<>(source.deliveredCollections); }
        synchronized (this) { deliveredDraws = copy; deliveredCollections = collectionCopy; }
    }
    public synchronized boolean hasDeliveredCollection(UUID token) { return deliveredCollections.contains(token); }
    public synchronized boolean canRecordDeliveredCollection(UUID token) {
        Objects.requireNonNull(token, "collection delivery token");
        return deliveredCollections.contains(token) || deliveredCollections.size() < 8192;
    }
    /** Only a successfully loaded journal can prove which inventory receipts still need to survive. */
    public synchronized void retainCollectionDeliveryReceipts(Set<UUID> unresolvedTokens) {
        deliveredCollections.retainAll(unresolvedTokens);
    }
    public synchronized void recordDeliveredCollection(UUID token) {
        if (!deliveredCollections.contains(token) && deliveredCollections.size() >= 8192)
            throw new IllegalStateException("Collection delivery receipts require save verification");
        deliveredCollections.add(token);
    }
    public synchronized void acknowledgeDeliveredCollection(UUID token) { deliveredCollections.remove(token); }
}
