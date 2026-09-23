package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questplayer.persistence.PlayerNbtFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.arcadia.arc_quest.trade.gacha.network.PendingDrawJournal.Stage.*;
import static org.junit.jupiter.api.Assertions.*;

class PendingDrawRecoveryTest {
    @TempDir Path root;
    private final PlayerNbtFiles files = new PlayerNbtFiles(1024 * 1024, 2 * 1024 * 1024);

    @Test
    void onlyMatchingVanillaSaveReceiptAcknowledgesCompletedDelivery() throws Exception {
        var entry = entry();
        Path playerFile = root.resolve("player.dat");
        var journal = journal();
        try {
            prepareDelivered(journal, entry);
            assertTrue(journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS).isEmpty());
            CompoundTag player = player(entry);
            player.getCompound("ForgeCaps").getCompound("arc_quest:player_data").remove("DeliveredDrawReceipts");
            files.write(playerFile, player);
            assertTrue(journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS).isEmpty());
            // 独立进度检查点即使含同名回执，也不构成背包保存证据。
            CompoundTag checkpoint = new CompoundTag();
            CompoundTag checkpointReceipts = new CompoundTag();
            checkpointReceipts.putBoolean(entry.transactionId().toString(), true);
            checkpoint.put("DeliveredDrawReceipts", checkpointReceipts);
            player.getCompound("ForgeCaps").getCompound("arc_quest:player_data").put("GachaData", checkpoint);
            files.write(playerFile, player);
            assertTrue(journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS).isEmpty());
            files.write(playerFile, player(entry));
            assertEquals(List.of(entry.transactionId()), journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS));
            assertFalse(Files.exists(root.resolve("journal").resolve(entry.transactionId() + ".dat")));
        } finally { journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void receiptCannotReleaseAnUnfinishedOrDifferentPlayersDraw() throws Exception {
        var entry = entry();
        Path playerFile = root.resolve("player.dat");
        var journal = journal();
        try {
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
            journal.advance(entry.transactionId(), PREPARED, PAID).get(5, TimeUnit.SECONDS);
            journal.advance(entry.transactionId(), PAID, DELIVERING).get(5, TimeUnit.SECONDS);
            files.write(playerFile, player(entry));
            assertTrue(journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS).isEmpty());
            journal.advance(entry.transactionId(), DELIVERING, DELIVERED).get(5, TimeUnit.SECONDS);
            CompoundTag other = player(entry);
            other.putUUID("UUID", UUID.randomUUID());
            files.write(playerFile, other);
            var error = assertThrows(ExecutionException.class,
                    () -> journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS));
            assertInstanceOf(PendingDrawJournal.PlayerSaveReadException.class, error.getCause());
            // 读原版文件失败不应停用日志写入，也不能删除待核验记录。
            journal.prepare(entry()).get(5, TimeUnit.SECONDS);
            assertTrue(Files.exists(root.resolve("journal").resolve(entry.transactionId() + ".dat")));
        } finally { journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void unreadablePlayerSaveCanBeRetriedAfterNextSuccessfulSave() throws Exception {
        var entry = entry();
        Path playerFile = root.resolve("player.dat");
        var journal = journal();
        try {
            prepareDelivered(journal, entry);
            Files.writeString(playerFile, "incomplete vanilla replacement");
            assertThrows(ExecutionException.class,
                    () -> journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS));
            files.write(playerFile, player(entry));
            assertEquals(List.of(entry.transactionId()), journal.acknowledgeSavedPlayer(entry.playerId(), playerFile).get(5, TimeUnit.SECONDS));
        } finally { journal.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void manualResolutionArchivesEvidenceAndReleasesCapacityAcrossRestart() throws Exception {
        var entry = entry();
        var journal = journal();
        try {
            journal.prepare(entry).get(5, TimeUnit.SECONDS);
            journal.advance(entry.transactionId(), PREPARED, REVIEW).get(5, TimeUnit.SECONDS);
            assertThrows(IllegalArgumentException.class, () -> journal.resolve(entry.transactionId(), REVIEW, "admin", " ", 10));
            assertThrows(ExecutionException.class,
                    () -> journal.resolve(entry.transactionId(), PREPARED, "admin", "verified refund", 10).get(5, TimeUnit.SECONDS));
            journal.resolve(entry.transactionId(), REVIEW, "admin", "verified refund", 10).get(5, TimeUnit.SECONDS);
        } finally { journal.shutdown(Duration.ofSeconds(5)); }
        CompoundTag archived = files.read(root.resolve("journal/reconciled").resolve(entry.transactionId() + ".dat"));
        assertEquals("RESOLVED", archived.getString("Stage"));
        assertEquals("admin", archived.getCompound("Payload").getString("ResolvedBy"));
        assertEquals("verified refund", archived.getCompound("Payload").getString("Resolution"));
        var restarted = journal();
        try { assertTrue(restarted.loaded().get(5, TimeUnit.SECONDS).isEmpty()); }
        finally { restarted.shutdown(Duration.ofSeconds(5)); }
    }

    @Test
    void restartFinishesArchivalAfterResolvedWriteWithoutReplayingReward() throws Exception {
        var entry = entry().at(RESOLVED);
        files.write(root.resolve("journal").resolve(entry.transactionId() + ".dat"), entry.serialize());
        var journal = journal();
        try {
            assertTrue(journal.loaded().get(5, TimeUnit.SECONDS).isEmpty());
            assertTrue(Files.exists(root.resolve("journal/reconciled").resolve(entry.transactionId() + ".dat")));
            journal.prepare(entry()).get(5, TimeUnit.SECONDS);
        } finally { journal.shutdown(Duration.ofSeconds(5)); }
    }

    private PendingDrawJournal journal() throws Exception {
        var journal = new PendingDrawJournal(root.resolve("journal"), 4);
        journal.loaded().get(5, TimeUnit.SECONDS);
        return journal;
    }

    private static PendingDrawJournal.Entry entry() {
        return new PendingDrawJournal.Entry(UUID.randomUUID(), UUID.randomUUID(), "arc_quest:test", PREPARED, new CompoundTag());
    }

    private static CompoundTag player(PendingDrawJournal.Entry entry) {
        CompoundTag player = new CompoundTag();
        player.putUUID("UUID", entry.playerId());
        CompoundTag receipts = new CompoundTag();
        receipts.putBoolean(entry.transactionId().toString(), true);
        CompoundTag capability = new CompoundTag();
        capability.put("DeliveredDrawReceipts", receipts);
        CompoundTag caps = new CompoundTag();
        caps.put("arc_quest:player_data", capability);
        player.put("ForgeCaps", caps);
        return player;
    }

    private static void prepareDelivered(PendingDrawJournal journal, PendingDrawJournal.Entry entry) throws Exception {
        journal.prepare(entry).get(5, TimeUnit.SECONDS);
        journal.advance(entry.transactionId(), PREPARED, PAID).get(5, TimeUnit.SECONDS);
        journal.advance(entry.transactionId(), PAID, DELIVERING).get(5, TimeUnit.SECONDS);
        journal.advance(entry.transactionId(), DELIVERING, DELIVERED).get(5, TimeUnit.SECONDS);
    }
}
