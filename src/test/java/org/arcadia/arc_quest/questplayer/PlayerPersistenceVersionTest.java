package org.arcadia.arc_quest.questplayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerPersistenceVersionTest {
    @Test
    void keepsVersionsMonotonicWhenWallClockMovesBackwards() {
        PlayerPersistenceVersion next = new PlayerPersistenceVersion(7, 100).next(20);
        assertEquals(8, next.revision());
        assertEquals(100, next.writtenAt());
    }

    @Test
    void saturatedRevisionContinuesWithTimestampInsteadOfWrappingToOne() {
        PlayerPersistenceVersion next = new PlayerPersistenceVersion(Long.MAX_VALUE, 100).next(20);
        assertEquals(Long.MAX_VALUE, next.revision());
        assertEquals(101, next.writtenAt());
    }

    @Test
    void refusesToGenerateAVersionThatWouldBeOlderThanSavedData() {
        assertThrows(IllegalStateException.class,
                () -> new PlayerPersistenceVersion(Long.MAX_VALUE, Long.MAX_VALUE).next(0));
    }
}
