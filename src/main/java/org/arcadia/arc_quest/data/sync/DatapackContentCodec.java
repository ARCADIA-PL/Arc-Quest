package org.arcadia.arc_quest.data.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class DatapackContentCodec {
    private static final int MAGIC = 0x41514344;
    private static final int FORMAT_VERSION = 1;
    public static final int MAX_DOCUMENTS = 4096;
    public static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    public static final int MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;
    public static final int MAX_COMPRESSED_BYTES = 64 * 1024 * 1024;

    private DatapackContentCodec() {
    }

    public static DatapackContentTransfer encode(DatapackContentSnapshot snapshot) throws IOException {
        byte[] uncompressed = encodeUncompressed(snapshot);
        if (uncompressed.length > MAX_UNCOMPRESSED_BYTES) {
            throw new IOException("Datapack content snapshot exceeds uncompressed limit: " + uncompressed.length);
        }
        byte[] compressed = compress(uncompressed);
        if (compressed.length > MAX_COMPRESSED_BYTES) {
            throw new IOException("Datapack content snapshot exceeds compressed limit: " + compressed.length);
        }
        return new DatapackContentTransfer(snapshot.epoch(), sha256(uncompressed), uncompressed.length, compressed);
    }

    public static DatapackContentSnapshot decode(DatapackContentTransfer transfer) throws IOException {
        byte[] uncompressed = decompress(transfer.payloadView(), transfer.uncompressedBytes());
        String actualHash = sha256(uncompressed);
        if (!MessageDigest.isEqual(actualHash.getBytes(StandardCharsets.US_ASCII),
                transfer.contentHash().getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("Datapack content hash mismatch");
        }
        DatapackContentSnapshot snapshot = decodeUncompressed(uncompressed);
        if (snapshot.epoch() != transfer.epoch()) throw new IOException("Datapack content epoch mismatch");
        return snapshot;
    }

    private static byte[] encodeUncompressed(DatapackContentSnapshot snapshot) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeLong(snapshot.epoch());
            output.writeInt(DatapackContentModule.values().length);
            int totalDocuments = 0;
            for (DatapackContentModule module : DatapackContentModule.values()) {
                List<String> documents = snapshot.documents(module);
                totalDocuments += documents.size();
                if (totalDocuments > MAX_DOCUMENTS) {
                    throw new IOException("Datapack content document count exceeds limit " + MAX_DOCUMENTS);
                }
                output.writeInt(module.ordinal());
                output.writeInt(documents.size());
                for (String document : documents) {
                    byte[] documentBytes = document.getBytes(StandardCharsets.UTF_8);
                    if (documentBytes.length > MAX_DOCUMENT_BYTES) {
                        throw new IOException("Datapack document exceeds limit in module " + module);
                    }
                    output.writeInt(documentBytes.length);
                    output.write(documentBytes);
                }
            }
        }
        return bytes.toByteArray();
    }

    private static DatapackContentSnapshot decodeUncompressed(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid datapack content magic");
            if (input.readInt() != FORMAT_VERSION) throw new IOException("Unsupported datapack content format");
            long epoch = input.readLong();
            int moduleCount = input.readInt();
            if (moduleCount != DatapackContentModule.values().length) {
                throw new IOException("Unexpected datapack content module count: " + moduleCount);
            }
            EnumMap<DatapackContentModule, List<String>> documents = new EnumMap<>(DatapackContentModule.class);
            int totalDocuments = 0;
            for (int moduleIndex = 0; moduleIndex < moduleCount; moduleIndex++) {
                int ordinal = input.readInt();
                if (ordinal < 0 || ordinal >= DatapackContentModule.values().length) {
                    throw new IOException("Invalid datapack content module ordinal: " + ordinal);
                }
                DatapackContentModule module = DatapackContentModule.values()[ordinal];
                if (documents.containsKey(module)) throw new IOException("Duplicate datapack content module: " + module);
                int documentCount = input.readInt();
                if (documentCount < 0 || totalDocuments + documentCount > MAX_DOCUMENTS) {
                    throw new IOException("Invalid datapack content document count: " + documentCount);
                }
                totalDocuments += documentCount;
                List<String> moduleDocuments = new ArrayList<>(documentCount);
                for (int documentIndex = 0; documentIndex < documentCount; documentIndex++) {
                    int length = input.readInt();
                    if (length < 0 || length > MAX_DOCUMENT_BYTES || length > input.available()) {
                        throw new IOException("Invalid datapack document length: " + length);
                    }
                    moduleDocuments.add(new String(input.readNBytes(length), StandardCharsets.UTF_8));
                }
                documents.put(module, List.copyOf(moduleDocuments));
            }
            if (input.available() != 0) throw new IOException("Trailing datapack content bytes: " + input.available());
            return new DatapackContentSnapshot(epoch, documents);
        }
    }

    private static byte[] compress(byte[] input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(input);
        }
        return bytes.toByteArray();
    }

    private static byte[] decompress(byte[] compressed, int expectedSize) throws IOException {
        if (compressed.length > MAX_COMPRESSED_BYTES) throw new IOException("Compressed datapack content exceeds limit");
        if (expectedSize < 0 || expectedSize > MAX_UNCOMPRESSED_BYTES) throw new IOException("Invalid uncompressed size");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(expectedSize, 1024 * 1024));
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                if (read == 0) continue;
                if (output.size() + read > MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Decompressed datapack content exceeds limit");
                }
                output.write(buffer, 0, read);
            }
        }
        byte[] result = output.toByteArray();
        if (result.length != expectedSize) {
            throw new IOException("Datapack content size mismatch: expected " + expectedSize + ", got " + result.length);
        }
        return result;
    }

    private static String sha256(byte[] value) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }
}
