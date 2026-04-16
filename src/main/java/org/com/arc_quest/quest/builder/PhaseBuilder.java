package org.com.arc_quest.quest.builder;

import net.minecraft.network.chat.Component;
import org.com.arc_quest.quest.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 流式构建 PhaseDefinition。
 *
 * <pre>
 *   PhaseBuilder.create("phase_hunt")
 *       .displayName("消灭怪物")
 *       .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3))
 *       .thenGoTo("phase_return")
 *       .build();
 * </pre>
 */
public final class PhaseBuilder {

    private final String phaseId;
    private Component displayName;
    private final List<ObjectiveEntry> objectives = new ArrayList<>();
    private final List<PhaseTransition> transitions = new ArrayList<>();
    private final List<ChoiceOption> choices = new ArrayList<>();
    private final List<IReward> phaseRewards = new ArrayList<>();
    private final List<String> flagsOnEnter = new ArrayList<>();
    private final List<String> flagsOnComplete = new ArrayList<>();

    private int transitionPriorityCounter = 0;

    private PhaseBuilder(String phaseId) {
        Objects.requireNonNull(phaseId);
        this.phaseId = phaseId;
    }

    public static PhaseBuilder create(String phaseId) {
        return new PhaseBuilder(phaseId);
    }

    // ── 显示名称 ──

    public PhaseBuilder displayName(String literal) {
        this.displayName = Component.literal(literal);
        return this;
    }

    public PhaseBuilder displayName(Component component) {
        this.displayName = component;
        return this;
    }

    // ── 目标 ──

    /**
     * 添加一个目标（传入 ObjectiveBuilder，自动 build）
     */
    public PhaseBuilder objective(ObjectiveBuilder objectiveBuilder) {
        this.objectives.add(objectiveBuilder.build());
        return this;
    }

    /**
     * 添加一个已构建好的 ObjectiveEntry
     */
    public PhaseBuilder objective(ObjectiveEntry entry) {
        this.objectives.add(entry);
        return this;
    }

    // ── 跳转 ──

    /**
     * 无条件跳转到指定阶段（默认分支）
     */
    public PhaseBuilder thenGoTo(String targetPhaseId) {
        this.transitions.add(new PhaseTransition(
                targetPhaseId, null, this.transitionPriorityCounter++));
        return this;
    }

    /**
     * 有条件跳转（优先于无条件分支）
     */
    public PhaseBuilder thenGoToIf(String targetPhaseId, ICondition condition) {
        this.transitions.add(new PhaseTransition(
                targetPhaseId, condition, this.transitionPriorityCounter++));
        return this;
    }

    // ── 选择分支（对话选项） ──

    /**
     * 添加玩家可见的选择项
     */
    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId) {
        this.choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, null));
        return this;
    }

    /**
     * 添加带可见条件的选择项
     */
    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId,
                               ICondition visibleCondition) {
        this.choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, visibleCondition));
        return this;
    }

    // ── 奖励 ──

    public PhaseBuilder reward(IReward reward) {
        this.phaseRewards.add(reward);
        return this;
    }

    // ── Flags ──

    public PhaseBuilder setFlagOnEnter(String flag) {
        this.flagsOnEnter.add(flag);
        return this;
    }

    public PhaseBuilder setFlagOnComplete(String flag) {
        this.flagsOnComplete.add(flag);
        return this;
    }

    // ── 构建 ──

    public PhaseDefinition build() {
        if (this.displayName == null) {
            this.displayName = Component.literal(this.phaseId);
        }
        if (this.objectives.isEmpty()) {
            throw new IllegalStateException(
                    "Phase '" + this.phaseId + "' has no objectives defined");
        }
        return new PhaseDefinition(
                this.phaseId,
                this.displayName,
                new ArrayList<>(this.objectives),
                new ArrayList<>(this.transitions),
                new ArrayList<>(this.choices),
                new ArrayList<>(this.phaseRewards),
                new ArrayList<>(this.flagsOnEnter),
                new ArrayList<>(this.flagsOnComplete)
        );
    }
}