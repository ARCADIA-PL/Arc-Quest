package org.arcadia.arc_quest.quest.data;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CollectionPersistenceTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("arc_quest:test_record");
    @Test void sparseRecordsClampAndSnapshotsDoNotShareMutableState() {
        var state = new CollectionRecordState();
        assertFalse(state.isDirty());
        assertFalse(state.isDiscovered(ENTRY));
        assertTrue(state.discover(ENTRY));
        assertFalse(state.discover(ENTRY));
        assertTrue(state.increment(ENTRY, "research:craft", Integer.MAX_VALUE, 7));
        assertEquals(7, state.getProgress(ENTRY, "research:craft"));
        assertFalse(state.increment(ENTRY, "research:craft", 1, 7));
        assertTrue(state.markSeen(ENTRY, "entry"));
        var restored = new CollectionRecordState();
        restored.readSnapshot(state.serializeNBT());
        state.increment(ENTRY, "research:other", 2, 9);
        assertEquals(0, restored.getProgress(ENTRY, "research:other"));
        assertEquals(Set.of(ENTRY), state.getDirtyEntryIds());
        restored.clearDirty();
        assertTrue(restored.getRecord(ENTRY).isSeen());
        assertFalse(restored.isDirty());
    }
    @Test void deltaIgnoresOldRevisionsAndKeepsOtherRecords() {
        var server = new CollectionRecordState();
        var other = ResourceLocation.parse("arc_quest:other");
        server.discover(other);
        var client = new CollectionRecordState();
        client.readSnapshot(server.serializeNBT());
        server.discover(ENTRY);
        var older = server.serializeEntries(Set.of(ENTRY));
        server.increment(ENTRY, "research:kill", 4, 8);
        client.applyDelta(server.serializeEntries(Set.of(ENTRY)));
        client.applyDelta(older);
        assertEquals(4, client.getProgress(ENTRY, "research:kill"));
        assertTrue(client.isDiscovered(other));
    }
    @Test void runBaselineFrozenCandidatesAndCompletionSurviveNbtAndNetwork() {
        var run = new CollectionRuntimeData();
        run.initializeSheet("field", List.of("a", "b", "c"), 2, Set.of(ENTRY.toString()));
        String runId = run.getRunId();
        assertTrue(run.markBindingComplete("field", "a"));
        run.initializeSheet("field", List.of("replacement"), 1, Set.of());
        assertEquals(2, run.getFrozenSheetTarget("field", 1));
        assertEquals(List.of("a", "b", "c"), run.getFrozenBindingIds("field"));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            run.writeToNetwork(buffer);
            var network = CollectionRuntimeData.readFromNetwork(buffer);
            assertEquals(0, buffer.readableBytes());
            assertEquals(run.serializeNBT(), network.serializeNBT());
            assertEquals(runId, network.getRunId());
        } finally { buffer.release(); }
        var restored = CollectionRuntimeData.deserializeNBT(run.serializeNBT());
        assertTrue(restored.wasDiscoveredAtAccept("field", ENTRY.toString()));
        assertTrue(restored.isBindingComplete("field", "a"));
        var copy = run.copy();
        run.markBindingComplete("field", "b");
        assertFalse(copy.isBindingComplete("field", "b"));
    }
    @Test void abandonmentAndRepeatKeepRecordsAndArchiveThePreviousRun() {
        var player = new ArcQuestPlayer(UUID.randomUUID());
        player.getCollectionRecords().discover(ENTRY);
        var runtime = new QuestRuntimeData("arc_quest:test", "field", 1, 1, 2, 3);
        runtime.getOrCreateCollectionData().initializeSheet("field", List.of("a"), 1, Set.of());
        runtime.setState(QuestState.FAILED);
        player.addActiveQuest(runtime);
        player.markFailed(runtime.getQuestId());
        var copy = new ArcQuestPlayer(UUID.randomUUID());
        copy.deserializeNBT(player.serializeNBT());
        assertTrue(copy.getCollectionRecords().isDiscovered(ENTRY));
        assertNotNull(copy.getCollectionArchives().get(runtime.getQuestId()));
        var repeat = new QuestRuntimeData(runtime.getQuestId(), "field", 1, 4, 5, 6);
        repeat.getOrCreateCollectionData().initializeSheet("field", List.of("a"), 1, Set.of(ENTRY.toString()));
        copy.addActiveQuest(repeat);
        assertNotEquals(runtime.getCollectionData().getRunId(), repeat.getCollectionData().getRunId());
        assertEquals(0, repeat.getObjectiveProgress("field", 0));
        assertTrue(copy.getCollectionRecords().isDiscovered(ENTRY));
    }
    @Test void migratedRewardReceiptsAreScopedToTheirQuestAndSurviveRestart() {
        var records = new CollectionRecordState();
        assertTrue(records.markLegacyRewardClaimed("example:old_quest", "milestone"));
        assertFalse(records.markLegacyRewardClaimed("example:old_quest", "milestone"));
        assertTrue(records.isLegacyRewardClaimed("example:old_quest", "milestone"));
        assertFalse(records.isLegacyRewardClaimed("example:other_quest", "milestone"));
        var restored = new CollectionRecordState(); restored.readSnapshot(records.serializeNBT());
        assertTrue(restored.isLegacyRewardClaimed("example:old_quest", "milestone"));
        assertFalse(restored.isLegacyRewardClaimed("example:other_quest", "milestone"));
    }

    @Test void versionFourUpgradeAndFailedRestorePreserveCurrentKnowledge() {
        var legacy = new CompoundTag();
        legacy.putInt("_ArcQuestVer", 4);
        var player = new ArcQuestPlayer(UUID.randomUUID());
        player.deserializeNBT(legacy);
        assertFalse(player.getCollectionRecords().isDiscovered(ENTRY));
        player.getCollectionRecords().discover(ENTRY);
        var invalid = new CompoundTag();
        invalid.putInt("_ArcQuestVer", Integer.MAX_VALUE);
        assertThrows(RuntimeException.class, () -> player.deserializeNBT(invalid));
        assertTrue(player.getCollectionRecords().isDiscovered(ENTRY));
    }
}
