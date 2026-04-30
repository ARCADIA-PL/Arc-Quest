package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.quest.service.QuestOfferService;

import java.util.function.Supplier;

public class C2SSubmitOfferPacket {
    private final String questId;
    private final String phaseId;
    private final int objectiveIndex;
    private final int submitAmount; // 建议先支持 x1/x5/xall

    public C2SSubmitOfferPacket(String questId, String phaseId, int objectiveIndex, int submitAmount) {
        this.questId = questId;
        this.phaseId = phaseId;
        this.objectiveIndex = objectiveIndex;
        this.submitAmount = submitAmount;
    }

    public static C2SSubmitOfferPacket of(String questId, String phaseId, int objectiveIndex, int submitAmount) {
        return new C2SSubmitOfferPacket(questId, phaseId, objectiveIndex, submitAmount);
    }

    public static void encode(C2SSubmitOfferPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.questId);
        buf.writeUtf(pkt.phaseId);
        buf.writeVarInt(pkt.objectiveIndex);
        buf.writeVarInt(pkt.submitAmount);
    }

    public static C2SSubmitOfferPacket decode(FriendlyByteBuf buf) {
        return new C2SSubmitOfferPacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public static void handle(C2SSubmitOfferPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            QuestOfferService.OfferSubmitResult result =
                    QuestOfferService.submitOffer(player, pkt.questId, pkt.phaseId, pkt.objectiveIndex, pkt.submitAmount);

            S2COfferSubmitResultPacket.CloseMode mode = S2COfferSubmitResultPacket.CloseMode.NONE;
            if (result.accepted()) {
                if (result.phaseChanged()) {
                    mode = S2COfferSubmitResultPacket.CloseMode.CLEARED_CLOSE;
                } else if (result.objectiveReached()) {
                    mode = S2COfferSubmitResultPacket.CloseMode.NORMAL_CLOSE;
                }
            }

            ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2COfferSubmitResultPacket(pkt.questId, pkt.phaseId, pkt.objectiveIndex, mode)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}