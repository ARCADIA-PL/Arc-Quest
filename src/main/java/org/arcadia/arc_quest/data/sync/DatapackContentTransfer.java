package org.arcadia.arc_quest.data.sync;

import java.util.Arrays;

public record DatapackContentTransfer(long epoch, String contentHash, int uncompressedBytes,
                                      byte[] compressedPayload) {

    public DatapackContentTransfer {
        compressedPayload = Arrays.copyOf(compressedPayload, compressedPayload.length);
    }

    @Override
    public byte[] compressedPayload() {
        return Arrays.copyOf(compressedPayload, compressedPayload.length);
    }

    byte[] payloadView() {
        return compressedPayload;
    }
}
