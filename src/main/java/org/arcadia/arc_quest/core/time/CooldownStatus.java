package org.arcadia.arc_quest.core.time;

public record CooldownStatus(
        boolean active,
        long remainingRealMillis,
        int remainingGameTicks
) {

    public static CooldownStatus inactive() {
        return new CooldownStatus(false, 0L, 0);
    }

    public int remainingRealSecondsCeiling() {
        if (!active || remainingRealMillis <= 0L) return 0;
        return (int) Math.min(Integer.MAX_VALUE, 1L + (remainingRealMillis - 1L) / 1000L);
    }

    public int remainingRealSecondsFloor() {
        if (!active || remainingRealMillis <= 0L) return 0;
        return (int) Math.min(Integer.MAX_VALUE, remainingRealMillis / 1000L);
    }

    public int remainingGameSecondsFloor() {
        return !active || remainingGameTicks <= 0 ? 0 : remainingGameTicks / 20;
    }
}
