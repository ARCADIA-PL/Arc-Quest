package org.arcadia.arc_quest.core.time;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeProcessorTest {

    @Test
    void captureSamplesEachClockExactlyOnce() {
        AtomicInteger realSamples = new AtomicInteger();
        AtomicInteger gameSamples = new AtomicInteger();
        AtomicInteger daySamples = new AtomicInteger();
        TimeProcessor processor = new TimeProcessor() {
            @Override
            public long realTimeMillis() {
                realSamples.incrementAndGet();
                return 10L;
            }

            @Override
            public long gameTime(Level level) {
                gameSamples.incrementAndGet();
                return 20L;
            }

            @Override
            public long dayTime(Level level) {
                daySamples.incrementAndGet();
                return 6000L;
            }
        };

        assertEquals(new TimeSnapshot(10L, 20L, 6000L), processor.capture((Level) null));
        assertEquals(1, realSamples.get());
        assertEquals(1, gameSamples.get());
        assertEquals(1, daySamples.get());
        assertFalse(processor.isMorning(null));
        assertTrue(processor.isAfternoon(null));
        assertFalse(processor.isNight(null));
    }
}
