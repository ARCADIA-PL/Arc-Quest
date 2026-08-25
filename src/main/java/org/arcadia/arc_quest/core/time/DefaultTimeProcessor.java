package org.arcadia.arc_quest.core.time;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.world.level.Level;

final class DefaultTimeProcessor implements TimeProcessor {

    static final DefaultTimeProcessor INSTANCE = new DefaultTimeProcessor();
    private static final long TICKS_PER_DAY = 24000L;

    private DefaultTimeProcessor() {
    }

    @Override
    public long realTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long gameTime(Level level) {
        if (level == null) {
            ArcQuestLog.warn(ArcQuestLog.Category.CORE, "Level is null, returning 0 for gameTime");
            return 0L;
        }
        return level.getGameTime();
    }

    @Override
    public long dayTime(Level level) {
        if (level == null) {
            ArcQuestLog.warn(ArcQuestLog.Category.CORE, "Level is null, returning 0 for dayTime");
            return 0L;
        }
        return Math.floorMod(level.getDayTime(), TICKS_PER_DAY);
    }
}
