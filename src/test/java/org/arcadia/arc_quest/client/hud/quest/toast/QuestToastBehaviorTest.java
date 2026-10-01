package org.arcadia.arc_quest.client.hud.quest.toast;

import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager.ToastType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuestToastBehaviorTest {
    @Test void phaseAddedNotificationsCannotBeEnabledByOldConfigurationOrCallers() {
        assertFalse(ToastType.PHASE_ADDED.isEnabled());
    }

    @Test void manualConfirmationExpiresNormallyInsteadOfEnteringThePersistentQueue() {
        var scheduler = new ToastScheduler<ToastType, String>();
        scheduler.tick(0, true);
        scheduler.submit(notice(ToastType.PHASE_PENDING_CONFIRM, "phase"), true);
        scheduler.tick(200, true);
        assertFalse(scheduler.current().persistent());
        assertEquals(0, scheduler.pendingCount());
        scheduler.tick(200 + ToastScheduler.EVENT_DURATION_MILLIS - 1, true);
        assertNotNull(scheduler.current());
        scheduler.tick(200 + ToastScheduler.EVENT_DURATION_MILLIS, true);
        assertNull(scheduler.current());
        scheduler.tick(60_000, true);
        assertNull(scheduler.current());
    }

    @Test void snapshotRebuildsNeverRestoreAnExpiredConfirmationAsAPersistentAction() {
        var scheduler = new ToastScheduler<ToastType, String>();
        scheduler.replacePendingForQuest("quest", List.of(notice(ToastType.PHASE_PENDING_CONFIRM, "phase")));
        scheduler.tick(0, true);
        assertEquals(0, scheduler.pendingCount());
        assertNull(scheduler.current());
        assertTrue(ToastType.BRANCH_CHOICE.persistent());
    }

    @Test void resolvedConfirmationsAreRemovedWithoutRemovingOtherPhasesOrCompletionNotices() {
        var scheduler = new ToastScheduler<ToastType, String>();
        scheduler.tick(0, true);
        scheduler.submit(notice(ToastType.PHASE_PENDING_CONFIRM, "resolved"), true);
        scheduler.submit(notice(ToastType.PHASE_PENDING_CONFIRM, "waiting"), true);
        scheduler.submit(notice(ToastType.PHASE_COMPLETED, "completed"), true);
        scheduler.tick(200, true);
        assertEquals("resolved", scheduler.current().payload());
        scheduler.retainSubjects(ToastType.PHASE_PENDING_CONFIRM, "quest", Set.of("waiting"));
        assertNull(scheduler.current());
        scheduler.tick(200, true);
        assertEquals("waiting", scheduler.current().payload());
        scheduler.retainSubjects(ToastType.PHASE_PENDING_CONFIRM, "quest", Set.of());
        scheduler.tick(200, true);
        assertEquals(ToastType.PHASE_COMPLETED, scheduler.current().type());
    }

    private static ToastScheduler.Notice<ToastType, String> notice(ToastType type, String phase) {
        return new ToastScheduler.Notice<>(type, "quest", phase, phase);
    }
}
