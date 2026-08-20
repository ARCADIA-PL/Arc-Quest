package org.arcadia.arc_quest.quest.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
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
    private static final int MAX_COMPRESSED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_UNCOMPRESSED_NBT_BYTES = 16 * 1024 * 1024;

    private final CompoundTag playerData;
    private final long playerSessionEpoch;
    private final long revision;

    // ── 构造（服务端）──────────────────────────────────

    public S2CSyncFullDataPacket(ArcQuestPlayer data) {
        this(data, 0L, 0L);
    }

    public S2CSyncFullDataPacket(ArcQuestPlayer data, long playerSessionEpoch, long revision) {
        playerData = data.serializeNBT();
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.revision = Math.max(0L, revision);
    }

    private S2CSyncFullDataPacket(CompoundTag data, long playerSessionEpoch, long revision) {
        playerData = data;
        this.playerSessionEpoch = Math.max(0L, playerSessionEpoch);
        this.revision = Math.max(0L, revision);
    }

    // ── 编码 ──────────────────────────────────────────

    public static void encode(S2CSyncFullDataPacket pkt, FriendlyByteBuf buf) {
        buf.writeLong(pkt.playerSessionEpoch);
        buf.writeLong(pkt.revision);
        buf.writeByteArray(compress(pkt.playerData));
    }

    // ── 解码 ──────────────────────────────────────────

    public static S2CSyncFullDataPacket decode(FriendlyByteBuf buf) {
        long playerSessionEpoch = buf.readLong();
        long revision = buf.readLong();
        CompoundTag tag = decompress(buf.readByteArray(MAX_COMPRESSED_BYTES));
        return new S2CSyncFullDataPacket(tag, playerSessionEpoch, revision);
    }

    // ── 处理（客户端）─────────────────────────────────

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncFullDataPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 在客户端主线程上更新缓存
            if (ClientQuestCache.INSTANCE.acceptSnapshot(pkt.playerSessionEpoch, pkt.revision)) {
                ClientQuestCache.INSTANCE.applyFullSync(pkt.playerData);
            }
        });
    }

    public long getPlayerSessionEpoch() {
        return playerSessionEpoch;
    }

    public long getRevision() {
        return revision;
    }

    private static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, output);
            byte[] compressed = output.toByteArray();
            if (compressed.length > MAX_COMPRESSED_BYTES) {
                throw new IllegalArgumentException("Player quest snapshot exceeds compressed limit");
            }
            return compressed;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode player quest snapshot", exception);
        }
    }

    private static CompoundTag decompress(byte[] compressed) {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
            return NbtIo.read(input, new NbtAccounter(MAX_UNCOMPRESSED_NBT_BYTES));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid compressed player quest snapshot", exception);
        }
    }
}
