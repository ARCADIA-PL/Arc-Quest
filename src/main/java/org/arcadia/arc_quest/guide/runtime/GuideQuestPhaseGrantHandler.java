package org.arcadia.arc_quest.guide.runtime;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseActivatedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestPhaseCompletedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestStartedEvent;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuideQuestPhaseGrantHandler {

    private static final GuideUnlockService UNLOCK_SERVICE = new GuideUnlockService();

    private GuideQuestPhaseGrantHandler() {
    }

    @SubscribeEvent
    public static void onQuestStarted(QuestStartedEvent event) {
        GuideAutoTriggerService.onQuestStarted(event.getPlayer());
        QuestDefinition quest = QuestRegistry.get(event.getQuestId());
        if (quest == null) return;
        var playerData = ArcQuestPlayerManager.get(event.getPlayer());
        var runtime = playerData == null ? null : playerData.getActiveQuest(event.getQuestId().toString());
        PhaseDefinition initialPhase = runtime == null ? null : quest.getPhase(runtime.getCurrentPhaseId());
        if (initialPhase != null) UNLOCK_SERVICE.grantAll(event.getPlayer(), initialPhase.getGuidesToGrantOnEnter());
    }

    @SubscribeEvent
    public static void onPhaseActivated(QuestPhaseActivatedEvent event) {
        GuideAutoTriggerService.onPhaseActivated(event.getPlayer());
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
