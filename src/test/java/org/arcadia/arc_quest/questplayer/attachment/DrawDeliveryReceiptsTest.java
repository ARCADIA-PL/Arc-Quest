package org.arcadia.arc_quest.questplayer.attachment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DrawDeliveryReceiptsTest {
    @Test
    void progressSnapshotsCannotRestoreOrForgeDeliveryEvidence() {
        var capability = ArcQuestPlayerAttachment.empty();
        UUID delivered = UUID.randomUUID();
        capability.recordDeliveredDraw(delivered);
        assertFalse(capability.snapshot().contains("DeliveredDrawReceipts"));

        var oldProgress = new CompoundTag();
        UUID forged = UUID.randomUUID();
        var forgedReceipts = new CompoundTag();
        forgedReceipts.putBoolean(forged.toString(), true);
        oldProgress.put("DeliveredDrawReceipts", forgedReceipts);
        capability.replaceSnapshot(oldProgress);
        assertTrue(receipts(capability).getBoolean(delivered.toString()));
        assertFalse(receipts(capability).contains(forged.toString()));
        capability.clear();
        assertTrue(receipts(capability).getBoolean(delivered.toString()));
    }

    @Test
    void vanillaSaveAndPlayerCloneCopyReceiptsIndependently() {
        UUID token = UUID.randomUUID();
        var original = ArcQuestPlayerAttachment.empty();
        original.recordDeliveredDraw(token);
        var loaded = new ArcQuestPlayerAttachmentSerializer().read(null, serialize(original), null);
        assertFalse(loaded.snapshot().contains("DeliveredDrawReceipts"));
        assertTrue(receipts(loaded).getBoolean(token.toString()));
        var clone = ArcQuestPlayerAttachment.empty();
        clone.copyDeliveryReceiptsFrom(loaded);
        loaded.acknowledgeDeliveredDraw(token);
        assertTrue(receipts(clone).getBoolean(token.toString()));
        clone.retainDeliveryReceipts(Set.of());
        assertTrue(receipts(clone).isEmpty());
        assertTrue(receipts(original).getBoolean(token.toString()));
    }

    @Test
    void malformedReceiptsAreRejectedWithoutReplacingExistingState() {
        var capability = ArcQuestPlayerAttachment.empty();
        UUID token = UUID.randomUUID();
        capability.recordDeliveredDraw(token);
        CompoundTag before = serialize(capability);
        var invalid = new CompoundTag();
        invalid.putString("DeliveredDrawReceipts", "bad");
        assertThrows(IllegalArgumentException.class, () -> capability.readDeliveryReceipts(invalid));
        var receipts = new CompoundTag();
        receipts.putBoolean("invalid-uuid", true);
        invalid.put("DeliveredDrawReceipts", receipts);
        assertThrows(IllegalArgumentException.class, () -> capability.readDeliveryReceipts(invalid));
        receipts.remove("invalid-uuid");
        receipts.putByte(token.toString(), (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> capability.readDeliveryReceipts(invalid));
        assertEquals(before, serialize(capability));
    }

    @Test
    void receiptCapacityIsBoundedAndAcknowledgementReleasesSpace() {
        var capability = ArcQuestPlayerAttachment.empty();
        UUID first = new UUID(0, 0);
        for (int i = 0; i < 4096; i++) capability.recordDeliveredDraw(new UUID(0, i));
        capability.recordDeliveredDraw(first);
        UUID extra = new UUID(1, 0);
        assertThrows(IllegalStateException.class, () -> capability.recordDeliveredDraw(extra));
        CompoundTag tooMany = serialize(capability);
        tooMany.getCompound("DeliveredDrawReceipts").putBoolean(extra.toString(), true);
        assertThrows(IllegalArgumentException.class, () -> capability.readDeliveryReceipts(tooMany));
        capability.acknowledgeDeliveredDraw(first);
        capability.recordDeliveredDraw(extra);
        assertEquals(4096, receipts(capability).size());
    }

    @Test
    void receiptOnlyAttachmentSurvivesPlayerSerializationAndProgressClear() {
        var attachment = ArcQuestPlayerAttachment.empty();
        UUID token = UUID.randomUUID();
        assertNull(new ArcQuestPlayerAttachmentSerializer().write(attachment, null));
        attachment.recordDeliveredDraw(token);
        assertTrue(attachment.isEmpty());
        CompoundTag encoded = serialize(attachment);
        assertNotNull(encoded);
        assertFalse(encoded.getCompound("Snapshot").contains("DeliveredDrawReceipts"));
        var loaded = new ArcQuestPlayerAttachmentSerializer().read(null, encoded, null);
        assertTrue(loaded.isEmpty());
        assertTrue(receipts(loaded).getBoolean(token.toString()));
        loaded.clear();
        assertNotNull(serialize(loaded));
        loaded.acknowledgeDeliveredDraw(token);
        assertNull(serialize(loaded));
    }

    @Test
    void savingANewProgressRevisionPreservesExistingReceiptEvidence() {
        var attachment = ArcQuestPlayerAttachment.empty();
        UUID token = UUID.randomUUID();
        attachment.recordDeliveredDraw(token);
        CompoundTag progress = new CompoundTag();
        progress.putString("Owner", "progress");
        progress.putLong("ArcQuestPersistenceRevision", 7);
        progress.putLong("ArcQuestPersistenceWrittenAt", 1234);
        attachment.replaceSnapshot(progress);
        progress.putString("Owner", "mutated");
        var loaded = new ArcQuestPlayerAttachmentSerializer().read(null, serialize(attachment), null);
        assertEquals(7, loaded.revision());
        assertEquals(1234, loaded.writtenAt());
        assertEquals("progress", loaded.snapshot().getString("Owner"));
        assertTrue(receipts(loaded).getBoolean(token.toString()));
    }

    @Test
    void receiptsInsideARestoredSnapshotCannotBecomePlayerFileEvidence() {
        UUID forged = UUID.randomUUID();
        CompoundTag snapshot = new CompoundTag();
        CompoundTag forgedReceipts = new CompoundTag();
        forgedReceipts.putBoolean(forged.toString(), true);
        snapshot.put("DeliveredDrawReceipts", forgedReceipts);
        var restored = ArcQuestPlayerAttachment.fromSnapshot(snapshot);
        assertFalse(restored.hasDeliveryReceipts());
        assertFalse(restored.snapshot().contains("DeliveredDrawReceipts"));
        assertNull(serialize(restored));
    }

    private static CompoundTag serialize(ArcQuestPlayerAttachment attachment) {
        return new ArcQuestPlayerAttachmentSerializer().write(attachment, null);
    }

    private static CompoundTag receipts(ArcQuestPlayerAttachment capability) {
        CompoundTag encoded = serialize(capability);
        return encoded == null ? new CompoundTag() : encoded.getCompound("DeliveredDrawReceipts");
    }
}
