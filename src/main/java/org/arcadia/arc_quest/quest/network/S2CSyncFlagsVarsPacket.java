package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
public final class S2CSyncFlagsVarsPacket implements CustomPacketPayload {

    public static final Type<S2CSyncFlagsVarsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_flags_vars"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncFlagsVarsPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncFlagsVarsPacket::encode, S2CSyncFlagsVarsPacket::decode);

    private final Set<String> flags;
    private final Map<String, Integer> variables;

    public S2CSyncFlagsVarsPacket(ArcQuestPlayer data) {
        flags = new HashSet<>(data.getAllFlags());
        variables = new HashMap<>(data.getAllVariables());
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncFlagsVarsPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientQuestCache.INSTANCE.updateFlagsAndVars(pkt.flags, pkt.variables);
        });
    }
}