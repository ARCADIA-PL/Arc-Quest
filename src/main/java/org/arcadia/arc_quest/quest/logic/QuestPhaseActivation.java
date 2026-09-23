package org.arcadia.arc_quest.quest.logic;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseActivatedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseChangedEvent;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import static org.arcadia.arc_quest.quest.logic.QuestObjectiveService.registerPhaseObjectives;
import static org.arcadia.arc_quest.quest.logic.QuestProgressEffects.evaluateCondition;

/**
 * 阶段激活与条件扫描；一次调用的计数和标志变化保存在局部上下文中。
 */
final class QuestPhaseActivation {
    private QuestPhaseActivation() {
    }

    static boolean canEnterPhase(ServerPlayer player,
            ArcQuestPlayer data,
            PhaseDefinition phase,
            @Nullable QuestRuntimeData qdata) {
        if (data != null && qdata.isEnterConditionCached(phase.getPhaseId())) {
            return qdata.canEnterPhaseCached(phase.getPhaseId());
        }
        ICondition cond = phase.getEnterCondition();
        if (cond == null) return true;
        boolean result = evaluateCondition(cond, player, data.getCompletedQuestLocations(), data);
        if (data != null) qdata.setEnterConditionCached(phase.getPhaseId(), result);
        return result;
    }

    static boolean activatePhase(ServerPlayer player,
            ArcQuestPlayer data,
            QuestRuntimeData qdata,
            QuestDefinition def,
            String fromPhaseId,
            String targetPhaseId,
            boolean enforceEnterCondition,
            ActivationContext ctx) {
        PhaseDefinition next = def.getPhase(targetPhaseId);
        if (next == null) return false;
        if (qdata.isPhaseActive(targetPhaseId) || qdata.isPhaseCompleted(targetPhaseId)) return false;

        if (enforceEnterCondition && !canEnterPhase(player, data, next, qdata)) {
            return false;
        }

        qdata.activatePhase(next.getPhaseId(), next.getObjectives().size());
        registerPhaseObjectives(player, def, next);

        for (String flag : next.getFlagsToSetOnEnter()) {
            data.setFlag(flag);
            ctx.flagsChanged = true;
        }

        QuestMarkerTriggerService.triggerPhase(
                player, data, qdata, next, MarkTrigger.PHASE_ENTERED);

        ctx.activatedCount++;

        QuestEventBus.fire(QuestChangeEvent.phaseChanged(def.getId(), fromPhaseId, next.getPhaseId()));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseChangedEvent(player, def.getId(), fromPhaseId, next.getPhaseId()));
        MinecraftForge.EVENT_BUS.post(new QuestPhaseActivatedEvent(player, def.getId(), fromPhaseId, next.getPhaseId(), !enforceEnterCondition));
        return true;
    }

    static int tryAutoEnterPhases(ServerPlayer player,
            ArcQuestPlayer data,
            QuestRuntimeData qdata,
            QuestDefinition def,
            String fromPhaseId,
            ActivationContext ctx) {
        int before = ctx.activatedCount;
        boolean changed;

        do {
            changed = false;

            for (String pid : def.getPhaseIds()) {
                if (qdata.isPhaseActive(pid) || qdata.isPhaseCompleted(pid)) continue;

                PhaseDefinition phase = def.getPhase(pid);
                if (phase == null) continue;
                if (!phase.isAutoEnterByCondition()) continue;
                if (phase.getEnterCondition() == null) continue;
                if (!canEnterPhase(player, data, phase, qdata)) continue;

                boolean ok = activatePhase(player, data, qdata, def, fromPhaseId, pid, false, ctx);
                if (ok) changed = true;
            }
        } while (changed);

        return ctx.activatedCount - before;
    }

    static final class ActivationContext {
        int activatedCount;
        boolean flagsChanged;
    }
}
