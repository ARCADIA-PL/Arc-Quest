package org.arcadia.arc_quest.quest.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.quest.service.QuestOfferService;

/** The client selects an objective, never a storage network or an asserted item count. */
public record C2SRequestOfferCountPacket(long requestId, String questId, String phaseId, int objectiveIndex) {
    public static void encode(C2SRequestOfferCountPacket p, FriendlyByteBuf b) {
        b.writeLong(p.requestId); b.writeUtf(p.questId); b.writeUtf(p.phaseId); b.writeVarInt(p.objectiveIndex);
    }
    public static C2SRequestOfferCountPacket decode(FriendlyByteBuf b) {
        return new C2SRequestOfferCountPacket(b.readLong(), b.readUtf(), b.readUtf(), b.readVarInt());
    }
    public static void handle(C2SRequestOfferCountPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            int count = QuestOfferService.previewOfferable(player, p.questId, p.phaseId, p.objectiveIndex);
            ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2COfferCountPacket(p.requestId, p.questId, p.phaseId, p.objectiveIndex, count));
        });
        context.setPacketHandled(true);
    }
}
