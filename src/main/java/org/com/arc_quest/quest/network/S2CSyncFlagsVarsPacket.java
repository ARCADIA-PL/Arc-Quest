package org.com.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.quest.capability.IQuestCapability;

import java.util.*;
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

    public S2CSyncFlagsVarsPacket(IQuestCapability cap) {
        this.flags = new HashSet<>(cap.getAllFlags());
        this.variables = new HashMap<>(cap.getAllVariables());
    }

    private S2CSyncFlagsVarsPacket(Set<String> flags, Map<String, Integer> variables) {
        this.flags = flags;
        this.variables = variables;
    }

    // ── 编码 ──────────────────────────────────────────

    public static void encode(S2CSyncFlagsVarsPacket pkt, FriendlyByteBuf buf) {
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

        return new S2CSyncFlagsVarsPacket(flags, vars);
    }

    // ── 处理（客户端）─────────────────────────────────

    public static void handle(S2CSyncFlagsVarsPacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientQuestCache.INSTANCE.updateFlagsAndVars(pkt.flags, pkt.variables);
        });
        ctx.get().setPacketHandled(true);
    }
}