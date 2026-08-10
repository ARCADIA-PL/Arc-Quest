package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingState;
import org.jetbrains.annotations.Nullable;

public final class S2CSyncTrackedQuestPacket implements CustomPacketPayload {

    public static final Type<S2CSyncTrackedQuestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_tracked_quest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncTrackedQuestPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncTrackedQuestPacket::encode, S2CSyncTrackedQuestPacket::decode);

    private final QuestTrackingSnapshot snapshot;
    private final QuestTrackingChangeReason reason;
    private final long playerSessionEpoch;
    private final long baseRevision;
    private final long newRevision;

    public S2CSyncTrackedQuestPacket(QuestTrackingSnapshot snapshot,
                                     QuestTrackingChangeReason reason,
                                     long playerSessionEpoch,
                                     long baseRevision,
                                     long newRevision) {
        this.snapshot = snapshot;
        this.reason = reason;
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.baseRevision = Math.max(0L, baseRevision);
        this.newRevision = Math.max(0L, newRevision);
    }

    public static void encode(S2CSyncTrackedQuestPacket packet, FriendlyByteBuf buffer) {
        String questId = packet.snapshot.questId();
        buffer.writeBoolean(questId != null);
        if (questId != null) buffer.writeUtf(questId, 256);
        buffer.writeVarInt(packet.snapshot.state().ordinal());
        buffer.writeLong(packet.snapshot.revision());
        buffer.writeVarInt(packet.reason.ordinal());
        buffer.writeLong(packet.playerSessionEpoch);
        buffer.writeLong(packet.baseRevision);
        buffer.writeLong(packet.newRevision);
    }

    public static S2CSyncTrackedQuestPacket decode(FriendlyByteBuf buffer) {
        String questId = buffer.readBoolean() ? buffer.readUtf(256) : null;
        QuestTrackingState state = enumValue(
                QuestTrackingState.values(), buffer.readVarInt(), QuestTrackingState.EMPTY);
        long trackingRevision = buffer.readLong();
        QuestTrackingChangeReason reason = enumValue(
                QuestTrackingChangeReason.values(), buffer.readVarInt(), QuestTrackingChangeReason.UNKNOWN);
        long playerSessionEpoch = buffer.readLong();
        long baseRevision = buffer.readLong();
        long newRevision = buffer.readLong();
        return new S2CSyncTrackedQuestPacket(
                new QuestTrackingSnapshot(questId, state, trackingRevision),
                reason, playerSessionEpoch, baseRevision, newRevision);
    }

    public static void handle(S2CSyncTrackedQuestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (ClientQuestCache.INSTANCE.acceptDelta(
                    packet.playerSessionEpoch, packet.baseRevision, packet.newRevision)) {
                ClientQuestCache.INSTANCE.applyTrackedQuestSync(packet.snapshot, packet.reason);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
