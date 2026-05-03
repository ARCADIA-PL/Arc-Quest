package org.arcadia.arc_quest.quest.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.client.ponder.ArcQuestPonderHelper;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.questmarker.api.MarkActivation;
import org.arcadia.arc_quest.questmarker.api.MarkActivations;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final List<ObjectiveEntry> objectives = new ArrayList<>();
    private final List<PhaseTransition> transitions = new ArrayList<>();
    private final List<ChoiceOption> choices = new ArrayList<>();
    private final List<IReward> phaseRewards = new ArrayList<>();
    private final List<String> flagsOnEnter = new ArrayList<>();
    private final List<String> flagsOnComplete = new ArrayList<>();
    private final List<MarkSpec> relatedMarks = new ArrayList<>();
    private QuestText displayName;
    private QuestText description = QuestText.component(Component.empty());
    private QuestText story = QuestText.component(Component.empty());
    private int transitionPriorityCounter = 0;
    private QuestVisualConfig.Builder visualConfigBuilder = QuestVisualConfig.builder();
    @Nullable
    private CollectionEntryConfig collectionEntryConfig = null;
    private String tradeShopId = null;
    @Nullable
    private ICondition enterCondition = null;
    private boolean autoEnterByCondition = true;

    @Nullable
    private SoundEvent phaseStartSound;
    @Nullable
    private SoundEvent phaseCompleteSound;

    @Nullable
    private ResourceLocation intelSceneId = null;

    private PhaseBuilder(String phaseId) {
        Objects.requireNonNull(phaseId);
        this.phaseId = phaseId;
    }

    public static PhaseBuilder create(String phaseId) {
        return new PhaseBuilder(phaseId);
    }

    public PhaseBuilder displayName(String literal) {
        this.displayName = QuestText.literal(literal);
        return this;
    }

    public PhaseBuilder displayName(Component component) {
        this.displayName = QuestText.component(component);
        return this;
    }

    public PhaseBuilder displayName(QuestText text) {
        this.displayName = text;
        return this;
    }

    public PhaseBuilder description(String literal) {
        this.description = QuestText.literal(literal);
        return this;
    }

    public PhaseBuilder description(Component component) {
        this.description = QuestText.component(component);
        return this;
    }

    public PhaseBuilder description(QuestText text) {
        this.description = text;
        return this;
    }

    public PhaseBuilder story(String literal) {
        this.story = QuestText.literal(literal);
        return this;
    }

    public PhaseBuilder story(Component component) {
        this.story = QuestText.component(component);
        return this;
    }

    public PhaseBuilder story(QuestText text) {
        this.story = text;
        return this;
    }

    public PhaseBuilder objective(ObjectiveBuilder objectiveBuilder) {
        this.objectives.add(objectiveBuilder.build());
        return this;
    }

    public PhaseBuilder objective(ObjectiveEntry entry) {
        this.objectives.add(entry);
        return this;
    }

    public PhaseBuilder thenGoTo(String targetPhaseId) {
        this.transitions.add(new PhaseTransition(
                targetPhaseId, null, this.transitionPriorityCounter++));
        return this;
    }

    public PhaseBuilder thenGoToIf(String targetPhaseId, ICondition condition) {
        this.transitions.add(new PhaseTransition(
                targetPhaseId, condition, this.transitionPriorityCounter++));
        return this;
    }

    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId) {
        this.choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, null));
        return this;
    }

    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId,
                               ICondition visibleCondition) {
        this.choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, visibleCondition));
        return this;
    }

    public PhaseBuilder reward(IReward reward) {
        this.phaseRewards.add(reward);
        return this;
    }

    public PhaseBuilder collectionEntryConfig(CollectionEntryConfig collectionEntryConfig) {
        this.collectionEntryConfig = collectionEntryConfig;
        return this;
    }

    public PhaseBuilder setFlagOnEnter(String flag) {
        this.flagsOnEnter.add(flag);
        return this;
    }

    public PhaseBuilder setFlagOnComplete(String flag) {
        this.flagsOnComplete.add(flag);
        return this;
    }

    public PhaseBuilder enterWhen(ICondition condition) {
        this.enterCondition = condition;
        this.autoEnterByCondition = true;
        return this;
    }

    public PhaseBuilder enterWhen(ICondition condition, boolean autoEnterByCondition) {
        this.enterCondition = condition;
        this.autoEnterByCondition = autoEnterByCondition;
        return this;
    }

    public PhaseBuilder markRelatedObject(MarkableObject object) {
        return markRelatedObject(object, MarkActivations.always());
    }

    public PhaseBuilder markRelatedObject(MarkableObject object, MarkActivation activation) {
        String id = this.phaseId + "::phase_mark_" + relatedMarks.size();
        this.relatedMarks.add(new MarkSpec(id, object, activation, MarkActivations.never(),
                QuestMarkerType.QUEST_OBJECTIVE, 0, 256, 20, true, false, Map.of()));
        return this;
    }

    public PhaseBuilder markRelatedObject(MarkSpec spec) {
        this.relatedMarks.add(spec);
        return this;
    }

    public PhaseBuilder phaseTrade(String shopId) {
        this.tradeShopId = shopId;
        return this;
    }

    public PhaseBuilder intelScene(ResourceLocation sceneId) {
        this.intelSceneId = sceneId;
        return this;
    }

    public PhaseBuilder intelScene(String questId, String phaseId) {
        this.intelSceneId = ArcQuestPonderHelper.questPhaseId(questId, phaseId);
        return this;
    }

    public PhaseBuilder phaseStartSound(SoundEvent sound) {
        this.phaseStartSound = sound;
        return this;
    }

    public PhaseBuilder phaseCompleteSound(SoundEvent sound) {
        this.phaseCompleteSound = sound;
        return this;
    }

    public PhaseBuilder visualConfig(QuestVisualConfig config) {
        if (config != null) {
            this.visualConfigBuilder = QuestVisualConfig.builder()
                    .themeColor(config.getThemeColor());
            for (SplashType type : SplashType.values()) {
                config.getSplash(type).ifPresent(asset ->
                        this.visualConfigBuilder.splash(type, asset));
            }
            for (IconPosition pos : IconPosition.values()) {
                config.getIcon(pos).ifPresent(asset ->
                        this.visualConfigBuilder.icon(pos, asset));
            }
        }
        return this;
    }

    public PhaseBuilder startSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.PHASE_START, texture, scale);
        return this;
    }

    public PhaseBuilder startSplash(ResourceLocation texture) {
        return startSplash(texture, 1.0f);
    }

    public PhaseBuilder completeSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.PHASE_COMPLETE, texture, scale);
        return this;
    }

    public PhaseBuilder completeSplash(ResourceLocation texture) {
        return completeSplash(texture, 1.0f);
    }

    public PhaseBuilder labelIcon(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.icon(IconPosition.PHASE_LABEL, texture, scale);
        return this;
    }

    public PhaseBuilder labelIcon(ResourceLocation texture) {
        return labelIcon(texture, 1.0f);
    }

    public PhaseBuilder themeColor(int color) {
        this.visualConfigBuilder.themeColor(color);
        return this;
    }

    public PhaseBuilder themeColor(ChatFormatting formatting) {
        this.visualConfigBuilder.themeColorFromChatFormatting(formatting);
        return this;
    }

    public PhaseDefinition build() {
        if (this.displayName == null) {
            this.displayName = QuestText.literal(this.phaseId);
        }
        if (this.objectives.isEmpty()) {
            throw new IllegalStateException(
                    "Phase '" + this.phaseId + "' has no objectives defined");
        }
        return new PhaseDefinition(
                this.phaseId,
                this.displayName,
                this.description,
                this.story,
                new ArrayList<>(this.objectives),
                new ArrayList<>(this.transitions),
                new ArrayList<>(this.choices),
                new ArrayList<>(this.phaseRewards),
                new ArrayList<>(this.flagsOnEnter),
                new ArrayList<>(this.flagsOnComplete),
                new ArrayList<>(this.relatedMarks),
                this.visualConfigBuilder.build(),
                this.tradeShopId,
                this.phaseStartSound,
                this.phaseCompleteSound,
                this.collectionEntryConfig,
                this.intelSceneId,
                this.enterCondition,
                this.autoEnterByCondition
        );
    }
}
