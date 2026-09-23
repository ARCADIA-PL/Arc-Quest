package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 合并同一路径的待写快照，所有文件操作按队列顺序执行。
 * 待写数据有数量和内存上限；版本缓存仅在写入线程使用，关服后释放。
 */
final class CheckpointWriteQueue {
    interface Storage {
        CompoundTag read(Path path, UUID playerUuid) throws IOException;

        void write(Path path, UUID playerUuid, CompoundTag snapshot) throws IOException;

        void delete(Path path) throws IOException;
    }

    private static final int MAX_WRITE_ATTEMPTS = 3;
    private static final int MAX_CACHED_VERSIONS = 4096;
    private final Storage storage;
    private final long delayMillis;
    private final int maxPendingCount;
    private final long maxPendingBytes;
    private Session session;

    CheckpointWriteQueue(Storage storage, Duration delay, int maxPendingCount, long maxPendingBytes) {
        this.storage = Objects.requireNonNull(storage);
        this.delayMillis = delay.toMillis();
        if (delayMillis < 0 || maxPendingCount < 1 || maxPendingBytes < 1) {
            throw new IllegalArgumentException("Invalid checkpoint queue limits");
        }
        this.maxPendingCount = maxPendingCount;
        this.maxPendingBytes = maxPendingBytes;
    }

    void schedule(Path path, UUID playerUuid, CompoundTag snapshot) {
        current().schedule(detach(path, playerUuid, snapshot), 0);
    }

    Future<CompoundTag> read(Path path, UUID playerUuid) {
        Session current = current();
        synchronized (current) {
            PendingWrite pending = current.pending.get(path);
            CompoundTag queued = pending == null ? null : pending.checkpoint.snapshot();
            return current.executor.submit(() -> {
                CompoundTag disk = storage.read(path, playerUuid);
                current.versions.put(path, Version.of(disk));
                return ArcQuestPlayerPersistenceMetadata.newer(disk, queued);
            });
        }
    }

    Future<?> writeNow(Path path, UUID playerUuid, CompoundTag snapshot) {
        Checkpoint checkpoint = detach(path, playerUuid, snapshot);
        Session current = current();
        synchronized (current) {
            PendingWrite pending = current.removePending(path);
            Checkpoint selected = pending != null && isNewer(pending.checkpoint, checkpoint)
                    ? pending.checkpoint : checkpoint;
            return current.executor.submit(() -> {
                current.write(selected);
                return null;
            });
        }
    }

    Future<?> delete(Path path) {
        Session current = current();
        synchronized (current) {
            // 取消尚未取出的写入；已取出的写入与删除在同一线程上依次执行。
            current.removePending(path);
            return current.executor.submit(() -> {
                storage.delete(path);
                current.versions.remove(path);
                return null;
            });
        }
    }

    synchronized Future<?> flush() {
        if (session == null) return CompletableFuture.completedFuture(null);
        return session.flush();
    }

    synchronized void shutdown(Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (session == null) return;
        Future<?> barrier = session.stop();
        long budgetNanos = Math.max(1L, timeout.toNanos());
        long started = System.nanoTime();
        barrier.get(budgetNanos, TimeUnit.NANOSECONDS);
        long remaining = Math.max(1L, budgetNanos - (System.nanoTime() - started));
        if (!session.executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
            throw new TimeoutException("Checkpoint writer did not terminate");
        }
        session = null;
    }

    synchronized boolean isRunning() {
        return session != null && !session.executor.isTerminated();
    }

    private synchronized Session current() {
        if (session != null && session.executor.isShutdown()) {
            if (!session.executor.isTerminated()) {
                throw new RejectedExecutionException("Previous checkpoint writer is still stopping");
            }
            session = null;
        }
        if (session == null) session = new Session();
        return session;
    }

    private Checkpoint detach(Path path, UUID playerUuid, CompoundTag snapshot) {
        int size = snapshot.sizeInBytes();
        if (size < 0 || size > maxPendingBytes) {
            throw new RejectedExecutionException("Checkpoint exceeds queue memory limit: " + path);
        }
        return new Checkpoint(path, playerUuid, snapshot.copy(), size);
    }

    private static boolean isNewer(Checkpoint first, Checkpoint second) {
        return ArcQuestPlayerPersistenceMetadata.compare(first.snapshot(), second.snapshot()) > 0;
    }

    private final class Session {
        private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "ArcQuest-PlayerCheckpoint");
            thread.setDaemon(true);
            return thread;
        });
        private final Map<Path, PendingWrite> pending = new LinkedHashMap<>();
        private long pendingBytes;
        private long sequence;
        private PendingWrite activeWrite;
        private Future<?> stopBarrier;
        // 此缓存只包含版本，不持有 NBT；逐出后从磁盘重新取得水位，避免旧快照覆盖新快照。
        private final Map<Path, Version> versions = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, Version> eldest) {
                return size() > MAX_CACHED_VERSIONS;
            }
        };

        private Session() {
            executor.setRemoveOnCancelPolicy(true);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        private synchronized void schedule(Checkpoint checkpoint, int attempt) {
            schedule(checkpoint, attempt, ++sequence);
        }

        private synchronized void schedule(Checkpoint checkpoint, int attempt, long submittedSequence) {
            if (executor.isShutdown()) throw new RejectedExecutionException("Checkpoint writer is stopping");
            PendingWrite previous = pending.get(checkpoint.path());
            if (previous != null) {
                int order = ArcQuestPlayerPersistenceMetadata.compare(previous.checkpoint.snapshot(), checkpoint.snapshot());
                if (order > 0 || (order == 0 && previous.sequence > submittedSequence)) return;
            }
            long replacedBytes = previous == null ? 0L : previous.checkpoint.estimatedBytes();
            if ((previous == null && pending.size() >= maxPendingCount)
                    || checkpoint.estimatedBytes() > maxPendingBytes - (pendingBytes - replacedBytes)) {
                throw new RejectedExecutionException("Checkpoint queue capacity exceeded for " + checkpoint.path());
            }
            if (previous != null) {
                pendingBytes += checkpoint.estimatedBytes() - replacedBytes;
                previous.checkpoint = checkpoint;
                previous.attempt = attempt;
                previous.sequence = submittedSequence;
                return;
            }
            PendingWrite write = new PendingWrite(checkpoint, attempt, submittedSequence);
            pending.put(checkpoint.path(), write);
            pendingBytes += checkpoint.estimatedBytes();
            write.future = executor.schedule(() -> drain(write), delayMillis * (attempt + 1L), TimeUnit.MILLISECONDS);
        }

        private void drain(PendingWrite pendingWrite) {
            Checkpoint checkpoint;
            int attempt;
            synchronized (this) {
                if (pending.get(pendingWrite.checkpoint.path()) != pendingWrite) return;
                checkpoint = pendingWrite.checkpoint;
                attempt = pendingWrite.attempt;
                pending.remove(checkpoint.path());
                pendingBytes -= checkpoint.estimatedBytes();
                activeWrite = pendingWrite;
            }
            try {
                write(checkpoint);
            } catch (IOException | RuntimeException exception) {
                synchronized (this) {
                    pendingWrite.failed = true;
                    if (!pendingWrite.cancelled && attempt + 1 < MAX_WRITE_ATTEMPTS && !executor.isShutdown()) {
                        try {
                            schedule(checkpoint, attempt + 1, pendingWrite.sequence);
                            ArcQuestLog.warn(ArcQuestLog.Category.PERSISTENCE,
                                    "Retrying checkpoint for player {} at {} after attempt {}",
                                    checkpoint.playerUuid(), checkpoint.path(), attempt + 1, exception);
                            return;
                        } catch (RejectedExecutionException retryFailure) {
                            exception.addSuppressed(retryFailure);
                        }
                    }
                }
                ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE,
                        "Failed to write checkpoint for player {} at {} after {} attempts",
                        checkpoint.playerUuid(), checkpoint.path(), attempt + 1, exception);
            } finally {
                synchronized (this) {
                    activeWrite = null;
                }
            }
        }

        private PendingWrite removePending(Path path) {
            if (activeWrite != null && activeWrite.checkpoint.path().equals(path)) activeWrite.cancelled = true;
            PendingWrite removed = pending.remove(path);
            if (removed != null) {
                pendingBytes -= removed.checkpoint.estimatedBytes();
                removed.future.cancel(false);
            }
            return removed;
        }

        private synchronized Future<?> flush() {
            return stopBarrier == null ? flush(false) : stopBarrier;
        }

        private synchronized Future<?> stop() {
            if (stopBarrier == null) {
                stopBarrier = flush(true);
                executor.shutdown();
            }
            return stopBarrier;
        }

        private synchronized Future<?> flush(boolean stopping) {
            long barrierSequence = sequence;
            PendingWrite inFlight = activeWrite;
            Map<Path, PendingWrite> captured = new LinkedHashMap<>(pending);
            for (PendingWrite write : pending.values()) {
                write.future.cancel(false);
            }
            pending.clear();
            pendingBytes = 0L;
            return executor.submit(() -> {
                // 屏障排队期间，前一个写入可能失败并产生重试；这些重试仍属于本次刷新。
                synchronized (this) {
                    if (inFlight != null && inFlight.failed && !inFlight.cancelled) {
                        captured.merge(inFlight.checkpoint.path(), inFlight, CheckpointWriteQueue::newerWrite);
                    }
                    var iterator = pending.values().iterator();
                    while (iterator.hasNext()) {
                        PendingWrite write = iterator.next();
                        if (write.sequence > barrierSequence) continue;
                        captured.merge(write.checkpoint.path(), write, CheckpointWriteQueue::newerWrite);
                        write.future.cancel(false);
                        pendingBytes -= write.checkpoint.estimatedBytes();
                        iterator.remove();
                    }
                }
                IOException failure = null;
                for (PendingWrite pendingWrite : captured.values()) {
                    Checkpoint checkpoint = pendingWrite.checkpoint;
                    try {
                        write(checkpoint);
                    } catch (IOException | RuntimeException exception) {
                        IOException contextual = new IOException("Checkpoint flush failed for player "
                                + checkpoint.playerUuid() + " at " + checkpoint.path(), exception);
                        if (failure == null) failure = contextual;
                        else failure.addSuppressed(contextual);
                    }
                }
                if (stopping) versions.clear();
                if (failure != null) throw failure;
                return null;
            });
        }

        private void write(Checkpoint checkpoint) throws IOException {
            Version previous = versions.get(checkpoint.path());
            if (previous == null) previous = Version.of(storage.read(checkpoint.path(), checkpoint.playerUuid()));
            Version next = Version.of(checkpoint.snapshot());
            if (next.compareTo(previous) < 0) return;
            storage.write(checkpoint.path(), checkpoint.playerUuid(), checkpoint.snapshot());
            versions.put(checkpoint.path(), next);
        }
    }

    private record Checkpoint(Path path, UUID playerUuid, CompoundTag snapshot, int estimatedBytes) {
    }

    private static PendingWrite newerWrite(PendingWrite first, PendingWrite second) {
        int order = ArcQuestPlayerPersistenceMetadata.compare(first.checkpoint.snapshot(), second.checkpoint.snapshot());
        if (order != 0) return order > 0 ? first : second;
        return first.sequence >= second.sequence ? first : second;
    }

    private static final class PendingWrite {
        private Checkpoint checkpoint;
        private int attempt;
        private long sequence;
        private boolean cancelled;
        private boolean failed;
        private ScheduledFuture<?> future;

        private PendingWrite(Checkpoint checkpoint, int attempt, long sequence) {
            this.checkpoint = checkpoint;
            this.attempt = attempt;
            this.sequence = sequence;
        }
    }

    private record Version(boolean hasPayload, long revision, long writtenAt) implements Comparable<Version> {
        private static Version of(CompoundTag snapshot) {
            return new Version(!ArcQuestPlayerPersistenceMetadata.isPayloadEmpty(snapshot),
                    ArcQuestPlayerPersistenceMetadata.revision(snapshot),
                    ArcQuestPlayerPersistenceMetadata.writtenAt(snapshot));
        }

        @Override
        public int compareTo(Version other) {
            int payload = Boolean.compare(hasPayload, other.hasPayload);
            if (payload != 0) return payload;
            if (!hasPayload) return 0;
            int revisionOrder = Long.compare(revision, other.revision);
            return revisionOrder != 0 ? revisionOrder : Long.compare(writtenAt, other.writtenAt);
        }
    }
}
