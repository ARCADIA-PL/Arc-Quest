package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.CollectionDetailDocument;
import java.util.ArrayList;

/** Bounded, display-only data; item templates retain their tooltip components. */
public record S2CCollectionDetailPacket(C2SCollectionDetailRequest request, CollectionDetailDocument document) implements CustomPacketPayload {
    public static final Type<S2CCollectionDetailPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "collection_detail"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CCollectionDetailPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CCollectionDetailPacket::encode, S2CCollectionDetailPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static final int MAX_BYTES = 262144;
    public static void encode(S2CCollectionDetailPacket packet, RegistryFriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        C2SCollectionDetailRequest.encode(packet.request, buffer);
        text(buffer, packet.document.title()); buffer.writeVarInt(packet.document.sections().size());
        for (var section : packet.document.sections()) {
            text(buffer, section.title()); text(buffer, section.status()); text(buffer, section.hint());
            buffer.writeVarInt(section.lines().size());
            for (var line : section.lines()) {
                text(buffer, line.label()); buffer.writeVarInt(line.required()); buffer.writeInt(line.owned());
                buffer.writeVarInt(line.alternatives().size());
                for (var stack : line.alternatives()) ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            }
        }
        if (buffer.writerIndex() - start > MAX_BYTES) throw new IllegalArgumentException("Collection detail response too large");
    }
    public static S2CCollectionDetailPacket decode(RegistryFriendlyByteBuf buffer) {
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
                for (int k = 0; k < alternatives; k++) stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
                rows.add(new CollectionDetailDocument.Line(stacks, label, required, owned));
            }
            sections.add(new CollectionDetailDocument.Section(heading, status, hint, rows));
        }
        return new S2CCollectionDetailPacket(request, new CollectionDetailDocument(title, sections));
    }
    private static int size(RegistryFriendlyByteBuf buffer, int max) {
        int value = buffer.readVarInt();
        if (value < 0 || value > max) throw new io.netty.handler.codec.DecoderException("Invalid collection detail size");
        return value;
    }
    private static void text(RegistryFriendlyByteBuf buffer, Component value) { buffer.writeUtf(Component.Serializer.toJson(value, buffer.registryAccess()), 8192); }
    private static Component text(RegistryFriendlyByteBuf buffer) {
        var value = Component.Serializer.fromJson(buffer.readUtf(8192), buffer.registryAccess());
        if (value == null) throw new io.netty.handler.codec.DecoderException("Missing collection detail text");
        return value;
    }
    public static void handle(S2CCollectionDetailPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCollectionDetails.INSTANCE.accept(packet));
    }
}
