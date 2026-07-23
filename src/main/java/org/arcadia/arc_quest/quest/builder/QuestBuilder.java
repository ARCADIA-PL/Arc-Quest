package org.arcadia.arc_quest.quest.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
        displayName = QuestText.literal(literal);
        return this;
    }

    public QuestBuilder displayName(Component component) {
        displayName = QuestText.component(component);
        return this;
    }

    public QuestBuilder displayName(QuestText text) {
        displayName = text;
        return this;
    }

    public QuestBuilder description(String literal) {
        description = QuestText.literal(literal);
        return this;
    }

    public QuestBuilder description(Component component) {
        description = QuestText.component(component);
        return this;
    }

    public QuestBuilder description(QuestText text) {
        description = text;
        return this;
    }

    public QuestBuilder icon(ResourceLocation texture) {
        iconTexture = texture;
        return this;
    }

    /**
     * 直接传入物品，通过原版物品模型渲染图标（无纹理路径猜测问题）。
     */
    public QuestBuilder icon(Item item) {
        return icon(new ItemStack(item));
    }

    /**
     * 直接传入 ItemStack，通过原版物品模型渲染图标。
     */
    public QuestBuilder icon(ItemStack stack) {
        VisualAsset asset = VisualAsset.of(stack);
        visualConfigBuilder.icon(IconPosition.QUEST_LIST, asset);
        visualConfigBuilder.icon(IconPosition.QUEST_TITLE, asset);
        return this;
    }

    public QuestBuilder sortOrder(int order) {
        sortOrder = order;
        return this;
    }

    public QuestBuilder repeatable() {
        repeatable = true;
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
        completionPolicy = policy;
        return this;
    }

    public QuestBuilder completionRequiredCount(int count) {
        completionRequiredCount = count;
        return this;
    }

    public QuestBuilder completionTargetPhase(String phaseId) {
        completionTargetPhaseId = phaseId;
        return this;
    }

    public QuestBuilder questTimeLimitSeconds(long seconds) {
        if (seconds <= 0L) throw new IllegalArgumentException("questTimeLimitSeconds requires seconds > 0");
        timeLimitType = QuestTimeLimitType.REAL_SECONDS;
        timeLimitValue = seconds;
        return this;
    }

    public QuestBuilder questTimeLimitDayTicks(long dayTicks) {
        if (dayTicks <= 0L) throw new IllegalArgumentException("questTimeLimitDayTicks requires dayTicks > 0");
        timeLimitType = QuestTimeLimitType.GAME_DAY_TIME;
        timeLimitValue = dayTicks;
        return this;
    }

    public QuestBuilder clearQuestTimeLimit() {
        timeLimitType = null;
        timeLimitValue = 0L;
        return this;
    }

    public QuestBuilder markRelatedObject(MarkableObject object) {
        return markRelatedObject(object, MarkActivations.always());
    }

    public QuestBuilder markRelatedObject(MarkableObject object, MarkActivation activation) {
        String markId = id + "::quest_mark_" + relatedMarks.size();
        relatedMarks.add(new MarkSpec(markId, object, activation, MarkActivations.never(), QuestMarkerType.QUEST_MAIN, 0, 256, 20, true, false, Map.of()));
        return this;
    }

    public QuestBuilder markRelatedObject(MarkSpec spec) {
        relatedMarks.add(spec);
        return this;
    }

    public QuestBuilder unlockCondition(ICondition condition) {
        unlockConditions.add(condition);
        return this;
    }

    public QuestBuilder requiresQuest(String questId) {
        ResourceLocation location = questId.contains(":") ? ResourceLocation.tryParse(questId) : ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, questId);
        unlockConditions.add(ICondition.questCompleted(location));
        return this;
    }

    public QuestBuilder requiresQuest(ResourceLocation questId) {
        unlockConditions.add(ICondition.questCompleted(questId));
        return this;
    }

    public QuestBuilder requiresFlag(String flag) {
        unlockConditions.add(ICondition.flagSet(flag));
        return this;
    }

    public QuestBuilder phase(PhaseBuilder phaseBuilder) {
        return phase(phaseBuilder.build());
    }

    public QuestBuilder phase(PhaseDefinition phase) {
        String pid = phase.getPhaseId();
        if (phases.containsKey(pid))
            throw new IllegalArgumentException("Duplicate phase id '" + pid + "' in quest '" + id + "'");
        phases.put(pid, phase);
        if (initialPhaseId == null) initialPhaseId = pid;
        return this;
    }

    public QuestBuilder startAt(String phaseId) {
        initialPhaseId = phaseId;
        return this;
    }

    public QuestBuilder reward(IReward reward) {
        completionRewards.add(reward);
        return this;
    }

    public QuestBuilder chapterShop(String shopId, boolean persistent) {
        chapterShopId = shopId;
        chapterShopType = ChapterShopType.TRADE;
        chapterShopPersistent = persistent;
        return this;
    }

    public QuestBuilder chapterShop(String shopId) {
        return chapterShop(shopId, true);
    }

    public QuestBuilder chapterTradeShop(String shopId, boolean persistent) {
        chapterShopId = shopId;
        chapterShopType = ChapterShopType.TRADE;
        chapterShopPersistent = persistent;
        return this;
    }

    public QuestBuilder chapterTradeShop(String shopId) {
        return chapterTradeShop(shopId, true);
    }

    public QuestBuilder chapterGachaShop(String shopId, boolean persistent) {
        chapterShopId = shopId;
        chapterShopType = ChapterShopType.GACHA;
        chapterShopPersistent = persistent;
        return this;
    }

    public QuestBuilder chapterGachaShop(String shopId) {
        return chapterGachaShop(shopId, true);
    }

    public QuestBuilder chapterStartSound(SoundEvent sound) {
        chapterStartSound = sound;
        return this;
    }

    public QuestBuilder chapterFailSound(SoundEvent sound) {
        chapterFailSound = sound;
        return this;
    }

    public QuestBuilder chapterCompleteSound(SoundEvent sound) {
        chapterCompleteSound = sound;
        return this;
    }

    public QuestBuilder setFlagOnAccept(String flag) {
        flagsOnAccept.add(flag);
        return this;
    }

    public QuestBuilder setFlagOnComplete(String flag) {
        flagsOnComplete.add(flag);
        return this;
    }

    public QuestBuilder visualConfig(QuestVisualConfig config) {
        if (config != null) {
            visualConfigBuilder = QuestVisualConfig.builder()
                    .themeColor(config.getThemeColor())
                    .useQuestSplashPresentation(config.usesQuestSplashPresentation());
            for (SplashType type : SplashType.values())
                config.getSplash(type).ifPresent(asset -> visualConfigBuilder.splash(type, asset));
            for (IconPosition pos : IconPosition.values())
                config.getIcon(pos).ifPresent(asset -> visualConfigBuilder.icon(pos, asset));
        }
        return this;
    }

    public QuestBuilder acquisitionSplash(ResourceLocation texture, float scale) {
        visualConfigBuilder.splash(SplashType.QUEST_ACQUIRED, texture, scale);
        return this;
    }

    public QuestBuilder acquisitionSplash(ResourceLocation texture) {
        return acquisitionSplash(texture, 1.0f);
    }

    public QuestBuilder detailSplash(ResourceLocation texture, float scale) {
        visualConfigBuilder.splash(SplashType.QUEST_DETAIL, texture, scale);
        return this;
    }

    public QuestBuilder detailSplash(ResourceLocation texture) {
        return detailSplash(texture, 1.0f);
    }

    public QuestBuilder completionSplash(ResourceLocation texture, float scale) {
        visualConfigBuilder.splash(SplashType.QUEST_COMPLETED, texture, scale);
        return this;
    }

    public QuestBuilder completionSplash(ResourceLocation texture) {
        return completionSplash(texture, 1.0f);
    }

    public QuestBuilder listIcon(ResourceLocation texture, float scale) {
        visualConfigBuilder.icon(IconPosition.QUEST_LIST, texture, scale);
        return this;
    }

    public QuestBuilder listIcon(ResourceLocation texture) {
        return listIcon(texture, 1.0f);
    }

    public QuestBuilder titleIcon(ResourceLocation texture, float scale) {
        visualConfigBuilder.icon(IconPosition.QUEST_TITLE, texture, scale);
        return this;
    }

    public QuestBuilder titleIcon(ResourceLocation texture) {
        return titleIcon(texture, 1.0f);
    }

    public QuestBuilder themeColor(int color) {
        visualConfigBuilder.themeColor(color);
        return this;
    }

    public QuestBuilder themeColor(ChatFormatting formatting) {
        visualConfigBuilder.themeColorFromChatFormatting(formatting);
        return this;
    }

    public QuestDefinition build() {
        if (displayName == null) displayName = QuestText.literal(id.getPath());
        if (phases.isEmpty()) throw new IllegalStateException("Quest '" + id + "' has no phases");
        if (initialPhaseId == null) initialPhaseId = phases.keySet().iterator().next();
        for (PhaseDefinition phase : phases.values()) {
            for (PhaseTransition tr : phase.getTransitions())
                if (!phases.containsKey(tr.getTargetPhaseId()))
                    throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "' references unknown phase '" + tr.getTargetPhaseId() + "'");
            for (ChoiceOption ch : phase.getChoices())
                if (!phases.containsKey(ch.getTargetPhaseId()))
                    throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "' choice references unknown phase '" + ch.getTargetPhaseId() + "'");
        }
        int phaseCount = phases.size();
        if ((completionPolicy == QuestCompletionPolicy.ALL || completionPolicy == QuestCompletionPolicy.ANY) && completionRequiredCount > 0)
            throw new IllegalStateException("Quest '" + id + "': completionRequiredCount only valid for N_OF_M");
        if (completionPolicy == QuestCompletionPolicy.N_OF_M) {
            if (completionRequiredCount < 1 || completionRequiredCount > phaseCount)
                throw new IllegalStateException("Quest '" + id + "': N_OF_M requires completionRequiredCount in [1," + phaseCount + "], got " + completionRequiredCount);
            if (completionTargetPhaseId != null && !completionTargetPhaseId.isEmpty())
                throw new IllegalStateException("Quest '" + id + "': completionTargetPhase not allowed with N_OF_M");
        }
        if (completionPolicy == QuestCompletionPolicy.SPECIFIC_PHASE) {
            if (completionTargetPhaseId == null || completionTargetPhaseId.isEmpty())
                throw new IllegalStateException("Quest '" + id + "': SPECIFIC_PHASE requires completionTargetPhase");
            if (!phases.containsKey(completionTargetPhaseId))
                throw new IllegalStateException("Quest '" + id + "': completionTargetPhase '" + completionTargetPhaseId + "' not found");
            if (completionRequiredCount > 0)
                throw new IllegalStateException("Quest '" + id + "': completionRequiredCount not allowed with SPECIFIC_PHASE");
        }
        if (completionPolicy != QuestCompletionPolicy.SPECIFIC_PHASE && completionTargetPhaseId != null && !completionTargetPhaseId.isEmpty())
            throw new IllegalStateException("Quest '" + id + "': completionTargetPhase only valid for SPECIFIC_PHASE");
        validateCollectionDefinition();
        return new QuestDefinition(id, category, displayName, description, iconTexture, sortOrder, repeatable, new ArrayList<>(unlockConditions), new LinkedHashMap<>(phases), initialPhaseId, new ArrayList<>(completionRewards), new ArrayList<>(flagsOnAccept), new ArrayList<>(flagsOnComplete), new ArrayList<>(relatedMarks), visualConfigBuilder.build(), mode, collectionConfig, chapterShopId, chapterShopType, chapterShopPersistent, chapterStartSound, chapterFailSound, chapterCompleteSound, completionPolicy, completionRequiredCount, completionTargetPhaseId, timeLimitType, timeLimitValue);
    }

    private void validateCollectionDefinition() {
        QuestMode effectiveMode = mode != null ? mode : QuestMode.PROGRESSION;
        if (effectiveMode == QuestMode.PROGRESSION) {
            if (collectionConfig != null) {
                throw new IllegalStateException("Quest '" + id + "': collectionConfig requires QuestMode.COLLECTION");
            }
            return;
        }

        if (collectionConfig == null) {
            throw new IllegalStateException("Quest '" + id + "': COLLECTION mode requires collectionConfig");
        }

        Set<String> categoryIds = new HashSet<>();
        for (CollectionCategoryDefinition category : collectionConfig.getCategories()) {
            String categoryId = category.getCategoryId();
            if (categoryId == null || categoryId.isBlank()) {
                throw new IllegalStateException("Quest '" + id + "': collection category id must not be blank");
            }
            if (!categoryIds.add(categoryId)) {
                throw new IllegalStateException("Quest '" + id + "': duplicate collection category id '" + categoryId + "'");
            }
        }
        if (categoryIds.isEmpty()) {
            throw new IllegalStateException("Quest '" + id + "': COLLECTION mode requires at least one category");
        }

        int entryCount = 0;
        for (PhaseDefinition phase : phases.values()) {
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) continue;
            entryCount++;
            validateCollectionEntryConfig(phase, entryConfig, categoryIds);
        }
        if (entryCount <= 0) {
            throw new IllegalStateException("Quest '" + id + "': COLLECTION mode requires at least one collection entry phase");
        }
    }

    private void validateCollectionEntryConfig(PhaseDefinition phase, CollectionEntryConfig entryConfig, Set<String> categoryIds) {
        String categoryId = entryConfig.getCategoryId();
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "': collection category id must not be blank");
        }
        if (!categoryIds.contains(categoryId)) {
            throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "': unknown collection category id '" + categoryId + "'");
        }
        if (entryConfig.getCompletionTarget() <= 0) {
            throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "': collection completionTarget must be > 0");
        }
        if (entryConfig.getMaxCount() > 0 && entryConfig.getMaxCount() < entryConfig.getCompletionTarget()) {
            throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "': collection maxCount must be 0 or >= completionTarget");
        }
        if (entryConfig.getCountingMode() == null) {
            throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "': collection countingMode must not be null");
        }
        if (entryConfig.getVisibilityMode() == null) {
            throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "': collection visibilityMode must not be null");
        }
        if (entryConfig.getRewardGrantMode() == null) {
            throw new IllegalStateException("Quest '" + id + "', phase '" + phase.getPhaseId() + "': collection rewardGrantMode must not be null");
        }
    }

    public QuestDefinition buildAndRegister() {
        QuestDefinition def = build();
        QuestRegistry.register(def);
        return def;
    }
}
