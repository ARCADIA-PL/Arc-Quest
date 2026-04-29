package org.arcadia.arc_quest.dialogue.runtime;

/**
 * 三时钟冷却快照接口。
 * <p>
 * 统一 {@link DialogueProgressStore.Entry}
 * 和 {@link org.arcadia.arc_quest.quest.capability.GachaDataStore.CooldownEntry}
 * 以及 {@link org.arcadia.arc_quest.quest.capability.TradeDataStore.TradeCooldownEntry}
 * 的公共访问契约，使 {@link UnifiedCooldownManager} 无需为每种记录类型单独重载。
 */
public interface ICooldownRecord {
    /** 真实时间戳（毫秒），来自 {@code System.currentTimeMillis()}。 */
    long realTime();
    /** 游戏总刻数（单调，不受 /time set 影响）。 */
    long gameTime();
    /** 当日刻数 [0, 24000]（受 /time set 影响）。 */
    long dayTime();
    /** 是否有有效记录（realTime &gt; 0）。 */
    boolean exists();
}
