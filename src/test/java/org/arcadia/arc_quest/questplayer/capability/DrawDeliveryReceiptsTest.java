package org.arcadia.arc_quest.questplayer.capability;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DrawDeliveryReceiptsTest {
    @Test
    void progressSnapshotsCannotRestoreOrForgeDeliveryEvidence() {
        var capability = new ArcQuestPlayerCapability();
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
        var original = new ArcQuestPlayerCapability();
        original.recordDeliveredDraw(token);
        var loaded = new ArcQuestPlayerCapability();
        loaded.deserializeNBT(original.serializeNBT());
        assertFalse(loaded.snapshot().contains("DeliveredDrawReceipts"));
        assertTrue(receipts(loaded).getBoolean(token.toString()));
        var clone = new ArcQuestPlayerCapability();
        clone.copyDeliveryReceiptsFrom(loaded);
        loaded.acknowledgeDeliveredDraw(token);
        assertTrue(receipts(clone).getBoolean(token.toString()));
        clone.retainDeliveryReceipts(Set.of());
        assertTrue(receipts(clone).isEmpty());
        assertTrue(receipts(original).getBoolean(token.toString()));
    }

    @Test
    void malformedReceiptsAreRejectedWithoutReplacingExistingState() {
        var capability = new ArcQuestPlayerCapability();
        UUID token = UUID.randomUUID();
        capability.recordDeliveredDraw(token);
        CompoundTag before = capability.serializeNBT();
        var invalid = new CompoundTag();
        invalid.putString("DeliveredDrawReceipts", "bad");
        assertThrows(IllegalArgumentException.class, () -> capability.deserializeNBT(invalid));
        var receipts = new CompoundTag();
        receipts.putBoolean("invalid-uuid", true);
        invalid.put("DeliveredDrawReceipts", receipts);
        assertThrows(IllegalArgumentException.class, () -> capability.deserializeNBT(invalid));
        receipts.remove("invalid-uuid");
        receipts.putByte(token.toString(), (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> capability.deserializeNBT(invalid));
        assertEquals(before, capability.serializeNBT());
    }

    @Test
    void receiptCapacityIsBoundedAndAcknowledgementReleasesSpace() {
        var capability = new ArcQuestPlayerCapability();
        UUID first = new UUID(0, 0);
        for (int i = 0; i < 4096; i++) capability.recordDeliveredDraw(new UUID(0, i));
        capability.recordDeliveredDraw(first);
        UUID extra = new UUID(1, 0);
        assertThrows(IllegalStateException.class, () -> capability.recordDeliveredDraw(extra));
        CompoundTag tooMany = capability.serializeNBT();
        tooMany.getCompound("DeliveredDrawReceipts").putBoolean(extra.toString(), true);
        assertThrows(IllegalArgumentException.class, () -> capability.deserializeNBT(tooMany));
        capability.acknowledgeDeliveredDraw(first);
        capability.recordDeliveredDraw(extra);
        assertEquals(4096, receipts(capability).size());
    }

    private static CompoundTag receipts(ArcQuestPlayerCapability capability) {
        return capability.serializeNBT().getCompound("DeliveredDrawReceipts");
    }
}
