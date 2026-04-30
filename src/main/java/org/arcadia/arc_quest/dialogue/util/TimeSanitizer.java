package org.arcadia.arc_quest.dialogue.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.arcadia.arc_quest.Arc_Quest;

/**
 * 游戏时间校准工具 —— 确保时间判断的一致性。
 */
public final class TimeSanitizer {

    private TimeSanitizer() {
    }

    /**
     * 获取当前真实时间戳（毫秒）
     */
    public static long getCurrentRealTime() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前游戏总刻（gameTime）
     *
     * @param level 世界对象
     * @return 游戏总刻数
     */
    public static long getCurrentGameTime(Level level) {
        if (level == null) {
            Arc_Quest.LOGGER.warn("[TimeSanitizer] Level is null, returning 0 for gameTime");
            return 0;
        }
        return level.getGameTime();
    }

    /**
     * 获取当前游戏总刻（gameTime）- ServerPlayer重载
     */
    public static long getCurrentGameTime(ServerPlayer player) {
        if (player == null) {
            Arc_Quest.LOGGER.warn("[TimeSanitizer] Player or level is null, returning 0 for gameTime");
            return 0;
        } else {
            player.level();
        }
        return player.level().getGameTime();
    }

    /**
     * 获取当前当天刻（dayTime），已校准到 [0, 24000]
     *
     * @param level 世界对象
     * @return 当天刻数
     */
    public static long getCurrentDayTime(Level level) {
        return sanitizeDayTime(level);
    }

    /**
     * 获取当前当天刻（dayTime）- ServerPlayer重载
     */
    public static long getCurrentDayTime(ServerPlayer player) {
        if (player == null) {
            Arc_Quest.LOGGER.warn("[TimeSanitizer] Player or level is null, returning 0 for dayTime");
            return 0;
        } else {
            player.level();
        }
        return sanitizeDayTime(player.level());
    }

    /**
     * 一次性获取所有时间数据（用于冷却检查等场景）
     *
     * @param level 世界对象
     * @return 时间数据数组 [realTime, gameTime, dayTime]
     */
    public static long[] getAllTimes(Level level) {
        return new long[]{
                getCurrentRealTime(),
                getCurrentGameTime(level),
                getCurrentDayTime(level)
        };
    }

    /**
     * 一次性获取所有时间数据 - ServerPlayer重载
     */
    public static long[] getAllTimes(ServerPlayer player) {
        if (player == null) {
            return new long[]{getCurrentRealTime(), 0, 0};
        } else {
            player.level();
        }
        return new long[]{
                getCurrentRealTime(),
                getCurrentGameTime(player),
                getCurrentDayTime(player)
        };
    }

    /**
     * 校准游戏时间刻，确保在 [0, 24000] 范围内。
     */
    public static long sanitizeDayTime(Level level) {
        if (level == null) {
            Arc_Quest.LOGGER.warn("[TimeSanitizer] Level is null, returning 0");
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
            Arc_Quest.LOGGER.error("[TimeSanitizer] ⚠️ 时间判断异常！tick={}, morning={}, afternoon={}, night={}, matches={}",
                    t, morning, afternoon, night, matchCount);
        } else {
            Arc_Quest.LOGGER.debug("[TimeSanitizer]  时间判断正常: {}", getTimePeriodDescription(level));
        }
    }
}
