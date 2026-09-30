package org.arcadia.arc_quest.integration.jei.network;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class JeiSnapshotAssemblerTest {
    private static final long NONCE = 42;
    private static final int CHUNK = JeiCatalogCodec.CHUNK_BYTES;
    private static byte[] data(int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte)(i * 31);
        return bytes;
    }
    private static Optional<JeiSnapshotAssembler.Snapshot> accept(JeiSnapshotAssembler assembler,
            long nonce, long revision, long epoch, byte[] bytes, int index) {
        return assembler.accept(nonce, revision, epoch, index, (bytes.length + CHUNK - 1) / CHUNK,
                bytes.length, JeiSnapshotAssembler.hash(bytes),
                Arrays.copyOfRange(bytes, index * CHUNK, Math.min(bytes.length, (index + 1) * CHUNK)));
    }
    @Test void assemblesOutOfOrderWithIdenticalDuplicateWithoutDuplicatingResults() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] bytes = data(CHUNK * 2 + 7);
        assertTrue(accept(assembler, NONCE, 1, 3, bytes, 2).isEmpty());
        assertTrue(accept(assembler, NONCE, 1, 3, bytes, 0).isEmpty());
        assertTrue(accept(assembler, NONCE, 1, 3, bytes, 0).isEmpty());
        var result = accept(assembler, NONCE, 1, 3, bytes, 1).orElseThrow();
        assertEquals(1, result.revision());
        assertEquals(3, result.epoch());
        assertArrayEquals(bytes, result.bytes());
        assertTrue(accept(assembler, NONCE, 1, 3, bytes, 1).isEmpty());
    }
    @Test void anotherConnectionCannotReplaceTheInFlightSnapshot() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] bytes = data(CHUNK + 1);
        accept(assembler, NONCE, 1, 1, bytes, 0);
        assertTrue(accept(assembler, NONCE + 1, 100, 2, bytes, 0).isEmpty());
        assertArrayEquals(bytes, accept(assembler, NONCE, 1, 1, bytes, 1).orElseThrow().bytes());
    }
    @Test void newerRevisionReplacesPartialStateAndOlderPacketsCannotResurrectIt() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] old = data(CHUNK + 2), next = data(CHUNK + 3);
        accept(assembler, NONCE, 1, 1, old, 0);
        accept(assembler, NONCE, 2, 2, next, 1);
        assertTrue(accept(assembler, NONCE, 1, 1, old, 1).isEmpty());
        assertArrayEquals(next, accept(assembler, NONCE, 2, 2, next, 0).orElseThrow().bytes());
    }
    @Test void rejectsConflictingDuplicateBytes() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] bytes = data(CHUNK + 1);
        accept(assembler, NONCE, 1, 1, bytes, 0);
        byte[] corrupt = Arrays.copyOf(bytes, CHUNK); corrupt[0]++;
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 0, 2,
                bytes.length, JeiSnapshotAssembler.hash(bytes), corrupt));
    }
    @Test void rejectsMixedEpochAndMixedDigestWithinOneRevision() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] bytes = data(CHUNK + 1);
        accept(assembler, NONCE, 1, 1, bytes, 0);
        assertThrows(IllegalArgumentException.class, () -> accept(assembler, NONCE, 1, 2, bytes, 1));
        byte[] different = bytes.clone(); different[CHUNK]++;
        assertThrows(IllegalArgumentException.class, () -> accept(assembler, NONCE, 1, 1, different, 1));
    }
    @Test void rejectsChecksumFailureAndAcceptsLaterValidRevision() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] bytes = data(17), corrupt = bytes.clone(); corrupt[0]++;
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 0, 1,
                bytes.length, JeiSnapshotAssembler.hash(bytes), corrupt));
        assertArrayEquals(bytes, accept(assembler, NONCE, 2, 1, bytes, 0).orElseThrow().bytes());
    }
    @Test void copiesInboundChunksBeforeTheCallerCanModifyThem() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] bytes = data(CHUNK + 1), first = Arrays.copyOf(bytes, CHUNK), digest = JeiSnapshotAssembler.hash(bytes);
        assembler.accept(NONCE, 1, 1, 0, 2, bytes.length, digest, first);
        first[0]++; digest[0]++;
        assertArrayEquals(bytes, accept(assembler, NONCE, 1, 1, bytes, 1).orElseThrow().bytes());
    }
    @Test void validatesGeometryAndBoundsBeforeAllocating() {
        var assembler = new JeiSnapshotAssembler(NONCE);
        byte[] digest = new byte[32], payload = new byte[1];
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 0, Integer.MAX_VALUE, 1, digest, payload));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 0, 1, 0, digest, payload));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 0, 1, JeiCatalogCodec.MAX_BYTES + 1, digest, payload));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, -1, 1, 1, digest, payload));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 1, 1, 1, digest, payload));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 0, 1, 1, new byte[31], payload));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, 1, 0, 1, 2, digest, payload));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(NONCE, 1, -1, 0, 1, 1, digest, payload));
    }
}
