package org.com.arc_quest.quest.api;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 不可变阶段定义，任务由有序 Phase 构成。
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
    @org.jetbrains.annotations.Nullable
    private final String tradeShopId;

    public PhaseDefinition(String phaseId,
                           Component displayName,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig) {
        this(phaseId, displayName, objectives, transitions, choices, phaseRewards,
                flagsToSetOnEnter, flagsToSetOnComplete, visualConfig, null);
    }

    public PhaseDefinition(String phaseId,
                           Component displayName,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @org.jetbrains.annotations.Nullable String tradeShopId) {
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
        this.tradeShopId = tradeShopId;
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


    public int getRequiredObjectiveCount() {
        int count = 0;
        for (ObjectiveEntry obj : this.objectives) {
            if (!obj.isOptional()) count++;
        }
        return count;
    }


    public List<PhaseTransition> getTransitions() {
        return this.transitions;
    }


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


    public QuestVisualConfig getVisualConfig() {
        return this.visualConfig;
    }


    public int getThemeColor(int parentQuestThemeColor) {
        int phaseColor = this.visualConfig.getThemeColor();
        return (phaseColor != 0xFFFFFFFF) ? phaseColor : parentQuestThemeColor;
    }


    public Optional<VisualAsset> getSplashConfig(SplashType type) {
        return this.visualConfig.getSplash(type);
    }


    public Optional<VisualAsset> getIconConfig(IconPosition position) {
        return this.visualConfig.getIcon(position);
    }

    @org.jetbrains.annotations.Nullable
    public String getTradeShopId() {
        return this.tradeShopId;
    }

    public boolean hasTradeShop() {
        return this.tradeShopId != null;
    }

    @Override
    public String toString() {
        return "Phase[" + this.phaseId + ", " + this.objectives.size() + " obj]";
    }
}