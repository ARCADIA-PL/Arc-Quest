package org.arcadia.arc_quest.data.sync;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentChunkPacket;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentStartPacket;

import java.io.IOException;
import java.util.Arrays;

public final class DatapackContentSyncService {
    public static final int CHUNK_BYTES = 128 * 1024;
    private static volatile DatapackContentTransfer current = createEmptyTransfer();

    private DatapackContentSyncService() {
    }

    public static DatapackContentTransfer prepare(DatapackContentSnapshot snapshot) throws IOException {
        return DatapackContentCodec.encode(snapshot);
    }

    public static void commit(DatapackContentTransfer transfer) {
        current = transfer;
    }

    public static DatapackContentTransfer current() {
        return current;
    }

    public static void reset() {
        current = createEmptyTransfer();
    }

    public static void sendToPlayer(ServerPlayer player) {
        sendToPlayer(player, current);
    }

    public static boolean broadcastCurrent() {
        if (ServerLifecycleHooks.getCurrentServer() == null) return false;
        sendToAll(current);
        return true;
    }

    private static void sendToPlayer(ServerPlayer player, DatapackContentTransfer transfer) {
        send(transfer, packet -> PacketDistributor.sendToPlayer(player, packet));
    }

    private static void sendToAll(DatapackContentTransfer transfer) {
        send(transfer, PacketDistributor::sendToAllPlayers);
    }

    private static void send(DatapackContentTransfer transfer,
                             java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> sender) {
        byte[] payload = transfer.payloadView();
        int chunkCount = Math.max(1, (payload.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        sender.accept(new S2CDatapackContentStartPacket(transfer.epoch(), transfer.contentHash(), chunkCount,
                payload.length, transfer.uncompressedBytes()));
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int start = chunkIndex * CHUNK_BYTES;
            int end = Math.min(payload.length, start + CHUNK_BYTES);
            byte[] chunk = Arrays.copyOfRange(payload, start, end);
            sender.accept(new S2CDatapackContentChunkPacket(
                    transfer.epoch(), transfer.contentHash(), chunkIndex, chunk));
        }
    }

    private static DatapackContentTransfer createEmptyTransfer() {
        try {
            return DatapackContentCodec.encode(DatapackContentSnapshot.empty(0L));
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
