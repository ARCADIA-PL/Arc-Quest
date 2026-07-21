package org.arcadia.arc_quest.core.time;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public interface TimeProcessor {

    long realTimeMillis();

    long gameTime(Level level);

    long dayTime(Level level);

    default TimeSnapshot capture(Level level) {
        return new TimeSnapshot(realTimeMillis(), gameTime(level), dayTime(level));
    }

    default TimeSnapshot capture(ServerPlayer player) {
        return player == null
                ? new TimeSnapshot(realTimeMillis(), 0L, 0L)
                : capture(player.level());
    }

    default boolean isMorning(Level level) {
        return dayTime(level) < 6000L;
    }

    default boolean isAfternoon(Level level) {
        long dayTick = dayTime(level);
        return dayTick >= 6000L && dayTick < 12000L;
    }

    default boolean isNight(Level level) {
        return dayTime(level) >= 12000L;
    }

    static TimeProcessor system() {
        return DefaultTimeProcessor.INSTANCE;
    }
}
