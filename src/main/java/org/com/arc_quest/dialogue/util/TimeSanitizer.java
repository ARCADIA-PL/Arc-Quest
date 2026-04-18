package org.com.arc_quest.dialogue.util;

import net.minecraft.world.level.Level;
import org.com.arc_quest.Arc_quest;

/**
 * 游戏时间校准工具 —— 确保时间判断的一致性。
 */
public final class TimeSanitizer {

    private TimeSanitizer() {
    }

    /**
     * 校准游戏时间刻，确保在 [0, 23999] 范围内。
     */
    public static long sanitizeDayTime(Level level) {
        if (level == null) {
            Arc_quest.LOGGER.warn("[TimeSanitizer] Level is null, returning 0");
            return 0;
        }
        
        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 0) {
            dayTime += 24000;
        }
        
        return dayTime;
    }

    /**
     * 校准后的早晨判断：tick [0, 6000)
     */
    public static boolean isMorning(Level level) {
        long t = sanitizeDayTime(level);
        return t >= 0 && t < 6000;
    }

    /**
     * 校准后的下午判断：tick [6000, 12000)
     */
    public static boolean isAfternoon(Level level) {
        long t = sanitizeDayTime(level);
        return t >= 6000 && t < 12000;
    }

    /**
     * 校准后的夜晚判断：tick [12000, 24000)
     */
    public static boolean isNight(Level level) {
        long t = sanitizeDayTime(level);
        return t >= 12000 && t < 24000;
    }

    /**
     * 获取当前时间段描述（用于调试）。
     */
    public static String getTimePeriodDescription(Level level) {
        long t = sanitizeDayTime(level);
        
        if (t >= 0 && t < 6000) {
            return String.format("早晨 (tick=%d, 6:00-12:00)", t);
        } else if (t >= 6000 && t < 12000) {
            return String.format("下午 (tick=%d, 12:00-18:00)", t);
        } else if (t >= 12000 && t < 18000) {
            return String.format("傍晚 (tick=%d, 18:00-0:00)", t);
        } else {
            return String.format("深夜 (tick=%d, 0:00-6:00)", t);
        }
    }

    /**
     * 验证时间一致性（用于调试）。
     */
    public static void validateTimeConsistency(Level level) {
        long t = sanitizeDayTime(level);
        boolean morning = isMorning(level);
        boolean afternoon = isAfternoon(level);
        boolean night = isNight(level);
        
        int matchCount = 0;
        if (morning) matchCount++;
        if (afternoon) matchCount++;
        if (night) matchCount++;
        
        if (matchCount != 1) {
            Arc_quest.LOGGER.error("[TimeSanitizer] ⚠️ 时间判断异常！tick={}, morning={}, afternoon={}, night={}, matches={}",
                    t, morning, afternoon, night, matchCount);
        } else {
            Arc_quest.LOGGER.debug("[TimeSanitizer] ✅ 时间判断正常: {}", getTimePeriodDescription(level));
        }
    }
}
