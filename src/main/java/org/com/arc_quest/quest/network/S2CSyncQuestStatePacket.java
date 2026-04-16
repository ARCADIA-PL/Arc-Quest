package org.com.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.quest.capability.QuestRuntimeData;

import java.util.function.Supplier;

/**
 * S2C：单个任务的完整状态同步。
 * <p>
 * 在以下时机发送：
 * <ul>
 *   <li>任务接受</li>
 *   <li>阶段推进</li>
 *   <li>任务完成/失败</li>
 *   <li>等待玩家选择分支</li>
 * </ul>
 */
public class S2CSyncQuestStatePacket {

    private final QuestRuntimeData data;

    public S2CSyncQuestStatePacket(QuestRuntimeData data) {
        this.data = data;
    }

    // ── 编码 ──────────────────────────────────────────

    public static void encode(S2CSyncQuestStatePacket pkt, FriendlyByteBuf buf) {
        pkt.data.writeToNetwork(buf);
    }

    // ── 解码 ──────────────────────────────────────────

    public static S2CSyncQuestStatePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncQuestStatePacket(QuestRuntimeData.readFromNetwork(buf));
    }

    // ── 处理（客户端）─────────────────────────────────

    public static void handle(S2CSyncQuestStatePacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientQuestCache.INSTANCE.updateQuest(pkt.data);
        });
        ctx.get().setPacketHandled(true);
    }
}