package org.arcadia.arc_quest.data.sync.network;

import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;

public final class DatapackContentClientNetworkBridge {

    private DatapackContentClientNetworkBridge() {
    }

    public static void handleStart(S2CDatapackContentStartPacket packet) {
        ClientOnly.handleStart(packet);
    }

    public static void handleChunk(S2CDatapackContentChunkPacket packet) {
        ClientOnly.handleChunk(packet);
    }

    public static void handleEpoch(long epoch) {
        ClientOnly.handleEpoch(epoch);
    }

    private static final class ClientOnly {
        private static void handleStart(S2CDatapackContentStartPacket packet) {
            ClientDatapackContentReceiver.INSTANCE.begin(packet);
        }

        private static void handleChunk(S2CDatapackContentChunkPacket packet) {
            ClientDatapackContentReceiver.INSTANCE.accept(packet);
        }

        private static void handleEpoch(long epoch) {
            ClientDatapackContentReceiver.INSTANCE.acceptEpochNotice(epoch);
        }
    }
}
