package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * S2C：全局 Flags 和 Variables 同步。
 * <p>
 * 在以下时机发送：
 * <ul>
 *   <li>任务完成后（奖励可能修改 flags/vars）</li>
 *   <li>管理员手动修改后</li>
 *   <li>FlagReward / VariableReward 执行后</li>
 * </ul>
 */
public class S2CSyncFlagsVarsPacket {

    private final Set<String> flags;
    private final Map<String, Integer> variables;
    private final long playerSessionEpoch;
    private final long baseRevision;
    private final long newRevision;

    public S2CSyncFlagsVarsPacket(ArcQuestPlayer data) {
        this(data, 0L, 0L, 0L);
    }

    public S2CSyncFlagsVarsPacket(ArcQuestPlayer data, long playerSessionEpoch,
                                  long baseRevision, long newRevision) {
        flags = new HashSet<>(data.getAllFlags());
        variables = new HashMap<>(data.getAllVariables());
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.baseRevision = Math.max(0L, baseRevision);
        this.newRevision = Math.max(0L, newRevision);
    }

    private S2CSyncFlagsVarsPacket(Set<String> flags, Map<String, Integer> variables,
                                   long playerSessionEpoch, long baseRevision, long newRevision) {
        this.flags = flags;
        this.variables = variables;
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.baseRevision = Math.max(0L, baseRevision);
        this.newRevision = Math.max(0L, newRevision);
    }

    // ── 编码 ──────────────────────────────────────────

    public static void encode(S2CSyncFlagsVarsPacket pkt, FriendlyByteBuf buf) {
        buf.writeLong(pkt.playerSessionEpoch);
        buf.writeLong(pkt.baseRevision);
        buf.writeLong(pkt.newRevision);
        // Flags
        buf.writeVarInt(pkt.flags.size());
        for (String f : pkt.flags) {
            buf.writeUtf(f, 256);
        }

        // Variables
        buf.writeVarInt(pkt.variables.size());
        for (Map.Entry<String, Integer> e : pkt.variables.entrySet()) {
            buf.writeUtf(e.getKey(), 256);
            buf.writeVarInt(e.getValue());
        }
    }

    // ── 解码 ──────────────────────────────────────────

    public static S2CSyncFlagsVarsPacket decode(FriendlyByteBuf buf) {
        long playerSessionEpoch = buf.readLong();
        long baseRevision = buf.readLong();
        long newRevision = buf.readLong();
        int flagCount = buf.readVarInt();
        Set<String> flags = new HashSet<>(flagCount);
        for (int i = 0; i < flagCount; i++) {
            flags.add(buf.readUtf(256));
        }

        int varCount = buf.readVarInt();
        Map<String, Integer> vars = new HashMap<>(varCount);
        for (int i = 0; i < varCount; i++) {
            String key = buf.readUtf(256);
            int val = buf.readVarInt();
            vars.put(key, val);
        }

        return new S2CSyncFlagsVarsPacket(flags, vars, playerSessionEpoch, baseRevision, newRevision);
    }

    // ── 处理（客户端）─────────────────────────────────

    public static void handle(S2CSyncFlagsVarsPacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ClientQuestCache.INSTANCE.acceptDelta(
                    pkt.playerSessionEpoch, pkt.baseRevision, pkt.newRevision)) {
                ClientQuestCache.INSTANCE.updateFlagsAndVars(pkt.flags, pkt.variables);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public long getBaseRevision() {
        return baseRevision;
    }

    public long getNewRevision() {
        return newRevision;
    }
}
