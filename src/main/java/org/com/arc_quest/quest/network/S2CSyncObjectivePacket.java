package org.com.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C：单个目标进度增量更新。
 * <p>
 * 最轻量级的同步包（仅 3 个字段），适合高频发送（如连续击杀怪物）。
 */
public class S2CSyncObjectivePacket {

    private final String questId;
    private final int objectiveIndex;
    private final int newProgress;

    public S2CSyncObjectivePacket(String questId, int objectiveIndex, int newProgress) {
        this.questId = questId;
        this.objectiveIndex = objectiveIndex;
        this.newProgress = newProgress;
    }

    // ── 编码 ──────────────────────────────────────────

    public static void encode(S2CSyncObjectivePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.questId, 256);
        buf.writeVarInt(pkt.objectiveIndex);
        buf.writeVarInt(pkt.newProgress);
    }

    // ── 解码 ──────────────────────────────────────────

    public static S2CSyncObjectivePacket decode(FriendlyByteBuf buf) {
        String questId = buf.readUtf(256);
        int objIndex = buf.readVarInt();
        int progress = buf.readVarInt();
        return new S2CSyncObjectivePacket(questId, objIndex, progress);
    }

    // ── 处理（客户端）─────────────────────────────────

    public static void handle(S2CSyncObjectivePacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientQuestCache.INSTANCE.updateObjectiveProgress(
                    pkt.questId, pkt.objectiveIndex, pkt.newProgress);
        });
        ctx.get().setPacketHandled(true);
    }
}