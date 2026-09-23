package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.questplayer.persistence.PlayerNbtFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 单写线程拥有日志；工作任务只持有脱离游戏对象的 NBT，调用方不得在游戏线程等待 Future。 */
final class PendingDrawJournal {
    private static final int VERSION = 1;
    private static final int MAX_RECORD_BYTES = 256 * 1024;
    private static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;

    enum Stage { PREPARED, PAID, DELIVERING, DELIVERED, REVIEW, RESOLVED }

    record Entry(UUID transactionId, UUID playerId, String shopId, Stage stage, CompoundTag payload) {
        Entry {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(stage, "stage");
            if (shopId == null || shopId.isBlank() || shopId.length() > 32767) {
                throw new IllegalArgumentException("Invalid draw shop ID");
            }
            payload = Objects.requireNonNull(payload, "payload").copy();
            if (payload.sizeInBytes() < 0 || payload.sizeInBytes() > MAX_RECORD_BYTES) {
                throw new IllegalArgumentException("Draw journal payload exceeds limit");
            }
        }

        @Override
        public CompoundTag payload() { return payload.copy(); }

        Entry at(Stage next) { return new Entry(transactionId, playerId, shopId, next, payload); }

        CompoundTag serialize() {
            CompoundTag root = new CompoundTag();
            root.putInt("Version", VERSION);
            root.putUUID("Transaction", transactionId);
            root.putUUID("Player", playerId);
            root.putString("Shop", shopId);
            root.putString("Stage", stage.name());
            root.put("Payload", payload.copy());
            return root;
        }

        static Entry read(CompoundTag root) throws IOException {
            if (!root.contains("Version", Tag.TAG_INT) || root.getInt("Version") != VERSION
                    || !root.hasUUID("Transaction") || !root.hasUUID("Player")
                    || !root.contains("Shop", Tag.TAG_STRING) || !root.contains("Stage", Tag.TAG_STRING)
                    || !root.contains("Payload", Tag.TAG_COMPOUND)) {
                throw new IOException("Invalid draw journal envelope");
            }
            try {
                return new Entry(root.getUUID("Transaction"), root.getUUID("Player"), root.getString("Shop"),
                        Stage.valueOf(root.getString("Stage")), root.getCompound("Payload"));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid draw journal entry", exception);
            }
        }
    }

    interface Storage {
        List<Path> entries(Path directory, int limit) throws IOException;
        Entry read(Path path) throws IOException;
        void write(Path path, Entry entry) throws IOException;
        void delete(Path path) throws IOException;
        void archive(Path path) throws IOException;
    }

    private final Path directory;
    private final int capacity;
    private final Storage storage;
    private final ThreadPoolExecutor worker;
    private final Map<UUID, Entry> records = new HashMap<>();
    private long retainedBytes;
    private final CompletableFuture<List<Entry>> loaded;
    private Throwable storageFailure;

    PendingDrawJournal(Path directory, int capacity) {
        this(directory, capacity, new FileStorage());
    }

    PendingDrawJournal(Path directory, int capacity, Storage storage) {
        if (capacity <= 0) throw new IllegalArgumentException("Journal capacity must be positive");
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.capacity = capacity;
        this.storage = Objects.requireNonNull(storage);
        worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.multiplyExact(capacity, 4)), runnable -> {
                    Thread thread = new Thread(runnable, "arc-quest-draw-journal");
                    thread.setDaemon(true);
                    return thread;
                });
        loaded = submit(() -> {
            for (Path path : storage.entries(this.directory, capacity)) {
                Entry entry = storage.read(path);
                if (!path.toAbsolutePath().normalize().equals(path(entry.transactionId()))) {
                    throw new IOException("Draw journal file and transaction ID differ: " + path);
                }
                if (entry.stage() == Stage.RESOLVED) {
                    storage.archive(path);
                    continue;
                }
                if (records.putIfAbsent(entry.transactionId(), entry) != null) {
                    throw new IOException("Duplicate draw transaction " + entry.transactionId());
                }
                retainedBytes += size(entry);
                if (records.size() > capacity || retainedBytes > MAX_TOTAL_BYTES) {
                    throw new IOException("Draw recovery records exceed journal capacity");
                }
            }
            return List.copyOf(records.values());
        });
    }

    CompletableFuture<List<Entry>> loaded() { return loaded.copy(); }

    CompletableFuture<Void> prepare(Entry entry) {
        Objects.requireNonNull(entry);
        if (entry.stage() != Stage.PREPARED) throw new IllegalArgumentException("Expected PREPARED draw");
        return submit(() -> {
            requireLoaded();
            if (records.containsKey(entry.transactionId())) throw new IllegalStateException("Duplicate draw transaction");
            if (records.size() >= capacity || size(entry) > MAX_TOTAL_BYTES - retainedBytes) {
                throw new RejectedExecutionException("Unresolved draw journal is full");
            }
            storage.write(path(entry.transactionId()), entry);
            records.put(entry.transactionId(), entry);
            retainedBytes += size(entry);
            return null;
        });
    }

    CompletableFuture<Void> advance(UUID transactionId, Stage expected, Stage next) {
        if (!allowed(expected, next)) throw new IllegalArgumentException("Invalid draw stage transition");
        return submit(() -> {
            requireLoaded();
            Entry previous = requireEntry(transactionId, expected);
            Entry replacement = previous.at(next);
            storage.write(path(transactionId), replacement);
            records.put(transactionId, replacement);
            retainedBytes += size(replacement) - size(previous);
            return null;
        });
    }

    /** 只有未扣费的撤销或有存档证据的已发奖记录可以移除；REVIEW 不允许自动清理。 */
    CompletableFuture<Void> remove(UUID transactionId, Stage expected) {
        if (expected != Stage.PREPARED && expected != Stage.DELIVERED) {
            throw new IllegalArgumentException("Cannot automatically remove unresolved draw");
        }
        return submit(() -> {
            requireLoaded();
            Entry previous = requireEntry(transactionId, expected);
            storage.delete(path(transactionId));
            records.remove(transactionId);
            retainedBytes -= size(previous);
            return null;
        });
    }

    /** 重新读取原版玩家文件中的同笔发奖回执，不能用独立的进度检查点代替背包存档证明。 */
    CompletableFuture<List<UUID>> acknowledgeSavedPlayer(UUID playerId, Path playerFile) {
        return submit(() -> {
            requireLoaded();
            CompoundTag player;
            try {
                if (Files.notExists(playerFile)) return List.of();
                player = new PlayerNbtFiles(8L * 1024 * 1024, 32L * 1024 * 1024).read(playerFile);
                if (!player.hasUUID("UUID") || !playerId.equals(player.getUUID("UUID"))) {
                    throw new IOException("Player save identity differs from draw receipt owner");
                }
            } catch (IOException | RuntimeException failure) {
                // 原版可能正在替换文件；保留事务，在下一次保存后复核，不停用健康的日志写线程。
                throw new PlayerSaveReadException(playerFile, failure);
            }
            CompoundTag receipts = player.getCompound("ForgeCaps").getCompound("arc_quest:player_data")
                    .getCompound("DeliveredDrawReceipts");
            List<UUID> acknowledged = new java.util.ArrayList<>();
            for (Entry entry : List.copyOf(records.values())) {
                if (entry.playerId().equals(playerId) && entry.stage() == Stage.DELIVERED
                        && receipts.contains(entry.transactionId().toString(), Tag.TAG_BYTE)
                        && receipts.getByte(entry.transactionId().toString()) == 1) {
                    storage.delete(path(entry.transactionId()));
                    records.remove(entry.transactionId());
                    retainedBytes -= size(entry);
                    acknowledged.add(entry.transactionId());
                }
            }
            return List.copyOf(acknowledged);
        });
    }

    /** 管理员已在游戏外核实/结算；先持久化终态和原因，再归档，绝不重放任意附属奖励。 */
    CompletableFuture<Void> resolve(UUID transactionId, Stage expected, String operator, String note, long now) {
        if (operator == null || operator.isBlank() || operator.length() > 256
                || note == null || note.isBlank() || note.length() > 512) {
            throw new IllegalArgumentException("Resolution requires an operator and a note of at most 512 characters");
        }
        return submit(() -> {
            requireLoaded();
            Entry previous = requireEntry(transactionId, expected);
            CompoundTag payload = previous.payload();
            payload.putString("ResolvedFrom", expected.name());
            payload.putString("ResolvedBy", operator);
            payload.putString("Resolution", note);
            payload.putLong("ResolvedAt", now);
            Entry resolved = new Entry(transactionId, previous.playerId(), previous.shopId(), Stage.RESOLVED, payload);
            storage.write(path(transactionId), resolved);
            storage.archive(path(transactionId));
            records.remove(transactionId);
            retainedBytes -= size(previous);
            return null;
        });
    }

    static final class PlayerSaveReadException extends RuntimeException {
        PlayerSaveReadException(Path path, Throwable cause) { super("Cannot verify player save " + path, cause); }
    }

    void shutdown(Duration timeout) throws InterruptedException, TimeoutException {
        worker.shutdown();
        if (!worker.awaitTermination(Math.max(1L, timeout.toNanos()), TimeUnit.NANOSECONDS)) {
            // 超时不取消已接收的写操作，也不能在同一路径启动第二个写线程。
            throw new TimeoutException("Draw journal still draining pending writes");
        }
    }

    boolean isTerminated() { return worker.isTerminated(); }

    private void requireLoaded() {
        if (!loaded.isDone() || loaded.isCompletedExceptionally()) {
            throw new IllegalStateException("Draw recovery journal is unavailable");
        }
    }

    private Entry requireEntry(UUID transactionId, Stage expected) {
        Entry entry = records.get(transactionId);
        if (entry == null || entry.stage() != expected) throw new IllegalStateException("Stale draw journal transition");
        return entry;
    }

    private static boolean allowed(Stage from, Stage to) {
        return to == Stage.REVIEW && from != Stage.DELIVERED && from != Stage.REVIEW && from != Stage.RESOLVED
                || from == Stage.PREPARED && to == Stage.PAID
                || from == Stage.PAID && to == Stage.DELIVERING
                || from == Stage.DELIVERING && to == Stage.DELIVERED;
    }

    private Path path(UUID transactionId) { return directory.resolve(transactionId + ".dat"); }
    private static int size(Entry entry) { return entry.serialize().sizeInBytes(); }

    private <T> CompletableFuture<T> submit(Operation<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                if (storageFailure != null) {
                    result.completeExceptionally(new IllegalStateException("Draw journal disabled after I/O failure", storageFailure));
                    return;
                }
                try {
                    result.complete(operation.run());
                } catch (Exception failure) {
                    // 文件替换失败的结果可能不明确，禁止后续操作覆盖磁盘证据。
                    if (failure instanceof IOException) storageFailure = failure;
                    result.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    private interface Operation<T> { T run() throws Exception; }

    private static final class FileStorage implements Storage {
        private final PlayerNbtFiles files = new PlayerNbtFiles(MAX_RECORD_BYTES, MAX_RECORD_BYTES * 2L);

        @Override
        public List<Path> entries(Path directory, int limit) throws IOException {
            if (Files.notExists(directory)) return List.of();
            try (var paths = Files.list(directory)) {
                List<Path> records = paths.filter(path -> path.getFileName().toString().endsWith(".dat"))
                        .limit((long) limit + 1L).toList();
                if (records.size() > limit) throw new IOException("Too many pending draw journal files");
                return records;
            }
        }

        @Override
        public Entry read(Path path) throws IOException { return Entry.read(files.read(path)); }

        @Override
        public void write(Path path, Entry entry) throws IOException { files.write(path, entry.serialize()); }

        @Override
        public void delete(Path path) throws IOException { Files.delete(path); }

        @Override
        public void archive(Path path) throws IOException {
            Path target = path.getParent().resolve("reconciled").resolve(path.getFileName());
            Files.createDirectories(target.getParent());
            try {
                Files.move(path, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(path, target);
            }
        }
    }
}
