package org.arcadia.arc_quest.dialogue.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DialogueTranscriptPacketTest {
    private static final UUID SESSION = UUID.fromString("12345678-1234-1234-1234-123456789012");

    @Test
    void emptyOpeningSnapshotRemainsValid() {
        var packet = new S2CDialogueTranscriptSnapshotPacket(SESSION, List.of());
        byte[] encoded = bytes(packet::encode);
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            assertArrayEquals(encoded, bytes(S2CDialogueTranscriptSnapshotPacket.decode(input)::encode));
            assertEquals(0, input.readableBytes());
        } finally {
            input.release();
        }
    }

    @Test
    void snapshotKeepsLegacyWireFormatAndRoundTripsStyledText() {
        var entry = new S2CDialogueTranscriptDeltaPacket.Entry(123, "npc", Component.literal("Speaker"),
                Component.literal("你好").withStyle(ChatFormatting.GOLD), "node", "say", null, -1);
        byte[] oldWire = bytes(buffer -> {
            buffer.writeUUID(SESSION);
            buffer.writeVarInt(1);
            writeLegacyEntry(buffer, entry);
        });
        var packet = new S2CDialogueTranscriptSnapshotPacket(SESSION, List.of(entry));
        assertArrayEquals(oldWire, bytes(packet::encode));
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(oldWire));
        try {
            var decoded = S2CDialogueTranscriptSnapshotPacket.decode(input);
            assertEquals(0, input.readableBytes());
            assertArrayEquals(oldWire, bytes(decoded::encode));
        } finally {
            input.release();
        }
    }

    @Test
    void deltaKeepsLegacyWireFormatIncludingOptionalFields() {
        var entry = new S2CDialogueTranscriptDeltaPacket.Entry(7, "player", null, null, null, null, "choice", 3);
        byte[] expected = bytes(buffer -> {
            buffer.writeUUID(SESSION);
            writeLegacyEntry(buffer, entry);
        });
        assertArrayEquals(expected, bytes(new S2CDialogueTranscriptDeltaPacket(SESSION, entry)::encode));
        FriendlyByteBuf input = new FriendlyByteBuf(Unpooled.wrappedBuffer(expected));
        try {
            assertArrayEquals(expected, bytes(S2CDialogueTranscriptDeltaPacket.decode(input)::encode));
        } finally {
            input.release();
        }
    }

    @Test
    void mutableTextAndAddonListReplacementCannotChangeDeferredEncoding() throws Exception {
        var text = Component.literal("before");
        var entry = new S2CDialogueTranscriptDeltaPacket.Entry(1, "npc", text, text, null, null, null, -1);
        var source = new ArrayList<>(List.of(entry));
        var snapshot = new S2CDialogueTranscriptSnapshotPacket(SESSION, source);
        var delta = new S2CDialogueTranscriptDeltaPacket(SESSION, entry);
        byte[] expectedSnapshot = bytes(snapshot::encode);
        byte[] expectedDelta = bytes(delta::encode);
        text.append(" after");
        source.clear();
        // 附属的构造器 RETURN Mixin 会重新赋值这个同名字段，编码仍须使用固定快照。
        Field field = S2CDialogueTranscriptSnapshotPacket.class.getDeclaredField("entries");
        field.setAccessible(true);
        field.set(snapshot, List.of(entry));
        assertArrayEquals(expectedSnapshot, bytes(snapshot::encode));
        assertArrayEquals(expectedDelta, bytes(delta::encode));
    }

    @Test
    void rejectsInvalidCountsBeforeAllocatingEntryLists() {
        for (int count : new int[]{-1, Integer.MAX_VALUE, DialogueTranscriptCodec.MAX_ENTRIES + 1, 1}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUUID(SESSION);
                buffer.writeVarInt(count);
                assertThrows(DecoderException.class, () -> S2CDialogueTranscriptSnapshotPacket.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void rejectsOversizedPacketBeforeReadingComponents() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeZero(DialogueTranscriptCodec.MAX_PACKET_BYTES + 1);
            assertThrows(DecoderException.class, () -> S2CDialogueTranscriptSnapshotPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsDeepComponentBeforeRecursiveDeserialization() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            String json = "{\"text\":\"leaf\"}";
            for (int index = 0; index < 100; index++) json = "{\"text\":\"\",\"extra\":[" + json + "]}";
            buffer.writeUUID(SESSION);
            buffer.writeLong(1);
            buffer.writeUtf("npc");
            buffer.writeUtf(json, 262144);
            assertThrows(DecoderException.class, () -> S2CDialogueTranscriptDeltaPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void bracesAndEscapedQuotesInsideTextAreNotNesting() {
        var text = Component.literal("[{}]\\\"".repeat(100));
        var entry = new S2CDialogueTranscriptDeltaPacket.Entry(1, "npc", null, text, null, null, null, -1);
        byte[] encoded = bytes(new S2CDialogueTranscriptDeltaPacket(SESSION, entry)::encode);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded));
        try {
            assertArrayEquals(encoded, bytes(S2CDialogueTranscriptDeltaPacket.decode(buffer)::encode));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsInvalidChoiceIndexOnBothWireAndConstruction() {
        var entry = new S2CDialogueTranscriptDeltaPacket.Entry(1, "npc", null, null, null, null, null, -2);
        assertThrows(IllegalArgumentException.class, () -> new S2CDialogueTranscriptDeltaPacket(SESSION, entry));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(SESSION);
            writeLegacyEntry(buffer, entry);
            assertThrows(DecoderException.class, () -> S2CDialogueTranscriptDeltaPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static byte[] bytes(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static void writeLegacyEntry(FriendlyByteBuf buffer, S2CDialogueTranscriptDeltaPacket.Entry entry) {
        buffer.writeLong(entry.clientMs());
        buffer.writeUtf(entry.role());
        buffer.writeComponent(entry.speaker() == null ? Component.empty() : entry.speaker());
        buffer.writeComponent(entry.text() == null ? Component.empty() : entry.text());
        for (String value : new String[]{entry.nodeId(), entry.sayId(), entry.choiceId()}) {
            buffer.writeBoolean(value != null);
            if (value != null) buffer.writeUtf(value);
        }
        buffer.writeVarInt(entry.choiceIndexOrNeg1());
    }
}
