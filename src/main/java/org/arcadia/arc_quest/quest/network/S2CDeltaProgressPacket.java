package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

/**
 * 增量同步包，仅同步任务进度变化。
 */
public final class S2CDeltaProgressPacket implements CustomPacketPayload {

    public static final Type<S2CDeltaProgressPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "delta_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDeltaProgressPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDeltaProgressPacket::encode, S2CDeltaProgressPacket::decode);

    private final String questId;
    private final String phaseId;
    private final int objectiveIndex;
    private final int newProgress;

    public S2CDeltaProgressPacket(String questId, String phaseId, int objectiveIndex, int newProgress) {
        this.questId = questId;
        this.phaseId = phaseId;
        this.objectiveIndex = objectiveIndex;
        this.newProgress = newProgress;
    }

    public static void encode(S2CDeltaProgressPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.questId);
        buf.writeUtf(pkt.phaseId);
        buf.writeVarInt(pkt.objectiveIndex);
        buf.writeVarInt(pkt.newProgress);
    }

    public static S2CDeltaProgressPacket decode(FriendlyByteBuf buf) {
        return new S2CDeltaProgressPacket(
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

    public static void handle(S2CDeltaProgressPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 第二批会把 ClientQuestCache 升级到 phase 维度；先走新签名
            ClientQuestCache.INSTANCE.updateObjectiveProgress(pkt.questId, pkt.phaseId, pkt.objectiveIndex, pkt.newProgress);
        });
    }
}