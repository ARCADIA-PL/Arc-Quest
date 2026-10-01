package org.arcadia.arc_quest.client.hud.quest.toast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToastSchedulerTest {
    private final ToastScheduler<Type, String> scheduler = new ToastScheduler<>();
    private long now;

    @BeforeEach
    void startClock() { scheduler.tick(0, true); }

    @Test
    void aShortAggregationWindowPreventsSameTickEventsFromFlashingSeparately() {
        submit(Type.PHASE, "quest", "one");
        submit(Type.PHASE, "quest", "two");
        scheduler.tick(now, true);
        assertNull(scheduler.current());
        advance(199);
        assertNull(scheduler.current());
        advance(1);
        assertEquals(2, scheduler.current().mergedCount());
        assertEquals(0, scheduler.current().elapsedMillis());
        long version = scheduler.current().version();
        advance(250);
        assertEquals(250, scheduler.current().elapsedMillis());
        assertEquals(version, scheduler.current().version());
    }

    @Test
    void hiddenTimeFreezesAnActiveEventAndDoesNotCatchUpOnResume() {
        submit(Type.ACCEPTED, "quest", "");
        advance(200);
        advance(500);
        long version = scheduler.current().version();
        now += 50;
        scheduler.tick(now, false);
        now += 60_000;
        scheduler.tick(now, false);
        assertEquals(500, scheduler.current().elapsedMillis());
        now += 5000;
        scheduler.tick(now, true);
        assertEquals(500, scheduler.current().elapsedMillis());
        advance(250);
        assertEquals(750, scheduler.current().elapsedMillis());
        assertEquals(version, scheduler.current().version());
    }

    @Test
    void messagesQueuedWhileOfflineWaitForVisibleActivityInsteadOfExpiring() {
        scheduler.tick(now, false);
        submit(Type.PHASE, "quest", "one");
        now = 100_000;
        scheduler.tick(now, false);
        assertNull(scheduler.current());
        assertEquals(1, scheduler.queuedCount());
        scheduler.tick(now, true);
        advance(200);
        assertEquals(Type.PHASE, scheduler.current().type());
        assertEquals(0, scheduler.current().elapsedMillis());
    }

    @Test
    void aBackwardClockSampleNeverAgesOrRewindsTheVisibleNotification() {
        submit(Type.ACCEPTED, "quest", "");
        advance(200);
        advance(500);
        scheduler.tick(400, true);
        assertEquals(500, scheduler.current().elapsedMillis());
        scheduler.tick(700, true);
        assertEquals(500, scheduler.current().elapsedMillis());
        advance(100);
        assertEquals(600, scheduler.current().elapsedMillis());
    }

    @Test
    void identityUsesQuestAndSubjectIdsRatherThanTranslatedTitles() {
        assertTrue(scheduler.submit(notice(Type.PHASE, "quest:a", "phase:a", "Same title"), true));
        assertFalse(scheduler.submit(notice(Type.PHASE, "quest:a", "phase:a", "Renamed title"), true));
        assertTrue(scheduler.submit(notice(Type.PHASE, "quest:b", "phase:a", "Same title"), true));
        assertEquals(2, scheduler.queuedCount());
        advance(200);
        assertEquals("quest:a", scheduler.current().questId());
        advance(ToastScheduler.EVENT_DURATION_MILLIS);
        assertEquals("quest:b", scheduler.current().questId());
    }

    @Test
    void anActiveDuplicateDoesNotRestartTheLifetimeOrReplayAfterCompletion() {
        submit(Type.ACCEPTED, "quest", "");
        advance(200);
        advance(1000);
        long version = scheduler.current().version();
        assertFalse(submit(Type.ACCEPTED, "quest", ""));
        assertEquals(version, scheduler.current().version());
        assertEquals(1000, scheduler.current().elapsedMillis());
        advance(ToastScheduler.EVENT_DURATION_MILLIS - 1000);
        assertNull(scheduler.current());
        assertEquals(0, scheduler.queuedCount());
    }

    @Test
    void burstsMergeOnlySameQuestAndTypeAndCountDistinctSubjects() {
        for (int i = 0; i < 30; i++) submit(Type.COLLECTION, "quest", "entry:" + i);
        assertFalse(submit(Type.COLLECTION, "quest", "entry:4"));
        submit(Type.PHASE, "quest", "entry:4");
        submit(Type.COLLECTION, "different", "entry:4");
        assertEquals(3, scheduler.queuedCount());
        advance(200);
        assertEquals(30, scheduler.current().mergedCount());
        assertEquals("quest/entry:0", scheduler.current().payload());
    }

    @Test
    void aLaterBurstDoesNotGetFoldedIntoAnUnrelatedEarlierTimeWindow() {
        submit(Type.ACCEPTED, "blocking", "");
        advance(200);
        submit(Type.PHASE, "quest", "first");
        advance(ToastScheduler.MERGE_WINDOW_MILLIS + 1);
        submit(Type.PHASE, "quest", "later");
        assertEquals(2, scheduler.queuedCount());
        advance(ToastScheduler.EVENT_DURATION_MILLIS - ToastScheduler.MERGE_WINDOW_MILLIS - 1);
        assertEquals("quest/first", scheduler.current().payload());
        assertEquals(1, scheduler.current().mergedCount());
    }

    @Test
    void theBacklogIsBoundedAndDropsOldestUnshownEventsUnderLoad() {
        for (int i = 0; i < 200; i++) submit(Type.ACCEPTED, "quest:" + i, "");
        assertEquals(ToastScheduler.MAX_QUEUED, scheduler.queuedCount());
        advance(200);
        assertEquals("quest:" + (200 - ToastScheduler.MAX_QUEUED), scheduler.current().questId());
    }

    @Test
    void mergedGroupsAlsoBoundTheirStoredSubjectKeys() {
        for (int i = 0; i < ToastScheduler.MAX_MERGED_SUBJECTS + 7; i++)
            submit(Type.COLLECTION, "quest", "entry:" + i);
        assertEquals(2, scheduler.queuedCount());
        advance(200);
        assertEquals(ToastScheduler.MAX_MERGED_SUBJECTS, scheduler.current().mergedCount());
        advance(ToastScheduler.EVENT_DURATION_MILLIS);
        assertEquals(7, scheduler.current().mergedCount());
    }

    @Test
    void staleQueuedEventsAreDiscardedInsteadOfPlayingLongAfterTheirContext() {
        submit(Type.ACCEPTED, "quest", "");
        advance(200);
        submit(Type.PHASE, "quest", "old");
        advance(ToastScheduler.STALE_MILLIS);
        assertNull(scheduler.current());
        assertEquals(0, scheduler.queuedCount());
    }

    @Test
    void terminalStateReplacesObsoleteMessagesOnlyForItsOwnQuest() {
        submit(Type.ACCEPTED, "finished", "");
        submit(Type.PHASE, "finished", "phase");
        submit(Type.OBJECTIVE, "finished", "objective");
        submit(Type.CONFIRM, "finished", "pending");
        submit(Type.PHASE, "other", "phase");
        submit(Type.COMPLETE, "finished", "");
        assertEquals(2, scheduler.queuedCount());
        assertEquals(0, scheduler.pendingCount());
        assertFalse(submit(Type.OBJECTIVE, "finished", "stale"));
        advance(200);
        assertEquals("other", scheduler.current().questId());
        advance(ToastScheduler.EVENT_DURATION_MILLIS);
        assertEquals(Type.COMPLETE, scheduler.current().type());
    }

    @Test
    void aDisabledTerminalNotificationStillClearsObsoleteVisibleAndQueuedState() {
        submit(Type.ACCEPTED, "quest", "");
        advance(200);
        submit(Type.PHASE, "quest", "phase");
        submit(Type.BRANCH, "quest", "branch");
        assertFalse(scheduler.submit(notice(Type.FAILED, "quest", "", "Failure"), false));
        assertNull(scheduler.current());
        assertEquals(0, scheduler.queuedCount());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void repeatableQuestCanCompleteAndRestartWithinTheSameTick() {
        submit(Type.COMPLETE, "repeatable", "");
        assertTrue(submit(Type.ACCEPTED, "repeatable", ""));
        assertTrue(submit(Type.OBJECTIVE, "repeatable", "same-objective"));
        assertTrue(submit(Type.CONFIRM, "repeatable", "same-phase"));
        assertEquals(3, scheduler.queuedCount());
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    void aSinglePendingActionDoesNotExpireOrRestartItsAnimationWhileIdle() {
        submit(Type.CONFIRM, "quest", "phase");
        scheduler.tick(now, true);
        long version = scheduler.current().version();
        advance(120_000);
        assertTrue(scheduler.current().persistent());
        assertEquals(version, scheduler.current().version());
        assertEquals(120_000, scheduler.current().elapsedMillis());
    }

    @Test
    void unchangedPendingSnapshotsAreIdempotentAndTextUpdatesKeepAnimationAge() {
        var pending = notice(Type.CONFIRM, "quest", "phase", "Original");
        scheduler.replacePendingForQuest("quest", List.of(pending));
        scheduler.tick(now, true);
        advance(1000);
        long version = scheduler.current().version();
        scheduler.replacePendingForQuest("quest", List.of(pending));
        assertEquals(version, scheduler.current().version());
        assertEquals(1000, scheduler.current().elapsedMillis());
        scheduler.replacePendingForQuest("quest", List.of(notice(Type.CONFIRM, "quest", "phase", "Updated")));
        assertEquals("Updated", scheduler.current().payload());
        assertTrue(scheduler.current().version() > version);
        assertEquals(1000, scheduler.current().elapsedMillis());
    }

    @Test
    void pendingActionsRotateFairlyWithoutBeingRemovedFromTheBacklog() {
        submit(Type.CONFIRM, "a", "phase");
        submit(Type.BRANCH, "b", "phase");
        submit(Type.CONFIRM, "c", "phase");
        scheduler.tick(now, true);
        assertEquals("a", scheduler.current().questId());
        for (String next : List.of("b", "c", "a", "b")) {
            advance(ToastScheduler.PENDING_ROTATION_MILLIS);
            assertEquals(next, scheduler.current().questId());
            assertEquals(3, scheduler.pendingCount());
        }
    }

    @Test
    void eventInterruptionResumesAtTheNextPendingActionRatherThanStarvingIt() {
        submit(Type.CONFIRM, "a", "phase");
        submit(Type.BRANCH, "b", "phase");
        scheduler.tick(now, true);
        submit(Type.COLLECTION, "event", "entry");
        advance(199);
        assertTrue(scheduler.current().persistent());
        advance(1);
        assertFalse(scheduler.current().persistent());
        advance(ToastScheduler.EVENT_DURATION_MILLIS);
        assertTrue(scheduler.current().persistent());
        assertEquals("b", scheduler.current().questId());
    }

    @Test
    void sustainedOrdinaryTrafficStillGivesPendingActionsAReadableTurn() {
        submit(Type.CONFIRM, "pending", "phase");
        for (int i = 0; i < 6; i++) submit(Type.ACCEPTED, "event:" + i, "");
        advance(200);
        for (int i = 0; i < 3; i++) {
            assertFalse(scheduler.current().persistent());
            advance(ToastScheduler.EVENT_DURATION_MILLIS);
        }
        assertTrue(scheduler.current().persistent());
        assertEquals("pending", scheduler.current().questId());
        advance(ToastScheduler.PENDING_MINIMUM_MILLIS - 1);
        assertTrue(scheduler.current().persistent());
        advance(1);
        assertFalse(scheduler.current().persistent());
        assertEquals("event:3", scheduler.current().questId());
    }

    @Test
    void dismissingANullPhaseClearsOnlyThatQuestsMatchingPendingType() {
        submit(Type.CONFIRM, "a", "first");
        submit(Type.CONFIRM, "a", "second");
        submit(Type.BRANCH, "a", "branch");
        submit(Type.CONFIRM, "b", "first");
        scheduler.dismissPending("a", null, Type.CONFIRM);
        assertEquals(2, scheduler.pendingCount());
        assertEquals("b", scheduler.firstPendingQuestId(Type.CONFIRM));
        assertEquals("a", scheduler.firstPendingQuestId(Type.BRANCH));
        scheduler.clearPendingForQuest("a");
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    void aFullSyncRemovesDeletedQuestsWithoutRestartingSurvivingPendingState() {
        var a = notice(Type.CONFIRM, "a", "phase", "A");
        var b = notice(Type.BRANCH, "b", "phase", "B");
        scheduler.replaceAllPending(Map.of("a", List.of(a)));
        scheduler.tick(now, true);
        advance(700);
        long version = scheduler.current().version();
        scheduler.replaceAllPending(Map.of("a", List.of(a), "b", List.of(b)));
        assertEquals(version, scheduler.current().version());
        assertEquals(700, scheduler.current().elapsedMillis());
        scheduler.replaceAllPending(Map.of("b", List.of(b)));
        assertNull(scheduler.current());
        scheduler.tick(now, true);
        assertEquals("b", scheduler.current().questId());
        scheduler.replaceAllPending(Map.of());
        assertNull(scheduler.current());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void disablingATypeClearsItImmediatelyAndReenablingOnlyRestoresActualPendingState() {
        var pending = notice(Type.CONFIRM, "pending", "phase", "Confirm");
        scheduler.replacePendingForQuest("pending", List.of(pending));
        submit(Type.PHASE, "event", "phase");
        advance(200);
        scheduler.retainTypes(type -> type != Type.PHASE && type != Type.CONFIRM);
        assertNull(scheduler.current());
        assertEquals(0, scheduler.pendingCount());
        assertEquals(0, scheduler.queuedCount());
        scheduler.replacePendingForQuest("pending", List.of(pending));
        scheduler.tick(now, true);
        assertEquals(Type.CONFIRM, scheduler.current().type());
        assertEquals(0, scheduler.queuedCount(), "Re-enablement must not replay past ordinary events");
    }

    @Test
    void disabledPendingUpdatesCanBeAcceptedWithoutProducingAVisibleToast() {
        assertTrue(scheduler.submit(notice(Type.CONFIRM, "quest", "phase", "Confirm"), false));
        scheduler.tick(now, true);
        assertNull(scheduler.current());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void clearingASessionDropsAllStateAndDoesNotReuseAnOldRendererCacheVersion() {
        submit(Type.CONFIRM, "quest", "phase");
        scheduler.tick(now, true);
        long previousVersion = scheduler.current().version();
        scheduler.clear();
        assertNull(scheduler.current());
        assertEquals(0, scheduler.pendingCount());
        assertEquals(0, scheduler.queuedCount());
        scheduler.tick(now, true);
        submit(Type.BRANCH, "next-session", "phase");
        scheduler.tick(now, true);
        assertTrue(scheduler.current().version() > previousVersion);
    }

    private boolean submit(Type type, String quest, String subject) {
        return scheduler.submit(notice(type, quest, subject, quest + "/" + subject), true);
    }

    @Test
    void authoritativeActiveSnapshotRestoresARepeatableQuestEvenWithoutPendingActions() {
        submit(Type.COMPLETE, "repeatable", "");
        assertFalse(submit(Type.OBJECTIVE, "repeatable", "objective"));
        scheduler.replaceAllPending(Map.of("repeatable", List.of()));
        assertTrue(submit(Type.OBJECTIVE, "repeatable", "objective"));
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void abandoningAQuestDropsItsQueuedProgressButPreservesFinalOutcomesAndOtherQuests() {
        submit(Type.PHASE, "abandoned", "phase");
        submit(Type.COLLECTION, "abandoned", "entry");
        submit(Type.PHASE, "live", "phase");
        submit(Type.COMPLETE, "finished", "");
        scheduler.retainActiveQuests(Set.of("live"));
        assertEquals(2, scheduler.queuedCount());
        advance(200);
        assertEquals("live", scheduler.current().questId());
        advance(ToastScheduler.EVENT_DURATION_MILLIS);
        assertEquals(Type.COMPLETE, scheduler.current().type());
    }

    @Test
    void authoritativeRemovalAlsoDropsAnAlreadyVisibleProgressNotice() {
        submit(Type.ACCEPTED, "removed", "");
        advance(200);
        assertNotNull(scheduler.current());
        scheduler.retainActiveQuests(Set.of());
        assertNull(scheduler.current());
        assertTrue(submit(Type.ACCEPTED, "removed", ""), "Immediate re-acceptance starts a new lifetime");
    }

    private static ToastScheduler.Notice<Type, String> notice(Type type, String quest, String subject, String title) {
        return new ToastScheduler.Notice<>(type, quest, subject, title);
    }

    private void advance(long millis) { now += millis; scheduler.tick(now, true); }

    private enum Type implements ToastScheduler.Kind {
        ACCEPTED, COMPLETE, FAILED, PHASE, OBJECTIVE, COLLECTION, CONFIRM, BRANCH;
        @Override public boolean persistent() { return this == CONFIRM || this == BRANCH; }
        @Override public boolean terminal() { return this == COMPLETE || this == FAILED; }
        @Override public boolean mergeable() { return this == PHASE || this == OBJECTIVE || this == COLLECTION; }
        @Override public boolean restartsQuest() { return this == ACCEPTED; }
        @Override public boolean supersededByTerminal() {
            return this == ACCEPTED || this == PHASE || this == OBJECTIVE || persistent();
        }
    }
}
