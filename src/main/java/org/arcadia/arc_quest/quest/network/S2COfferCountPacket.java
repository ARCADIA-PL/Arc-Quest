package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;

public record S2COfferCountPacket(long requestId, String questId, String phaseId, int objectiveIndex, int count) {
    public static void encode(S2COfferCountPacket p, FriendlyByteBuf b) {
        b.writeLong(p.requestId); b.writeUtf(p.questId); b.writeUtf(p.phaseId);
        b.writeVarInt(p.objectiveIndex); b.writeVarInt(p.count);
    }
    public static S2COfferCountPacket decode(FriendlyByteBuf b) {
        return new S2COfferCountPacket(b.readLong(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt());
    }
}
