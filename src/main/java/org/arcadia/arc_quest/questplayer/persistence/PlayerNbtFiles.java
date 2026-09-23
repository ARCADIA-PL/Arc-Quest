package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** 共享的有界压缩 NBT 文件读写；调用方负责线程调度及文件所有权。 */
public final class PlayerNbtFiles {
    private final long maxCompressedBytes;
    private final long maxNbtBytes;

    public PlayerNbtFiles(long maxCompressedBytes, long maxNbtBytes) {
        if (maxCompressedBytes <= 0 || maxNbtBytes <= 0) {
            throw new IllegalArgumentException("NBT size limits must be positive");
        }
        this.maxCompressedBytes = maxCompressedBytes;
        this.maxNbtBytes = maxNbtBytes;
    }

    public CompoundTag read(Path path) throws IOException {
        if (Files.size(path) > maxCompressedBytes) {
            throw new IOException("Compressed player NBT exceeds " + maxCompressedBytes + " bytes");
        }
        // GZIP 构造器也可能因损坏头抛错，文件句柄必须先纳入资源作用域。
        try (InputStream file = Files.newInputStream(path);
             GZIPInputStream gzip = new GZIPInputStream(file);
             DataInputStream input = new DataInputStream(new BufferedInputStream(gzip))) {
            return NbtIo.read(input, new NbtAccounter(maxNbtBytes));
        }
    }

    public void write(Path path, CompoundTag root) throws IOException {
        path = path.toAbsolutePath().normalize();
        int estimatedSize = root.sizeInBytes();
        if (estimatedSize < 0 || estimatedSize > maxNbtBytes) {
            throw new IOException("Player NBT exceeds " + maxNbtBytes + " bytes");
        }

        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName() + ".", ".tmp");
        try {
            try (OutputStream file = Files.newOutputStream(temporary);
                 OutputStream compressedLimit = new LimitedOutputStream(file, maxCompressedBytes);
                 GZIPOutputStream gzip = new GZIPOutputStream(compressedLimit);
                 DataOutputStream output = new DataOutputStream(new LimitedOutputStream(gzip, maxNbtBytes))) {
                NbtIo.write(root, output);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private static final class LimitedOutputStream extends FilterOutputStream {
        private final long limit;
        private long written;

        private LimitedOutputStream(OutputStream output, long limit) {
            super(output);
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            reserve(length);
            out.write(bytes, offset, length);
        }

        private void reserve(int length) throws IOException {
            if (length < 0 || length > limit - written) {
                throw new IOException("Player NBT stream exceeds " + limit + " bytes");
            }
            written += length;
        }
    }
}
