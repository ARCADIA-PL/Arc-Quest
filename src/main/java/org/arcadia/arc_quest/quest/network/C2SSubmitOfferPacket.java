package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.service.QuestOfferService;

public final class C2SSubmitOfferPacket implements CustomPacketPayload {

    public static final Type<C2SSubmitOfferPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "submit_offer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SSubmitOfferPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SSubmitOfferPacket::encode, C2SSubmitOfferPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SSubmitOfferPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
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

            PacketDistributor.sendToPlayer(
                    player,
                    new S2COfferSubmitResultPacket(pkt.questId, pkt.phaseId, pkt.objectiveIndex, mode)
            );
        });
    }
}