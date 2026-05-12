package org.arcadia.arc_quest.quest.network;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryStore;
import org.arcadia.arc_quest.client.hud.quest.toast.PhaseUpdateToast;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import javax.annotation.Nullable;
import java.util.*;

public class QuestHistoryAndToastListener implements QuestCacheListener {

    @Override
    public void onQuestAccepted(String questId) {
        QuestChangeHistoryStore.INSTANCE.recordQuestAccepted(questId);
    }

    @Override
    public void onQuestCompleted(String questId) {
        QuestChangeHistoryStore.INSTANCE.recordQuestCompleted(questId);
    }

    @Override
    public void onQuestFailed(String questId) {
        QuestChangeHistoryStore.INSTANCE.recordQuestFailed(questId);
        String name = ClientQuestCache.INSTANCE.getQuestDisplayName(questId);
        QuestToastManager.show(QuestToastManager.ToastType.QUEST_FAILED, name);
    }

    @Override
    public void onQuestUpdated(String questId,
                               QuestRuntimeData newData,
                               @Nullable QuestState oldState,
                               @Nullable String oldPhaseId,
                               @Nullable QuestRuntimeData previousData) {
        recordQuestDeltaHistory(questId, previousData, newData);
        maybeShowCollectionToasts(questId, previousData, newData);
    }

    @Override
    public void onObjectiveProgress(String questId, String phaseId, int objIndex,
                                    int oldProgress, int newProgress, int required) {
        QuestChangeHistoryStore.INSTANCE.recordObjectiveProgress(questId, phaseId, objIndex, oldProgress, newProgress, required);
        if (required > 0 && oldProgress < required && newProgress >= required) {
            QuestChangeHistoryStore.INSTANCE.recordObjectiveCompleted(questId, phaseId, objIndex, required);
        }
    }

    private void recordQuestDeltaHistory(String questId, @Nullable QuestRuntimeData previousData, QuestRuntimeData newData) {
        if (previousData == null || newData == null) return;

        Set<String> beforeActive = previousData.getActivePhaseIds();
        Set<String> afterActive = newData.getActivePhaseIds();
        Set<String> beforeCompleted = previousData.getCompletedPhaseIds();
        Set<String> afterCompleted = newData.getCompletedPhaseIds();
        Set<String> beforePending = previousData.getPendingManualAdvancePhaseIds();
        Set<String> afterPending = newData.getPendingManualAdvancePhaseIds();

        for (String phaseId : afterActive) {
            if (!beforeActive.contains(phaseId))
                QuestChangeHistoryStore.INSTANCE.recordPhaseAdded(questId, phaseId);
        }
        for (String phaseId : afterCompleted) {
            if (!beforeCompleted.contains(phaseId))
                QuestChangeHistoryStore.INSTANCE.recordPhaseCompleted(questId, phaseId);
        }
        for (String phaseId : afterPending) {
            if (!beforePending.contains(phaseId)) {
                String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, phaseId);
                int themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0xFFD166);
                QuestHudOverlay.INSTANCE.showPhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.PENDING_CONFIRM);
            }
        }

        String oldCurrent = previousData.getCurrentPhaseId();
        String newCurrent = newData.getCurrentPhaseId();
        if (!Objects.equals(oldCurrent, newCurrent) && newCurrent != null && !newCurrent.isEmpty()) {
            QuestChangeHistoryStore.INSTANCE.recordPhaseSwitched(questId, oldCurrent, newCurrent);
            if (oldCurrent != null && !oldCurrent.isEmpty() && afterCompleted.contains(oldCurrent))
                QuestChangeHistoryStore.INSTANCE.recordPhaseAdvanced(questId, oldCurrent, newCurrent);
        }
    }

    private void maybeShowCollectionToasts(String questId, @Nullable QuestRuntimeData previousData, QuestRuntimeData newData) {
        CollectionRuntimeData before = previousData != null ? previousData.getCollectionData() : null;
        CollectionRuntimeData after = newData.getCollectionData();
        if (after == null) return;
        String questName = ClientQuestCache.INSTANCE.getQuestDisplayName(questId);

        Set<String> beforeDiscovered = before != null ? before.getDiscoveredPhaseIds() : Set.of();
        for (String phaseId : after.getDiscoveredPhaseIds())
            if (!beforeDiscovered.contains(phaseId)) {
                QuestToastManager.show(QuestToastManager.ToastType.COLLECTION_ENTRY_DISCOVERED, questName);
                QuestChangeHistoryStore.INSTANCE.recordCollectionEntryDiscovered(questId, phaseId);
            }

        Set<String> beforeUnlocked = before != null ? before.getUnlockedRewardIds() : Set.of();
        for (String rewardId : after.getUnlockedRewardIds())
            if (!beforeUnlocked.contains(rewardId)) {
                QuestToastManager.show(QuestToastManager.ToastType.COLLECTION_REWARD_UNLOCKED, questName);
                QuestChangeHistoryStore.INSTANCE.recordCollectionRewardUnlocked(questId, rewardId);
            }

        Set<String> beforeClaimed = before != null ? before.getClaimedRewardIds() : Set.of();
        for (String rewardId : after.getClaimedRewardIds())
            if (!beforeClaimed.contains(rewardId)) {
                QuestToastManager.show(QuestToastManager.ToastType.COLLECTION_REWARD_CLAIMED, questName);
                QuestChangeHistoryStore.INSTANCE.recordCollectionRewardClaimed(questId, rewardId);
            }

        Set<String> beforeCompleted = previousData != null ? previousData.getCompletedPhaseIds() : Set.of();
        for (String phaseId : newData.getCompletedPhaseIds())
            if (!beforeCompleted.contains(phaseId)) {
                String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(questId, phaseId);
                int themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(questId, 0x66FF66);
                QuestHudOverlay.INSTANCE.showPhaseUpdateToast(phaseName, themeColor, PhaseUpdateToast.Kind.COMPLETED);
                QuestChangeHistoryStore.INSTANCE.recordCollectionEntryCompleted(questId, phaseId);
            }

        recordCollectionCompletionHistory(questId, previousData, newData);
    }

    private void recordCollectionCompletionHistory(String questId, @Nullable QuestRuntimeData previousData, QuestRuntimeData newData) {
        if (previousData == null || newData == null) return;
        int beforeDone = countCompletedCollectionEntries(questId, previousData);
        int afterDone = countCompletedCollectionEntries(questId, newData);
        int total = ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(questId);
        if (total > 0 && beforeDone < total && afterDone >= total) {
            QuestChangeHistoryStore.INSTANCE.recordCollectionQuestCompleted(questId);
        }

        ResourceLocation rl = ResourceLocation.tryParse(questId);
        QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
        if (def == null || !def.isCollectionQuest()) return;
        Set<String> beforeCategories = completedCollectionCategories(def, previousData);
        Set<String> afterCategories = completedCollectionCategories(def, newData);
        for (String categoryId : afterCategories) {
            if (!beforeCategories.contains(categoryId))
                QuestChangeHistoryStore.INSTANCE.recordCollectionCategoryCompleted(questId, categoryId);
        }
    }

    private int countCompletedCollectionEntries(String questId, QuestRuntimeData runtime) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
        if (def == null || runtime == null) return 0;
        int completed = 0;
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase != null && phase.hasCollectionEntryConfig() && runtime.isPhaseCompleted(phaseId)) completed++;
        }
        return completed;
    }

    private Set<String> completedCollectionCategories(QuestDefinition def, @Nullable QuestRuntimeData runtime) {
        Set<String> completed = new LinkedHashSet<>();
        if (def.getCollectionConfig() == null || runtime == null) return completed;
        for (var category : def.getCollectionConfig().getCategories()) {
            String categoryId = category.getCategoryId();
            int total = 0;
            int done = 0;
            for (String phaseId : def.getPhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null || !phase.hasCollectionEntryConfig()) continue;
                if (!categoryId.equals(phase.getCollectionEntryConfig().getCategoryId())) continue;
                total++;
                if (runtime.isPhaseCompleted(phaseId)) done++;
            }
            if (total > 0 && done >= total) completed.add(categoryId);
        }
        return completed;
    }
}
