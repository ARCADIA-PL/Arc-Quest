package org.com.arc_quest.quest.event;

import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.QuestState;
import org.jetbrains.annotations.Nullable;

/**
 * 任务状态变更事件——用于驱动客户端 UI 动画。
 * <p>
 * 这是纯数据载体，不可变。
 * <p>
 * 用法：
 * <pre>
 *   QuestEventBus.subscribe(event -> {
 *       if (event.getType() == Type.QUEST_COMPLETED) {
 *           MySplashRenderer.trigger(event.getQuestId());
 *       }
 *   });
 * </pre>
 */
public final class QuestChangeEvent {

    private final Type type;
    @Nullable
    private final ResourceLocation questId;
    @Nullable
    private final QuestState oldState;
    @Nullable
    private final QuestState newState;
    @Nullable
    private final String oldPhaseId;
    @Nullable
    private final String newPhaseId;
    private final int objectiveIndex;
    private final int currentProgress;
    private final int requiredProgress;

    private QuestChangeEvent(Builder builder) {
        this.type = builder.type;
        this.questId = builder.questId;
        this.oldState = builder.oldState;
        this.newState = builder.newState;
        this.oldPhaseId = builder.oldPhaseId;
        this.newPhaseId = builder.newPhaseId;
        this.objectiveIndex = builder.objectiveIndex;
        this.currentProgress = builder.currentProgress;
        this.requiredProgress = builder.requiredProgress;
    }

    public static QuestChangeEvent questUnlocked(ResourceLocation questId) {
        return new Builder(Type.QUEST_UNLOCKED)
                .questId(questId)
                .states(QuestState.LOCKED, QuestState.AVAILABLE)
                .build();
    }

    // ── Getters ──

    public static QuestChangeEvent questAccepted(ResourceLocation questId) {
        return new Builder(Type.QUEST_ACCEPTED)
                .questId(questId)
                .states(QuestState.AVAILABLE, QuestState.ACTIVE)
                .build();
    }

    public static QuestChangeEvent questCompleted(ResourceLocation questId) {
        return new Builder(Type.QUEST_COMPLETED)
                .questId(questId)
                .states(QuestState.ACTIVE, QuestState.COMPLETED)
                .build();
    }

    public static QuestChangeEvent questFailed(ResourceLocation questId) {
        return new Builder(Type.QUEST_FAILED)
                .questId(questId)
                .states(QuestState.ACTIVE, QuestState.FAILED)
                .build();
    }

    public static QuestChangeEvent phaseChanged(ResourceLocation questId,
                                                String oldPhaseId,
                                                String newPhaseId) {
        return new Builder(Type.PHASE_CHANGED)
                .questId(questId)
                .phases(oldPhaseId, newPhaseId)
                .build();
    }

    public static QuestChangeEvent objectiveProgressed(ResourceLocation questId,
                                                       int objectiveIndex,
                                                       int current,
                                                       int required) {
        return new Builder(Type.OBJECTIVE_PROGRESSED)
                .questId(questId)
                .objective(objectiveIndex, current, required)
                .build();
    }

    public static QuestChangeEvent objectiveCompleted(ResourceLocation questId,
                                                      int objectiveIndex,
                                                      int required) {
        return new Builder(Type.OBJECTIVE_COMPLETED)
                .questId(questId)
                .objective(objectiveIndex, required, required)
                .build();
    }

    public static QuestChangeEvent fullSync() {
        return new Builder(Type.FULL_SYNC).build();
    }

    public Type getType() {
        return this.type;
    }

    @Nullable
    public ResourceLocation getQuestId() {
        return this.questId;
    }

    // ════════════════════════════════════════
    //  静态工厂方法
    // ════════════════════════════════════════

    @Nullable
    public QuestState getOldState() {
        return this.oldState;
    }

    @Nullable
    public QuestState getNewState() {
        return this.newState;
    }

    @Nullable
    public String getOldPhaseId() {
        return this.oldPhaseId;
    }

    @Nullable
    public String getNewPhaseId() {
        return this.newPhaseId;
    }

    public int getObjectiveIndex() {
        return this.objectiveIndex;
    }

    public int getCurrentProgress() {
        return this.currentProgress;
    }

    public int getRequiredProgress() {
        return this.requiredProgress;
    }

    @Override
    public String toString() {
        return "QuestChangeEvent{" + this.type
                + (this.questId != null ? ", quest=" + this.questId : "")
                + "}";
    }

    public enum Type {
        /**
         * 新任务可接取
         */
        QUEST_UNLOCKED,
        /**
         * 玩家接取了任务
         */
        QUEST_ACCEPTED,
        /**
         * 任务完成
         */
        QUEST_COMPLETED,
        /**
         * 任务失败
         */
        QUEST_FAILED,
        /**
         * 阶段切换
         */
        PHASE_CHANGED,
        /**
         * 单个目标进度更新
         */
        OBJECTIVE_PROGRESSED,
        /**
         * 单个目标完成
         */
        OBJECTIVE_COMPLETED,
        /**
         * 所有数据全量同步（登录/维度切换）
         */
        FULL_SYNC
    }

    // ── 内部 Builder ──

    private static final class Builder {
        private final Type type;
        private ResourceLocation questId;
        private QuestState oldState;
        private QuestState newState;
        private String oldPhaseId;
        private String newPhaseId;
        private int objectiveIndex = -1;
        private int currentProgress;
        private int requiredProgress;

        Builder(Type type) {
            this.type = type;
        }

        Builder questId(ResourceLocation id) {
            this.questId = id;
            return this;
        }

        Builder states(QuestState oldState, QuestState newState) {
            this.oldState = oldState;
            this.newState = newState;
            return this;
        }

        Builder phases(String oldPhaseId, String newPhaseId) {
            this.oldPhaseId = oldPhaseId;
            this.newPhaseId = newPhaseId;
            return this;
        }

        Builder objective(int index, int current, int required) {
            this.objectiveIndex = index;
            this.currentProgress = current;
            this.requiredProgress = required;
            return this;
        }

        QuestChangeEvent build() {
            return new QuestChangeEvent(this);
        }
    }
}