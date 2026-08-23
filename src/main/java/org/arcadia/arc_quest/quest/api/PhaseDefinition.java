package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final List<ResourceLocation> guidesToGrantOnEnter;
    private final List<ResourceLocation> guidesToGrantOnComplete;
    private final List<MarkSpec> relatedMarks;
    private final List<MarkSpec> trackingMarks;
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

    @Deprecated
    PhaseDefinition(String phaseId,
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

    @Deprecated
    PhaseDefinition(String phaseId,
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

    @Deprecated
    PhaseDefinition(String phaseId,
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

    @Deprecated
    PhaseDefinition(String phaseId,
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

    @Deprecated
    PhaseDefinition(String phaseId,
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
        this(phaseId, displayName, description, story, objectives, transitions, choices, phaseRewards,
                flagsToSetOnEnter, flagsToSetOnComplete, relatedMarks, visualConfig, tradeShopId,
                phaseStartSound, phaseCompleteSound, collectionEntryConfig, intelSceneId, enterCondition,
                autoEnterByCondition, autoAdvanceOnComplete, List.of(), List.of());
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
                           boolean autoAdvanceOnComplete,
                           List<ResourceLocation> guidesToGrantOnEnter,
                           List<ResourceLocation> guidesToGrantOnComplete) {
        this(phaseId, displayName, description, story, objectives, transitions, choices, phaseRewards,
                flagsToSetOnEnter, flagsToSetOnComplete, relatedMarks, visualConfig, tradeShopId,
                phaseStartSound, phaseCompleteSound, collectionEntryConfig, intelSceneId, enterCondition,
                autoEnterByCondition, autoAdvanceOnComplete, guidesToGrantOnEnter, guidesToGrantOnComplete,
                List.of());
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
                           boolean autoAdvanceOnComplete,
                           List<ResourceLocation> guidesToGrantOnEnter,
                           List<ResourceLocation> guidesToGrantOnComplete,
                           List<MarkSpec> trackingMarks) {
        Objects.requireNonNull(phaseId);
        Objects.requireNonNull(displayName);
        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("Phase '" + phaseId + "' must have at least one objective");
        }
        this.phaseId = phaseId;
        this.displayName = displayName;
        this.description = description != null ? description : QuestText.component(Component.empty());
        this.story = story != null ? story : QuestText.component(Component.empty());
        this.objectives = Collections.unmodifiableList(normalizeObjectiveIds(objectives));
        this.transitions = Collections.unmodifiableList(transitions);
        this.choices = Collections.unmodifiableList(choices);
        this.phaseRewards = Collections.unmodifiableList(phaseRewards);
        this.flagsToSetOnEnter = Collections.unmodifiableList(flagsToSetOnEnter);
        this.flagsToSetOnComplete = Collections.unmodifiableList(flagsToSetOnComplete);
        this.guidesToGrantOnEnter = List.copyOf(guidesToGrantOnEnter == null ? List.of() : guidesToGrantOnEnter);
        this.guidesToGrantOnComplete = List.copyOf(guidesToGrantOnComplete == null ? List.of() : guidesToGrantOnComplete);
        this.relatedMarks = relatedMarks == null ? List.of() : List.copyOf(relatedMarks);
        this.trackingMarks = trackingMarks == null ? List.of() : List.copyOf(trackingMarks);
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
        return phaseId;
    }

    public Component getDisplayName() {
        return displayName.resolve(null, QuestTextContext.empty());
    }

    public Component getDisplayName(ServerPlayer player, QuestTextContext context) {
        return displayName.resolve(player, context);
    }

    public Component getDescription() {
        return description.resolve(null, QuestTextContext.empty());
    }

    public Component getDescription(ServerPlayer player, QuestTextContext context) {
        return description.resolve(player, context);
    }

    public boolean hasDescription() {
        return !description.resolve(null, QuestTextContext.empty()).getString().isEmpty();
    }

    public Component getStory() {
        return story.resolve(null, QuestTextContext.empty());
    }

    public Component getStory(ServerPlayer player, QuestTextContext context) {
        return story.resolve(player, context);
    }

    public boolean hasStory() {
        return !story.resolve(null, QuestTextContext.empty()).getString().isEmpty();
    }

    public List<ObjectiveEntry> getObjectives() {
        return objectives;
    }

    @Nullable
    public ObjectiveEntry getObjective(String objectiveId) {
        int index = getObjectiveIndex(objectiveId);
        return index >= 0 ? objectives.get(index) : null;
    }

    public int getObjectiveIndex(String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) return -1;
        for (int i = 0; i < objectives.size(); i++) {
            if (objectiveId.equals(objectives.get(i).getObjectiveId())) return i;
        }
        return -1;
    }

    private static List<ObjectiveEntry> normalizeObjectiveIds(List<ObjectiveEntry> source) {
        List<ObjectiveEntry> normalized = new ArrayList<>(source.size());
        HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < source.size(); i++) {
            ObjectiveEntry objective = Objects.requireNonNull(source.get(i), "objective");
            String objectiveId = objective.getObjectiveId();
            if (objectiveId.isBlank()) objectiveId = "objective_" + (i + 1);
            if (!ids.add(objectiveId)) {
                throw new IllegalArgumentException("Duplicate objective id: " + objectiveId);
            }
            normalized.add(objectiveId.equals(objective.getObjectiveId())
                    ? objective
                    : objective.withObjectiveId(objectiveId));
        }
        return normalized;
    }

    public int getRequiredObjectiveCount() {
        int count = 0;
        for (ObjectiveEntry obj : objectives) {
            if (!obj.isOptional()) count++;
        }
        return count;
    }

    public List<PhaseTransition> getTransitions() {
        return transitions;
    }

    public List<ChoiceOption> getChoices() {
        return choices;
    }

    public boolean hasChoices() {
        return !choices.isEmpty();
    }

    public List<IReward> getPhaseRewards() {
        return phaseRewards;
    }

    @Nullable
    public CollectionEntryConfig getCollectionEntryConfig() {
        return collectionEntryConfig;
    }

    public boolean hasCollectionEntryConfig() {
        return collectionEntryConfig != null;
    }

    @Nullable
    public ICondition getEnterCondition() {
        return enterCondition;
    }

    public boolean isAutoEnterByCondition() {
        return autoEnterByCondition;
    }

    public boolean hasEnterCondition() {
        return enterCondition != null;
    }

    public boolean shouldAutoEnterByCondition() {
        return autoEnterByCondition;
    }

    public boolean shouldAutoAdvanceOnComplete() {
        return autoAdvanceOnComplete;
    }

    public List<String> getFlagsToSetOnEnter() {
        return flagsToSetOnEnter;
    }

    public List<String> getFlagsToSetOnComplete() {
        return flagsToSetOnComplete;
    }

    public List<ResourceLocation> getGuidesToGrantOnEnter() {
        return guidesToGrantOnEnter;
    }

    public List<ResourceLocation> getGuidesToGrantOnComplete() {
        return guidesToGrantOnComplete;
    }

    public List<MarkSpec> getRelatedMarks() {
        return relatedMarks;
    }

    public List<MarkSpec> getTrackingMarks() {
        return trackingMarks;
    }

    public QuestVisualConfig getVisualConfig() {
        return visualConfig;
    }

    public int getThemeColor(int parentQuestThemeColor) {
        int phaseColor = visualConfig.getThemeColor();
        return (phaseColor != 0xFFFFFFFF) ? phaseColor : parentQuestThemeColor;
    }

    public Optional<VisualAsset> getSplashConfig(SplashType type) {
        return visualConfig.getSplash(type);
    }

    public Optional<VisualAsset> getIconConfig(IconPosition position) {
        return visualConfig.getIcon(position);
    }

    @Nullable
    public String getTradeShopId() {
        return tradeShopId;
    }

    @Nullable
    public SoundEvent getPhaseStartSound() {
        return phaseStartSound;
    }

    @Nullable
    public SoundEvent getPhaseCompleteSound() {
        return phaseCompleteSound;
    }

    public boolean hasTradeShop() {
        return tradeShopId != null;
    }

    @Nullable
    public ResourceLocation getIntelSceneId() {
        return intelSceneId;
    }

    public boolean hasIntelScene() {
        return intelSceneId != null;
    }

    @Override
    public String toString() {
        return "Phase[" + phaseId + ", " + objectives.size() + " obj]";
    }
}
