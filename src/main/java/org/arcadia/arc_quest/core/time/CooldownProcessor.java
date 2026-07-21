package org.arcadia.arc_quest.core.time;

public interface CooldownProcessor {

    static CooldownProcessor createDefault() {
        return new DefaultCooldownProcessor();
    }

    boolean isOnCooldown(CooldownRecord record, CooldownPolicy policy, TimeSnapshot now);

    int remainingGameTicks(CooldownRecord record, int resetTick, TimeSnapshot now);

    boolean isDayTimeRegressed(CooldownRecord record, long nowDayTime);
}
