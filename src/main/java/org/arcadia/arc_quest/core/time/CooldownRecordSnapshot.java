package org.arcadia.arc_quest.core.time;

public record CooldownRecordSnapshot(
        long realTime,
        long gameTime,
        long dayTime,
        boolean exists
) implements CooldownRecord {

    public static CooldownRecordSnapshot recorded(long realTime, long gameTime, long dayTime) {
        return new CooldownRecordSnapshot(realTime, gameTime, dayTime, realTime > 0L);
    }

    public static CooldownRecordSnapshot missing() {
        return new CooldownRecordSnapshot(0L, -1L, -1L, false);
    }
}
