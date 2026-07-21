package org.arcadia.arc_quest.core.time;

public interface CooldownProcessor {

    static CooldownProcessor createDefault() {
        return new DefaultCooldownProcessor();
    }

    default CooldownStatus evaluate(CooldownRecord record, CooldownPolicy policy, TimeSnapshot now) {
        if (!isOnCooldown(record, policy, now)) return CooldownStatus.inactive();
        return switch (policy.mode()) {
            case NONE -> CooldownStatus.inactive();
            case REAL_TIME -> new CooldownStatus(
                    true,
                    Math.max(0L, policy.value() - (now.realTime() - record.realTime())),
                    0);
            case GAME_DAY -> new CooldownStatus(
                    true, 0L, (int) (24000L - Math.floorMod(now.dayTime(), 24000L)));
            case GAME_TICK -> new CooldownStatus(
                    true, 0L, remainingGameTicks(record, policy.resetTick(), now));
        };
    }

    boolean isOnCooldown(CooldownRecord record, CooldownPolicy policy, TimeSnapshot now);

    int remainingGameTicks(CooldownRecord record, int resetTick, TimeSnapshot now);

    boolean isDayTimeRegressed(CooldownRecord record, long nowDayTime);
}
