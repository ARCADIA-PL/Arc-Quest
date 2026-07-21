package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import javax.annotation.Nullable;

/**
 * 对话条件评估上下文
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

        /** 日夜周期时钟 [0, 24000]，被 /time set 直接影响 */
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
        var time = CoreProcessors.get().time().capture(player);
        return new DialogueEvalContext(
                player, npc, namespace, progress,
                time.dayTime(), time.gameTime(), time.realTime()
        );
    }

    public org.arcadia.arc_quest.core.time.TimeSnapshot timeSnapshot() {
        return new org.arcadia.arc_quest.core.time.TimeSnapshot(nowRealTime, gameTime, dayTime);
    }

    /**
     * 快捷获取玩家任务能力。
     */
    public ArcQuestPlayer questData() {
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) throw new IllegalStateException(
                "Player has no ArcQuestPlayer data: " + player.getName().getString());
        return data;
    }

    /**
     * 日内时间刻 [0, 24000]，供 IsMorning/IsAfternoon/IsNight 等时间条件使用。
     */
    public long dayTimeTick() {
        return CoreProcessors.get().time().dayTime(player.level());
    }
}
