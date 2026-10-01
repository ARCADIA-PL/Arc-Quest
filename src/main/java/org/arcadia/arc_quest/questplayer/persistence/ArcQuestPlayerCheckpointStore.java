package org.arcadia.arc_quest.questplayer.persistence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** 游戏线程上的稳定入口；后台只接触路径、UUID 和复制后的 NBT。 */
public final class ArcQuestPlayerCheckpointStore {
    public static final ArcQuestPlayerCheckpointStore INSTANCE = new ArcQuestPlayerCheckpointStore();
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(5);
    private final CheckpointWriteQueue queue = new CheckpointWriteQueue(
            new CheckpointFileIO(), Duration.ofSeconds(2), 1024, 64L * 1024L * 1024L);
    private long lastRejectionLogNanos;

    private ArcQuestPlayerCheckpointStore() {
    }

    /** 加载是否成功影响恢复来源选择；失败时不得用空快照冒充检查点不存在。 */
    public CompoundTag loadLatestOrThrow(ServerPlayer player, UUID playerUuid) {
        Path path = checkpointPath(player, playerUuid);
        return required("load", path, () -> queue.read(path, playerUuid));
    }

    public void writeNowOrThrow(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        Path path = checkpointPath(player, playerUuid);
        required("write", path, () -> queue.writeNow(path, playerUuid, snapshot));
    }

    public void deleteOrThrow(ServerPlayer player, UUID playerUuid) {
        Path path = checkpointPath(player, playerUuid);
        required("delete", path, () -> queue.delete(path));
    }

    private static <T> T required(String operation, Path path, Supplier<Future<T>> submit) {
        try {
            return await(submit.get(), IO_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Checkpoint " + operation + " interrupted at " + path, exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            throw new IllegalStateException("Checkpoint " + operation + " failed at " + path, exception);
        }
    }

    public CompoundTag loadLatest(ServerPlayer player, UUID playerUuid) {
        Path path = checkpointPath(player, playerUuid);
        try {
            return await(queue.read(path, playerUuid), IO_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure("load", path, exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            logFailure("load", path, exception);
        }
        return new CompoundTag();
    }

    public void schedule(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        Path path = checkpointPath(player, playerUuid);
        try {
            queue.schedule(path, playerUuid, snapshot);
        } catch (RejectedExecutionException exception) {
            logRejection(path, exception);
        }
    }

    public void writeNow(ServerPlayer player, UUID playerUuid, CompoundTag snapshot) {
        Path path = checkpointPath(player, playerUuid);
        try {
            await(queue.writeNow(path, playerUuid, snapshot), IO_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure("write", path, exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            logFailure("write", path, exception);
        }
    }

    public void delete(ServerPlayer player, UUID playerUuid) {
        Path path = checkpointPath(player, playerUuid);
        try {
            await(queue.delete(path), IO_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure("delete", path, exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            logFailure("delete", path, exception);
        }
    }

    public void flush(Duration timeout) {
        try {
            await(queue.flush(), timeout);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure("flush", null, exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            logFailure("flush", null, exception);
        }
    }

    /** 关服时刷新已提交的检查点并释放写入线程；下次打开存档时按需重建。 */
    public void shutdown(Duration timeout) {
        try {
            queue.shutdown(timeout);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure("shutdown", null, exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            logFailure("shutdown", null, exception);
        }
    }

    private synchronized void logRejection(Path path, RejectedExecutionException exception) {
        long now = System.nanoTime();
        if (lastRejectionLogNanos == 0L || now - lastRejectionLogNanos >= TimeUnit.SECONDS.toNanos(30)) {
            lastRejectionLogNanos = now;
            ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE,
                    "Checkpoint queue rejected {}; primary player persistence remains authoritative", path, exception);
        }
    }

    private static <T> T await(Future<T> operation, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        return operation.get(Math.max(1L, timeout.toNanos()), TimeUnit.NANOSECONDS);
    }

    private static void logFailure(String operation, Path path, Exception exception) {
        ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE,
                "Checkpoint {} failed at {}", operation, path == null ? "writer" : path, exception);
    }

    private static Path checkpointPath(ServerPlayer player, UUID playerUuid) {
        return player.server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("arc_quest").resolve("player_checkpoints")
                .resolve(playerUuid + ".dat").toAbsolutePath().normalize();
    }
}
