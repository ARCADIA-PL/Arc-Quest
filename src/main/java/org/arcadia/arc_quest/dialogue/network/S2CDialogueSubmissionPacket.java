package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

public record S2CDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, Component title,
                                        List<Row> rows, boolean closed, boolean rejected, boolean bundle, boolean batch) implements CustomPacketPayload {
    public static final Type<S2CDialogueSubmissionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("arc_quest", "dialogue_submission_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDialogueSubmissionPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDialogueSubmissionPacket::encode, S2CDialogueSubmissionPacket::decode);

    @Override public Type<S2CDialogueSubmissionPacket> type() { return TYPE; }

    public static void handle(S2CDialogueSubmissionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> org.arcadia.arc_quest.client.hud.dialogue.DialogueDeliveryPanel.accept(packet));
    }
    public S2CDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, Component title,
                                       List<Row> rows, boolean closed, boolean rejected, boolean bundle) {
        this(sessionId, epoch, token, title, rows, closed, rejected, bundle, false);
    }
    public S2CDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, Component title,
                                       List<Row> rows, boolean closed, boolean rejected) {
        this(sessionId, epoch, token, title, rows, closed, rejected, false, false);
    }
    public S2CDialogueSubmissionPacket {
        rows = List.copyOf(rows);
        if (rows.size() > 128) throw new IllegalArgumentException("Submission row count");
        if (batch && (bundle || rows.stream().anyMatch(row -> row.required() != 1)))
            throw new IllegalArgumentException("Batch samples must each require one item");
    }
    public record Row(String id, ItemStack icon, int required, int canSubmit) {
        public Row {
            if (required < 1 || required > 4096 || canSubmit < 0 || canSubmit > required)
                throw new IllegalArgumentException("Submission row quantities");
        }
    }
    public int requiredTotal() { return bundle ? rows.stream().mapToInt(Row::required).sum() : batch ? rows.size() : 1; }
    public void encode(RegistryFriendlyByteBuf b) {
        b.writeUUID(sessionId); b.writeLong(epoch); b.writeUUID(token); ComponentSerialization.STREAM_CODEC.encode(b, title);
        b.writeCollection(rows, (buf, row) -> {
            buf.writeUtf(row.id(), 256); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, row.icon());
            buf.writeVarInt(row.required()); buf.writeVarInt(row.canSubmit());
        });
        b.writeBoolean(closed); b.writeBoolean(rejected); b.writeBoolean(bundle); b.writeBoolean(batch);
    }
    public static S2CDialogueSubmissionPacket decode(RegistryFriendlyByteBuf b) {
        UUID session = b.readUUID(); long epoch = b.readLong(); UUID token = b.readUUID();
        Component title = ComponentSerialization.STREAM_CODEC.decode(b); int count = b.readVarInt();
        if (count < 0 || count > 128) throw new IllegalArgumentException("Submission row count");
        var rows = new java.util.ArrayList<Row>(count);
        for (int i = 0; i < count; i++) rows.add(new Row(b.readUtf(256), ItemStack.OPTIONAL_STREAM_CODEC.decode(b), b.readVarInt(), b.readVarInt()));
        return new S2CDialogueSubmissionPacket(session, epoch, token, title, List.copyOf(rows),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean());
    }
}
