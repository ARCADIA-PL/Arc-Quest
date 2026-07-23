package org.arcadia.arc_quest.core.time;

public record CooldownPolicy(CooldownMode mode, long value, int resetTick) {

    public CooldownPolicy {
        mode = mode == null ? CooldownMode.NONE : mode;
    }

    public static CooldownPolicy none() {
        return new CooldownPolicy(CooldownMode.NONE, 0L, 0);
    }
}
