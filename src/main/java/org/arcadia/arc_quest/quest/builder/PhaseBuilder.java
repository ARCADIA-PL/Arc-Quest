package org.arcadia.arc_quest.quest.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.util.IntelSceneIdHelper;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.questmarker.api.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
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
    private final List<ResourceLocation> guidesOnEnter = new ArrayList<>();
    private final List<ResourceLocation> guidesOnComplete = new ArrayList<>();
    private final List<MarkSpec> relatedMarks = new ArrayList<>();
    private final List<MarkSpec> trackingMarks = new ArrayList<>();
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
    private boolean autoAdvanceOnComplete = true;

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
        displayName = QuestText.literal(literal);
        return this;
    }

    public PhaseBuilder displayName(Component component) {
        displayName = QuestText.component(component);
        return this;
    }

    public PhaseBuilder displayName(QuestText text) {
        displayName = text;
        return this;
    }

    public PhaseBuilder description(String literal) {
        description = QuestText.literal(literal);
        return this;
    }

    public PhaseBuilder description(Component component) {
        description = QuestText.component(component);
        return this;
    }

    public PhaseBuilder description(QuestText text) {
        description = text;
        return this;
    }

    public PhaseBuilder story(String literal) {
        story = QuestText.literal(literal);
        return this;
    }

    public PhaseBuilder story(Component component) {
        story = QuestText.component(component);
        return this;
    }

    public PhaseBuilder story(QuestText text) {
        story = text;
        return this;
    }

    public PhaseBuilder objective(ObjectiveBuilder objectiveBuilder) {
        objectives.add(objectiveBuilder.build());
        return this;
    }

    public PhaseBuilder objective(ObjectiveEntry entry) {
        objectives.add(entry);
        return this;
    }

    public PhaseBuilder transition(PhaseTransition transition) {
        transitions.add(transition);
        return this;
    }

    public PhaseBuilder choice(ChoiceOption choice) {
        choices.add(choice);
        return this;
    }

    public PhaseBuilder thenGoTo(String targetPhaseId) {
        return thenGoTo(targetPhaseId, (ICondition) null);
    }

    public PhaseBuilder thenGoTo(String targetPhaseId, @Nullable ICondition condition) {
        transitions.add(new PhaseTransition(
                targetPhaseId, condition, transitionPriorityCounter++));
        return this;
    }

    public PhaseBuilder thenGoTo(String firstTargetPhaseId, String... additionalTargetPhaseIds) {
        List<String> targetPhaseIds = new ArrayList<>(1 + additionalTargetPhaseIds.length);
        targetPhaseIds.add(firstTargetPhaseId);
        targetPhaseIds.addAll(List.of(additionalTargetPhaseIds));
        return thenGoTo(targetPhaseIds);
    }

    public PhaseBuilder thenGoTo(Collection<String> targetPhaseIds) {
        return thenGoTo(targetPhaseIds, null);
    }

    public PhaseBuilder thenGoTo(Collection<String> targetPhaseIds, @Nullable ICondition condition) {
        transitions.add(new PhaseTransition(
                targetPhaseIds, condition, transitionPriorityCounter++));
        return this;
    }

    public PhaseBuilder thenGoTo(String firstTargetPhaseId,
                                 ICondition condition,
                                 String... additionalTargetPhaseIds) {
        List<String> targetPhaseIds = new ArrayList<>(1 + additionalTargetPhaseIds.length);
        targetPhaseIds.add(firstTargetPhaseId);
        targetPhaseIds.addAll(List.of(additionalTargetPhaseIds));
        return thenGoTo(targetPhaseIds, Objects.requireNonNull(condition, "condition"));
    }

    public PhaseBuilder thenGoToIf(Collection<String> targetPhaseIds, ICondition condition) {
        return thenGoTo(targetPhaseIds, Objects.requireNonNull(condition, "condition"));
    }

    public PhaseBuilder thenGoToIf(String firstTargetPhaseId,
                                   ICondition condition,
                                   String... additionalTargetPhaseIds) {
        List<String> targetPhaseIds = new ArrayList<>(1 + additionalTargetPhaseIds.length);
        targetPhaseIds.add(firstTargetPhaseId);
        targetPhaseIds.addAll(List.of(additionalTargetPhaseIds));
        return thenGoTo(targetPhaseIds, Objects.requireNonNull(condition, "condition"));
    }

    public PhaseBuilder thenGoToIf(String targetPhaseId, ICondition condition) {
        return thenGoTo(targetPhaseId, Objects.requireNonNull(condition, "condition"));
    }

    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId) {
        choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, null));
        return this;
    }

    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId,
                               ICondition visibleCondition) {
        choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, visibleCondition));
        return this;
    }

    public PhaseBuilder reward(IReward reward) {
        phaseRewards.add(reward);
        return this;
    }

    public PhaseBuilder collectionEntryConfig(CollectionEntryConfig collectionEntryConfig) {
        this.collectionEntryConfig = collectionEntryConfig;
        return this;
    }

    public PhaseBuilder setFlagOnEnter(String flag) {
        flagsOnEnter.add(flag);
        return this;
    }

    public PhaseBuilder setFlagOnComplete(String flag) {
        flagsOnComplete.add(flag);
        return this;
    }

    public PhaseBuilder grantGuideOnEnter(ResourceLocation guideId) {
        guidesOnEnter.add(Objects.requireNonNull(guideId, "guideId"));
        return this;
    }

    public PhaseBuilder grantGuideOnEnter(String guideId) {
        return grantGuideOnEnter(ResourceLocation.parse(guideId));
    }

    public PhaseBuilder grantGuideOnComplete(ResourceLocation guideId) {
        guidesOnComplete.add(Objects.requireNonNull(guideId, "guideId"));
        return this;
    }

    public PhaseBuilder grantGuideOnComplete(String guideId) {
        return grantGuideOnComplete(ResourceLocation.parse(guideId));
    }

    public PhaseBuilder enterWhen(ICondition condition) {
        enterCondition = condition;
        autoEnterByCondition = true;
        return this;
    }

    public PhaseBuilder enterWhen(ICondition condition, boolean autoEnterByCondition) {
        enterCondition = condition;
        this.autoEnterByCondition = autoEnterByCondition;
        return this;
    }

    public PhaseBuilder autoAdvanceOnComplete(boolean autoAdvanceOnComplete) {
        this.autoAdvanceOnComplete = autoAdvanceOnComplete;
        return this;
    }

    public PhaseBuilder markRelatedObject(MarkableObject object) {
        return markRelatedObject(object, MarkActivations.always());
    }

    public PhaseBuilder markRelatedObject(MarkableObject object, MarkActivation activation) {
        String id = phaseId + "::phase_mark_" + relatedMarks.size();
        relatedMarks.add(new MarkSpec(id, object, activation, MarkActivations.never(),
                QuestMarkerType.QUEST_OBJECTIVE, 0, MarkSpec.DEFAULT_MAX_DISTANCE, 20, true, false, Map.of()));
        return this;
    }

    public PhaseBuilder markRelatedObject(MarkSpec spec) {
        relatedMarks.add(spec);
        return this;
    }

    public PhaseBuilder markRelatedObject(MarkSpec spec, MarkTrigger trigger, int durationTicks) {
        relatedMarks.add(MarkTriggers.withTrigger(spec, trigger, durationTicks));
        return this;
    }

    public PhaseBuilder markOnEnter(MarkSpec spec) {
        return markRelatedObject(spec, MarkTrigger.PHASE_ENTERED, MarkTriggers.DEFAULT_TRIGGER_DURATION_TICKS);
    }

    public PhaseBuilder markOnComplete(MarkSpec spec) {
        return markRelatedObject(spec, MarkTrigger.PHASE_COMPLETED, MarkTriggers.DEFAULT_TRIGGER_DURATION_TICKS);
    }

    public PhaseBuilder markOnAdvance(MarkSpec spec) {
        return markRelatedObject(spec, MarkTrigger.PHASE_ADVANCED, MarkTriggers.DEFAULT_TRIGGER_DURATION_TICKS);
    }

    public PhaseBuilder trackingMarker(MarkableObject object) {
        return trackingMarker(object, MarkActivations.always());
    }

    public PhaseBuilder trackingMarker(MarkableObject object, MarkActivation activation) {
        String id = phaseId + "::tracking_mark_" + trackingMarks.size();
        return trackingMarker(new MarkSpec(id, object, activation, MarkActivations.never(),
                QuestMarkerType.QUEST_OBJECTIVE, 0, MarkSpec.DEFAULT_MAX_DISTANCE, 20, true, false, Map.of()));
    }

    public PhaseBuilder trackingMarker(MarkSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (!MarkTriggers.isContinuous(spec)) {
            throw new IllegalArgumentException("Phase tracking markers must use the CONTINUOUS trigger");
        }
        trackingMarks.add(spec);
        return this;
    }

    public PhaseBuilder phaseTrade(String shopId) {
        tradeShopId = shopId;
        return this;
    }

    public PhaseBuilder intelScene(ResourceLocation sceneId) {
        intelSceneId = sceneId;
        return this;
    }

    public PhaseBuilder intelScene(String questId, String phaseId) {
        intelSceneId = IntelSceneIdHelper.questPhaseId(questId, phaseId);
        return this;
    }

    public PhaseBuilder phaseStartSound(SoundEvent sound) {
        phaseStartSound = sound;
        return this;
    }

    public PhaseBuilder phaseCompleteSound(SoundEvent sound) {
        phaseCompleteSound = sound;
        return this;
    }

    public PhaseBuilder visualConfig(QuestVisualConfig config) {
        if (config != null) {
            visualConfigBuilder = QuestVisualConfig.builder()
                    .themeColor(config.getThemeColor())
                    .useQuestSplashPresentation(config.usesQuestSplashPresentation());
            for (SplashType type : SplashType.values()) {
                config.getSplash(type).ifPresent(asset ->
                        visualConfigBuilder.splash(type, asset));
            }
            for (IconPosition pos : IconPosition.values()) {
                config.getIcon(pos).ifPresent(asset ->
                        visualConfigBuilder.icon(pos, asset));
            }
        }
        return this;
    }

    public PhaseBuilder startSplash(ResourceLocation texture, float scale) {
        visualConfigBuilder.splash(SplashType.PHASE_START, texture, scale);
        return this;
    }

    public PhaseBuilder startSplash(ResourceLocation texture) {
        return startSplash(texture, 1.0f);
    }

    public PhaseBuilder completeSplash(ResourceLocation texture, float scale) {
        visualConfigBuilder.splash(SplashType.PHASE_COMPLETE, texture, scale);
        return this;
    }

    public PhaseBuilder completeSplash(ResourceLocation texture) {
        return completeSplash(texture, 1.0f);
    }

    public PhaseBuilder historyImage(ResourceLocation texture, float scale) {
        visualConfigBuilder.splash(SplashType.QUEST_DETAIL, texture, scale);
        return this;
    }

    public PhaseBuilder historyImage(ResourceLocation texture) {
        return historyImage(texture, 1.0f);
    }

    public PhaseBuilder historyImage(Item item, float scale) {
        return historyImage(item, scale, 0f, -2f);
    }

    public PhaseBuilder historyImage(Item item) {
        return historyImage(item, 1.0f);
    }

    public PhaseBuilder historyImage(Item item, float offsetX, float offsetY) {
        return historyImage(item, 1.0f, offsetX, offsetY);
    }

    public PhaseBuilder historyImage(Item item, float scale, float offsetX, float offsetY) {
        return historyImage(new ItemStack(Objects.requireNonNull(item)), scale, offsetX, offsetY);
    }

    public PhaseBuilder historyImage(ItemStack stack, float scale) {
        return historyImage(stack, scale, 0f, -2f);
    }

    public PhaseBuilder historyImage(ItemStack stack, float offsetX, float offsetY) {
        return historyImage(stack, 1.0f, offsetX, offsetY);
    }

    public PhaseBuilder historyImage(ItemStack stack, float scale, float offsetX, float offsetY) {
        VisualAsset asset = VisualAsset.builder()
                .item(Objects.requireNonNull(stack))
                .scale(scale)
                .offset(offsetX, offsetY)
                .build();
        visualConfigBuilder.splash(SplashType.QUEST_DETAIL, asset);
        return this;
    }

    public PhaseBuilder historyImage(ItemStack stack) {
        return historyImage(stack, 1.0f);
    }

    public PhaseBuilder useQuestSplashPresentation(boolean useQuestSplashPresentation) {
        visualConfigBuilder.useQuestSplashPresentation(useQuestSplashPresentation);
        return this;
    }

    public PhaseBuilder labelIcon(ResourceLocation texture, float scale) {
        visualConfigBuilder.icon(IconPosition.PHASE_LABEL, texture, scale);
        return this;
    }

    public PhaseBuilder labelIcon(ResourceLocation texture) {
        return labelIcon(texture, 1.0f);
    }

    public PhaseBuilder themeColor(int color) {
        visualConfigBuilder.themeColor(color);
        return this;
    }

    public PhaseBuilder themeColor(ChatFormatting formatting) {
        visualConfigBuilder.themeColorFromChatFormatting(formatting);
        return this;
    }

    public PhaseDefinition build() {
        if (displayName == null) {
            displayName = QuestText.literal(phaseId);
        }
        if (objectives.isEmpty()) {
            throw new IllegalStateException(
                    "Phase '" + phaseId + "' has no objectives defined");
        }
        return new PhaseDefinition(
                phaseId,
                displayName,
                description,
                story,
                new ArrayList<>(objectives),
                new ArrayList<>(transitions),
                new ArrayList<>(choices),
                new ArrayList<>(phaseRewards),
                new ArrayList<>(flagsOnEnter),
                new ArrayList<>(flagsOnComplete),
                new ArrayList<>(relatedMarks),
                visualConfigBuilder.build(),
                tradeShopId,
                phaseStartSound,
                phaseCompleteSound,
                collectionEntryConfig,
                intelSceneId,
                enterCondition,
                autoEnterByCondition,
                autoAdvanceOnComplete,
                new ArrayList<>(guidesOnEnter),
                new ArrayList<>(guidesOnComplete),
                new ArrayList<>(trackingMarks)
        );
    }
}
