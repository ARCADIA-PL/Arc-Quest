package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.condition.CoreCondition;
import org.arcadia.arc_quest.dialogue.api.DialogueCondition;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.List;
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
 * 所有参数都从玩家的运行时存档中读取，
 * 此处的签名抽象为“只读快照”参数 + ServerPlayer 对象。
 * <p>
 * <b>重要：根据使用场景选择合适的方法：</b>
 * <ul>
 *   <li>服务端逻辑：使用 {@link #testServer(ServerPlayer, Set, Set, Map)}</li>
 *   <li>客户端GUI：使用 {@link #testClient(Set, Set, Map)}</li>
 * </ul>
 */
@FunctionalInterface
public interface ICondition extends CoreCondition<QuestConditionContext> {

    /**
     * 永远为 true 的条件（无前置）
     */
    static ICondition always() {
        return (player, cq, f, v) -> true;
    }

    /**
     * 条件：指定 Flag 已设置。
     * <p>推荐替代 {@code new FlagSetCondition(flag)}。
     */
    static ICondition flagSet(String flag) {
        return (player, cq, f, v) -> f.contains(flag);
    }

    /**
     * 条件：指定 Flag 未设置。
     * <p>推荐替代 {@code new FlagNotSetCondition(flag)}。
     */
    static ICondition flagNotSet(String flag) {
        return (player, cq, f, v) -> !f.contains(flag);
    }

    /**
     * 条件：指定任务已完成。
     * <p>推荐替代 {@code new QuestCompletedCondition(questId)}。
     */
    static ICondition questCompleted(ResourceLocation questId) {
        return new WithRequiredQuest() {
            @Override
            public boolean test(@Nullable ServerPlayer player, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return cq.contains(questId);
            }

            @Override
            public ResourceLocation getRequiredQuestId() {
                return questId;
            }
        };
    }

    /**
     * 条件：全局变量满足数值比较。
     * <p>推荐替代 {@code new VariableCondition(name, op, value)}。
     *
     * @param name  变量名
     * @param op    比较操作符
     * @param value 比较目标值
     */
    static ICondition variable(String name, CompareOp op, int value) {
        return (player, cq, f, v) -> op.evaluate(v.getOrDefault(name, 0), value);
    }

    /**
     * 将此 {@link ICondition} 包装为 {@link DialogueCondition}，
     * 使商店/任务条件可以直接用于对话选项的可见性判断。
     * <p>
     * 使用示例：
     * <pre>{@code
     * DialogueChoice.conditional("choice_id", "购买剑", "node_shop",
     *     myCanBuyCondition.asDialogueCondition())
     * }</pre>
     */
    default DialogueCondition asDialogueCondition() {
        return new DialogueCondition.IConditionWrapper(this);
    }

    /**
     * 测试条件是否满足。
     * <p>
     * <b>重要：</b>
     * <ul>
     *   <li>服务端逻辑：直接调用此方法，传入非 null 的 player</li>
     *   <li>客户端GUI：使用 {@link #testClient(Set, Set, Map)}</li>
     * </ul>
     *
     * @param player          当前玩家对象（服务端不可为 null，客户端传 null）
     * @param completedQuests 玩家已完成的任务 ID 集合
     * @param flags           玩家已设置的全局 Flag 集合
     * @param variables       玩家全局变量表 (name → value)
     * @return true = 条件满足
     */
    boolean test(@Nullable ServerPlayer player,
                 Set<ResourceLocation> completedQuests,
                 Set<String> flags,
                 Map<String, Integer> variables);

    @Override
    default boolean evaluate(QuestConditionContext context) {
        return test(context.player(), context.completedQuests(), context.flags(), context.variables());
    }

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
        return test(null, completedQuests, flags, variables);
    }

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
            public boolean test(@Nullable ServerPlayer serverPlayer, Set<ResourceLocation> cq, Set<String> f, Map<String, Integer> v) {
                return CoreProcessors.get().conditions().all(
                        List.of(self, other), new QuestConditionContext(serverPlayer, cq, f, v));
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
                return CoreProcessors.get().conditions().any(
                        List.of(self, other), new QuestConditionContext(serverPlayer, cq, f, v));
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
                return CoreProcessors.get().conditions().none(
                        self, new QuestConditionContext(serverPlayer, cq, f, v));
            }

            @Override
            public String describe() {
                return "NOT(" + self.describe() + ")";
            }
        };
    }

    /**
     * 允许注册表在冻结时对 questCompleted 条件做交叉引用验证。
     */
    interface WithRequiredQuest extends ICondition {
        ResourceLocation getRequiredQuestId();
    }
}
