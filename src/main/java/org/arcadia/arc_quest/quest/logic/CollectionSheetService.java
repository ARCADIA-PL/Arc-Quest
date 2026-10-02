package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.List;

/** Modern collection tasks use the ordinary phase lifecycle, rewards and objective trackers. */
public final class CollectionSheetService {
    private CollectionSheetService() {}

    public static void initialize(QuestDefinition quest, QuestRuntimeData runtime, CollectionRecordState records) {
        if (!quest.hasCollectionSheets()) return;
        CollectionRuntimeData run = runtime.getOrCreateCollectionData();
        if (!run.getRunId().isEmpty()) return;
        for (String phaseId : quest.getPhaseIds()) {
            PhaseDefinition phase = quest.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionSheet()) continue;
            CollectionSheetDefinition sheet = phase.getCollectionSheet();
            java.util.Set<String> baseline = new java.util.HashSet<>();
            sheet.getBindings().forEach(binding -> {
                if (records.isDiscovered(binding.getEntryId())) baseline.add(binding.getEntryId().toString());
            });
            run.initializeSheet(phaseId, sheet.getBindings().stream().map(EntryRequirementBinding::getBindingId).toList(),
                    sheet.getRequiredCount(), baseline);
        }
    }

    public static boolean satisfied(QuestDefinition quest, PhaseDefinition phase, QuestRuntimeData runtime, CollectionRecordState records) {
        CollectionSheetProgress progress = CollectionProgressProjector.project(quest, phase, runtime, records);
        CollectionRuntimeData run = runtime.getOrCreateCollectionData();
        for (CollectionBindingProgress row : progress.bindings())
            if (row.complete()) run.markBindingComplete(phase.getPhaseId(), row.bindingId());
        return progress.complete();
    }

    public static void refresh(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        for (QuestRuntimeData runtime : List.copyOf(data.getAllActiveQuests().values())) {
            QuestDefinition quest = QuestRegistry.get(ResourceLocation.tryParse(runtime.getQuestId()));
            if (quest == null || !quest.hasCollectionSheets() || runtime.getState() != QuestState.ACTIVE) continue;
            initialize(quest, runtime, data.getCollectionRecords());
            runtime.invalidatePhaseCache();
            QuestProgressHandler.refreshCollectionSheets(player, data, runtime, quest);
        }
    }
}
