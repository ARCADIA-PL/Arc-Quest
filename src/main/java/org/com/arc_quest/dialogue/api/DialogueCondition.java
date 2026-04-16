package org.com.arc_quest.dialogue.api;

import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.capability.QuestRuntimeData;

/**
 * 对话条件门控。
 * <p>
 * 在服务端评估，决定某个 {@link DialogueChoice} 是否对当前玩家可见。
 *
 * <pre>
 * JSON 示例:
 * { "type": "HAS_QUEST", "quest_id": "rescue_villager" }
 * { "type": "QUEST_COMPLETED", "quest_id": "rescue_villager" }
 * { "type": "QUEST_PHASE", "quest_id": "rescue_villager", "phase_id": "find_cave" }
 * { "type": "MIN_LEVEL", "level": 10 }
 * { "type": "NOT", "condition": { "type": "HAS_QUEST", "quest_id": "..." } }
 * </pre>
 */
public sealed interface DialogueCondition {

    /**
     * 在服务端评估条件。
     *
     * @param player 当前玩家
     * @return true = 条件满足
     */
    boolean test(ServerPlayer player);

    // ═══════════════════════════════════════════════════════
    //  具体条件类型
    // ═══════════════════════════════════════════════════════

    /** 玩家拥有指定任务（任何状态）。 */
    record HasQuest(String questId) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                    .map(cap -> cap.isQuestActive(questId) || cap.isQuestCompleted(questId))
                    .orElse(false);
        }
    }

    /** 玩家的指定任务处于活跃状态 (ACTIVE)。 */
    record QuestActive(String questId) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                    .map(cap -> {
                        QuestRuntimeData data = cap.getActiveQuest(questId);
                        return data != null && data.getState() == QuestState.ACTIVE;
                    }).orElse(false);
        }
    }

    /** 玩家已完成指定任务。 */
    record QuestCompleted(String questId) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                    .map(cap -> cap.isQuestCompleted(questId))
                    .orElse(false);
        }
    }

    /** 玩家的指定任务处于特定阶段。 */
    record QuestPhase(String questId, String phaseId) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                    .map(cap -> {
                        QuestRuntimeData data = cap.getActiveQuest(questId);
                        return data != null
                                && data.getState() == QuestState.ACTIVE
                                && java.util.Objects.equals(data.getCurrentPhaseId(), phaseId);
                    }).orElse(false);
        }
    }

    /** 玩家等级 >= level。 */
    record MinLevel(int level) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return player.experienceLevel >= level;
        }
    }

    /** 逻辑取反。 */
    record Not(DialogueCondition inner) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return !inner.test(player);
        }
    }

    /** 所有条件均满足 (AND)。 */
    record All(java.util.List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return conditions.stream().allMatch(c -> c.test(player));
        }
    }

    /** 任一条件满足 (OR)。 */
    record Any(java.util.List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return conditions.stream().anyMatch(c -> c.test(player));
        }
    }

    /** 无条件通过。 */
    record Always() implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) { return true; }
    }
}