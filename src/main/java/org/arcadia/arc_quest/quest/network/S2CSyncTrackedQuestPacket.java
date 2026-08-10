package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingState;

import java.util.function.Supplier;

public final class S2CSyncTrackedQuestPacket {

    private final QuestTrackingSnapshot snapshot;
    private final QuestTrackingChangeReason reason;
    private final long playerSessionEpoch;
    private final long baseRevision;
    private final long newRevision;

    public S2CSyncTrackedQuestPacket(QuestTrackingSnapshot snapshot, QuestTrackingChangeReason reason,
                                     long playerSessionEpoch, long baseRevision, long newRevision) {
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
        QuestTrackingState state = enumValue(QuestTrackingState.values(), buffer.readVarInt(), QuestTrackingState.EMPTY);
        long trackingRevision = buffer.readLong();
        QuestTrackingChangeReason reason = enumValue(QuestTrackingChangeReason.values(), buffer.readVarInt(), QuestTrackingChangeReason.UNKNOWN);
        return new S2CSyncTrackedQuestPacket(new QuestTrackingSnapshot(questId, state, trackingRevision), reason,
                buffer.readLong(), buffer.readLong(), buffer.readLong());
    }

    public static void handle(S2CSyncTrackedQuestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (ClientQuestCache.INSTANCE.acceptDelta(packet.playerSessionEpoch, packet.baseRevision, packet.newRevision)) {
                ClientQuestCache.INSTANCE.applyTrackedQuestSync(packet.snapshot, packet.reason);
            }
        });
        context.setPacketHandled(true);
    }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
