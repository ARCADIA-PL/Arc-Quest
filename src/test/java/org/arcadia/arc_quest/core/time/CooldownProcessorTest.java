package org.arcadia.arc_quest.core.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownProcessorTest {

    private final CooldownProcessor processor = CooldownProcessor.createDefault();

    @Test
    void realTimeCooldownKeepsLegacyExclusiveEndBoundary() {
        CooldownRecord record = record(1000L, 10L, 10L);
        CooldownPolicy policy = new CooldownPolicy(CooldownMode.REAL_TIME, 5000L, 0);

        assertTrue(processor.isOnCooldown(record, policy, new TimeSnapshot(5999L, 20L, 20L)));
        assertFalse(processor.isOnCooldown(record, policy, new TimeSnapshot(6000L, 20L, 20L)));
    }

    @Test
    void gameDayCooldownExpiresAfterDayOrFullDayElapsed() {
        CooldownRecord record = record(1000L, 100L, 100L);
        CooldownPolicy policy = new CooldownPolicy(CooldownMode.GAME_DAY, 0L, 0);

        assertTrue(processor.isOnCooldown(record, policy, new TimeSnapshot(0L, 200L, 200L)));
        assertFalse(processor.isOnCooldown(record, policy, new TimeSnapshot(0L, 24100L, 200L)));
        assertFalse(processor.isOnCooldown(record, policy, new TimeSnapshot(0L, 200L, 24100L)));
    }

    @Test
    void gameTickRemainingUsesConfiguredResetBoundary() {
        CooldownRecord record = record(1000L, 5000L, 5000L);
        TimeSnapshot now = new TimeSnapshot(0L, 6000L, 6000L);

        assertEquals(19000, processor.remainingGameTicks(record, 1000, now));
        assertTrue(processor.isDayTimeRegressed(record, 4999L));
    }

    private static CooldownRecord record(long realTime, long gameTime, long dayTime) {
        return new CooldownRecord() {
            @Override
            public long realTime() {
                return realTime;
            }

            @Override
            public long gameTime() {
                return gameTime;
            }

            @Override
            public long dayTime() {
                return dayTime;
            }

            @Override
            public boolean exists() {
                return realTime > 0L;
            }
        };
    }
}
