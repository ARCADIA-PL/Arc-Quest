package org.arcadia.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;

public final class S2COfferSubmitResultPacket implements CustomPacketPayload {

    public static final Type<S2COfferSubmitResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "offer_submit_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2COfferSubmitResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2COfferSubmitResultPacket::encode, S2COfferSubmitResultPacket::decode);

    private final String questId;
    private final String phaseId;
    private final int objectiveIndex;
    private final CloseMode closeMode;

    public S2COfferSubmitResultPacket(String questId, String phaseId, int objectiveIndex, CloseMode closeMode) {
        this.questId = questId;
        this.phaseId = phaseId;
        this.objectiveIndex = objectiveIndex;
        this.closeMode = closeMode == null ? CloseMode.NONE : closeMode;
    }

    public static void encode(S2COfferSubmitResultPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.questId);
        buf.writeUtf(pkt.phaseId);
        buf.writeVarInt(pkt.objectiveIndex);
        buf.writeEnum(pkt.closeMode);
    }

    public static S2COfferSubmitResultPacket decode(FriendlyByteBuf buf) {
        return new S2COfferSubmitResultPacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readEnum(CloseMode.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2COfferSubmitResultPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            QuestOfferPanel.onServerSubmitResult(pkt.questId, pkt.phaseId, pkt.objectiveIndex, pkt.closeMode);
        });
    }

    public enum CloseMode {
        NONE,
        NORMAL_CLOSE,
        CLEARED_CLOSE
    }
}