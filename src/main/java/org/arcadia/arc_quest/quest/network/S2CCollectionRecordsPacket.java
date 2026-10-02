package org.arcadia.arc_quest.quest.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sparse permanent record delta on the same ordered revision stream as task progress. */
public record S2CCollectionRecordsPacket(CompoundTag records, long epoch, long baseRevision, long revision) {
    public S2CCollectionRecordsPacket { records = records.copy(); }
    public static void encode(S2CCollectionRecordsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.epoch);
        buffer.writeLong(packet.baseRevision);
        buffer.writeLong(packet.revision);
        buffer.writeNbt(packet.records);
    }
    public static S2CCollectionRecordsPacket decode(FriendlyByteBuf buffer) {
        long epoch = buffer.readLong(), base = buffer.readLong(), revision = buffer.readLong();
        CompoundTag tag = buffer.readNbt();
        if (tag == null || tag.getCompound("Entries").size() > 4096)
            throw new IllegalArgumentException("Invalid collection record delta");
        return new S2CCollectionRecordsPacket(tag, epoch, base, revision);
    }
    public static void handle(S2CCollectionRecordsPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            if (ClientQuestCache.INSTANCE.acceptDelta(packet.epoch, packet.baseRevision, packet.revision))
                ClientQuestCache.INSTANCE.applyCollectionRecordDelta(packet.records);
        });
        context.get().setPacketHandled(true);
    }
}
