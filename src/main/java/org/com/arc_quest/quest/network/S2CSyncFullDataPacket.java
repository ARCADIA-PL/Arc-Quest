package org.com.arc_quest.quest.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.quest.capability.IQuestCapability;

import java.util.function.Supplier;

/**
 * S2C：登录时全量同步玩家所有任务数据到客户端。
 * <p>
 * 策略：将整个 Capability 序列化为一个 CompoundTag，通过网络传输后在客户端反序列化。
 * 虽然数据量稍大，但仅在登录/重生时发送，可接受。
 */
public class S2CSyncFullDataPacket {

    private final CompoundTag capabilityData;

    // ── 构造（服务端）──────────────────────────────────

    public S2CSyncFullDataPacket(IQuestCapability cap) {
        this.capabilityData = cap.serializeNBT();
    }

    private S2CSyncFullDataPacket(CompoundTag data) {
        this.capabilityData = data;
    }

    // ── 编码 ──────────────────────────────────────────

    public static void encode(S2CSyncFullDataPacket pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.capabilityData);
    }

    // ── 解码 ──────────────────────────────────────────

    public static S2CSyncFullDataPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new S2CSyncFullDataPacket(tag != null ? tag : new CompoundTag());
    }

    // ── 处理（客户端）─────────────────────────────────

    public static void handle(S2CSyncFullDataPacket pkt,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 在客户端主线程上更新缓存
            ClientQuestCache.INSTANCE.applyFullSync(pkt.capabilityData);
        });
        ctx.get().setPacketHandled(true);
    }
}
