package org.com.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;
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
    private final Component description;
    private final List<ObjectiveEntry> objectives;
    private final List<PhaseTransition> transitions;
    private final List<ChoiceOption> choices;
    private final List<IReward> phaseRewards;
    private final List<String> flagsToSetOnEnter;
    private final List<String> flagsToSetOnComplete;
    private final QuestVisualConfig visualConfig;
    @Nullable
    private final String tradeShopId;
    @Nullable
    private final SoundEvent phaseStartSound;
    @Nullable
    private final SoundEvent phaseCompleteSound;
    /** 该阶段关联的 Ponder 情报场景 ID，为 null 表示不显示 Intel 按钮。 */
    @Nullable
    private final ResourceLocation intelSceneId;

    public PhaseDefinition(String phaseId,
                           Component displayName,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig) {
        this(phaseId, displayName, Component.empty(), objectives, transitions, choices,
                phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete, visualConfig, null, null, null, null);
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
                           @Nullable String tradeShopId) {
        this(phaseId, displayName, Component.empty(), objectives, transitions, choices,
                phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete, visualConfig, tradeShopId, null, null, null);
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
                           @Nullable String tradeShopId,
                           @Nullable SoundEvent phaseStartSound,
                           @Nullable SoundEvent phaseCompleteSound) {
        this(phaseId, displayName, Component.empty(), objectives, transitions, choices,
                phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete, visualConfig,
                tradeShopId, phaseStartSound, phaseCompleteSound, null);
    }

    /** 旧的完整构造器（含 description），委托给带 intelSceneId 的新构造器。 */
    public PhaseDefinition(String phaseId,
                           Component displayName,
                           Component description,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @Nullable String tradeShopId,
                           @Nullable SoundEvent phaseStartSound,
                           @Nullable SoundEvent phaseCompleteSound) {
        this(phaseId, displayName, description, objectives, transitions, choices,
                phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete, visualConfig,
                tradeShopId, phaseStartSound, phaseCompleteSound, null);
    }

    /** 含 intelSceneId 的最终构造器，所有其他构造器最终委托至此。 */
    public PhaseDefinition(String phaseId,
                           Component displayName,
                           Component description,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @Nullable String tradeShopId,
                           @Nullable SoundEvent phaseStartSound,
                           @Nullable SoundEvent phaseCompleteSound,
                           @Nullable ResourceLocation intelSceneId) {
        Objects.requireNonNull(phaseId);
        Objects.requireNonNull(displayName);
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("Phase '" + phaseId + "' must have at least one objective");
        }
        this.phaseId = phaseId;
        this.displayName = displayName;
        this.description = description != null ? description : Component.empty();
        this.objectives = Collections.unmodifiableList(objectives);
        this.transitions = Collections.unmodifiableList(transitions);
        this.choices = Collections.unmodifiableList(choices);
        this.phaseRewards = Collections.unmodifiableList(phaseRewards);
        this.flagsToSetOnEnter = Collections.unmodifiableList(flagsToSetOnEnter);
        this.flagsToSetOnComplete = Collections.unmodifiableList(flagsToSetOnComplete);
        this.visualConfig = visualConfig != null ? visualConfig : QuestVisualConfig.EMPTY;
        this.tradeShopId = tradeShopId;
        this.phaseStartSound = phaseStartSound;
        this.phaseCompleteSound = phaseCompleteSound;
        this.intelSceneId = intelSceneId;
    }

    public String getPhaseId() { return this.phaseId; }

    public Component getDisplayName() { return this.displayName; }

    public Component getDescription() { return this.description; }

    public boolean hasDescription() {
        return !this.description.getString().isEmpty();
    }

    public List<ObjectiveEntry> getObjectives() { return this.objectives; }

    public int getRequiredObjectiveCount() {
        int count = 0;
        for (ObjectiveEntry obj : this.objectives) {
            if (!obj.isOptional()) count++;
        }
        return count;
    }

    public List<PhaseTransition> getTransitions() { return this.transitions; }

    public List<ChoiceOption> getChoices() { return this.choices; }

    public boolean hasChoices() { return !this.choices.isEmpty(); }

    public List<IReward> getPhaseRewards() { return this.phaseRewards; }

    public List<String> getFlagsToSetOnEnter() { return this.flagsToSetOnEnter; }

    public List<String> getFlagsToSetOnComplete() { return this.flagsToSetOnComplete; }

    public QuestVisualConfig getVisualConfig() { return this.visualConfig; }

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

    @Nullable
    public String getTradeShopId() { return this.tradeShopId; }

    @Nullable
    public SoundEvent getPhaseStartSound() { return phaseStartSound; }

    @Nullable
    public SoundEvent getPhaseCompleteSound() { return phaseCompleteSound; }

    public boolean hasTradeShop() { return this.tradeShopId != null; }

    @Nullable
    public ResourceLocation getIntelSceneId() { return this.intelSceneId; }

    public boolean hasIntelScene() { return this.intelSceneId != null; }

    @Override
    public String toString() {
        return "Phase[" + this.phaseId + ", " + this.objectives.size() + " obj]";
    }
}
