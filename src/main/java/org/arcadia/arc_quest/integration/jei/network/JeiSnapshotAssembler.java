package org.arcadia.arc_quest.integration.jei.network;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;

/** One bounded in-flight snapshot per connection; no Minecraft or JEI classes needed. */
public final class JeiSnapshotAssembler {
    private final long nonce;
    private long newestRevision = -1, epoch;
    private byte[][] chunks;
    private byte[] digest;
    private int expectedBytes, received, receivedBytes;
    public JeiSnapshotAssembler(long nonce) { this.nonce = nonce; }

    public Optional<Snapshot> accept(long nonce, long revision, long epoch, int index, int count,
                                     int totalBytes, byte[] digest, byte[] payload) {
        if (nonce != this.nonce || revision < newestRevision) return Optional.empty();
        if (revision < 0 || epoch < 0 || totalBytes < 1 || totalBytes > JeiCatalogCodec.MAX_BYTES
                || count != (totalBytes + JeiCatalogCodec.CHUNK_BYTES - 1) / JeiCatalogCodec.CHUNK_BYTES
                || index < 0 || index >= count || digest.length != 32
                || payload.length != Math.min(JeiCatalogCodec.CHUNK_BYTES, totalBytes - index * JeiCatalogCodec.CHUNK_BYTES))
            throw new IllegalArgumentException("Invalid catalog chunk geometry");
        if (revision > newestRevision) {
            newestRevision = revision;
            this.epoch = epoch;
            expectedBytes = totalBytes;
            this.digest = digest.clone();
            chunks = new byte[count][];
            received = receivedBytes = 0;
        }
        if (chunks == null) return Optional.empty(); // Already completed.
        if (this.epoch != epoch || chunks.length != count || expectedBytes != totalBytes || !Arrays.equals(this.digest, digest))
            throw new IllegalArgumentException("Inconsistent catalog chunk header");
        if (chunks[index] != null) {
            if (!Arrays.equals(chunks[index], payload)) throw new IllegalArgumentException("Conflicting duplicate catalog chunk");
            return Optional.empty();
        }
        chunks[index] = payload.clone();
        received++;
        receivedBytes += payload.length;
        if (received != chunks.length) return Optional.empty();
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedBytes);
        for (byte[] chunk : chunks) output.writeBytes(chunk);
        chunks = null;
        byte[] bytes = output.toByteArray();
        if (receivedBytes != expectedBytes || !Arrays.equals(hash(bytes), digest)) throw new IllegalArgumentException("Catalog checksum mismatch");
        return Optional.of(new Snapshot(revision, epoch, bytes));
    }
    public static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public record Snapshot(long revision, long epoch, byte[] bytes) {}
}
