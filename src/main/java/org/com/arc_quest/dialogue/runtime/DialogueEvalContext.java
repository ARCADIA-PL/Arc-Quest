package org.com.arc_quest.dialogue.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;

import javax.annotation.Nullable;

/**
 * 对话条件评估上下文 —— 替代 ThreadLocal&lt;String&gt; CURRENT_NAMESPACE。
 * <p>
 * <b>不可变</b>，在 DialogueSession 构造时创建，随 choose() 刷新。
 * 条件评估所需的一切信息都从这里显式获取，零隐式依赖。
 *
 * <h3>迁移指南</h3>
 * <pre>
 * // 重构前（DialogueCondition.test 内部）：
 * String ns = DialogueSession.getCurrentNamespace(); // ThreadLocal!
 * cap.hasVisitedNode(ns, nodeId);
 *
 * // 重构后：
 * ctx.progress().hasVisitedNode(ctx.namespace(), nodeId);
 * </pre>
 */
public record DialogueEvalContext(
        /** 服务端玩家 */
        ServerPlayer player,

        /** 正在对话的 NPC 实体，命令触发时为 null */
        @Nullable Entity npc,

        /** 进度命名空间（由 ProgressScope 决定） */
        String namespace,

        /** 统一进度存储 */
        DialogueProgressStore progress,

        /** 日夜周期时钟 [0, 23999]，被 /time set 直接影响 */
        long dayTime,

        /** 单调递增时钟，世界创建以来的总刻数，不受 /time set 影响 */
        long gameTime,

        /** 当前现实时间戳 ms */
        long nowRealTime
) {

    /**
     * 便捷构造：自动从 player 获取时间信息。
     */
    public static DialogueEvalContext of(ServerPlayer player, @Nullable Entity npc,
                                         String namespace, DialogueProgressStore progress) {
        return new DialogueEvalContext(
                player, npc, namespace, progress,
                player.level().getDayTime(),    // 日夜周期（给时间条件用）
                player.level().getGameTime(),   // 单调时钟（给冷却计时用）
                System.currentTimeMillis()
        );
    }

    /**
     * 快捷获取玩家任务能力。
     */
    public IQuestCapability questCap() {
        return player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                .orElseThrow(() -> new IllegalStateException(
                        "Player has no QuestCapability: " + player.getName().getString()));
    }

    /**
     * 日内时间刻 [0, 23999]，供 IsMorning/IsAfternoon/IsNight 等时间条件使用。
     * <p>
     * 注意：此值基于 getDayTime()，会被 /time set 指令直接影响。
     */
    public long dayTimeTick() {
        return dayTime % 24000;
    }
}