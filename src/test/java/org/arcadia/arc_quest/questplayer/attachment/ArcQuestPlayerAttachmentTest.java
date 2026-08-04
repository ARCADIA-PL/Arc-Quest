package org.arcadia.arc_quest.questplayer.attachment;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questplayer.persistence.ArcQuestPlayerPersistenceMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArcQuestPlayerAttachmentTest {

    @Test
    void snapshotIsDefensivelyCopied() {
        CompoundTag source = ArcQuestPlayerPersistenceMetadata.stamp(snapshot("original"), 8L, 120L);
        ArcQuestPlayerAttachment attachment = ArcQuestPlayerAttachment.fromSnapshot(source);

        source.putString("Value", "source-mutated");
        CompoundTag extracted = attachment.snapshot();
        extracted.putString("Value", "copy-mutated");

        assertEquals("original", attachment.snapshot().getString("Value"));
        assertEquals(8L, attachment.revision());
        assertEquals(120L, attachment.writtenAt());
    }

    @Test
    void serializerRoundTripsEnvelopeAndSnapshot() {
        ArcQuestPlayerAttachmentSerializer serializer = new ArcQuestPlayerAttachmentSerializer();
        ArcQuestPlayerAttachment source = ArcQuestPlayerAttachment.of(12L, 500L, snapshot("saved"));

        CompoundTag encoded = serializer.write(source, null);
        ArcQuestPlayerAttachment decoded = serializer.read(null, encoded, null);

        assertEquals(12L, decoded.revision());
        assertEquals(500L, decoded.writtenAt());
        assertEquals("saved", decoded.snapshot().getString("Value"));
    }

    @Test
    void serializerSkipsEmptyDefaultAttachment() {
        ArcQuestPlayerAttachmentSerializer serializer = new ArcQuestPlayerAttachmentSerializer();

        assertNull(serializer.write(ArcQuestPlayerAttachment.empty(), null));
    }

    private static CompoundTag snapshot(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Value", value);
        return tag;
    }
}
