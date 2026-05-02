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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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
    private String tradeShopId = null;
    @Nullable
    private ICondition enterCondition = null;
    private boolean autoEnterByCondition = true;

    // 音效配置
    @Nullable
    private SoundEvent phaseStartSound;
    @Nullable
    private SoundEvent phaseCompleteSound;

    // Ponder 情报场景
    @Nullable
    private ResourceLocation intelSceneId = null;

    private PhaseBuilder(String phaseId) {
        Objects.requireNonNull(phaseId);
        this.phaseId = phaseId;
    }

    public static PhaseBuilder create(String phaseId) {
        return new PhaseBuilder(phaseId);
    }

    // ── 显示名称 ──

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

    // ── 目标 ──

    /**
     * 添加一个目标（传入 ObjectiveBuilder，自动 build）
     */
    public PhaseBuilder objective(ObjectiveBuilder objectiveBuilder) {
        this.objectives.add(objectiveBuilder.build());
        return this;
    }

    /**
     * 添加一个已构建好的 ObjectiveEntry
     */
    public PhaseBuilder objective(ObjectiveEntry entry) {
        this.objectives.add(entry);
        return this;
    }

    // ── 跳转 ──

    /**
     * 无条件跳转到指定阶段（默认分支）
     */
    public PhaseBuilder thenGoTo(String targetPhaseId) {
        this.transitions.add(new PhaseTransition(
                targetPhaseId, null, this.transitionPriorityCounter++));
        return this;
    }

    /**
     * 有条件跳转（优先于无条件分支）
     */
    public PhaseBuilder thenGoToIf(String targetPhaseId, ICondition condition) {
        this.transitions.add(new PhaseTransition(
                targetPhaseId, condition, this.transitionPriorityCounter++));
        return this;
    }

    // ── 选择分支（对话选项） ──

    /**
     * 添加玩家可见的选择项
     */
    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId) {
        this.choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, null));
        return this;
    }

    /**
     * 添加带可见条件的选择项
     */
    public PhaseBuilder choice(Component text, String flagToSet, String targetPhaseId,
                               ICondition visibleCondition) {
        this.choices.add(new ChoiceOption(text, flagToSet, targetPhaseId, visibleCondition));
        return this;
    }

    // ── 奖励 ──

    public PhaseBuilder reward(IReward reward) {
        this.phaseRewards.add(reward);
        return this;
    }

    // ── Flags ──

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
                org.arcadia.arc_quest.questmarker.api.QuestMarkerType.QUEST_OBJECTIVE, 0, 256, 20, true, false, java.util.Map.of()));
        return this;
    }

    public PhaseBuilder markRelatedObject(MarkSpec spec) {
        this.relatedMarks.add(spec);
        return this;
    }

    // ── Phase 交易 ──

    /**
     * 为此阶段配置专属交易商店。
     *
     * @param shopId 交易商店注册 ID
     */
    public PhaseBuilder phaseTrade(String shopId) {
        this.tradeShopId = shopId;
        return this;
    }

    // ── Ponder 情报场景 ──

    /**
     * 绑定一个 Ponder 情报场景，玩家在任务日志中可通过 [PHASE INTEL] 按钮打开。
     *
     * <p>推荐配合 {@link ArcQuestPonderHelper} 生成 sceneId：
     * <pre>
     *   // 本模组阶段
     *   .intelScene(ArcQuestPonderHelper.questPhaseId("arc_quest:epic_prologue", "arc_quest:defend_village"))
     *
     *   // 附属模组阶段（自动编码命名空间，避免冲突）
     *   .intelScene(ArcQuestPonderHelper.questPhaseId("addon:prologue", "addon:phase_1"))
     * </pre>
     *
     * <p>对应的场景须已在 {@link org.arcadia.arc_quest.client.ponder.ArcQuestPonderSceneRegistry} 中注册，
     * 且结构文件（.nbt）已放置于 {@code assets/<modid>/ponder/} 目录。
     *
     * @param sceneId Ponder 场景 ID，由 {@code ArcQuestPonderHelper.questPhaseId()} 生成
     */
    public PhaseBuilder intelScene(ResourceLocation sceneId) {
        this.intelSceneId = sceneId;
        return this;
    }

    /**
     * 快捷方法：直接传入 questId + phaseId，内部调用
     * {@link ArcQuestPonderHelper#questPhaseId}。
     *
     * <p>支持带或不带命名空间的 ID：
     * <pre>
     *   .intelScene("arc_quest:epic_prologue", "arc_quest:defend_village")
     *   .intelScene("addon:prologue",          "addon:phase_1")
     *   .intelScene("epic_prologue",           "defend_village")
     * </pre>
     */
    public PhaseBuilder intelScene(String questId, String phaseId) {
        this.intelSceneId = ArcQuestPonderHelper.questPhaseId(questId, phaseId);
        return this;
    }

    // ════════════════════════════════════════
    //  音效配置
    // ════════════════════════════════════════

    public PhaseBuilder phaseStartSound(SoundEvent sound) {
        this.phaseStartSound = sound;
        return this;
    }

    public PhaseBuilder phaseCompleteSound(SoundEvent sound) {
        this.phaseCompleteSound = sound;
        return this;
    }

    // ════════════════════════════════════════
    //  视觉配置（高可扩展 API）
    // ════════════════════════════════════════

    /**
     * 设置视觉配置（高级 API）。
     */
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

    /**
     * 便捷方法：添加阶段开始立绘。
     */
    public PhaseBuilder startSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.PHASE_START, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加阶段开始立绘（默认缩放 1.0）。
     */
    public PhaseBuilder startSplash(ResourceLocation texture) {
        return startSplash(texture, 1.0f);
    }

    /**
     * 便捷方法：添加阶段完成立绘。
     */
    public PhaseBuilder completeSplash(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.splash(SplashType.PHASE_COMPLETE, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加阶段完成立绘（默认缩放 1.0）。
     */
    public PhaseBuilder completeSplash(ResourceLocation texture) {
        return completeSplash(texture, 1.0f);
    }

    /**
     * 便捷方法：添加阶段标签图标。
     */
    public PhaseBuilder labelIcon(ResourceLocation texture, float scale) {
        this.visualConfigBuilder.icon(IconPosition.PHASE_LABEL, texture, scale);
        return this;
    }

    /**
     * 便捷方法：添加阶段标签图标（默认缩放 1.0）。
     */
    public PhaseBuilder labelIcon(ResourceLocation texture) {
        return labelIcon(texture, 1.0f);
    }

    /**
     * 便捷方法：设置阶段主题色。
     */
    public PhaseBuilder themeColor(int color) {
        this.visualConfigBuilder.themeColor(color);
        return this;
    }

    /**
     * 便捷方法：从 ChatFormatting 设置阶段主题色。
     */
    public PhaseBuilder themeColor(ChatFormatting formatting) {
        this.visualConfigBuilder.themeColorFromChatFormatting(formatting);
        return this;
    }

    // ── 构建 ──

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
                this.intelSceneId,
                this.enterCondition,
                this.autoEnterByCondition
        );
    }
}