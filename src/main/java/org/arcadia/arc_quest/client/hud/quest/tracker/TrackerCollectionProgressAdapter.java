package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;

final class TrackerCollectionProgressAdapter {

    private static final String SUMMARY_PHASE_ID = "arc_quest:collection_tracker_summary";
    private List<ObjectiveEntry> cachedObjectives = List.of();
    private String cachedQuestId = "";
    private int cachedCompleted = -1;
    private int cachedTotal = -1;
    private int cachedDiscovered = -1;
    private int cachedClaimable = -1;
    private String cachedTrackedPhaseId = "";

    void reset() {
        cachedObjectives = List.of();
        cachedQuestId = "";
        cachedCompleted = -1;
        cachedTotal = -1;
        cachedDiscovered = -1;
        cachedClaimable = -1;
        cachedTrackedPhaseId = "";
    }

    List<ObjectiveEntry> buildObjectives(QuestDefinition def, QuestRuntimeData runtime, String trackedPhaseId) {
        String questId = runtime.getQuestId();
        int completed = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(questId);
        int total = Math.max(1, ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(questId));
        int discovered = ClientQuestCache.INSTANCE.getCollectionDiscoveredEntryCount(questId);
        int claimable = ClientQuestCache.INSTANCE.getCollectionClaimableRewardCount(questId);
        String tracked = trackedPhaseId != null ? trackedPhaseId : "";
        if (questId.equals(cachedQuestId) && completed == cachedCompleted && total == cachedTotal && discovered == cachedDiscovered && claimable == cachedClaimable && tracked.equals(cachedTrackedPhaseId)) {
            return cachedObjectives;
        }

        List<ObjectiveEntry> rows = new ArrayList<>();
        PhaseDefinition trackedPhase = resolveTrackedCollectionPhase(def, tracked);
        if (trackedPhase != null) {
            CollectionEntryConfig entry = trackedPhase.getCollectionEntryConfig();
            int target = Math.max(1, entry.getCompletionTarget());
            rows.add(objective("Entry: " + trackedPhase.getDisplayName().getString(), ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, tracked), target));
        }
        rows.add(objective("Collection Progress", completed, total));
        rows.add(objective("Discovered Entries", discovered, total));
        if (claimable > 0) rows.add(objective("Claimable Rewards", claimable, claimable));

        cachedQuestId = questId;
        cachedCompleted = completed;
        cachedTotal = total;
        cachedDiscovered = discovered;
        cachedClaimable = claimable;
        cachedTrackedPhaseId = tracked;
        cachedObjectives = List.copyOf(rows);
        return cachedObjectives;
    }

    void applyProgress(QuestRuntimeData runtime, String displayedPhaseId, List<ObjectiveEntry> objectives, String trackedPhaseId, QuestDefinition def) {
        String phaseId = displayedPhaseId != null && !displayedPhaseId.isEmpty() ? displayedPhaseId : runtime.getCurrentPhaseId();
        int row = 0;
        PhaseDefinition trackedPhase = resolveTrackedCollectionPhase(def, trackedPhaseId);
        if (trackedPhase != null && !objectives.isEmpty()) {
            runtime.setObjectiveProgress(phaseId, row++, Math.min(ClientQuestCache.INSTANCE.getCollectionEntryCount(runtime.getQuestId(), trackedPhaseId), trackedPhase.getCollectionEntryConfig().getCompletionTarget()));
        }
        int completed = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(runtime.getQuestId());
        int total = Math.max(1, ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(runtime.getQuestId()));
        int discovered = ClientQuestCache.INSTANCE.getCollectionDiscoveredEntryCount(runtime.getQuestId());
        int claimable = ClientQuestCache.INSTANCE.getCollectionClaimableRewardCount(runtime.getQuestId());
        if (objectives.size() > row) runtime.setObjectiveProgress(phaseId, row++, Math.min(completed, total));
        if (objectives.size() > row) runtime.setObjectiveProgress(phaseId, row++, Math.min(discovered, total));
        if (objectives.size() > row) runtime.setObjectiveProgress(phaseId, row, claimable);
    }

    String phaseId() {
        return SUMMARY_PHASE_ID;
    }

    private PhaseDefinition resolveTrackedCollectionPhase(QuestDefinition def, String trackedPhaseId) {
        if (def == null || trackedPhaseId == null || trackedPhaseId.isEmpty()) return null;
        PhaseDefinition phase = def.getPhase(trackedPhaseId);
        return phase != null && phase.getCollectionEntryConfig() != null ? phase : null;
    }

    private ObjectiveEntry objective(String label, int current, int required) {
        return new ObjectiveEntry(
                ObjectiveType.CUSTOM,
                ResourceLocation.parse(SUMMARY_PHASE_ID + "/" + label.toLowerCase().replace(' ', '_')),
                Math.max(1, required),
                Component.literal(label),
                false,
                false,
                java.util.Map.of()
        );
    }
}
