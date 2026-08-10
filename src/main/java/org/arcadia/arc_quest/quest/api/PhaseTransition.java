package org.arcadia.arc_quest.quest.api;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 阶段自动跳转规则。
 * <p>
 * 当阶段的所有必需目标完成后，按 priority 升序评估：
 * - 第一个 condition == null 或 condition.test() == true 的分支生效
 * - 如果没有任何分支命中，且没有 choices，则任务整体完成
 */
public final class PhaseTransition implements Comparable<PhaseTransition> {

    private final List<String> targetPhaseIds;
    @Nullable
    private final ICondition condition;
    private final int priority;

    public PhaseTransition(String targetPhaseId,
                           @Nullable ICondition condition,
                           int priority) {
        this(List.of(targetPhaseId), condition, priority);
    }

    public PhaseTransition(Collection<String> targetPhaseIds,
                           @Nullable ICondition condition,
                           int priority) {
        Objects.requireNonNull(targetPhaseIds, "targetPhaseIds");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String targetPhaseId : targetPhaseIds) {
            if (targetPhaseId == null || targetPhaseId.isBlank()) {
                throw new IllegalArgumentException("targetPhaseIds must not contain blank values");
            }
            normalized.add(targetPhaseId);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("targetPhaseIds must not be empty");
        }
        this.targetPhaseIds = Collections.unmodifiableList(new ArrayList<>(normalized));
        this.condition = condition;
        this.priority = priority;
    }

    public String getTargetPhaseId() {
        return targetPhaseIds.get(0);
    }

    public List<String> getTargetPhaseIds() {
        return targetPhaseIds;
    }

    public String selectTargetPhaseId(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return targetPhaseIds.get(random.nextInt(targetPhaseIds.size()));
    }

    public String selectTargetPhaseId(RandomSource random) {
        Objects.requireNonNull(random, "random");
        return targetPhaseIds.get(random.nextInt(targetPhaseIds.size()));
    }

    public String selectTargetPhaseId(long seed) {
        return selectTargetPhaseId(RandomSource.create(seed));
    }

    @Nullable
    public ICondition getCondition() {
        return condition;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 此过渡是否需要玩家手动选择（而非自动跳转）。
     * 如果 condition 为 null，则自动跳转；否则需要满足条件。
     */
    public boolean requiresChoice() {
        return condition != null;
    }

    @Override
    public int compareTo(PhaseTransition o) {
        return Integer.compare(priority, o.priority);
    }
}
