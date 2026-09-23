package org.arcadia.arc_quest.dialogue.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/** 沿用既有字段顺序，在分配集合前检查预算，并固定延迟编码所用的字节。 */
final class DialogueTranscriptCodec {
    static final int MAX_ENTRIES = 4096;
    static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    private static final int MIN_ENTRY_BYTES = 15;
    private static final int MAX_COMPONENT_CHARACTERS = 262144;
    private static final int MAX_COMPONENT_DEPTH = 64;

    private DialogueTranscriptCodec() {
    }

    static void checkPacketSize(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_PACKET_BYTES) {
            throw new DecoderException("Dialogue transcript exceeds packet byte limit");
        }
    }

    static int readCount(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES || count > buffer.readableBytes() / MIN_ENTRY_BYTES) {
            throw new DecoderException("Invalid dialogue transcript entry count: " + count);
        }
        return count;
    }

    static S2CDialogueTranscriptDeltaPacket.Entry readEntry(FriendlyByteBuf buffer) {
        long time = buffer.readLong();
        String role = buffer.readUtf();
        Component speaker = readComponent(buffer);
        Component text = readComponent(buffer);
        String nodeId = readOptional(buffer);
        String sayId = readOptional(buffer);
        String choiceId = readOptional(buffer);
        int index = buffer.readVarInt();
        if (index < -1) throw new DecoderException("Invalid dialogue transcript choice index: " + index);
        return new S2CDialogueTranscriptDeltaPacket.Entry(time, role, speaker, text, nodeId, sayId, choiceId, index);
    }

    static void writeEntry(FriendlyByteBuf buffer, S2CDialogueTranscriptDeltaPacket.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.choiceIndexOrNeg1() < -1) throw new IllegalArgumentException("Invalid dialogue choice index");
        buffer.writeLong(entry.clientMs());
        buffer.writeUtf(Objects.requireNonNull(entry.role(), "role"));
        writeComponent(buffer, entry.speaker());
        writeComponent(buffer, entry.text());
        writeOptional(buffer, entry.nodeId());
        writeOptional(buffer, entry.sayId());
        writeOptional(buffer, entry.choiceId());
        buffer.writeVarInt(entry.choiceIndexOrNeg1());
    }

    static byte[] freeze(Consumer<FriendlyByteBuf> encoder) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(256, MAX_PACKET_BYTES));
        try {
            encoder.accept(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static Component readComponent(FriendlyByteBuf buffer) {
        String json = buffer.readUtf(MAX_COMPONENT_CHARACTERS);
        checkComponentDepth(json);
        Component value = Component.Serializer.fromJson(json);
        if (value == null) throw new DecoderException("Null dialogue transcript component");
        return value;
    }

    private static void writeComponent(FriendlyByteBuf buffer, Component value) {
        String json = Component.Serializer.toJson(value == null ? Component.empty() : value);
        checkComponentDepth(json);
        buffer.writeUtf(json, MAX_COMPONENT_CHARACTERS);
    }

    /** 在递归 JSON/Component 解析之前检查深度；字符串中的括号不计入。 */
    private static void checkComponentDepth(String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char value = json.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '"') quoted = false;
            } else if (value == '"') quoted = true;
            else if (value == '{' || value == '[') {
                if (++depth > MAX_COMPONENT_DEPTH) throw new DecoderException("Dialogue component nesting is too deep");
            } else if (value == '}' || value == ']') depth--;
        }
    }

    private static String readOptional(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUtf() : null;
    }

    private static void writeOptional(FriendlyByteBuf buffer, String value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeUtf(value);
    }
}
