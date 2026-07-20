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
    private final long playerSessionEpoch;
    private final long baseRevision;
    private final long newRevision;

    public S2CDeltaProgressPacket(String questId, String phaseId, int objectiveIndex, int newProgress) {
        this(questId, phaseId, objectiveIndex, newProgress, 0L, 0L, 0L);
    }

    public S2CDeltaProgressPacket(String questId, String phaseId, int objectiveIndex, int newProgress,
                                  long playerSessionEpoch, long baseRevision, long newRevision) {
        this.questId = questId;
        this.phaseId = phaseId;
        this.objectiveIndex = objectiveIndex;
        this.newProgress = newProgress;
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.baseRevision = Math.max(0L, baseRevision);
        this.newRevision = Math.max(0L, newRevision);
    }

    public static void encode(S2CDeltaProgressPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.questId);
        buf.writeUtf(pkt.phaseId);
        buf.writeVarInt(pkt.objectiveIndex);
        buf.writeVarInt(pkt.newProgress);
        buf.writeLong(pkt.playerSessionEpoch);
        buf.writeLong(pkt.baseRevision);
        buf.writeLong(pkt.newRevision);
    }

    public static S2CDeltaProgressPacket decode(FriendlyByteBuf buf) {
        return new S2CDeltaProgressPacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong()
        );
    }

    public static void handle(S2CDeltaProgressPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 第二批会把 ClientQuestCache 升级到 phase 维度；先走新签名
            if (ClientQuestCache.INSTANCE.acceptDelta(
                    pkt.playerSessionEpoch, pkt.baseRevision, pkt.newRevision)) {
                ClientQuestCache.INSTANCE.updateObjectiveProgress(
                        pkt.questId, pkt.phaseId, pkt.objectiveIndex, pkt.newProgress);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public String getQuestId() {
        return questId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public int getObjectiveIndex() {
        return objectiveIndex;
    }

    public int getNewProgress() {
        return newProgress;
    }

    public long getBaseRevision() {
        return baseRevision;
    }

    public long getNewRevision() {
        return newRevision;
    }
}
