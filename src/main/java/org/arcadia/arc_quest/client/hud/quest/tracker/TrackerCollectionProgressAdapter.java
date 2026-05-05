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
        PhaseDefinition trackedPhase = resolveTrackedCollectionPhase(def, runtime, tracked);
        if (trackedPhase != null) {
            CollectionEntryConfig entry = trackedPhase.getCollectionEntryConfig();
            int target = Math.max(1, entry.getCompletionTarget());
            rows.add(objective("tracked_entry", "Entry: " + trackedPhase.getDisplayName().getString(), ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, tracked), target));
        }
        rows.add(objective("collection_progress", "Collection Progress", completed, total));
        rows.add(objective("discovered_entries", "Discovered Entries", discovered, total));
        if (claimable > 0) rows.add(objective("claimable_rewards", "Claimable Rewards", claimable, claimable));

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
    }

    String phaseId() {
        return SUMMARY_PHASE_ID;
    }

    private PhaseDefinition resolveTrackedCollectionPhase(QuestDefinition def, QuestRuntimeData runtime, String trackedPhaseId) {
        if (def == null || runtime == null || trackedPhaseId == null || trackedPhaseId.isEmpty() || runtime.isPhaseCompleted(trackedPhaseId)) return null;
        PhaseDefinition phase = def.getPhase(trackedPhaseId);
        return phase != null && phase.getCollectionEntryConfig() != null ? phase : null;
    }

    private ObjectiveEntry objective(String rowId, String label, int current, int required) {
        return new ObjectiveEntry(
                ObjectiveType.CUSTOM,
                ResourceLocation.fromNamespaceAndPath("arc_quest", "collection_tracker/" + rowId),
                Math.max(1, required),
                Component.literal(label),
                false,
                false,
                java.util.Map.of("tracker_progress", String.valueOf(Math.max(0, current)))
        );
    }
}
