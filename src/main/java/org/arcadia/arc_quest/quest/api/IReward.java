package org.arcadia.arc_quest.quest.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * 奖励接口。在服务端执行。
 */
public interface IReward {

    /**
     * 发放奖励给玩家。
     *
     * @param player 服务端玩家实例
     */
    void grant(ServerPlayer player);

    /**
     * 人类可读描述（用于 UI 显示 / 日志）
     */
    String describe();
}