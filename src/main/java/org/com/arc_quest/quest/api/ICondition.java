package org.com.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * 通用条件接口。
 * <p>
 * 用于：
 * - 任务解锁前置 (unlockConditions)
 * - 阶段跳转分支 (PhaseTransition.condition)
 * - 对话选项显示条件 (ChoiceOption.visibleCondition)
 * - 交易商品可见性条件 (TradeEntry.condition)
 * <p>
 * 所有参数都从玩家的运行时存档中读取（Phase 2 的 Capability），
 * 此处的签名抽象为"只读快照"参数 + ServerPlayer 对象。
 */
@FunctionalInterface
public interface ICondition {

    /**
     * 永远为 true 的条件（无前置）
     */
    static ICondition always() {
        return (player, cq, f, v) -> true;
    }

    /**
     * 测试条件是否满足（服务端完整版本）
     *
     * @param serverPlayer          当前玩家对象（可访问位置、维度、状态等实时信息）
     *                              <b>注意：客户端环境下可能为 null，自定义条件需做空值检查</b>
     * @param completedQuests 玩家已完成的任务 ID 集合
     * @param flags           玩家已设置的全局 Flag 集合
     * @param variables       玩家全局变量表 (name → value)
     * @return true = 条件满足
     */
    boolean test(@Nullable ServerPlayer serverPlayer,
                 Set<ResourceLocation> completedQuests,
                 Set<String> flags,
                 Map<String, Integer> variables);

    /**
     * 测试条件是否满足（客户端简化版本）
     * <p>
     * 专用于客户端 GUI 渲染等场景，此时无法获取 ServerPlayer 实例。
     * 此方法会自动传递 null 给 {@link #test(ServerPlayer, Set, Set, Map)}。
     * <p>
     * <b>使用建议：</b>
     * - 客户端代码优先使用此方法，避免手动传递 null
     * - 自定义条件如需支持客户端环境，应在 test() 中处理 player == null 的情况
     *
     * @param completedQuests 玩家已完成的任务 ID 集合（来自 ClientQuestCache）
     * @param flags           玩家已设置的全局 Flag 集合（来自 ClientQuestCache）
     * @param variables       玩家全局变量表（来自 ClientQuestCache）
     * @return true = 条件满足
     */
    default boolean testClient(Set<ResourceLocation> completedQuests,
                               Set<String> flags,
                               Map<String, Integer> variables) {
        return this.test(null, completedQuests, flags, variables);
    }

    // ── 组合器 ──

    /**
     * 人类可读的描述（用于调试日志 / UI 提示）
     */
    default String describe() {
        return getClass().getSimpleName();
    }

    default ICondition and(ICondition other) {
        ICondition self = this;
        return new ICondition() {
            @Override
            public boolean test(@Nullable ServerPlayer serverPlayer, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return self.test(serverPlayer, cq, f, v) && other.test(serverPlayer, cq, f, v);
            }

            @Override
            public String describe() {
                return "(" + self.describe() + " AND " + other.describe() + ")";
            }
        };
    }

    default ICondition or(ICondition other) {
        ICondition self = this;
        return new ICondition() {
            @Override
            public boolean test(@Nullable ServerPlayer serverPlayer, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return self.test(serverPlayer, cq, f, v) || other.test(serverPlayer, cq, f, v);
            }

            @Override
            public String describe() {
                return "(" + self.describe() + " OR " + other.describe() + ")";
            }
        };
    }

    default ICondition negate() {
        ICondition self = this;
        return new ICondition() {
            @Override
            public boolean test(@Nullable ServerPlayer serverPlayer, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return !self.test(serverPlayer, cq, f, v);
            }

            @Override
            public String describe() {
                return "NOT(" + self.describe() + ")";
            }
        };
    }
}