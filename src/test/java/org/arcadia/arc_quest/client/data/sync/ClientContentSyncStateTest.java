package org.arcadia.arc_quest.client.data.sync;

import org.junit.jupiter.api.Test;
import static org.arcadia.arc_quest.client.data.sync.ClientContentSyncState.HeaderResult.*;
import static org.arcadia.arc_quest.client.data.sync.ClientContentSyncState.RetryAction.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientContentSyncStateTest {
    private static final long SECOND = 1_000_000_000L;
    private static final String FIRST = "a".repeat(64), SECOND_HASH = "b".repeat(64);

    @Test void failedModuleDoesNotAcknowledgeContentAndIdenticalRetryCanSucceed() {
        var state = new ClientContentSyncState();
        assertEquals(APPLY, state.begin(4, FIRST));
        assertFalse(state.complete(4, FIRST, false, 0));
        assertEquals(-1, state.appliedEpoch());
        assertEquals("", state.appliedHash());
        assertEquals(REQUEST, state.pollRetry(SECOND));
        assertEquals(APPLY, state.begin(4, FIRST));
        assertTrue(state.complete(4, FIRST, true, SECOND));
        assertEquals(4, state.appliedEpoch());
        assertEquals(FIRST, state.appliedHash());
        assertEquals(ALREADY_APPLIED, state.begin(4, FIRST));
        assertEquals(NONE, state.pollRetry(100 * SECOND));
    }

    @Test void partialReloadKeepsPreviousCommitAndRejectsLateOlderHeaders() {
        var state = new ClientContentSyncState();
        state.begin(2, FIRST);
        assertTrue(state.complete(2, FIRST, true, 0));
        state.begin(3, SECOND_HASH);
        assertFalse(state.complete(3, SECOND_HASH, false, 0));
        assertEquals(2, state.appliedEpoch());
        assertEquals(FIRST, state.appliedHash());
        assertEquals(STALE, state.begin(2, FIRST));
        assertEquals(STALE, state.begin(1, SECOND_HASH));
        assertFalse(state.complete(2, FIRST, true, SECOND));
        assertEquals(APPLY, state.begin(3, SECOND_HASH));
        assertTrue(state.complete(3, SECOND_HASH, true, SECOND));
        assertEquals(3, state.appliedEpoch());
    }

    @Test void silentRequestsBackOffOneTwoFourSecondsThenStopAndReportOnce() {
        var state = new ClientContentSyncState();
        state.begin(1, FIRST);
        state.complete(1, FIRST, false, 0);
        assertEquals(NONE, state.pollRetry(SECOND - 1));
        assertEquals(REQUEST, state.pollRetry(SECOND));
        assertEquals(NONE, state.pollRetry(3 * SECOND - 1));
        assertEquals(REQUEST, state.pollRetry(3 * SECOND));
        assertEquals(NONE, state.pollRetry(7 * SECOND - 1));
        assertEquals(REQUEST, state.pollRetry(7 * SECOND));
        assertEquals(EXHAUSTED, state.pollRetry(11 * SECOND));
        state.scheduleRetry(12 * SECOND);
        assertEquals(NONE, state.pollRetry(100 * SECOND));
    }

    @Test void identicalFailureCannotResetBudgetOrRepeatedNoticePostponeScheduledRetry() {
        var state = new ClientContentSyncState();
        state.begin(1, FIRST);
        state.complete(1, FIRST, false, 0);
        assertEquals(REQUEST, state.pollRetry(SECOND));
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertEquals(APPLY, state.begin(1, FIRST));
            long failedAt = attempt == 1 ? SECOND : 3 * SECOND;
            state.complete(1, FIRST, false, failedAt);
            state.expect(1, failedAt + SECOND / 2);
            long dueAt = attempt == 1 ? 3 * SECOND : 7 * SECOND;
            assertEquals(NONE, state.pollRetry(dueAt - 1));
            assertEquals(REQUEST, state.pollRetry(dueAt));
        }
        assertEquals(APPLY, state.begin(1, FIRST));
        state.complete(1, FIRST, false, 7 * SECOND);
        assertEquals(EXHAUSTED, state.pollRetry(7 * SECOND));
        state.begin(1, FIRST);
        state.complete(1, FIRST, false, 8 * SECOND);
        assertEquals(NONE, state.pollRetry(100 * SECOND));
    }

    @Test void newContentGetsANewRetryBudgetAndNoticeMakesEarlierHeadersStale() {
        var state = new ClientContentSyncState();
        state.begin(1, FIRST);
        state.complete(1, FIRST, false, 0);
        state.pollRetry(SECOND);
        state.pollRetry(3 * SECOND);
        state.pollRetry(7 * SECOND);
        state.pollRetry(11 * SECOND);
        state.expect(2, 20 * SECOND);
        assertEquals(STALE, state.begin(1, FIRST));
        assertEquals(REQUEST, state.pollRetry(21 * SECOND));
        assertEquals(APPLY, state.begin(2, SECOND_HASH));
        state.complete(2, SECOND_HASH, false, 21 * SECOND);
        assertEquals(REQUEST, state.pollRetry(22 * SECOND));
    }

    @Test void disconnectDropsCommittedIdentityPendingRetriesAndOldServerEpoch() {
        var state = new ClientContentSyncState();
        state.begin(100, FIRST);
        state.complete(100, FIRST, true, 0);
        state.begin(101, SECOND_HASH);
        state.complete(101, SECOND_HASH, false, 0);
        state.clear();
        assertEquals(-1, state.appliedEpoch());
        assertEquals(-1, state.targetEpoch());
        assertEquals("", state.appliedHash());
        assertEquals(NONE, state.pollRetry(SECOND));
        assertEquals(APPLY, state.begin(0, FIRST));
        assertTrue(state.complete(0, FIRST, true, SECOND));
    }
}
