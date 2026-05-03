package org.arcadia.arc_quest.quest.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestBuilder {
    private final ResourceLocation id;
    private final List<ICondition> unlockConditions = new ArrayList<>();
    private final LinkedHashMap<String, PhaseDefinition> phases = new LinkedHashMap<>();
    private final List<IReward> completionRewards = new ArrayList<>();
    private final List<String> flagsOnAccept = new ArrayList<>();
    private final List<String> flagsOnComplete = new ArrayList<>();
    private final List<MarkSpec> relatedMarks = new ArrayList<>();
    private QuestCategory category = QuestCategory.ADVENTURE;
    private QuestText displayName;
    private QuestText description = QuestText.component(Component.empty());
    @Nullable
    private ResourceLocation iconTexture;
    private int sortOrder;
    private boolean repeatable;
    private String initialPhaseId;
    private QuestVisualConfig.Builder visualConfigBuilder = QuestVisualConfig.builder();
    private QuestMode mode = QuestMode.PROGRESSION;
    @Nullable
    private CollectionQuestConfig collectionConfig;
    @Nullable
    private String chapterShopId;
    private ChapterShopType chapterShopType = ChapterShopType.TRADE;
    private boolean chapterShopPersistent = true;
    private QuestCompletionPolicy completionPolicy = QuestCompletionPolicy.ALL;
    private int completionRequiredCount;
    @Nullable
    private String completionTargetPhaseId;
    @Nullable
    private QuestTimeLimitType timeLimitType;
    private long timeLimitValue;
    @Nullable
    private SoundEvent chapterStartSound;
    @Nullable
    private SoundEvent chapterFailSound;
    @Nullable
    private SoundEvent chapterCompleteSound;

    private QuestBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static QuestBuilder create(ResourceLocation id) {
        return new QuestBuilder(id);
    }

    public static QuestBuilder create(String id) {
        return id.contains(":") ? new QuestBuilder(ResourceLocation.tryParse(id)) : new QuestBuilder(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, id));
    }

    public QuestBuilder category(QuestCategory category) {
        this.category = category;
        return this;
    }

    public QuestBuilder displayName(String literal) {
        this.displayName = QuestText.literal(literal);
        return this;
    }

    public QuestBuilder displayName(Component component) {
        this.displayName = QuestText.component(component);
        return this;
    }

    public QuestBuilder displayName(QuestText text) {
        this.displayName = text;
        return this;
    }

    public QuestBuilder description(String literal) {
        this.description = QuestText.literal(literal);
        return this;
    }

    public QuestBuilder description(Component component) {
        this.description = QuestText.component(component);
        return this;
    }

    public QuestBuilder description(QuestText text) {
        this.description = text;
        return this;
    }

    public QuestBuilder icon(ResourceLocation texture) {
        this.iconTexture = texture;
        return this;
    }

    public QuestBuilder sortOrder(int order) {
        this.sortOrder = order;
        return this;
    }

    public QuestBuilder repeatable() {
        this.repeatable = true;
        return this;
    }

    public QuestBuilder mode(QuestMode mode) {
        this.mode = mode != null ? mode : QuestMode.PROGRESSION;
        return this;
    }

    public QuestBuilder collectionConfig(CollectionQuestConfig collectionConfig) {
        this.collectionConfig = collectionConfig;
        return this;
    }

    public QuestBuilder completionPolicy(QuestCompletionPolicy policy) {
        this.completionPolicy = policy;
        return this;
    }

    public QuestBuilder completionRequiredCount(int count) {
        this.completionRequiredCount = count;
        return this;
    }

    public QuestBuilder completionTargetPhase(String phaseId) {
        this.completionTargetPhaseId = phaseId;
        return this;
    }

    public QuestBuilder questTimeLimitSeconds(long seconds) {
        if (seconds <= 0L) throw new IllegalArgumentException("questTimeLimitSeconds requires seconds > 0");
        this.timeLimitType = QuestTimeLimitType.REAL_SECONDS;
        this.timeLimitValue = seconds;
        return this;
    }

    public QuestBuilder questTimeLimitDayTicks(long dayTicks) {
        if (dayTicks <= 0L) throw new IllegalArgumentException("questTimeLimitDayTicks requires dayTicks > 0");
        this.timeLimitType = QuestTimeLimitType.GAME_DAY_TIME;
        this.timeLimitValue = dayTicks;
        return this;
    }

    public QuestBuilder clearQuestTimeLimit() {
        this.timeLimitType = null;
        this.timeLimitValue = 0L;
        return this;
    }

    public QuestBuilder markRelatedObject(MarkableObject object) {
        return markRelatedObject(object, MarkActivations.always());
    }

    public QuestBuilder markRelatedObject(MarkableObject object, MarkActivation activation) {
        String markId = this.id + "::quest_mark_" + relatedMarks.size();
        this.relatedMarks.add(new MarkSpec(markId, object, activation, MarkActivations.never(), QuestMarkerType.QUEST_MAIN, 0, 256, 20, true, false, Map.of()));
        return this;
    }

    public QuestBuilder markRelatedObject(MarkSpec spec) {
        this.relatedMarks.add(spec);
        return this;
    }

    public QuestBuilder unlockCondition(ICondition condition) {
        this.unlockConditions.add(condition);
        return this;
    }

    public QuestBuilder requiresQuest(String questId) {
        ResourceLocation location = questId.contains(":") ? ResourceLocation.tryParse(questId) : ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, questId);
        this.unlockConditions.add(ICondition.questCompleted(location));
        return this;
    }

    public QuestBuilder requiresQuest(ResourceLocation questId) {
        this.unlockConditions.add(ICondition.questCompleted(questId));
        return this;
    }

    public QuestBuilder requiresFlag(String flag) {
        this.unlockConditions.add(ICondition.flagSet(flag));
        return this;
    }

    public QuestBuilder phase(PhaseBuilder phaseBuilder) {
        return this.phase(phaseBuilder.build());
    }

    public QuestBuilder phase(PhaseDefinition phase) {
        String pid = phase.getPhaseId();
        if (this.phases.containsKey(pid))
            throw new IllegalArgumentException("Duplicate phase id '" + pid + "' in quest '" + this.id + "'");
        this.phases.put(pid, phase);
        if (this.initialPhaseId == null) this.initialPhaseId = pid;
        return this;
    }

    public QuestBuilder startAt(String phaseId) {
        this.initialPhaseId = phaseId;
        return this;
    }

    public QuestBuilder reward(IReward reward) {
        this.completionRewards.add(reward);
        return this;
    }

    public QuestBuilder chapterShop(String shopId, boolean persistent) {
        this.chapterShopId = shopId;
        this.chapterShopType = ChapterShopType.TRADE;
        this.chapterShopPersistent = persistent;
        return this;
    }

    public QuestBuilder chapterShop(String shopId) {
        return chapterShop(shopId, true);
    }

    public QuestBuilder chapterTradeShop(String shopId, boolean persistent) {
        this.chapterShopId = shopId;
        this.chapterShopType = ChapterShopType.TRADE;
        this.chapterShopPersistent = persistent;
        return this;
    }

    public QuestBuilder chapterTradeShop(String shopId) {
        return chapterTradeShop(shopId, true);
    }

    public QuestBuilder chapterGachaShop(String shopId, boolean persistent) {
        this.chapterShopId = shopId;
        this.chapterShopType = ChapterShopType.GACHA;
        this.chapterShopPersistent = persistent;
        return this;
    }

    public QuestBuilder chapterGachaShop(String shopId) {
        return chapterGachaShop(shopId, true);
    }

    public QuestBuilder chapterStartSound(SoundEvent sound) {
        this.chapterStartSound = sound;
        return this;
    }

    public QuestBuilder chapterFailSound(SoundEvent sound) {
        this.chapterFailSound = sound;
        return this;
    }

    public QuestBuilder chapterCompleteSound(SoundEvent sound) {
        this.chapterCompleteSound = sound;
        return this;
    }

    public QuestBuilder setFlagOnAccept(String flag) {
        this.flagsOnAccept.add(flag);
        return this;
    }

    public QuestBuilder setFlagOnComplete(String flag) {
        this.flagsOnComplete.add(flag);
        return this;
    }

    public QuestBuilder visualConfig(QuestVisualConfig config) {
        if (config != null) {
            this.visualConfigBuilder = QuestVisualConfig.builder().themeColor(config.getThemeColor());
            for (SplashType type : SplashType.values())
                config.getSplash(type).ifPresent(asset -> this.visualConfigBuilder.splash(type, asset));
            for (IconPosition pos : IconPosition.values())
                config.getIcon(pos).ifPresent(asset -> this.visualConfigBuilder.icon(pos, asset));
        }
        return this;
    }

    public QuestBuilder acquisitionSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.QUEST_ACQUIRED, texture, scale);
        return this;
    }

    public QuestBuilder acquisitionSplash(ResourceLocation texture) {
        return acquisitionSplash(texture, 1.0f);
    }

    public QuestBuilder detailSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.QUEST_DETAIL, texture, scale);
        return this;
    }

    public QuestBuilder detailSplash(ResourceLocation texture) {
        return detailSplash(texture, 1.0f);
    }

    public QuestBuilder completionSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.QUEST_COMPLETED, texture, scale);
        return this;
    }

    public QuestBuilder completionSplash(ResourceLocation texture) {
        return completionSplash(texture, 1.0f);
    }

    public QuestBuilder listIcon(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.icon(IconPosition.QUEST_LIST, texture, scale);
        return this;
    }

    public QuestBuilder listIcon(ResourceLocation texture) {
        return listIcon(texture, 1.0f);
    }

    public QuestBuilder titleIcon(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.icon(IconPosition.QUEST_TITLE, texture, scale);
        return this;
    }

    public QuestBuilder titleIcon(ResourceLocation texture) {
        return titleIcon(texture, 1.0f);
    }

    public QuestBuilder themeColor(int color) {
        this.visualConfigBuilder.themeColor(color);
        return this;
    }

    public QuestBuilder themeColor(ChatFormatting formatting) {
        this.visualConfigBuilder.themeColorFromChatFormatting(formatting);
        return this;
    }

    public QuestDefinition build() {
        if (this.displayName == null) this.displayName = QuestText.literal(this.id.getPath());
        if (this.phases.isEmpty()) throw new IllegalStateException("Quest '" + this.id + "' has no phases");
        if (this.initialPhaseId == null) this.initialPhaseId = this.phases.keySet().iterator().next();
        for (PhaseDefinition phase : this.phases.values()) {
            for (PhaseTransition tr : phase.getTransitions())
                if (!this.phases.containsKey(tr.getTargetPhaseId()))
                    throw new IllegalStateException("Quest '" + this.id + "', phase '" + phase.getPhaseId() + "' references unknown phase '" + tr.getTargetPhaseId() + "'");
            for (ChoiceOption ch : phase.getChoices())
                if (!this.phases.containsKey(ch.getTargetPhaseId()))
                    throw new IllegalStateException("Quest '" + this.id + "', phase '" + phase.getPhaseId() + "' choice references unknown phase '" + ch.getTargetPhaseId() + "'");
        }
        int phaseCount = this.phases.size();
        if ((this.completionPolicy == QuestCompletionPolicy.ALL || this.completionPolicy == QuestCompletionPolicy.ANY) && this.completionRequiredCount > 0)
            throw new IllegalStateException("Quest '" + this.id + "': completionRequiredCount only valid for N_OF_M");
        if (this.completionPolicy == QuestCompletionPolicy.N_OF_M) {
            if (this.completionRequiredCount < 1 || this.completionRequiredCount > phaseCount)
                throw new IllegalStateException("Quest '" + this.id + "': N_OF_M requires completionRequiredCount in [1," + phaseCount + "], got " + this.completionRequiredCount);
            if (this.completionTargetPhaseId != null && !this.completionTargetPhaseId.isEmpty())
                throw new IllegalStateException("Quest '" + this.id + "': completionTargetPhase not allowed with N_OF_M");
        }
        if (this.completionPolicy == QuestCompletionPolicy.SPECIFIC_PHASE) {
            if (this.completionTargetPhaseId == null || this.completionTargetPhaseId.isEmpty())
                throw new IllegalStateException("Quest '" + this.id + "': SPECIFIC_PHASE requires completionTargetPhase");
            if (!this.phases.containsKey(this.completionTargetPhaseId))
                throw new IllegalStateException("Quest '" + this.id + "': completionTargetPhase '" + this.completionTargetPhaseId + "' not found");
            if (this.completionRequiredCount > 0)
                throw new IllegalStateException("Quest '" + this.id + "': completionRequiredCount not allowed with SPECIFIC_PHASE");
        }
        if (this.completionPolicy != QuestCompletionPolicy.SPECIFIC_PHASE && this.completionTargetPhaseId != null && !this.completionTargetPhaseId.isEmpty())
            throw new IllegalStateException("Quest '" + this.id + "': completionTargetPhase only valid for SPECIFIC_PHASE");
        return new QuestDefinition(this.id, this.category, this.displayName, this.description, this.iconTexture, this.sortOrder, this.repeatable, new ArrayList<>(this.unlockConditions), new LinkedHashMap<>(this.phases), this.initialPhaseId, new ArrayList<>(this.completionRewards), new ArrayList<>(this.flagsOnAccept), new ArrayList<>(this.flagsOnComplete), new ArrayList<>(this.relatedMarks), this.visualConfigBuilder.build(), this.mode, this.collectionConfig, this.chapterShopId, this.chapterShopType, this.chapterShopPersistent, this.chapterStartSound, this.chapterFailSound, this.chapterCompleteSound, this.completionPolicy, this.completionRequiredCount, this.completionTargetPhaseId, this.timeLimitType, this.timeLimitValue);
    }

    public QuestDefinition buildAndRegister() {
        QuestDefinition def = this.build();
        QuestRegistry.register(def);
        return def;
    }
}
