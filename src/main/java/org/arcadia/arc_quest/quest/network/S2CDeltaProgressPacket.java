package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 增量同步包，仅同步任务进度变化。
 */
public class S2CDeltaProgressPacket {

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

    public static void handle(S2CDeltaProgressPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 第二批会把 ClientQuestCache 升级到 phase 维度；先走新签名
            ClientQuestCache.INSTANCE.updateObjectiveProgress(pkt.questId, pkt.phaseId, pkt.objectiveIndex, pkt.newProgress);
        });
        ctx.get().setPacketHandled(true);
    }
}