package org.arcadia.arc_quest.quest.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.reward.QuestRewardText;

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

    /**
     * 客户端展示组件。内置奖励保留翻译键；自定义奖励可以重写此方法提供翻译、参数和样式。
     * {@link #describe()} 继续保留原有诊断描述，不随玩家语言改变。
     */
    default Component describeComponent() {
        return QuestRewardText.describe(this);
    }
}
