package org.com.arc_quest.quest.api;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 不可变阶段定义。一个任务由有序的 Phase 构成线性/分支链。
 */
public final class PhaseDefinition {

    private final String phaseId;
    private final Component displayName;
    private final List<ObjectiveEntry> objectives;
    private final List<PhaseTransition> transitions;
    private final List<ChoiceOption> choices;
    private final List<IReward> phaseRewards;
    private final List<String> flagsToSetOnEnter;
    private final List<String> flagsToSetOnComplete;
    private final QuestVisualConfig visualConfig;

    public PhaseDefinition(String phaseId,
                           Component displayName,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig) {
        Objects.requireNonNull(phaseId);
        Objects.requireNonNull(displayName);
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("Phase '" + phaseId + "' must have at least one objective");
        }
        this.phaseId = phaseId;
        this.displayName = displayName;
        this.objectives = Collections.unmodifiableList(objectives);
        this.transitions = Collections.unmodifiableList(transitions);
        this.choices = Collections.unmodifiableList(choices);
        this.phaseRewards = Collections.unmodifiableList(phaseRewards);
        this.flagsToSetOnEnter = Collections.unmodifiableList(flagsToSetOnEnter);
        this.flagsToSetOnComplete = Collections.unmodifiableList(flagsToSetOnComplete);
        this.visualConfig = visualConfig != null ? visualConfig : QuestVisualConfig.EMPTY;
    }

    public String getPhaseId() {
        return this.phaseId;
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public List<ObjectiveEntry> getObjectives() {
        return this.objectives;
    }

    /**
     * 计算必须完成的（非 optional）目标数量
     */
    public int getRequiredObjectiveCount() {
        int count = 0;
        for (ObjectiveEntry obj : this.objectives) {
            if (!obj.isOptional()) count++;
        }
        return count;
    }

    /**
     * 自动跳转规则（按 priority 排序）
     */
    public List<PhaseTransition> getTransitions() {
        return this.transitions;
    }

    /**
     * 玩家选择分支（如果非空，则阶段完成后弹出选择 UI，而非自动跳转）
     */
    public List<ChoiceOption> getChoices() {
        return this.choices;
    }

    public boolean hasChoices() {
        return !this.choices.isEmpty();
    }

    public List<IReward> getPhaseRewards() {
        return this.phaseRewards;
    }

    public List<String> getFlagsToSetOnEnter() {
        return this.flagsToSetOnEnter;
    }

    public List<String> getFlagsToSetOnComplete() {
        return this.flagsToSetOnComplete;
    }

    /**
     * 获取视觉配置。
     */
    public QuestVisualConfig getVisualConfig() {
        return this.visualConfig;
    }

    /**
     * 获取阶段的主题色（回退到父任务的主题色）。
     *
     * @param parentQuestThemeColor 父任务的主题色
     * @return 阶段主题色，如果未设置则返回父任务主题色
     */
    public int getThemeColor(int parentQuestThemeColor) {
        int phaseColor = this.visualConfig.getThemeColor();
        return (phaseColor != 0xFFFFFFFF) ? phaseColor : parentQuestThemeColor;
    }

    /**
     * 获取阶段的立绘配置。
     */
    public java.util.Optional<VisualAsset> getSplashConfig(SplashType type) {
        return this.visualConfig.getSplash(type);
    }

    /**
     * 获取阶段的图标配置。
     */
    public java.util.Optional<VisualAsset> getIconConfig(IconPosition position) {
        return this.visualConfig.getIcon(position);
    }

    @Override
    public String toString() {
        return "Phase[" + this.phaseId + ", " + this.objectives.size() + " obj]";
    }
}