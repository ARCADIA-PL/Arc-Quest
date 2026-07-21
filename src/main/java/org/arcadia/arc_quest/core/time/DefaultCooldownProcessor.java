package org.arcadia.arc_quest.core.time;

import java.util.Objects;

final class DefaultCooldownProcessor implements CooldownProcessor {

    private static final long TICKS_PER_DAY = 24000L;

    @Override
    public CooldownStatus evaluate(CooldownRecord record, CooldownPolicy policy, TimeSnapshot now) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(now, "now");
        if (!record.exists() || policy.mode() == CooldownMode.NONE) return CooldownStatus.inactive();

        return switch (policy.mode()) {
            case NONE -> CooldownStatus.inactive();
            case REAL_TIME -> evaluateRealTime(record, policy, now);
            case GAME_DAY -> evaluateGameDay(record, now);
            case GAME_TICK -> evaluateGameTick(record, policy.resetTick(), now);
        };
    }

    @Override
    public boolean isOnCooldown(CooldownRecord record, CooldownPolicy policy, TimeSnapshot now) {
        return evaluate(record, policy, now).active();
    }

    @Override
    public int remainingGameTicks(CooldownRecord record, int resetTick, TimeSnapshot now) {
        return evaluateGameTick(record, resetTick, now).remainingGameTicks();
    }

    @Override
    public boolean isDayTimeRegressed(CooldownRecord record, long nowDayTime) {
        return Objects.requireNonNull(record, "record").exists() && record.dayTime() > nowDayTime;
    }

    private boolean isWithinSameGameDay(CooldownRecord record, TimeSnapshot now) {
        if (record.dayTime() < 0L || record.gameTime() < 0L) return false;
        long elapsed = now.gameTime() - record.gameTime();
        if (elapsed >= TICKS_PER_DAY) return false;
        if (record.dayTime() / TICKS_PER_DAY != now.dayTime() / TICKS_PER_DAY) return false;
        return now.dayTime() >= record.dayTime() || elapsed <= 0L;
    }

    private CooldownStatus evaluateRealTime(CooldownRecord record, CooldownPolicy policy, TimeSnapshot now) {
        long remaining = policy.value() - (now.realTime() - record.realTime());
        return remaining > 0L
                ? new CooldownStatus(true, remaining, 0)
                : CooldownStatus.inactive();
    }

    private CooldownStatus evaluateGameDay(CooldownRecord record, TimeSnapshot now) {
        if (!isWithinSameGameDay(record, now)) return CooldownStatus.inactive();
        int remainingTicks = (int) (TICKS_PER_DAY - Math.floorMod(now.dayTime(), TICKS_PER_DAY));
        return new CooldownStatus(true, 0L, remainingTicks);
    }

    private CooldownStatus evaluateGameTick(CooldownRecord record, int resetTick, TimeSnapshot now) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(now, "now");
        if (!isWithinSameResetPeriod(record, resetTick, now)) return CooldownStatus.inactive();
        long currentDayTick = Math.floorMod(now.dayTime(), TICKS_PER_DAY);
        long normalizedResetTick = Math.floorMod(resetTick, TICKS_PER_DAY);
        int remainingTicks = (int) (currentDayTick >= normalizedResetTick
                ? TICKS_PER_DAY - currentDayTick + normalizedResetTick
                : normalizedResetTick - currentDayTick);
        return new CooldownStatus(true, 0L, remainingTicks);
    }

    private boolean isWithinSameResetPeriod(CooldownRecord record, int resetTick, TimeSnapshot now) {
        if (!record.exists() || record.dayTime() < 0L || record.gameTime() < 0L) return false;
        long elapsed = now.gameTime() - record.gameTime();
        if (elapsed >= TICKS_PER_DAY) return false;
        if (now.dayTime() < record.dayTime() && elapsed > 0L) return false;
        long recordedPeriod = Math.floorDiv(record.dayTime() - resetTick, TICKS_PER_DAY);
        long currentPeriod = Math.floorDiv(now.dayTime() - resetTick, TICKS_PER_DAY);
        return recordedPeriod == currentPeriod;
    }
}
