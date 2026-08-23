package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.jetbrains.annotations.Nullable;

public record S2CSyncTrackedPhaseFocusPacket(
        @Nullable String questId,
        @Nullable String phaseId,
        long playerSessionEpoch,
        long baseRevision,
        long newRevision) implements CustomPacketPayload {

    public static final Type<S2CSyncTrackedPhaseFocusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_tracked_phase_focus"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncTrackedPhaseFocusPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncTrackedPhaseFocusPacket::encode, S2CSyncTrackedPhaseFocusPacket::decode);

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

    public static void handle(S2CSyncTrackedPhaseFocusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (ClientQuestCache.INSTANCE.acceptDelta(
                    packet.playerSessionEpoch, packet.baseRevision, packet.newRevision)) {
                ClientQuestCache.INSTANCE.applyTrackedPhaseFocusSync(packet.questId, packet.phaseId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
