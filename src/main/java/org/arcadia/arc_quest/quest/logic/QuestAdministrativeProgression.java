package org.arcadia.arc_quest.quest.logic;

import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

/**
 * 管理命令的强制推进流程；复用正常阶段完成和任务收尾路径。
 */
final class QuestAdministrativeProgression {
    private final QuestPhaseProgression phases;
    private final QuestLifecycleService lifecycle;

    QuestAdministrativeProgression(QuestPhaseProgression phases, QuestLifecycleService lifecycle) {
        this.phases = Objects.requireNonNull(phases);
        this.lifecycle = Objects.requireNonNull(lifecycle);
    }

    QuestRejectCodeDictionary.Code forceCompleteResult(ServerPlayer player, String questId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) {
            return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        }

        int safetyLimit = Math.max(1, def.getPhaseIds().size() * 2);
        for (int pass = 0; pass < safetyLimit && qdata.getState() == QuestState.ACTIVE; pass++) {
            Set<String> activePhaseIds = qdata.getActivePhaseIds();
            if (activePhaseIds.isEmpty()) break;
            for (String phaseId : activePhaseIds) {
                phases.forceCompletePhaseInternal(player, data, qdata, def, phaseId);
                if (qdata.getState() != QuestState.ACTIVE) break;
            }
        }

        if (qdata.getState() == QuestState.ACTIVE) {
            if (!qdata.getActivePhaseIds().isEmpty()) {
                return QuestRejectCodeDictionary.Code.UNKNOWN;
            }
            lifecycle.doCompleteQuest(player, data, qdata, def, "force-completed");
        }
        return QuestRejectCodeDictionary.Code.OK;
    }

    QuestRejectCodeDictionary.Code forceCompletePhaseResult(ServerPlayer player, String questId, String phaseId) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        QuestDefinition def = QuestRegistry.get(ResourceLocation.parse(questId));
        if (def == null) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) {
            return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        }
        if (!qdata.isPhaseActive(phaseId)) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        if (def.getPhase(phaseId) == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;

        phases.forceCompletePhaseInternal(player, data, qdata, def, phaseId);
        return QuestRejectCodeDictionary.Code.OK;
    }
}
