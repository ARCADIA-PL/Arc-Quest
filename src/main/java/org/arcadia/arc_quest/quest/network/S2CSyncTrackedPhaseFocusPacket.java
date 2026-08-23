package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record S2CSyncTrackedPhaseFocusPacket(
        @Nullable String questId,
        @Nullable String phaseId,
        long playerSessionEpoch,
        long baseRevision,
        long newRevision) {

    public S2CSyncTrackedPhaseFocusPacket {
        playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        baseRevision = Math.max(0L, baseRevision);
        newRevision = Math.max(0L, newRevision);
        if (questId == null) phaseId = null;
    }

    public static void encode(S2CSyncTrackedPhaseFocusPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.questId != null);
        if (packet.questId != null) buffer.writeUtf(packet.questId, 256);
        buffer.writeBoolean(packet.phaseId != null);
        if (packet.phaseId != null) buffer.writeUtf(packet.phaseId, 256);
        buffer.writeLong(packet.playerSessionEpoch);
        buffer.writeLong(packet.baseRevision);
        buffer.writeLong(packet.newRevision);
    }

    public static S2CSyncTrackedPhaseFocusPacket decode(FriendlyByteBuf buffer) {
        String questId = buffer.readBoolean() ? buffer.readUtf(256) : null;
        String phaseId = buffer.readBoolean() ? buffer.readUtf(256) : null;
        return new S2CSyncTrackedPhaseFocusPacket(
                questId, phaseId, buffer.readLong(), buffer.readLong(), buffer.readLong());
    }

    public static void handle(S2CSyncTrackedPhaseFocusPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (ClientQuestCache.INSTANCE.acceptDelta(
                    packet.playerSessionEpoch, packet.baseRevision, packet.newRevision)) {
                ClientQuestCache.INSTANCE.applyTrackedPhaseFocusSync(packet.questId, packet.phaseId);
            }
        });
        context.setPacketHandled(true);
    }
}
