package org.com.arc_quest.quest.api;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 阶段自动跳转规则。
 * <p>
 * 当阶段的所有必需目标完成后，按 priority 升序评估：
 * - 第一个 condition == null 或 condition.test() == true 的分支生效
 * - 如果没有任何分支命中，且没有 choices，则任务整体完成
 */
public final class PhaseTransition implements Comparable<PhaseTransition> {

    private final String targetPhaseId;
    @Nullable
    private final ICondition condition;
    private final int priority;

    public PhaseTransition(String targetPhaseId,
                           @Nullable ICondition condition,
                           int priority) {
        Objects.requireNonNull(targetPhaseId);
        this.targetPhaseId = targetPhaseId;
        this.condition = condition;
        this.priority = priority;
    }

    public String getTargetPhaseId() {
        return this.targetPhaseId;
    }

    @Nullable
    public ICondition getCondition() {
        return this.condition;
    }

    public int getPriority() {
        return this.priority;
    }

    /**
     * 此过渡是否需要玩家手动选择（而非自动跳转）。
     * 如果 condition 为 null，则自动跳转；否则需要满足条件。
     */
    public boolean requiresChoice() {
        return this.condition != null;
    }

    @Override
    public int compareTo(PhaseTransition o) {
        return Integer.compare(this.priority, o.priority);
    }
}
