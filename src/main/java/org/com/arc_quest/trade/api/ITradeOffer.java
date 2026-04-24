package org.com.arc_quest.trade.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 交易物抽象接口 —— 交易系统的原子单元。
 * <p>
 * 用于描述交易中的"支付"或"获得"操作。不局限于物品，
 * 可以是效果、Flag、命令、经验等任何可量化的游戏资源。
 * <p>
 * 每个交易项 ({@link TradeEntry}) 包含两组 ITradeOffer：
 * <ul>
 *   <li><b>cost</b> — 玩家需要支付的（检查+扣除）</li>
 *   <li><b>reward</b> — 玩家获得的（执行给予）</li>
 * </ul>
 */
public interface ITradeOffer {

    /**
     * 检查玩家是否有足够的资源来支付此交易物。
     * <p>
     * 对于"奖励"侧的 offer，通常返回 true。
     *
     * @param player 服务端玩家
     * @return true = 资源充足
     */
    boolean canAfford(ServerPlayer player);

    /**
     * 执行此交易物的效果。
     * <p>
     * 对于"成本"侧：从玩家扣除资源。
     * 对于"奖励"侧：给予玩家资源。
     *
     * @param player 服务端玩家
     */
    void execute(ServerPlayer player);

    /**
     * 人类可读的描述（用于 UI 显示）。
     * 例如 "橡木原木 x5" 或 "力量 II 60秒"。
     */
    Component describe();

    /**
     * 用于 UI 显示的图标纹理路径。
     * 返回 null 时使用默认图标或物品自身图标。
     */
    @Nullable
    default ResourceLocation getIcon() {
        return null;
    }

    /**
     * 显示数量（用于 UI 右下角的数字角标）。
     * 返回 0 或负数时不显示数字。
     */
    default int getDisplayAmount() {
        return 0;
    }

    /**
     * 构建余额不足时的缺口摘要行。
     * <p>
     * 默认实现仅在无法支付时回退到描述文本，不暴露具体差值。
     */
    default List<CostShortfallLine> buildShortfallLines(ServerPlayer player) {
        if (canAfford(player)) {
            return List.of();
        }
        return List.of(new CostShortfallLine(describe(), 0, 0, 0));
    }

    /**
     * 此交易物的类型标识，用于序列化和日志。
     */
    String getType();
}
