package org.arcadia.arc_quest.quest.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

/** Sparse permanent record delta on the same ordered revision stream as task progress. */
public record S2CCollectionRecordsPacket(CompoundTag records, long epoch, long baseRevision, long revision) implements CustomPacketPayload {
    public static final Type<S2CCollectionRecordsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_collection_records"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CCollectionRecordsPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CCollectionRecordsPacket::encode, S2CCollectionRecordsPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
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
    public static void handle(S2CCollectionRecordsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (ClientQuestCache.INSTANCE.acceptDelta(packet.epoch, packet.baseRevision, packet.revision))
                ClientQuestCache.INSTANCE.applyCollectionRecordDelta(packet.records);
        });
    }
}
