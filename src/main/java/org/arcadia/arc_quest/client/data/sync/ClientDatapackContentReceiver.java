package org.arcadia.arc_quest.client.data.sync;
import org.arcadia.arc_quest.client.compat.jei.JeiCatalogClient;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.client.hud.shop.ClientRefreshingTestShop;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.data.sync.DatapackContentCodec;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;
import org.arcadia.arc_quest.data.sync.DatapackContentTransfer;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentChunkPacket;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentStartPacket;
import org.arcadia.arc_quest.data.sync.network.C2SRequestDatapackContentPacket;
import org.arcadia.arc_quest.data.sync.network.C2SDatapackContentReadyPacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class ClientDatapackContentReceiver {
    public static final ClientDatapackContentReceiver INSTANCE = new ClientDatapackContentReceiver();
    private static final int MAX_CHUNKS = DatapackContentCodec.MAX_COMPRESSED_BYTES
            / DatapackContentSyncService.CHUNK_BYTES + 1;
    private TransferState transfer;
    private final ClientContentSyncState state = new ClientContentSyncState();

    private ClientDatapackContentReceiver() {
    }

    public synchronized void begin(S2CDatapackContentStartPacket packet) {
        if (state.isStale(packet.epoch())) return;
        if (!isValidHash(packet.contentHash())
                || packet.chunkCount() <= 0 || packet.chunkCount() > MAX_CHUNKS
                || packet.compressedBytes() < 0 || packet.compressedBytes() > DatapackContentCodec.MAX_COMPRESSED_BYTES
                || packet.uncompressedBytes() < 0 || packet.uncompressedBytes() > DatapackContentCodec.MAX_UNCOMPRESSED_BYTES) {
            ArcQuestLog.warn(ArcQuestLog.Category.DATA, "Rejected invalid snapshot header: epoch={}, chunks={}, compressed={}, uncompressed={}",
                    packet.epoch(), packet.chunkCount(), packet.compressedBytes(), packet.uncompressedBytes());
            transfer = null;
            return;
        }
        ClientContentSyncState.HeaderResult header = state.begin(packet.epoch(), packet.contentHash());
        if (header == ClientContentSyncState.HeaderResult.STALE) return;
        if (header == ClientContentSyncState.HeaderResult.ALREADY_APPLIED) {
            transfer = null;
            signalReady(packet.epoch());
            return;
        }
        transfer = new TransferState(packet.epoch(), packet.contentHash(), packet.chunkCount(),
                packet.compressedBytes(), packet.uncompressedBytes());
        ObjectiveIconsClient.invalidate();
        JeiCatalogClient.invalidateContent(packet.epoch());
    }

    public synchronized void accept(S2CDatapackContentChunkPacket packet) {
        TransferState current = transfer;
        if (current == null || state.isStale(packet.epoch()) || packet.epoch() != current.epoch
                || !packet.contentHash().equals(current.contentHash)
                || packet.chunkIndex() < 0 || packet.chunkIndex() >= current.chunks.length) return;
        byte[] payload = packet.payload();
        if (payload.length > DatapackContentSyncService.CHUNK_BYTES || current.chunks[packet.chunkIndex()] != null) return;
        if (current.receivedBytes + payload.length > current.compressedBytes) {
            ArcQuestLog.warn(ArcQuestLog.Category.DATA, "Rejected oversized snapshot transfer for epoch {}", packet.epoch());
            transfer = null;
            requestResync();
            return;
        }
        current.chunks[packet.chunkIndex()] = payload;
        current.receivedChunks++;
        current.receivedBytes += payload.length;
        if (current.receivedChunks == current.chunks.length) finish(current);
    }

    public synchronized void clear() {
        ObjectiveIconsClient.clearSession();
        transfer = null;
        state.clear();
    }

    public synchronized long appliedEpoch() {
        return state.appliedEpoch();
    }

    public synchronized void acceptEpochNotice(long epoch) {
        if (state.appliedEpoch() >= epoch) ClientQuestCache.INSTANCE.setDatapackReloadEpoch(epoch);
        else {
            ObjectiveIconsClient.invalidate();
            JeiCatalogClient.invalidateContent(epoch);
            state.expect(epoch, System.nanoTime());
        }
    }

    private void finish(TransferState current) {
        transfer = null;
        if (current.receivedBytes != current.compressedBytes) {
            ArcQuestLog.warn(ArcQuestLog.Category.DATA, "Snapshot size mismatch for epoch {}", current.epoch);
            requestResync();
            return;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(current.compressedBytes);
            for (byte[] chunk : current.chunks) {
                if (chunk == null) throw new IOException("Missing datapack content chunk");
                output.write(chunk);
            }
            DatapackContentTransfer encoded = new DatapackContentTransfer(current.epoch, current.contentHash,
                    current.uncompressedBytes, output.toByteArray());
            ClientDatapackContentApplier.ApplyResult result = ClientDatapackContentApplier.apply(
                    DatapackContentCodec.decode(encoded));
            boolean committed = state.complete(current.epoch, current.contentHash, result.fullyApplied(), System.nanoTime());
            ObjectiveIconsClient.invalidate();
            refreshOpenJournal();
            ClientRefreshingTestShop.restore();
            if (!committed) {
                ArcQuestLog.warn(ArcQuestLog.Category.DATA,
                        "Client content snapshot epoch={} failed modules {}; retaining committed epoch={} and retrying",
                        current.epoch, result.failedModules(), state.appliedEpoch());
                return;
            }
            ClientQuestCache.INSTANCE.setDatapackReloadEpoch(current.epoch);
            signalReady(current.epoch);
            ArcQuestLog.info(ArcQuestLog.Category.DATA, "Applied client content snapshot epoch={} hash={} compressedBytes={}",
                    current.epoch, current.contentHash, current.compressedBytes);
        } catch (Exception exception) {
            ArcQuestLog.error(ArcQuestLog.Category.DATA, "Failed to apply client content snapshot epoch={}", current.epoch, exception);
            requestResync();
        }
    }

    private void requestResync() {
        state.scheduleRetry(System.nanoTime());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) INSTANCE.tickRetries();
    }

    private synchronized void tickRetries() {
        if (Minecraft.getInstance().getConnection() == null) return;
        switch (state.pollRetry(System.nanoTime())) {
            case REQUEST -> ArcQuestNetwork.CHANNEL.sendToServer(new C2SRequestDatapackContentPacket(state.appliedEpoch()));
            case EXHAUSTED -> ArcQuestLog.warn(ArcQuestLog.Category.DATA,
                    "Stopped client content resynchronization after 3 retries: requestedEpoch={}, committedEpoch={}",
                    state.targetEpoch(), state.appliedEpoch());
            case NONE -> { }
        }
    }

    private static void signalReady(long epoch) {
        ArcQuestNetwork.CHANNEL.sendToServer(new C2SDatapackContentReadyPacket(epoch));
    }

    private static boolean isValidHash(String hash) {
        if (hash == null || hash.length() != 64) return false;
        for (int index = 0; index < hash.length(); index++) {
            char value = Character.toLowerCase(hash.charAt(index));
            if ((value < '0' || value > '9') && (value < 'a' || value > 'f')) return false;
        }
        return hash.equals(hash.toLowerCase(Locale.ROOT));
    }

    private static void refreshOpenJournal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestJournalScreen journal) journal.rebuildEntries();
    }

    private static final class TransferState {
        private final long epoch;
        private final String contentHash;
        private final byte[][] chunks;
        private final int compressedBytes;
        private final int uncompressedBytes;
        private int receivedChunks;
        private int receivedBytes;

        private TransferState(long epoch, String contentHash, int chunkCount,
                              int compressedBytes, int uncompressedBytes) {
            this.epoch = epoch;
            this.contentHash = contentHash;
            this.chunks = new byte[chunkCount][];
            this.compressedBytes = compressedBytes;
            this.uncompressedBytes = uncompressedBytes;
        }
    }
}
