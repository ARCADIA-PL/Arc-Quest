package org.arcadia.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryFormatter;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryStore;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager.PendingNotice;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager.ToastType;
import org.arcadia.arc_quest.quest.api.ChoiceOption;
import org.arcadia.arc_quest.quest.api.CollectionRewardNode;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionRewardResolver;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The single event owner for quest notifications; renderers never infer transitions. */
public class QuestHistoryAndToastListener implements QuestCacheListener {

    @Override
    public void onCollectionEntriesDiscovered(Set<ResourceLocation> entries) {
        for (var runtime : ClientQuestCache.INSTANCE.getAllActiveQuests().values()) {
            var quest = definition(runtime.getQuestId());
            if (quest == null || !quest.hasCollectionSheets()) continue;
            Set<ResourceLocation> notified = new HashSet<>();
            for (var phase : quest.getAllPhases()) {
                if (!phase.hasCollectionSheet() || !runtime.isPhaseActive(phase.getPhaseId())) continue;
                for (var binding : phase.getCollectionSheet().getBindings()) {
                    if (!entries.contains(binding.getEntryId()) || !notified.add(binding.getEntryId())) continue;
                    var entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
                    var row = ClientQuestCache.INSTANCE.getCollectionBindingProgress(runtime.getQuestId(), phase.getPhaseId(), binding.getBindingId());
                    if (entry == null || row == null || !row.revealed()) continue;
                    QuestChangeHistoryStore.INSTANCE.recordCollectionEntryEvent(runtime.getQuestId(), phase.getPhaseId(),
                            binding.getBindingId(), entry.getDisplayName().getString(), true);
                    QuestToastManager.show(ToastType.COLLECTION_ENTRY_DISCOVERED, runtime.getQuestId(), binding.getEntryId().toString(),
                            entry.getDisplayName(), questName(runtime.getQuestId()));
                }
            }
        }
    }

    @Override
    public void onQuestAccepted(String questId) {
        QuestChangeHistoryStore.INSTANCE.recordQuestAccepted(questId);
        QuestChangeNotificationManager.INSTANCE.markNewQuestUnread(questId);
        showQuest(ToastType.QUEST_ACCEPTED, questId);
        refreshAllPending();
    }

    @Override
    public void onQuestCompleted(String questId) {
        QuestChangeHistoryStore.INSTANCE.recordQuestCompleted(questId);
        showQuest(ToastType.QUEST_COMPLETED, questId);
        refreshAllPending();
    }

    @Override
    public void onQuestFailed(String questId) {
        QuestChangeHistoryStore.INSTANCE.recordQuestFailed(questId);
        showQuest(ToastType.QUEST_FAILED, questId);
        refreshAllPending();
    }

    @Override
    public void onQuestUpdated(String questId,
                               QuestRuntimeData newData,
                               @Nullable QuestState oldState,
                               @Nullable String oldPhaseId,
                               @Nullable QuestRuntimeData previousData) {
        QuestDefinition definition = definition(questId);
        boolean notify = QuestNoticePolicy.transientUpdatesAllowed(previousData, newData);
        if (definition != null && previousData != null && newData != null) {
            if (definition.isCollectionQuest() && !definition.hasCollectionSheets()) {
                recordCollectionChanges(questId, definition, previousData, newData, notify);
            } else {
                recordPhaseChanges(questId, previousData, newData, notify);
                recordSnapshotObjectiveChanges(questId, definition, previousData, newData, notify);
                if (definition.hasCollectionSheets()) {
                    recordCollectionChanges(questId, definition, previousData, newData, notify);
                    for (var phase : definition.getAllPhases()) {
                        if (!phase.hasCollectionSheet() || previousData.getCollectionData() == null || newData.getCollectionData() == null) continue;
                        for (var binding : phase.getCollectionSheet().getBindings()) {
                            if (previousData.getCollectionData().isBindingComplete(phase.getPhaseId(), binding.getBindingId())
                                    || !newData.getCollectionData().isBindingComplete(phase.getPhaseId(), binding.getBindingId())) continue;
                            var entry = definition.getCollectionConfig().getEntry(binding.getEntryId());
                            var row = ClientQuestCache.INSTANCE.getCollectionBindingProgress(questId, phase.getPhaseId(), binding.getBindingId());
                            if (entry == null || row == null || !row.revealed()) continue;
                            QuestChangeHistoryStore.INSTANCE.recordCollectionEntryEvent(questId, phase.getPhaseId(), binding.getBindingId(),
                                    entry.getDisplayName().getString(), false);
                            if (notify) QuestToastManager.show(ToastType.COLLECTION_ENTRY_COMPLETED, questId,
                                    phase.getPhaseId() + "/" + binding.getBindingId(), entry.getDisplayName(), questName(questId));
                        }
                    }
                }
            }
        }
        if (definition != null && (!definition.isCollectionQuest() || definition.hasCollectionSheets())) {
            for (String phaseId : QuestNoticePolicy.newlyPendingConfirmations(previousData, newData)) {
                var phase = definition.getPhase(phaseId);
                if (phase != null && !phase.hasChoices()) showPhase(ToastType.PHASE_PENDING_CONFIRM, questId, phaseId);
            }
        }
        refreshPending(questId, newData);
    }

    @Override
    public void onObjectiveProgress(String questId, String phaseId, int objIndex,
                                    int oldProgress, int newProgress, int required) {
        onObjectiveProgress(questId, phaseId, objIndex, oldProgress, newProgress, required, required);
    }

    @Override
    public void onObjectiveProgress(String questId, String phaseId, int objIndex,
                                    int oldProgress, int newProgress, int oldRequired, int required) {
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        QuestDefinition definition = definition(questId);
        if (runtime != null && runtime.getState() == QuestState.ACTIVE && runtime.isPhaseActive(phaseId)
                && definition != null && (!definition.isCollectionQuest() || definition.hasCollectionSheets())) {
            recordObjectiveChange(questId, definition.getPhase(phaseId), objIndex,
                    oldProgress, newProgress, oldRequired, required, true);
        }
        // Hidden objectives and progress regressions can also change the available action.
        refreshPending(questId, runtime);
    }

    @Override
    public void onTrackedPhaseFocusChanged(String questId, @Nullable String oldPhaseId, String newPhaseId) {
        QuestDefinition definition = definition(questId);
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (definition == null || (definition.isCollectionQuest() && !definition.hasCollectionSheets()) || runtime == null
                || runtime.getState() != QuestState.ACTIVE || !runtime.isPhaseActive(newPhaseId)
                || Objects.equals(oldPhaseId, newPhaseId)) return;
        QuestChangeHistoryStore.INSTANCE.recordPhaseSwitched(questId, oldPhaseId, newPhaseId);
        showPhase(ToastType.PHASE_SWITCHED, questId, newPhaseId);
    }

    @Override
    public void onFullSync(Map<String, QuestRuntimeData> activeQuests) {
        replaceAllPending(activeQuests);
    }

    @Override
    public void onFlagsAndVariablesUpdated() {
        refreshAllPending();
    }

    @Override
    public void onCacheCleared() {
        QuestToastManager.clear();
    }

    private void recordPhaseChanges(String questId, QuestRuntimeData previousData,
                                    QuestRuntimeData newData, boolean notify) {
        Set<String> beforeActive = previousData.getActivePhaseIds();
        Set<String> afterActive = newData.getActivePhaseIds();
        Set<String> afterCompleted = newData.getCompletedPhaseIds();
        Set<String> beforePending = previousData.getPendingManualAdvancePhaseIds();
        Set<String> afterPending = newData.getPendingManualAdvancePhaseIds();
        boolean hasPhaseChange = false;
        for (String phaseId : afterActive) {
            if (!beforeActive.contains(phaseId)) {
                QuestChangeHistoryStore.INSTANCE.recordPhaseAdded(questId, phaseId);
                hasPhaseChange = true;
            }
        }
        // A removed/abandoned phase is not a completed phase.
        for (String phaseId : QuestNoticePolicy.newlyCompletedPhases(previousData, newData)) {
            QuestChangeHistoryStore.INSTANCE.recordPhaseCompleted(questId, phaseId);
            if (notify) showPhase(ToastType.PHASE_COMPLETED, questId, phaseId);
            hasPhaseChange = true;
        }
        for (String phaseId : afterPending) {
            if (!beforePending.contains(phaseId)) {
                hasPhaseChange = true;
            }
        }
        String oldCurrent = previousData.getCurrentPhaseId();
        String newCurrent = newData.getCurrentPhaseId();
        if (!Objects.equals(oldCurrent, newCurrent) && newCurrent != null && !newCurrent.isEmpty()) {
            QuestChangeHistoryStore.INSTANCE.recordPhaseSwitched(questId, oldCurrent, newCurrent);
            if (oldCurrent != null && !oldCurrent.isEmpty() && afterCompleted.contains(oldCurrent))
                QuestChangeHistoryStore.INSTANCE.recordPhaseAdvanced(questId, oldCurrent, newCurrent);
            // Automatic advancement is represented by phase completion, without a second toast.
            if (notify && beforeActive.contains(newCurrent) && afterActive.contains(newCurrent))
                showPhase(ToastType.PHASE_SWITCHED, questId, newCurrent);
            hasPhaseChange = true;
        }
        if (hasPhaseChange) markUnreadIfNotTracked(questId);
    }

    private void recordSnapshotObjectiveChanges(String questId, QuestDefinition definition,
                                                QuestRuntimeData previousData, QuestRuntimeData newData,
                                                boolean notify) {
        for (String phaseId : previousData.getActivePhaseIds()) {
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null || (!newData.isPhaseActive(phaseId) && !newData.isPhaseCompleted(phaseId))) continue;
            for (int index = 0; index < phase.getObjectives().size(); index++) {
                ObjectiveEntry objective = phase.getObjectives().get(index);
                int before = previousData.getObjectiveProgress(phaseId, index);
                int after = newData.getObjectiveProgress(phaseId, index);
                int oldRequired = previousData.getRequiredCount(phaseId, index, objective.getRequiredCount());
                int required = newData.getRequiredCount(phaseId, index, objective.getRequiredCount());
                if (before == after && oldRequired == required) continue;
                // Earlier objective deltas have already updated previousData, so they do not replay here.
                recordObjectiveChange(questId, phase, index, before, after, oldRequired, required,
                        notify && newData.isPhaseActive(phaseId));
            }
        }
    }

    private void recordObjectiveChange(String questId, @Nullable PhaseDefinition phase, int index,
                                       int oldProgress, int newProgress, int oldRequired, int required,
                                       boolean notify) {
        if (phase == null || index < 0 || index >= phase.getObjectives().size()) return;
        ObjectiveEntry objective = phase.getObjectives().get(index);
        if (!QuestNoticePolicy.visibleObjective(objective)) return;
        String phaseId = phase.getPhaseId();
        QuestChangeHistoryStore.INSTANCE.recordObjectiveProgress(questId, phaseId, index,
                oldProgress, newProgress, required);
        if (QuestNoticePolicy.completedNow(oldProgress, newProgress, oldRequired, required)) {
            QuestChangeHistoryStore.INSTANCE.recordObjectiveCompleted(questId, phaseId, index, required);
            if (notify) {
                String objectiveId = objective.getObjectiveId();
                String subject = phaseId + "/objective/" +
                        (objectiveId == null || objectiveId.isBlank() ? index : objectiveId);
                Component title = Component.literal(QuestChangeHistoryFormatter.objectiveName(questId, phaseId, index));
                Component detail = questName(questId).copy().append(" · ")
                        .append(ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, phaseId));
                QuestToastManager.show(ToastType.OBJECTIVE_COMPLETE, questId, subject, title, detail);
            }
        }
    }

    private void markUnreadIfNotTracked(String questId) {
        String trackedQuestId = ClientQuestCache.INSTANCE.getTrackedQuestId();
        if (questId.equals(trackedQuestId)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof QuestJournalScreen journal) {
            String selectedId = journal.getSelectedQuestId();
            if (questId.equals(selectedId)) return;
        }
        QuestChangeNotificationManager.INSTANCE.markUnread(questId);
    }

    private void recordCollectionChanges(String questId, QuestDefinition definition,
                                          QuestRuntimeData previousData, QuestRuntimeData newData, boolean notify) {
        CollectionRuntimeData before = previousData.getCollectionData();
        CollectionRuntimeData after = newData.getCollectionData();
        if (after == null) return;
        Set<String> newlyCompleted = QuestNoticePolicy.newlyCompletedPhases(previousData, newData);
        Set<String> beforeDiscovered = before != null ? before.getDiscoveredPhaseIds() : Set.of();
        for (String phaseId : after.getDiscoveredPhaseIds()) {
            if (!beforeDiscovered.contains(phaseId)
                    && QuestNoticePolicy.visibleCollectionEntry(definition, newData, phaseId)) {
                if (notify && !newlyCompleted.contains(phaseId))
                    showPhase(ToastType.COLLECTION_ENTRY_DISCOVERED, questId, phaseId);
                QuestChangeHistoryStore.INSTANCE.recordCollectionEntryDiscovered(questId, phaseId);
            }
        }
        Set<String> beforeUnlocked = before != null ? before.getUnlockedRewardIds() : Set.of();
        Set<String> beforeClaimed = before != null ? before.getClaimedRewardIds() : Set.of();
        for (String rewardId : after.getUnlockedRewardIds()) {
            if (!beforeUnlocked.contains(rewardId) && visibleReward(definition, newData, rewardId)) {
                if (notify && !after.isRewardClaimed(rewardId))
                    showReward(ToastType.COLLECTION_REWARD_UNLOCKED, questId, definition, rewardId);
                QuestChangeHistoryStore.INSTANCE.recordCollectionRewardUnlocked(questId, rewardId);
            }
        }
        for (String rewardId : after.getClaimedRewardIds()) {
            if (!beforeClaimed.contains(rewardId) && visibleReward(definition, newData, rewardId)) {
                if (notify) showReward(ToastType.COLLECTION_REWARD_CLAIMED, questId, definition, rewardId);
                QuestChangeHistoryStore.INSTANCE.recordCollectionRewardClaimed(questId, rewardId);
            }
        }
        for (String phaseId : newlyCompleted) {
            if (QuestNoticePolicy.visibleCollectionEntry(definition, newData, phaseId)) {
                if (notify) showPhase(ToastType.COLLECTION_ENTRY_COMPLETED, questId, phaseId);
                QuestChangeHistoryStore.INSTANCE.recordCollectionEntryCompleted(questId, phaseId);
            }
        }
        recordCollectionCompletionHistory(questId, previousData, newData);
    }

    private boolean visibleReward(QuestDefinition definition, QuestRuntimeData runtime, String rewardId) {
        CollectionRewardNode node = CollectionRewardResolver.findRewardNode(
                definition, definition.getCollectionConfig(), rewardId);
        if (node == null) return false;
        for (String phaseId : definition.getPhaseIds()) {
            PhaseDefinition phase = definition.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) continue;
            if (phase.getCollectionEntryConfig().getRewardNodes().stream()
                    .anyMatch(reward -> rewardId.equals(reward.getRewardNodeId()))) {
                return QuestNoticePolicy.visibleCollectionEntry(definition, runtime, phaseId);
            }
        }
        return true;
    }

    private void showReward(ToastType type, String questId, QuestDefinition definition, String rewardId) {
        CollectionRewardNode node = CollectionRewardResolver.findRewardNode(
                definition, definition.getCollectionConfig(), rewardId);
        if (node == null) return;
        String description = node.getRewards().stream().filter(Objects::nonNull)
                .map(reward -> reward.describe()).filter(text -> text != null && !text.isBlank())
                .limit(3).collect(Collectors.joining(", "));
        Component title = Component.literal(description.isBlank() ? rewardId : description);
        QuestToastManager.show(type, questId, rewardId, title, questName(questId));
    }

    private void showQuest(ToastType type, String questId) {
        if (type.terminal()) QuestToastManager.clearPendingForQuest(questId);
        QuestToastManager.show(type, questId, "quest", questName(questId), Component.empty());
    }

    private void showPhase(ToastType type, String questId, String phaseId) {
        QuestToastManager.show(type, questId, phaseId,
                ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, phaseId), questName(questId));
    }

    private void refreshPending(String questId, @Nullable QuestRuntimeData runtime) {
        QuestToastManager.retainPhaseConfirmations(questId, runtime != null && runtime.getState() == QuestState.ACTIVE
                ? runtime.getPendingManualAdvancePhaseIds() : Set.of());
        if (runtime == null || runtime.getState() != QuestState.ACTIVE) {
            QuestToastManager.clearPendingForQuest(questId);
        } else {
            QuestToastManager.replacePendingForQuest(questId, pendingNotices(questId, runtime, visibleChoices()));
        }
    }

    private void refreshAllPending() {
        replaceAllPending(ClientQuestCache.INSTANCE.getAllActiveQuests());
    }

    private void replaceAllPending(Map<String, QuestRuntimeData> activeQuests) {
        Map<String, List<PendingNotice>> snapshots = new LinkedHashMap<>();
        Predicate<ChoiceOption> visibleChoices = visibleChoices();
        activeQuests.forEach((questId, runtime) -> {
            QuestToastManager.retainPhaseConfirmations(questId, runtime != null && runtime.getState() == QuestState.ACTIVE
                    ? runtime.getPendingManualAdvancePhaseIds() : Set.of());
            List<PendingNotice> pending = pendingNotices(questId, runtime, visibleChoices);
            if (runtime != null && runtime.getState() == QuestState.ACTIVE) snapshots.put(questId, pending);
        });
        // Replace, rather than append: removed quests and obsolete branches must disappear.
        QuestToastManager.replaceAllPending(snapshots);
    }

    private List<PendingNotice> pendingNotices(String questId, @Nullable QuestRuntimeData runtime,
                                              Predicate<ChoiceOption> visibleChoices) {
        return QuestNoticePolicy.pendingPhases(definition(questId), runtime, visibleChoices).stream()
                .filter(pending -> pending.kind() == QuestNoticePolicy.PendingKind.BRANCH_CHOICE)
                .map(pending -> new PendingNotice(
                        ToastType.BRANCH_CHOICE,
                        pending.phaseId(), ClientQuestCache.INSTANCE.getPhaseDisplayComponent(questId, pending.phaseId()),
                        questName(questId)))
                .toList();
    }

    private Predicate<ChoiceOption> visibleChoices() {
        ClientQuestCache cache = ClientQuestCache.INSTANCE;
        Set<ResourceLocation> completed = cache.getCompletedQuestsAsRL();
        Set<String> flags = cache.getAllFlags();
        Map<String, Integer> variables = cache.getAllVariables();
        return choice -> {
            if (choice == null) return false;
            if (choice.getVisibleCondition() == null) return true;
            try {
                return choice.getVisibleCondition().testClient(completed, flags, variables);
            } catch (Exception ignored) {
                // An unsupported custom condition must not erase other actionable notices.
                return false;
            }
        };
    }

    @Nullable
    private QuestDefinition definition(String questId) {
        ResourceLocation id = questId == null ? null : ResourceLocation.tryParse(questId);
        return id == null ? null : QuestRegistry.get(id);
    }

    private Component questName(String questId) {
        return ClientQuestCache.INSTANCE.getQuestDisplayComponent(questId);
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
