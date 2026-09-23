package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

/**
 * 任务完成与目标数量规则；不执行游戏副作用，也不修改运行时状态。
 */
final class QuestProgressRules {
    private QuestProgressRules() {
    }

    static boolean shouldCompleteQuest(QuestDefinition def, QuestRuntimeData qdata) {
        int done = qdata.getCompletedPhaseIds().size();
        return switch (def.getCompletionPolicy()) {
            case ALL -> done >= def.getPhaseIds().size();
            case ANY -> done >= 1;
            case N_OF_M -> done >= Math.max(1, def.getCompletionRequiredCount());
            case SPECIFIC_PHASE -> {
                String target = def.getCompletionTargetPhaseId();
                yield target != null && qdata.getCompletedPhaseIds().contains(target);
            }
        };
    }

    static int requiredCount(String mode, int fallbackRequired, int base, int min, int max,
            int playerLevel, int countPerLevel, int variableValue, int countPerVar) {
        int safeMin = Math.max(1, min);
        int safeMax = max > 0 && max < safeMin ? safeMin : max;
        int computed = switch (mode) {
            case "player_level", "level_scale" -> base + Math.max(0, playerLevel) * countPerLevel;
            case "variable" -> base + variableValue * countPerVar;
            case "fixed" -> base;
            default -> fallbackRequired;
        };
        computed = Math.max(safeMin, computed);
        if (safeMax > 0) computed = Math.min(safeMax, computed);
        return Math.max(1, computed);
    }
}
