package org.arcadia.arc_quest.quest.logic;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import static org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncQuestStateAndPush;

/**
 * 任务推进与游戏环境的连接：同步、声音和条件上下文；仅在服务端主线程调用。
 */
final class QuestProgressEffects {
    private QuestProgressEffects() {
    }

    static void playChapterSound(ServerPlayer player, SoundEvent sound) {
        if (player == null || sound == null) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    static void syncToClient(ServerPlayer player, String questId) {
        if (player == null || questId == null || questId.isBlank()) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null) {
            ArcQuestLog.debug(ArcQuestLog.Category.QUEST_NETWORK,
                    "Skipped quest sync because quest is not active: player={}, quest={}",
                    player.getGameProfile().getName(), questId);
            return;
        }
        syncQuestStateAndPush(player, qdata);
    }

    static boolean evaluateCondition(ICondition condition,
            ServerPlayer player,
            Set<ResourceLocation> completedQuests,
            ArcQuestPlayer data) {
        return CoreProcessors.get().conditions().evaluate(condition, new QuestConditionContext(
                player, completedQuests, data.getAllFlags(), data.getAllVariables()));
    }
}
