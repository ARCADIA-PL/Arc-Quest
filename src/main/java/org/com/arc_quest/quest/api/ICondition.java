package org.com.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import java.util.Map;

/**
 * 通用条件接口。
 * <p>
 * 用于：
 * - 任务解锁前置 (unlockConditions)
 * - 阶段跳转分支 (PhaseTransition.condition)
 * - 对话选项显示条件 (ChoiceOption.visibleCondition)
 * <p>
 * 所有参数都从玩家的运行时存档中读取（Phase 2 的 Capability），
 * 此处的签名抽象为"只读快照"参数。
 */
@FunctionalInterface
public interface ICondition {

    /**
     * @param completedQuests 玩家已完成的任务 ID 集合
     * @param flags           玩家已设置的全局 Flag 集合
     * @param variables       玩家全局变量表 (name → value)
     * @return true = 条件满足
     */
    boolean test(Set<ResourceLocation> completedQuests,
                 Set<String> flags,
                 Map<String, Integer> variables);

    /**
     * 人类可读的描述（用于调试日志 / UI 提示）
     */
    default String describe() {
        return getClass().getSimpleName();
    }

    // ── 组合器 ──

    default ICondition and(ICondition other) {
        ICondition self = this;
        return new ICondition() {
            @Override
            public boolean test(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return self.test(cq, f, v) && other.test(cq, f, v);
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
            public boolean test(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return self.test(cq, f, v) || other.test(cq, f, v);
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
            public boolean test(Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return !self.test(cq, f, v);
            }

            @Override
            public String describe() {
                return "NOT(" + self.describe() + ")";
            }
        };
    }

    /** 永远为 true 的条件（无前置） */
    static ICondition always() {
        return (cq, f, v) -> true;
    }
}