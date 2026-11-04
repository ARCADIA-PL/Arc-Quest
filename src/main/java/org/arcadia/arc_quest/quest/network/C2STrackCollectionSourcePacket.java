package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import org.arcadia.arc_quest.quest.logic.CollectionSourceTrackingService;

import java.util.Objects;
import java.util.function.Supplier;

/** Only a frozen run/binding and a disclosed relation can request a destination. */
public record C2STrackCollectionSourcePacket(
        long playerEpoch,
        String questId,
        String phaseId,
        String bindingId,
        String runId,
        String relationId,
        boolean clear) {
    public C2STrackCollectionSourcePacket {
        for (String value : new String[] {questId, phaseId, bindingId, runId, relationId}) {
            Objects.requireNonNull(value);
            if (value.length() > 256)
                throw new IllegalArgumentException("Collection tracking context too long");
        }
        if (!clear
                && (questId.isBlank()
                        || phaseId.isBlank()
                        || bindingId.isBlank()
                        || runId.isBlank()))
            throw new IllegalArgumentException("Missing collection tracking context");
    }

    public static void encode(C2STrackCollectionSourcePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.playerEpoch);
        buffer.writeUtf(packet.questId, 256);
        buffer.writeUtf(packet.phaseId, 256);
        buffer.writeUtf(packet.bindingId, 256);
        buffer.writeUtf(packet.runId, 256);
        buffer.writeUtf(packet.relationId, 256);
        buffer.writeBoolean(packet.clear);
    }

    public static C2STrackCollectionSourcePacket decode(FriendlyByteBuf buffer) {
        return new C2STrackCollectionSourcePacket(
                buffer.readLong(),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readUtf(256),
                buffer.readBoolean());
    }

    public static void handle(
            C2STrackCollectionSourcePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get()
                .enqueueWork(
                        () -> {
                            var player = context.get().getSender();
                            if (player != null && CollectionDetailService.allowRequest(player))
                                CollectionSourceTrackingService.request(player, packet);
                        });
        context.get().setPacketHandled(true);
    }
}
