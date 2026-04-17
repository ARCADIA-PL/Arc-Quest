package org.com.arc_quest.dialogue.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
                                && Objects.equals(data.getCurrentPhaseId(), phaseId);
                    }).orElse(false);
        }
    }

    /**
     * Phase 区间条件 - 检查玩家是否处于指定的 Phase 区间。
     *
     * @param questId       任务ID
     * @param startPhase    起始 Phase（可为 null 表示无下限）
     * @param endPhase      结束 Phase（可为 null 表示无上限）
     * @param includeStart  是否包含起始 Phase
     * @param includeEnd    是否包含结束 Phase
     * @param boundaryType  边界类型：BEFORE/AFTER/BETWEEN
     */
    record QuestPhaseRange(
            String questId,
            String startPhase,
            String endPhase,
            boolean includeStart,
            boolean includeEnd,
            RangeType boundaryType
    ) implements DialogueCondition {

        public enum RangeType {
            BEFORE,    // 在 startPhase 之前
            AFTER,     // 在 endPhase 之后
            BETWEEN    // 在 startPhase 和 endPhase 之间
        }

        @Override
        public boolean test(ServerPlayer player) {
            return player.getCapability(QuestCapabilityProvider.QUEST_CAP)
                    .map(cap -> {
                        QuestRuntimeData data = cap.getActiveQuest(questId);
                        if (data == null || data.getState() != QuestState.ACTIVE) {
                            return false;
                        }

                        String currentPhase = data.getCurrentPhaseId();
                        List<String> phaseOrder = getPhaseOrder(cap, questId);
                        if (phaseOrder.isEmpty()) {
                            return false;
                        }

                        int currentIndex = phaseOrder.indexOf(currentPhase);
                        if (currentIndex == -1) {
                            return false;
                        }

                        return switch (boundaryType) {
                            case BEFORE -> checkBefore(phaseOrder, currentIndex, startPhase, includeStart);
                            case AFTER -> checkAfter(phaseOrder, currentIndex, endPhase, includeEnd);
                            case BETWEEN -> checkBetween(phaseOrder, currentIndex, startPhase, endPhase, includeStart, includeEnd);
                        };
                    }).orElse(false);
        }

        private boolean checkBefore(List<String> phaseOrder, int currentIndex, String targetPhase, boolean includeTarget) {
            if (targetPhase == null) return true;
            int targetIndex = phaseOrder.indexOf(targetPhase);
            if (targetIndex == -1) return false;
            return includeTarget ? currentIndex <= targetIndex : currentIndex < targetIndex;
        }

        private boolean checkAfter(List<String> phaseOrder, int currentIndex, String targetPhase, boolean includeTarget) {
            if (targetPhase == null) return true;
            int targetIndex = phaseOrder.indexOf(targetPhase);
            if (targetIndex == -1) return false;
            return includeTarget ? currentIndex >= targetIndex : currentIndex > targetIndex;
        }

        private boolean checkBetween(List<String> phaseOrder, int currentIndex, String startPhase, String endPhase, boolean includeStart, boolean includeEnd) {
            int startIndex = startPhase != null ? phaseOrder.indexOf(startPhase) : 0;
            int endIndex = endPhase != null ? phaseOrder.indexOf(endPhase) : phaseOrder.size() - 1;

            if (startIndex == -1 || endIndex == -1) return false;
            if (startIndex > endIndex) return false;

            int effectiveStart = includeStart ? startIndex : startIndex + 1;
            int effectiveEnd = includeEnd ? endIndex : endIndex - 1;

            return currentIndex >= effectiveStart && currentIndex <= effectiveEnd;
        }

        /**
         * 获取任务的 Phase 顺序列表。
         */
        private List<String> getPhaseOrder(IQuestCapability cap, String questId) {
            ResourceLocation rl = ResourceLocation.tryParse(questId);
            if (rl == null) return List.of();

            QuestDefinition def = QuestRegistry.get(rl);
            if (def == null) return List.of();

            // 使用 getPhaseIds() 获取有序的 Phase ID 列表
            return new ArrayList<>(def.getPhaseIds());
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
    record All(List<DialogueCondition> conditions) implements DialogueCondition {
        @Override
        public boolean test(ServerPlayer player) {
            return conditions.stream().allMatch(c -> c.test(player));
        }
    }

    /** 任一条件满足 (OR)。 */
    record Any(List<DialogueCondition> conditions) implements DialogueCondition {
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