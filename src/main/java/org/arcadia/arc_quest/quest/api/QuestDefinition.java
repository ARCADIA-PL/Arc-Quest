package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 不可变任务定义，由 QuestBuilder 构建。
 */
public final class QuestDefinition {

    private final ResourceLocation id;
    private final QuestCategory category;
    private final QuestText displayName;
    private final QuestText description;
    @Nullable
    private final ResourceLocation iconTexture;
    private final int sortOrder;
    private final boolean repeatable;
    private final QuestVisualConfig visualConfig;

    private final List<ICondition> unlockConditions;
    private final LinkedHashMap<String, PhaseDefinition> phases;
    private final String initialPhaseId;
    private final List<IReward> completionRewards;
    private final List<String> flagsToSetOnAccept;
    private final List<String> flagsToSetOnComplete;
    @Nullable
    private final String chapterShopId;
    private final ChapterShopType chapterShopType;
    private final boolean chapterShopPersistent;

    // 音效配置
    @Nullable
    private final SoundEvent chapterStartSound;
    @Nullable
    private final SoundEvent chapterFailSound;
    @Nullable
    private final SoundEvent chapterCompleteSound;

    private final QuestCompletionPolicy completionPolicy;
    private final int completionRequiredCount;
    @Nullable
    private final String completionTargetPhaseId;

    public QuestDefinition(ResourceLocation id,
                           QuestCategory category,
                           QuestText displayName,
                           QuestText description,
                           @Nullable ResourceLocation iconTexture,
                           int sortOrder,
                           boolean repeatable,
                           List<ICondition> unlockConditions,
                           LinkedHashMap<String, PhaseDefinition> phases,
                           String initialPhaseId,
                           List<IReward> completionRewards,
                           List<String> flagsToSetOnAccept,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig) {
        this(id, category, displayName, description, iconTexture, sortOrder, repeatable,
                unlockConditions, phases, initialPhaseId, completionRewards,
                flagsToSetOnAccept, flagsToSetOnComplete, visualConfig, null, ChapterShopType.TRADE, false);
    }

    public QuestDefinition(ResourceLocation id,
                           QuestCategory category,
                           QuestText displayName,
                           QuestText description,
                           @Nullable ResourceLocation iconTexture,
                           int sortOrder,
                           boolean repeatable,
                           List<ICondition> unlockConditions,
                           LinkedHashMap<String, PhaseDefinition> phases,
                           String initialPhaseId,
                           List<IReward> completionRewards,
                           List<String> flagsToSetOnAccept,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @Nullable String chapterShopId,
                           ChapterShopType chapterShopType,
                           boolean chapterShopPersistent) {
        this(id, category, displayName, description, iconTexture, sortOrder, repeatable,
                unlockConditions, phases, initialPhaseId, completionRewards,
                flagsToSetOnAccept, flagsToSetOnComplete, visualConfig,
                chapterShopId, chapterShopType, chapterShopPersistent, null, null, null, QuestCompletionPolicy.ALL, 0, null);
    }

    public QuestDefinition(ResourceLocation id,
                           QuestCategory category,
                           QuestText displayName,
                           QuestText description,
                           @Nullable ResourceLocation iconTexture,
                           int sortOrder,
                           boolean repeatable,
                           List<ICondition> unlockConditions,
                           LinkedHashMap<String, PhaseDefinition> phases,
                           String initialPhaseId,
                           List<IReward> completionRewards,
                           List<String> flagsToSetOnAccept,
                           List<String> flagsToSetOnComplete,
                           QuestVisualConfig visualConfig,
                           @Nullable String chapterShopId,
                           ChapterShopType chapterShopType,
                           boolean chapterShopPersistent,
                           @Nullable SoundEvent chapterStartSound,
                           @Nullable SoundEvent chapterFailSound,
                           @Nullable SoundEvent chapterCompleteSound,
                           QuestCompletionPolicy completionPolicy,
                           int completionRequiredCount,
                           @Nullable String completionTargetPhaseId) {
        Objects.requireNonNull(id, "Quest id must not be null");
        Objects.requireNonNull(category);
        Objects.requireNonNull(displayName);
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Quest '" + id + "' must have at least one phase");
        }
        if (!phases.containsKey(initialPhaseId)) {
            throw new IllegalArgumentException(
                    "Quest '" + id + "': initialPhaseId '" + initialPhaseId
                            + "' not found in phases " + phases.keySet());
        }
        this.id = id;
        this.category = category;
        this.displayName = displayName;
        this.description = description != null ? description : QuestText.component(Component.empty());
        this.iconTexture = iconTexture;
        this.sortOrder = sortOrder;
        this.repeatable = repeatable;
        this.visualConfig = visualConfig != null ? visualConfig : QuestVisualConfig.EMPTY;
        this.unlockConditions = Collections.unmodifiableList(unlockConditions);
        this.phases = new LinkedHashMap<>(phases);          // 防御性拷贝
        this.initialPhaseId = initialPhaseId;
        this.completionRewards = Collections.unmodifiableList(completionRewards);
        this.flagsToSetOnAccept = Collections.unmodifiableList(flagsToSetOnAccept);
        this.flagsToSetOnComplete = Collections.unmodifiableList(flagsToSetOnComplete);
        this.chapterShopId = chapterShopId;
        this.chapterShopType = chapterShopType != null ? chapterShopType : ChapterShopType.TRADE;
        this.chapterShopPersistent = chapterShopPersistent;
        this.chapterStartSound = chapterStartSound;
        this.chapterFailSound = chapterFailSound;
        this.chapterCompleteSound = chapterCompleteSound;
        this.completionPolicy = completionPolicy != null ? completionPolicy : QuestCompletionPolicy.ALL;
        this.completionRequiredCount = Math.max(0, completionRequiredCount);
        this.completionTargetPhaseId = completionTargetPhaseId;
        int phaseCount = this.phases.size();
        switch (this.completionPolicy) {
            case ALL, ANY -> {
                if (this.completionRequiredCount > 0) {
                    throw new IllegalArgumentException(
                            "Quest '" + id + "': completionRequiredCount only valid for N_OF_M");
                }
                if (this.completionTargetPhaseId != null && !this.completionTargetPhaseId.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Quest '" + id + "': completionTargetPhaseId only valid for SPECIFIC_PHASE");
                }
            }
            case N_OF_M -> {
                if (this.completionRequiredCount < 1 || this.completionRequiredCount > phaseCount) {
                    throw new IllegalArgumentException(
                            "Quest '" + id + "': N_OF_M requires completionRequiredCount in [1," + phaseCount + "], got " + this.completionRequiredCount);
                }
                if (this.completionTargetPhaseId != null && !this.completionTargetPhaseId.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Quest '" + id + "': completionTargetPhaseId not allowed with N_OF_M");
                }
            }
            case SPECIFIC_PHASE -> {
                if (this.completionRequiredCount > 0) {
                    throw new IllegalArgumentException(
                            "Quest '" + id + "': completionRequiredCount not allowed with SPECIFIC_PHASE");
                }
                if (this.completionTargetPhaseId == null || this.completionTargetPhaseId.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Quest '" + id + "': SPECIFIC_PHASE requires completionTargetPhaseId");
                }
                if (!this.phases.containsKey(this.completionTargetPhaseId)) {
                    throw new IllegalArgumentException(
                            "Quest '" + id + "': completionTargetPhaseId '" + this.completionTargetPhaseId + "' not found in phases");
                }
            }
        }
    }

    // ── Getters ──

    public ResourceLocation getId() {
        return this.id;
    }

    public QuestCategory getCategory() {
        return this.category;
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

    @Nullable
    public ResourceLocation getIconTexture() {
        return this.iconTexture;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public boolean isRepeatable() {
        return this.repeatable;
    }

    public List<ICondition> getUnlockConditions() {
        return this.unlockConditions;
    }

    public String getInitialPhaseId() {
        return this.initialPhaseId;
    }

    @Nullable
    public PhaseDefinition getPhase(String phaseId) {
        return this.phases.get(phaseId);
    }

    public PhaseDefinition getInitialPhase() {
        return this.phases.get(this.initialPhaseId);
    }

    public Collection<PhaseDefinition> getAllPhases() {
        return Collections.unmodifiableCollection(this.phases.values());
    }

    public Set<String> getPhaseIds() {
        return Collections.unmodifiableSet(this.phases.keySet());
    }

    public List<IReward> getCompletionRewards() {
        return this.completionRewards;
    }

    public List<String> getFlagsToSetOnAccept() {
        return this.flagsToSetOnAccept;
    }

    public List<String> getFlagsToSetOnComplete() {
        return this.flagsToSetOnComplete;
    }

    @Nullable
    public String getChapterShopId() {
        return this.chapterShopId;
    }

    public ChapterShopType getChapterShopType() {
        return this.chapterShopType;
    }

    public boolean isChapterShopPersistent() {
        return this.chapterShopPersistent;
    }

    public boolean hasChapterShop() {
        return this.chapterShopId != null;
    }

    @Nullable
    public SoundEvent getChapterStartSound() {
        return chapterStartSound;
    }

    @Nullable
    public SoundEvent getChapterFailSound() {
        return chapterFailSound;
    }

    @Nullable
    public SoundEvent getChapterCompleteSound() {
        return chapterCompleteSound;
    }


    public QuestVisualConfig getVisualConfig() {
        return this.visualConfig;
    }

    @Nullable
    public ResourceLocation getLegacyIcon() {
        return this.iconTexture;
    }

    public QuestCompletionPolicy getCompletionPolicy() {
        return completionPolicy;
    }

    @Nullable
    public String getCompletionTargetPhaseId() {
        return completionTargetPhaseId;
    }

    public int getCompletionRequiredCount() {
        return completionRequiredCount;
    }

    @Nullable
    public ResourceLocation getIconFor(IconPosition position) {
        return this.visualConfig.getIcon(position)
                .map(VisualAsset::texture)
                .orElse(this.iconTexture);  // 回退到旧字段
    }


    public int getThemeColor() {
        return this.visualConfig.getThemeColor();
    }


    public boolean hasSplash(SplashType type) {
        return this.visualConfig.getSplash(type).isPresent();
    }


    public Optional<VisualAsset> getSplashConfig(SplashType type) {
        return this.visualConfig.getSplash(type);
    }

    /**
     * 检测解锁条件是否全部满足
     */
    public boolean canUnlock(ServerPlayer player,
                             Set<ResourceLocation> completedQuests,
                             Set<String> flags,
                             Map<String, Integer> variables) {
        for (ICondition cond : this.unlockConditions) {
            if (!cond.test(player, completedQuests, flags, variables)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估阶段完成后应跳转到哪个 Phase。
     *
     * @return 下一阶段 ID，或 null 表示任务完成
     */
    @Nullable
    public String evaluateNextPhase(ServerPlayer player,
                                    PhaseDefinition currentPhase,
                                    Set<ResourceLocation> completedQuests,
                                    Set<String> flags,
                                    Map<String, Integer> variables) {
        // 如果有 choices，由玩家手动选择（不走 transitions）
        if (currentPhase.hasChoices()) {
            return null;
        }

        List<PhaseTransition> sorted = new ArrayList<>(currentPhase.getTransitions());
        Collections.sort(sorted);

        for (PhaseTransition transition : sorted) {
            ICondition cond = transition.getCondition();
            if (cond == null || cond.test(player, completedQuests, flags, variables)) {
                return transition.getTargetPhaseId();
            }
        }
        // 无命中 → 任务完成
        return null;
    }

    @Override
    public String toString() {
        return "Quest[" + this.id + ", " + this.phases.size() + " phases]";
    }
}