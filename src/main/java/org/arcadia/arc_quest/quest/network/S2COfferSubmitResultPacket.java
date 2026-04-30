package org.arcadia.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;

import java.util.function.Supplier;

public class S2COfferSubmitResultPacket {

    public enum CloseMode {
        NONE,
        NORMAL_CLOSE,
        CLEARED_CLOSE
    }

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

    public static void handle(S2COfferSubmitResultPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            QuestOfferPanel.onServerSubmitResult(pkt.questId, pkt.phaseId, pkt.objectiveIndex, pkt.closeMode);
        });
        ctx.get().setPacketHandled(true);
    }
}