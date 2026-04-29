package org.arcadia.arc_quest.dialogue.api;

/**
 * 对话冷却类型。
 */
public enum CooldownType {
    /**
     * 无冷却
     */
    NONE,

    /**
     * 基于现实时间的秒级冷却
     */
    SECONDS,

    /**
     * 基于游戏日的冷却（每天一次，在游戏日切换时重置）
     * <p>
     * 默认在 Minecraft 一天的开始（tick 0）重置。
     * 可通过 {@link DialogueNode#resetTimeTicks()} 自定义重置时间点。
     */
    GAME_DAY,

    /**
     * 基于游戏日内固定时间刻的冷却
     * <p>
     * 例如：设置 resetTimeTicks=1000（早上6点），则每天早上7点重置冷却。
     * 适用于需要精确控制刷新时间的场景。
     */
    GAME_TICK
}
