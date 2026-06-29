package org.arcadia.arc_quest.quest.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

/**
 * S2C：登录时全量同步玩家所有任务数据到客户端。
 * <p>
 * 策略：将整个 Capability 序列化为一个 CompoundTag，通过网络传输后在客户端反序列化。
 * 虽然数据量稍大，但仅在登录/重生时发送，可接受。
 */
public final class S2CSyncFullDataPacket implements CustomPacketPayload {

    public static final Type<S2CSyncFullDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_full_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncFullDataPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncFullDataPacket::encode, S2CSyncFullDataPacket::decode);

    private final CompoundTag playerData;

    // ── 构造（服务端）──────────────────────────────────

    public S2CSyncFullDataPacket(ArcQuestPlayer data) {
        playerData = data.serializeNBT();
    }

    private S2CSyncFullDataPacket(CompoundTag data) {
        playerData = data;
    }

    // ── 编码 ──────────────────────────────────────────

    public static void encode(S2CSyncFullDataPacket pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.playerData);
    }

    // ── 解码 ──────────────────────────────────────────

    public static S2CSyncFullDataPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new S2CSyncFullDataPacket(tag != null ? tag : new CompoundTag());
    }

    // ── 处理（客户端）─────────────────────────────────

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncFullDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 在客户端主线程上更新缓存
            ClientQuestCache.INSTANCE.applyFullSync(pkt.playerData);
        });
    }
}
