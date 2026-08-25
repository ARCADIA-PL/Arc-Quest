package org.arcadia.arc_quest.client.hud.quest.tracker;


import org.arcadia.arc_quest.client.hud.HudText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            rows.add(objective("tracked_entry", HudText.of("tracker.entry", trackedPhase.getDisplayName()), ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, tracked), target));
        }
        rows.add(objective("collection_progress", HudText.string("tracker.collection_progress"), completed, total));
        rows.add(objective("discovered_entries", HudText.string("tracker.discovered_entries"), discovered, total));
        if (claimable > 0) rows.add(objective("claimable_rewards", HudText.string("tracker.claimable_rewards"), claimable, claimable));

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
        if (def == null || runtime == null || trackedPhaseId == null || trackedPhaseId.isEmpty() || runtime.isPhaseCompleted(trackedPhaseId))
            return null;
        PhaseDefinition phase = def.getPhase(trackedPhaseId);
        return phase != null && phase.getCollectionEntryConfig() != null ? phase : null;
    }

    private ObjectiveEntry objective(String rowId, String label, int current, int required) {
        return objective(rowId, Component.literal(label), current, required);
    }

    private ObjectiveEntry objective(String rowId, Component label, int current, int required) {
        return new ObjectiveEntry(
                ObjectiveType.CUSTOM,
                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "collection_tracker/" + rowId),
                Math.max(1, required),
                label,
                false,
                false,
                Map.of("tracker_progress", String.valueOf(Math.max(0, current)))
        );
    }
}
