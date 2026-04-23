package org.com.arc_quest.quest.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.api.*;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 流式构建 QuestDefinition 的顶层 Builder。
 *
 * <pre>
 *   QuestBuilder.create("tutorial_main")
 *       .category(QuestCategory.ARCHON)
 *       .displayName("黎明之路")
 *       .phase(PhaseBuilder.create("step1")...)
 *       .phase(PhaseBuilder.create("step2")...)
 *       .reward(new ItemReward(...))
 *       .buildAndRegister();
 * </pre>
 */
public final class QuestBuilder {

    private final ResourceLocation id;
    private final List<ICondition> unlockConditions = new ArrayList<>();
    private final LinkedHashMap<String, PhaseDefinition> phases = new LinkedHashMap<>();
    private final List<IReward> completionRewards = new ArrayList<>();
    private final List<String> flagsOnAccept = new ArrayList<>();
    private final List<String> flagsOnComplete = new ArrayList<>();
    private QuestCategory category = QuestCategory.ADVENTURE;
    private Component displayName;
    private Component description = Component.empty();
    @Nullable
    private ResourceLocation iconTexture;
    private int sortOrder = 0;
    private boolean repeatable = false;
    private String initialPhaseId = null;
    private QuestVisualConfig.Builder visualConfigBuilder = QuestVisualConfig.builder();
    @Nullable
    private String chapterShopId;
    private boolean chapterShopPersistent = true;
    
    // 音效配置
    @Nullable private SoundEvent chapterStartSound;
    @Nullable private SoundEvent chapterFailSound;
    @Nullable private SoundEvent chapterCompleteSound;

    private QuestBuilder(ResourceLocation id) {
        this.id = id;
    }

    /**
     * 创建 Builder，使用完整 ResourceLocation（推荐）。
     * <p>
     * 支持自定义命名空间，适合主模组和附属模组使用。
     *
     * @param id 完整的资源位置，如 "arc_quest:my_quest" 或 "my_mod:my_quest"
     */
    public static QuestBuilder create(ResourceLocation id) {
        return new QuestBuilder(id);
    }

    /**
     * 创建 Builder，使用字符串 ID（自动解析命名空间）。
     * <p>
     * - 如果包含 ":"，则直接解析为 ResourceLocation
     * - 如果不包含 ":"，则默认使用 arc_quest 命名空间
     *
     * @param id 资源 ID，如 "arc_quest:my_quest" 或 "my_quest"
     */
    public static QuestBuilder create(String id) {
        if (id.contains(":")) {
            return new QuestBuilder(ResourceLocation.tryParse(id));
        } else {
            return new QuestBuilder(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, id));
        }
    }

    // ════════════════════════════════════════
    //  基本属性
    // ════════════════════════════════════════

    public QuestBuilder category(QuestCategory category) {
        this.category = category;
        return this;
    }

    public QuestBuilder displayName(String literal) {
        this.displayName = Component.literal(literal);
        return this;
    }

    public QuestBuilder displayName(Component component) {
        this.displayName = component;
        return this;
    }

    public QuestBuilder description(String literal) {
        this.description = Component.literal(literal);
        return this;
    }

    public QuestBuilder description(Component component) {
        this.description = component;
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

    // ════════════════════════════════════════
    //  解锁条件
    // ════════════════════════════════════════

    public QuestBuilder unlockCondition(ICondition condition) {
        this.unlockConditions.add(condition);
        return this;
    }

    /**
     * 快捷：需要指定任务已完成（支持智能命名空间解析）
     * <p>
     * - 如果包含 ":"，则直接解析
     * - 如果不包含 ":"，则自动添加 arc_quest: 前缀
     */
    public QuestBuilder requiresQuest(String questId) {
        ResourceLocation location;
        if (questId.contains(":")) {
            location = ResourceLocation.tryParse(questId);
        } else {
            location = ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, questId);
        }
        this.unlockConditions.add(ICondition.questCompleted(location));
        return this;
    }

    public QuestBuilder requiresQuest(ResourceLocation questId) {
        this.unlockConditions.add(ICondition.questCompleted(questId));
        return this;
    }

    /**
     * 快捷：需要指定 Flag 已设置
     */
    public QuestBuilder requiresFlag(String flag) {
        this.unlockConditions.add(ICondition.flagSet(flag));
        return this;
    }

    // ════════════════════════════════════════
    //  阶段
    // ════════════════════════════════════════

    /**
     * 添加阶段（传入 PhaseBuilder，自动 build）。
     * 第一个添加的阶段自动成为 initialPhase。
     */
    public QuestBuilder phase(PhaseBuilder phaseBuilder) {
        PhaseDefinition phase = phaseBuilder.build();
        return this.phase(phase);
    }

    public QuestBuilder phase(PhaseDefinition phase) {
        String pid = phase.getPhaseId();
        if (this.phases.containsKey(pid)) {
            throw new IllegalArgumentException(
                    "Duplicate phase id '" + pid + "' in quest '" + this.id + "'");
        }
        this.phases.put(pid, phase);
        if (this.initialPhaseId == null) {
            this.initialPhaseId = pid;
        }
        return this;
    }

    /**
     * 显式指定起始阶段（覆盖默认的"第一个添加的"）
     */
    public QuestBuilder startAt(String phaseId) {
        this.initialPhaseId = phaseId;
        return this;
    }

    // ════════════════════════════════════════
    //  完成奖励
    // ════════════════════════════════════════

    public QuestBuilder reward(IReward reward) {
        this.completionRewards.add(reward);
        return this;
    }

    // ════════════════════════════════════════
    //  章节商店
    // ════════════════════════════════════════

    /**
     * 配置章节商店。
     *
     * @param shopId     交易商店注册 ID
     * @param persistent true=长效（任务完成后仍可访问），false=非长效
     */
    public QuestBuilder chapterShop(String shopId, boolean persistent) {
        this.chapterShopId = shopId;
        this.chapterShopPersistent = persistent;
        return this;
    }

    public QuestBuilder chapterShop(String shopId) {
        return chapterShop(shopId, true);
    }

    // ════════════════════════════════════════
    //  音效配置
    // ════════════════════════════════════════

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

    // ════════════════════════════════════════
    //  Flags
    // ════════════════════════════════════════

    public QuestBuilder setFlagOnAccept(String flag) {
        this.flagsOnAccept.add(flag);
        return this;
    }

    public QuestBuilder setFlagOnComplete(String flag) {
        this.flagsOnComplete.add(flag);
        return this;
    }

    // ════════════════════════════════════════
    //  视觉配置（高可扩展 API）
    // ════════════════════════════════════════

    /**
     * 设置视觉配置（高级 API，直接传入完整配置）。
     */
    public QuestBuilder visualConfig(QuestVisualConfig config) {
        if (config != null) {
            // 重建配置
            this.visualConfigBuilder = QuestVisualConfig.builder()
                    .themeColor(config.getThemeColor());
            // 复制所有立绘配置
            for (SplashType type : SplashType.values()) {
                config.getSplash(type).ifPresent(asset ->
                        this.visualConfigBuilder.splash(type, asset));
            }
            // 复制所有图标配置
            for (IconPosition pos : IconPosition.values()) {
                config.getIcon(pos).ifPresent(asset ->
                        this.visualConfigBuilder.icon(pos, asset));
            }
        }
        return this;
    }

    /**
     * 便捷方法：添加任务获得时的立绘。
     */
    public QuestBuilder acquisitionSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.QUEST_ACQUIRED, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加任务获得时的立绘（默认缩放 1.0）。
     */
    public QuestBuilder acquisitionSplash(ResourceLocation texture) {
        return acquisitionSplash(texture, 1.0f);
    }

    /**
     * 便捷方法：添加任务详情的立绘。
     */
    public QuestBuilder detailSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.QUEST_DETAIL, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加任务详情的立绘（默认缩放 1.0）。
     */
    public QuestBuilder detailSplash(ResourceLocation texture) {
        return detailSplash(texture, 1.0f);
    }

    /**
     * 便捷方法：添加任务完成时的立绘。
     */
    public QuestBuilder completionSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.QUEST_COMPLETED, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加任务完成时的立绘（默认缩放 1.0）。
     */
    public QuestBuilder completionSplash(ResourceLocation texture) {
        return completionSplash(texture, 1.0f);
    }

    /**
     * 便捷方法：添加任务列表图标。
     */
    public QuestBuilder listIcon(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.icon(IconPosition.QUEST_LIST, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加任务列表图标（默认缩放 1.0）。
     */
    public QuestBuilder listIcon(ResourceLocation texture) {
        return listIcon(texture, 1.0f);
    }

    /**
     * 便捷方法：添加任务标题图标。
     */
    public QuestBuilder titleIcon(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.icon(IconPosition.QUEST_TITLE, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加任务标题图标（默认缩放 1.0）。
     */
    public QuestBuilder titleIcon(ResourceLocation texture) {
        return titleIcon(texture, 1.0f);
    }

    /**
     * 便捷方法：设置主题色（ARGB 整数）。
     */
    public QuestBuilder themeColor(int color) {
        this.visualConfigBuilder.themeColor(color);
        return this;
    }

    /**
     * 便捷方法：从 ChatFormatting 设置主题色。
     */
    public QuestBuilder themeColor(ChatFormatting formatting) {
        this.visualConfigBuilder.themeColorFromChatFormatting(formatting);
        return this;
    }

    // ════════════════════════════════════════
    //  构建
    // ════════════════════════════════════════

    public QuestDefinition build() {
        if (this.displayName == null) {
            this.displayName = Component.literal(this.id.getPath());
        }
        if (this.phases.isEmpty()) {
            throw new IllegalStateException("Quest '" + this.id + "' has no phases");
        }
        if (this.initialPhaseId == null) {
            this.initialPhaseId = this.phases.keySet().iterator().next();
        }

        // 验证所有 transition 引用的 phaseId 都存在
        for (PhaseDefinition phase : this.phases.values()) {
            for (PhaseTransition tr : phase.getTransitions()) {
                if (!this.phases.containsKey(tr.getTargetPhaseId())) {
                    throw new IllegalStateException(
                            "Quest '" + this.id + "', phase '" + phase.getPhaseId()
                                    + "' references unknown phase '" + tr.getTargetPhaseId() + "'");
                }
            }
            for (ChoiceOption ch : phase.getChoices()) {
                if (!this.phases.containsKey(ch.getTargetPhaseId())) {
                    throw new IllegalStateException(
                            "Quest '" + this.id + "', phase '" + phase.getPhaseId()
                                    + "' choice references unknown phase '" + ch.getTargetPhaseId() + "'");
                }
            }
        }

        return new QuestDefinition(
                this.id,
                this.category,
                this.displayName,
                this.description,
                this.iconTexture,
                this.sortOrder,
                this.repeatable,
                new ArrayList<>(this.unlockConditions),
                new LinkedHashMap<>(this.phases),
                this.initialPhaseId,
                new ArrayList<>(this.completionRewards),
                new ArrayList<>(this.flagsOnAccept),
                new ArrayList<>(this.flagsOnComplete),
                this.visualConfigBuilder.build(),
                this.chapterShopId,
                this.chapterShopPersistent,
                this.chapterStartSound,
                this.chapterFailSound,
                this.chapterCompleteSound
        );
    }

    /**
     * 构建并直接注册到全局 QuestRegistry。
     *
     * @return 构建好的 QuestDefinition（方便链式引用）
     */
    public QuestDefinition buildAndRegister() {
        QuestDefinition def = this.build();
        QuestRegistry.register(def);
        return def;
    }
}