package org.arcadia.arc_quest.client.hud.quest.trackingmenu;

import net.minecraft.Util;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class QuestTrackingMenuPhaseSelector {
    private static final long COMMIT_DELAY_MS = 140L;

    private final Map<String, String> selectedPhaseIds = new HashMap<>();
    @Nullable
    private String pendingQuestId;
    @Nullable
    private String pendingPhaseId;
    private long pendingCommitAt;

    void reconcile(List<QuestTrackingMenuEntry> entries,
                   @Nullable String trackedQuestId,
                   @Nullable String trackedPhaseId) {
        Set<String> activeQuestIds = new HashSet<>();
        for (QuestTrackingMenuEntry entry : entries) {
            activeQuestIds.add(entry.questId());
            String selectedPhaseId = selectedPhaseIds.get(entry.questId());
            boolean selectedValid = entry.phaseById(selectedPhaseId) != null;
            boolean hasPendingSelection = entry.questId().equals(pendingQuestId);
            if (entry.questId().equals(trackedQuestId) && !hasPendingSelection
                    && entry.phaseById(trackedPhaseId) != null) {
                selectedPhaseIds.put(entry.questId(), trackedPhaseId);
            } else if (!selectedValid) {
                QuestTrackingMenuPhaseEntry fallback = entry.firstActivePhase();
                if (fallback == null) selectedPhaseIds.remove(entry.questId());
                else selectedPhaseIds.put(entry.questId(), fallback.phaseId());
            }
        }
        selectedPhaseIds.keySet().removeIf(questId -> !activeQuestIds.contains(questId));
        if (pendingQuestId != null && !activeQuestIds.contains(pendingQuestId)) clearPending();
    }

    @Nullable
    QuestTrackingMenuPhaseEntry selectedPhase(QuestTrackingMenuEntry entry) {
        QuestTrackingMenuPhaseEntry selected = entry.phaseById(selectedPhaseIds.get(entry.questId()));
        return selected != null ? selected : entry.firstActivePhase();
    }

    boolean cycle(QuestTrackingMenuEntry entry, int direction) {
        List<QuestTrackingMenuPhaseEntry> phases = entry.activePhases();
        if (phases.size() <= 1 || direction == 0) return false;
        QuestTrackingMenuPhaseEntry selected = selectedPhase(entry);
        int currentIndex = selected == null ? 0 : phases.indexOf(selected);
        int nextIndex = Math.floorMod(currentIndex + Integer.signum(direction), phases.size());
        QuestTrackingMenuPhaseEntry next = phases.get(nextIndex);
        selectedPhaseIds.put(entry.questId(), next.phaseId());
        scheduleCommit(entry.questId(), next.phaseId());
        return true;
    }

    boolean selectImmediate(QuestTrackingMenuEntry entry, String phaseId) {
        QuestTrackingMenuPhaseEntry phase = entry.phaseById(phaseId);
        if (phase == null) return false;
        selectedPhaseIds.put(entry.questId(), phase.phaseId());
        clearPending();
        ClientQuestTrackingController.INSTANCE.requestFocus(entry.questId(), phase.phaseId());
        return true;
    }

    void commitSelected(QuestTrackingMenuEntry entry) {
        QuestTrackingMenuPhaseEntry selected = selectedPhase(entry);
        clearPending();
        if (selected == null) ClientQuestTrackingController.INSTANCE.requestTrack(entry.questId());
        else ClientQuestTrackingController.INSTANCE.requestFocus(entry.questId(), selected.phaseId());
    }

    void update(long now) {
        if (pendingQuestId == null || pendingPhaseId == null || now < pendingCommitAt) return;
        String questId = pendingQuestId;
        String phaseId = pendingPhaseId;
        clearPending();
        ClientQuestTrackingController.INSTANCE.requestFocus(questId, phaseId);
    }

    void flush() {
        if (pendingQuestId == null || pendingPhaseId == null) return;
        String questId = pendingQuestId;
        String phaseId = pendingPhaseId;
        clearPending();
        ClientQuestTrackingController.INSTANCE.requestFocus(questId, phaseId);
    }

    private void scheduleCommit(String questId, String phaseId) {
        pendingQuestId = questId;
        pendingPhaseId = phaseId;
        pendingCommitAt = Util.getMillis() + COMMIT_DELAY_MS;
    }

    private void clearPending() {
        pendingQuestId = null;
        pendingPhaseId = null;
        pendingCommitAt = 0L;
    }
}
