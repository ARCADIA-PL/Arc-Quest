package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.api.CollectionDetailDocument;
import java.util.ArrayList;
import java.util.function.Supplier;

/** Bounded, display-only data; item templates retain their tooltip NBT. */
public record S2CCollectionDetailPacket(C2SCollectionDetailRequest request, CollectionDetailDocument document) {
    private static final int MAX_BYTES = 262144;
    public static void encode(S2CCollectionDetailPacket packet, FriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        C2SCollectionDetailRequest.encode(packet.request, buffer);
        text(buffer, packet.document.title()); buffer.writeVarInt(packet.document.sections().size());
        for (var section : packet.document.sections()) {
            text(buffer, section.title()); text(buffer, section.status()); text(buffer, section.hint());
            buffer.writeVarInt(section.lines().size());
            for (var line : section.lines()) {
                text(buffer, line.label()); buffer.writeVarInt(line.required()); buffer.writeInt(line.owned());
                buffer.writeVarInt(line.alternatives().size());
                for (var stack : line.alternatives()) buffer.writeItemStack(stack, false);
            }
        }
        if (buffer.writerIndex() - start > MAX_BYTES) throw new IllegalArgumentException("Collection detail response too large");
    }
    public static S2CCollectionDetailPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) throw new io.netty.handler.codec.DecoderException("Collection detail response too large");
        var request = C2SCollectionDetailRequest.decode(buffer);
        var title = text(buffer);
        int count = size(buffer, 16), total = 0;
        var sections = new ArrayList<CollectionDetailDocument.Section>(count);
        for (int i = 0; i < count; i++) {
            var heading = text(buffer); var status = text(buffer); var hint = text(buffer);
            int lines = size(buffer, 64); total += lines;
            if (total > 128) throw new io.netty.handler.codec.DecoderException("Too many detail lines");
            var rows = new ArrayList<CollectionDetailDocument.Line>(lines);
            for (int j = 0; j < lines; j++) {
                var label = text(buffer); int required = buffer.readVarInt(), owned = buffer.readInt();
                int alternatives = size(buffer, 64);
                var stacks = new ArrayList<net.minecraft.world.item.ItemStack>(alternatives);
                for (int k = 0; k < alternatives; k++) stacks.add(buffer.readItem());
                rows.add(new CollectionDetailDocument.Line(stacks, label, required, owned));
            }
            sections.add(new CollectionDetailDocument.Section(heading, status, hint, rows));
        }
        return new S2CCollectionDetailPacket(request, new CollectionDetailDocument(title, sections));
    }
    private static int size(FriendlyByteBuf buffer, int max) {
        int value = buffer.readVarInt();
        if (value < 0 || value > max) throw new io.netty.handler.codec.DecoderException("Invalid collection detail size");
        return value;
    }
    private static void text(FriendlyByteBuf buffer, Component value) { buffer.writeUtf(Component.Serializer.toJson(value), 8192); }
    private static Component text(FriendlyByteBuf buffer) {
        var value = Component.Serializer.fromJson(buffer.readUtf(8192));
        if (value == null) throw new io.netty.handler.codec.DecoderException("Missing collection detail text");
        return value;
    }
    public static void handle(S2CCollectionDetailPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientCollectionDetails.INSTANCE.accept(packet));
        context.get().setPacketHandled(true);
    }
}
