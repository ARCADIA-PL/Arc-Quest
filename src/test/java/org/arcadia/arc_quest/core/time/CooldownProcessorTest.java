package org.arcadia.arc_quest.core.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownProcessorTest {

    private final CooldownProcessor processor = CooldownProcessor.createDefault();

    @Test
    void realTimeCooldownKeepsLegacyExclusiveEndBoundary() {
        CooldownRecord record = CooldownRecordSnapshot.recorded(1000L, 10L, 10L);
        CooldownPolicy policy = new CooldownPolicy(CooldownMode.REAL_TIME, 5000L, 0);

        assertTrue(processor.isOnCooldown(record, policy, new TimeSnapshot(5999L, 20L, 20L)));
        assertFalse(processor.isOnCooldown(record, policy, new TimeSnapshot(6000L, 20L, 20L)));
    }

    @Test
    void gameDayCooldownExpiresAfterDayOrFullDayElapsed() {
        CooldownRecord record = CooldownRecordSnapshot.recorded(1000L, 100L, 100L);
        CooldownPolicy policy = new CooldownPolicy(CooldownMode.GAME_DAY, 0L, 0);

        assertTrue(processor.isOnCooldown(record, policy, new TimeSnapshot(0L, 200L, 200L)));
        assertFalse(processor.isOnCooldown(record, policy, new TimeSnapshot(0L, 24100L, 200L)));
        assertFalse(processor.isOnCooldown(record, policy, new TimeSnapshot(0L, 200L, 24100L)));
    }

    @Test
    void gameTickRemainingUsesConfiguredResetBoundary() {
        CooldownRecord record = CooldownRecordSnapshot.recorded(1000L, 5000L, 5000L);
        TimeSnapshot now = new TimeSnapshot(0L, 6000L, 6000L);

        assertEquals(19000, processor.remainingGameTicks(record, 1000, now));
        assertTrue(processor.isDayTimeRegressed(record, 4999L));
    }

    @Test
    void evaluationProvidesLegacyDisplayRoundingModes() {
        CooldownRecord record = CooldownRecordSnapshot.recorded(1000L, 100L, 100L);
        CooldownStatus status = processor.evaluate(
                record,
                new CooldownPolicy(CooldownMode.REAL_TIME, 5000L, 0),
                new TimeSnapshot(2500L, 100L, 100L));

        assertTrue(status.active());
        assertEquals(3500L, status.remainingRealMillis());
        assertEquals(4, status.remainingRealSecondsCeiling());
        assertEquals(3, status.remainingRealSecondsFloor());
    }

    @Test
    void gameDayEvaluationProvidesRemainingTicks() {
        CooldownStatus status = processor.evaluate(
                CooldownRecordSnapshot.recorded(1000L, 100L, 100L),
                new CooldownPolicy(CooldownMode.GAME_DAY, 0L, 0),
                new TimeSnapshot(2000L, 200L, 6000L));

        assertTrue(status.active());
        assertEquals(18000, status.remainingGameTicks());
        assertEquals(900, status.remainingGameSecondsFloor());
    }

    @Test
    void legacyProcessorImplementationsReceiveDefaultEvaluation() {
        CooldownProcessor legacyProcessor = new CooldownProcessor() {
            @Override
            public boolean isOnCooldown(CooldownRecord record, CooldownPolicy policy, TimeSnapshot now) {
                return true;
            }

            @Override
            public int remainingGameTicks(CooldownRecord record, int resetTick, TimeSnapshot now) {
                return 1200;
            }

            @Override
            public boolean isDayTimeRegressed(CooldownRecord record, long nowDayTime) {
                return false;
            }
        };

        CooldownStatus status = legacyProcessor.evaluate(
                CooldownRecordSnapshot.recorded(1000L, 100L, 100L),
                new CooldownPolicy(CooldownMode.GAME_TICK, 0L, 1000),
                new TimeSnapshot(2000L, 200L, 200L));

        assertTrue(status.active());
        assertEquals(1200, status.remainingGameTicks());
    }
}
