package org.arcadia.arc_quest.dialogue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

public record S2CDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, Component title,
                                        List<Row> rows, boolean closed, boolean rejected, boolean bundle) {
    public S2CDialogueSubmissionPacket(UUID sessionId, long epoch, UUID token, Component title,
                                       List<Row> rows, boolean closed, boolean rejected) {
        this(sessionId, epoch, token, title, rows, closed, rejected, false);
    }
    public S2CDialogueSubmissionPacket {
        rows = List.copyOf(rows);
        if (rows.size() > 128) throw new IllegalArgumentException("Submission row count");
    }
    public record Row(String id, ItemStack icon, int required, int canSubmit) {
        public Row {
            if (required < 1 || required > 4096 || canSubmit < 0 || canSubmit > required)
                throw new IllegalArgumentException("Submission row quantities");
        }
    }
    public int requiredTotal() { return bundle ? rows.stream().mapToInt(Row::required).sum() : 1; }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(sessionId); b.writeLong(epoch); b.writeUUID(token); b.writeComponent(title);
        b.writeCollection(rows, (buf, row) -> {
            buf.writeUtf(row.id(), 256); buf.writeItem(row.icon());
            buf.writeVarInt(row.required()); buf.writeVarInt(row.canSubmit());
        });
        b.writeBoolean(closed); b.writeBoolean(rejected); b.writeBoolean(bundle);
    }
    public static S2CDialogueSubmissionPacket decode(FriendlyByteBuf b) {
        UUID session = b.readUUID(); long epoch = b.readLong(); UUID token = b.readUUID();
        Component title = b.readComponent(); int count = b.readVarInt();
        if (count < 0 || count > 128) throw new IllegalArgumentException("Submission row count");
        var rows = new java.util.ArrayList<Row>(count);
        for (int i = 0; i < count; i++) rows.add(new Row(b.readUtf(256), b.readItem(), b.readVarInt(), b.readVarInt()));
        return new S2CDialogueSubmissionPacket(session, epoch, token, title, List.copyOf(rows),
                b.readBoolean(), b.readBoolean(), b.readBoolean());
    }
}
