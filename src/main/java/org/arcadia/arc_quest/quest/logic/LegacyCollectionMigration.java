package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

/** Conservative, idempotent import. Unmapped legacy tasks and reward receipts remain intact. */
public final class LegacyCollectionMigration {
    private LegacyCollectionMigration() {}
    public static void migrate(ArcQuestPlayer player) {
        CollectionRecordState records = player.getCollectionRecords();
        for (QuestRuntimeData runtime : player.getAllActiveQuests().values()) {
            QuestDefinition quest = QuestRegistry.getServerDefinition(net.minecraft.resources.ResourceLocation.tryParse(runtime.getQuestId()));
            if (quest == null || !quest.isCollectionQuest() || quest.hasCollectionSheets() || !runtime.hasCollectionData()) continue;
            CollectionRuntimeData legacy = runtime.getCollectionData();
            legacy.getClaimedRewardIds().forEach(records::markLegacyRewardClaimed);
            for (PhaseDefinition phase : quest.getAllPhases()) {
                String key = quest.getId() + "/" + phase.getPhaseId();
                if (records.isLegacyMigrated(key) || !legacy.isDiscovered(phase.getPhaseId())) continue;
                boolean mapped = false;
                for (CollectionEntryDefinition entry : CollectionEntryRegistry.serverSnapshot().values()) {
                    boolean sameSubject = phase.getObjectives().stream().anyMatch(objective ->
                            entry.getSubjectId() != null && entry.getSubjectId().equals(objective.getTargetId()));
                    if (!sameSubject) continue;
                    records.discover(entry.getEntryId());
                    for (ObjectiveEntry step : entry.getResearchObjectives()) {
                        if (phase.getObjectives().stream().anyMatch(old -> old.getType().equals(step.getType())
                                && old.getTargetId().equals(step.getTargetId()) && !old.hasTargetTag() && !step.hasTargetTag()))
                            records.importProgress(entry.getEntryId(), CollectionProgressProjector.researchKey(step.getObjectiveId()),
                                    legacy.getEntryCount(phase.getPhaseId()), step.getRequiredCount());
                    }
                    mapped = true;
                }
                if (mapped) records.markLegacyMigrated(key);
            }
        }
    }
}
