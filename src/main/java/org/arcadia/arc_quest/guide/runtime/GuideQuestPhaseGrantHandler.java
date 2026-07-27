package org.arcadia.arc_quest.guide.runtime;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseActivatedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseCompletedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestStartedEvent;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class GuideQuestPhaseGrantHandler {

    private static final GuideUnlockService UNLOCK_SERVICE = new GuideUnlockService();

    private GuideQuestPhaseGrantHandler() {
    }

    @SubscribeEvent
    public static void onQuestStarted(QuestStartedEvent event) {
        QuestDefinition quest = QuestRegistry.get(event.getQuestId());
        if (quest == null || quest.getInitialPhase() == null) return;
        UNLOCK_SERVICE.grantAll(event.getPlayer(), quest.getInitialPhase().getGuidesToGrantOnEnter());
    }

    @SubscribeEvent
    public static void onPhaseActivated(QuestPhaseActivatedEvent event) {
        PhaseDefinition phase = resolvePhase(event.getQuestId(), event.getToPhaseId());
        if (phase != null) UNLOCK_SERVICE.grantAll(event.getPlayer(), phase.getGuidesToGrantOnEnter());
    }

    @SubscribeEvent
    public static void onPhaseCompleted(QuestPhaseCompletedEvent event) {
        PhaseDefinition phase = resolvePhase(event.getQuestId(), event.getPhaseId());
        if (phase != null) UNLOCK_SERVICE.grantAll(event.getPlayer(), phase.getGuidesToGrantOnComplete());
    }

    private static PhaseDefinition resolvePhase(net.minecraft.resources.ResourceLocation questId, String phaseId) {
        QuestDefinition quest = QuestRegistry.get(questId);
        return quest == null ? null : quest.getPhase(phaseId);
    }
}
