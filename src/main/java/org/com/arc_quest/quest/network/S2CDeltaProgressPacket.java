package org.com.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.quest.capability.QuestRuntimeData;

import java.util.function.Supplier;

/**
 * 增量同步包 - 仅同步任务进度变化，极大减少带宽占用。
 * 
 * <p>使用场景：
 * <ul>
 *   <li>目标进度更新（如击杀数+1）</li>
 *   <li>Flag/Variable变更</li>
 * </ul>
 * 
 * <p>与全量包的区别：
 * <ul>
 *   <li>不包含任务定义结构</li>
 *   <li>仅传输变化的字段</li>
 *   <li>体积约为全量包的1/10</li>
 * </ul>
 */
public class S2CDeltaProgressPacket {

    private final String questId;
    private final int objectiveIndex;
    private final int newProgress;
    
    /**
     * @param questId         任务ID
     * @param objectiveIndex  目标索引（-1表示非目标进度，如flag/variable）
     * @param newProgress     新进度值
     */
    public S2CDeltaProgressPacket(String questId, int objectiveIndex, int newProgress) {
        this.questId = questId;
        this.objectiveIndex = objectiveIndex;
        this.newProgress = newProgress;
    }

    public static void encode(S2CDeltaProgressPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.questId);
        buf.writeVarInt(pkt.objectiveIndex);
        buf.writeVarInt(pkt.newProgress);
    }

    public static S2CDeltaProgressPacket decode(FriendlyByteBuf buf) {
        return new S2CDeltaProgressPacket(
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public static void handle(S2CDeltaProgressPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 更新客户端缓存中的进度
            QuestRuntimeData cached = ClientQuestCache.INSTANCE.getActiveQuest(pkt.questId);
            if (cached != null && pkt.objectiveIndex >= 0) {
                cached.setObjectiveProgress(pkt.objectiveIndex, pkt.newProgress);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
