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

    public synchronized CompoundTag snapshot() {
        return snapshot.copy();
    }

    public synchronized void replaceSnapshot(CompoundTag snapshot) {
        this.snapshot = snapshot == null ? new CompoundTag() : snapshot.copy();
        // 操作回执不属于可恢复的进度：检查点和管理员导入不能为旧背包补造回执。
        this.snapshot.remove(RECEIPTS);
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
        replaceSnapshot(tag);
        deliveredDraws = parsed;
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
        synchronized (source) { copy = new HashSet<>(source.deliveredDraws); }
        synchronized (this) { deliveredDraws = copy; }
    }
}
