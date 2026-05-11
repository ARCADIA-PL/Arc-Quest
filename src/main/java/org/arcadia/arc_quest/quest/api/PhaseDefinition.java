package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;

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
    private final QuestText displayName;
    private final QuestText description;
    private final QuestText story;
    private final List<ObjectiveEntry> objectives;
    private final List<PhaseTransition> transitions;
    private final List<ChoiceOption> choices;
    private final List<IReward> phaseRewards;
    private final List<String> flagsToSetOnEnter;
    private final List<String> flagsToSetOnComplete;
    private final List<MarkSpec> relatedMarks;
    private final QuestVisualConfig visualConfig;
    @Nullable
    private final ICondition enterCondition;
    private final boolean autoEnterByCondition;
    private final boolean autoAdvanceOnComplete;
    @Nullable
    private final String tradeShopId;
    @Nullable
    private final SoundEvent phaseStartSound;
    @Nullable
    private final SoundEvent phaseCompleteSound;
    @Nullable
    private final CollectionEntryConfig collectionEntryConfig;
    /**
     * 该阶段关联的 Ponder 情报场景 ID，为 null 表示不显示 Intel 按钮。
     */
    @Nullable
    private final ResourceLocation intelSceneId;

    public PhaseDefinition(String phaseId,
                           QuestText displayName,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig, boolean autoEnterByCondition) {
        this(phaseId, displayName, QuestText.component(Component.empty()), QuestText.component(Component.empty()),
                objectives, transitions, choices, phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete,
                List.of(), visualConfig, null, null, null, null, null, null, autoEnterByCondition, true);
    }

    public PhaseDefinition(String phaseId,
                           QuestText displayName,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @Nullable String tradeShopId, boolean autoEnterByCondition) {
        this(phaseId, displayName, QuestText.component(Component.empty()), QuestText.component(Component.empty()),
                objectives, transitions, choices, phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete,
                List.of(), visualConfig, tradeShopId, null, null, null, null, null, autoEnterByCondition, true);
    }

    public PhaseDefinition(String phaseId,
                           QuestText displayName,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @Nullable String tradeShopId,
                           @Nullable SoundEvent phaseStartSound,
                           @Nullable SoundEvent phaseCompleteSound, boolean autoEnterByCondition) {
        this(phaseId, displayName, QuestText.component(Component.empty()), QuestText.component(Component.empty()),
                objectives, transitions, choices, phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete,
                List.of(), visualConfig, tradeShopId, phaseStartSound, phaseCompleteSound, null, null, null, autoEnterByCondition, true);
    }

    /**
     * 旧的完整构造器（含 description），委托给带 story 的新构造器。
     */
    public PhaseDefinition(String phaseId,
                           QuestText displayName,
                           QuestText description,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @Nullable String tradeShopId,
                           @Nullable SoundEvent phaseStartSound,
                           @Nullable SoundEvent phaseCompleteSound, boolean autoEnterByCondition) {
        this(phaseId, displayName, description, QuestText.component(Component.empty()),
                objectives, transitions, choices, phaseRewards, flagsToSetOnEnter, flagsToSetOnComplete,
                List.of(), visualConfig, tradeShopId, phaseStartSound, phaseCompleteSound, null, null, null, autoEnterByCondition, true);
    }

    /**
     * 含 story 和 intelSceneId 的最终构造器，所有其他构造器最终委托至此。
     */
    public PhaseDefinition(String phaseId,
                           QuestText displayName,
                           QuestText description,
                           QuestText story,
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
                           @Nullable CollectionEntryConfig collectionEntryConfig,
                           @Nullable ResourceLocation intelSceneId,
                           @Nullable ICondition enterCondition, boolean autoEnterByCondition) {
        this(phaseId, displayName, description, story, objectives, transitions, choices, phaseRewards,
                flagsToSetOnEnter, flagsToSetOnComplete, List.of(), visualConfig,
                tradeShopId, phaseStartSound, phaseCompleteSound, collectionEntryConfig, intelSceneId, enterCondition, autoEnterByCondition, true);
    }

    public PhaseDefinition(String phaseId,
                           QuestText displayName,
                           QuestText description,
                           QuestText story,
                           List<ObjectiveEntry> objectives,
                           List<PhaseTransition> transitions,
                           List<ChoiceOption> choices,
                           List<IReward> phaseRewards,
                           List<String> flagsToSetOnEnter,
                           List<String> flagsToSetOnComplete,
                           List<MarkSpec> relatedMarks,
                           QuestVisualConfig visualConfig,
                           @Nullable String tradeShopId,
                           @Nullable SoundEvent phaseStartSound,
                           @Nullable SoundEvent phaseCompleteSound,
                           @Nullable CollectionEntryConfig collectionEntryConfig,
                           @Nullable ResourceLocation intelSceneId,
                           @Nullable ICondition enterCondition,
                           boolean autoEnterByCondition,
                           boolean autoAdvanceOnComplete) {
        Objects.requireNonNull(phaseId);
        Objects.requireNonNull(displayName);
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("Phase '" + phaseId + "' must have at least one objective");
        }
        this.phaseId = phaseId;
        this.displayName = displayName;
        this.description = description != null ? description : QuestText.component(Component.empty());
        this.story = story != null ? story : QuestText.component(Component.empty());
        this.objectives = Collections.unmodifiableList(objectives);
        this.transitions = Collections.unmodifiableList(transitions);
        this.choices = Collections.unmodifiableList(choices);
        this.phaseRewards = Collections.unmodifiableList(phaseRewards);
        this.flagsToSetOnEnter = Collections.unmodifiableList(flagsToSetOnEnter);
        this.flagsToSetOnComplete = Collections.unmodifiableList(flagsToSetOnComplete);
        this.relatedMarks = relatedMarks == null ? List.of() : List.copyOf(relatedMarks);
        this.visualConfig = visualConfig != null ? visualConfig : QuestVisualConfig.EMPTY;
        this.tradeShopId = tradeShopId;
        this.phaseStartSound = phaseStartSound;
        this.phaseCompleteSound = phaseCompleteSound;
        this.collectionEntryConfig = collectionEntryConfig;
        this.intelSceneId = intelSceneId;
        this.enterCondition = enterCondition;
        this.autoEnterByCondition = autoEnterByCondition;
        this.autoAdvanceOnComplete = autoAdvanceOnComplete;
    }

    public String getPhaseId() {
        return this.phaseId;
    }

    public Component getDisplayName() {
        return this.displayName.resolve(null, QuestTextContext.empty());
    }

    public Component getDisplayName(ServerPlayer player, QuestTextContext context) {
        return this.displayName.resolve(player, context);
    }

    public Component getDescription() {
        return this.description.resolve(null, QuestTextContext.empty());
    }

    public Component getDescription(ServerPlayer player, QuestTextContext context) {
        return this.description.resolve(player, context);
    }

    public boolean hasDescription() {
        return !this.description.resolve(null, QuestTextContext.empty()).getString().isEmpty();
    }

    public Component getStory() {
        return this.story.resolve(null, QuestTextContext.empty());
    }

    public Component getStory(ServerPlayer player, QuestTextContext context) {
        return this.story.resolve(player, context);
    }

    public boolean hasStory() {
        return !this.story.resolve(null, QuestTextContext.empty()).getString().isEmpty();
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

    @Nullable
    public CollectionEntryConfig getCollectionEntryConfig() {
        return this.collectionEntryConfig;
    }

    public boolean hasCollectionEntryConfig() {
        return this.collectionEntryConfig != null;
    }

    @Nullable
    public ICondition getEnterCondition() {
        return enterCondition;
    }

    public boolean isAutoEnterByCondition() {
        return autoEnterByCondition;
    }

    public boolean hasEnterCondition() {
        return this.enterCondition != null;
    }

    public boolean shouldAutoEnterByCondition() {
        return autoEnterByCondition;
    }

    public boolean shouldAutoAdvanceOnComplete() {
        return autoAdvanceOnComplete;
    }

    public List<String> getFlagsToSetOnEnter() {
        return this.flagsToSetOnEnter;
    }

    public List<String> getFlagsToSetOnComplete() {
        return this.flagsToSetOnComplete;
    }

    public List<MarkSpec> getRelatedMarks() {
        return relatedMarks;
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

    @Nullable
    public String getTradeShopId() {
        return this.tradeShopId;
    }

    @Nullable
    public SoundEvent getPhaseStartSound() {
        return this.phaseStartSound;
    }

    @Nullable
    public SoundEvent getPhaseCompleteSound() {
        return this.phaseCompleteSound;
    }

    public boolean hasTradeShop() {
        return this.tradeShopId != null;
    }

    @Nullable
    public ResourceLocation getIntelSceneId() {
        return this.intelSceneId;
    }

    public boolean hasIntelScene() {
        return this.intelSceneId != null;
    }

    @Override
    public String toString() {
        return "Phase[" + this.phaseId + ", " + this.objectives.size() + " obj]";
    }
}
