package org.arcadia.arc_quest.quest.logic.profile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.api.event.quest.QuestAcceptedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestStartedEvent;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.logic.profile.collection.*;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.logic.QuestMarkerTriggerService;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.guide.runtime.GuideUnlockService;

import java.util.Set;

public final class CollectionQuestEngine {

    private static final GuideUnlockService GUIDE_UNLOCK_SERVICE = new GuideUnlockService();

    private CollectionQuestEngine() {
    }

    public static QuestRejectCodeDictionary.Code acceptQuest(ServerPlayer player,
                                                             ArcQuestPlayer data,
                                                             QuestDefinition def) {
        String questId = def.getId().toString();
        if (data.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.ALREADY_ACTIVE;
        }
        if ((data.isQuestCompleted(questId) || data.isQuestFailed(questId)) && !def.isRepeatable()) {
            return QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE;
        }

        PhaseDefinition firstPhase = def.selectInitialPhase(player.getRandom());
        if (firstPhase == null) {
            return QuestRejectCodeDictionary.Code.NO_INITIAL_PHASE;
        }

        long acceptedTick = player.getServer() != null ? player.getServer().getTickCount() : 0L;
        var acceptedTime = CoreProcessors.get().time().capture(player);
        long acceptedRealMs = acceptedTime.realTime();
        long acceptedDayTime = acceptedTime.dayTime();

        QuestRuntimeData runtime = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                Math.max(0, firstPhase.getObjectives().size()),
                acceptedTick,
                acceptedRealMs,
                acceptedDayTime
        );

        CollectionRuntimeData collectionData = new CollectionRuntimeData();
        initializeQuest(player, data, def, runtime, collectionData);
        runtime.setCollectionData(collectionData);
        data.addActiveQuest(runtime);
        QuestMarkerTriggerService.triggerQuest(
                player, data, runtime, def, MarkTrigger.QUEST_ACCEPTED);
        for (String activePhaseId : runtime.getActivePhaseIds()) {
            PhaseDefinition activePhase = def.getPhase(activePhaseId);
            if (activePhase != null) {
                GUIDE_UNLOCK_SERVICE.grantAll(player, activePhase.getGuidesToGrantOnEnter());
                QuestMarkerTriggerService.triggerPhase(
                        player, data, runtime, activePhase, MarkTrigger.PHASE_ENTERED);
            }
        }

        boolean flagsChanged = false;
        for (String flag : def.getFlagsToSetOnAccept()) {
            data.setFlag(flag);
            flagsChanged = true;
        }

        QuestSyncCoordinator.syncQuestStateAndPush(player, runtime);
        TrackedQuestService.onQuestAccepted(player, questId);
        if (flagsChanged) {
            QuestSyncCoordinator.syncFlagsVarsAndPush(player, data);
        }

        QuestEventBus.fire(QuestChangeEvent.questAccepted(def.getId()));
        NeoForge.EVENT_BUS.post(new QuestAcceptedEvent(player, def.getId()));
        NeoForge.EVENT_BUS.post(new QuestStartedEvent(player, def.getId()));
        return QuestRejectCodeDictionary.Code.OK;
    }

    public static void initializeQuest(ServerPlayer player,
                                       ArcQuestPlayer data,
                                       QuestDefinition def,
                                       QuestRuntimeData runtime,
                                       CollectionRuntimeData collectionData) {
        CollectionQuestConfig config = def.getCollectionConfig();
        boolean revealAll = config != null && config.isRevealAllEntriesByDefault();
        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();
        Set<String> flags = data.getAllFlags();

        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) {
                continue;
            }
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) {
                continue;
            }

            boolean visible = revealAll || CollectionVisibilityResolver.shouldBeVisible(player, completedQuests, flags, data, entryConfig);
            if (visible) {
                markEntryVisible(runtime, collectionData, phase, entryConfig, phaseId, true);
            }
        }

        if (!runtime.isPhaseActive(runtime.getCurrentPhaseId())) {
            PhaseDefinition initialPhase = def.selectInitialPhase(player.getRandom());
            runtime.activatePhase(initialPhase.getPhaseId(), Math.max(0, initialPhase.getObjectives().size()));
        }
        collectionData.clearDirty();
        runtime.clearDirty();
        runtime.setState(QuestState.ACTIVE);
    }

    public static CollectionVisibilityUpdateResult revealEntry(QuestDefinition def,
                                                               QuestRuntimeData runtime,
                                                               String phaseId) {
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (collectionData == null) {
            return CollectionVisibilityUpdateResult.unchanged(CollectionVisibilityUpdateResult.Status.NO_COLLECTION_DATA, phaseId);
        }
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) {
            return CollectionVisibilityUpdateResult.unchanged(CollectionVisibilityUpdateResult.Status.PHASE_NOT_FOUND, phaseId);
        }
        CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
        if (entryConfig == null) {
            return CollectionVisibilityUpdateResult.unchanged(CollectionVisibilityUpdateResult.Status.ENTRY_CONFIG_MISSING, phaseId);
        }
        boolean wasVisible = collectionData.isVisible(phaseId);
        boolean wasDiscovered = collectionData.isDiscovered(phaseId);
        markEntryVisible(runtime, collectionData, phase, entryConfig, phaseId, true);
        if (wasVisible && wasDiscovered) {
            return CollectionVisibilityUpdateResult.unchanged(CollectionVisibilityUpdateResult.Status.NO_VISIBILITY_CHANGE, phaseId);
        }
        return CollectionVisibilityUpdateResult.ok(phaseId, true, true);
    }

    public static CollectionVisibilityUpdateResult revealEntry(ServerPlayer player,
                                                               ArcQuestPlayer data,
                                                               QuestDefinition def,
                                                               QuestRuntimeData runtime,
                                                               String phaseId) {
        boolean wasActive = runtime.isPhaseActive(phaseId);
        CollectionVisibilityUpdateResult result = revealEntry(def, runtime, phaseId);
        PhaseDefinition phase = def.getPhase(phaseId);
        if (result.isChanged() && !wasActive && phase != null) {
            GUIDE_UNLOCK_SERVICE.grantAll(player, phase.getGuidesToGrantOnEnter());
        }
        return result;
    }

    public static int refreshVisibility(ServerPlayer player,
                                        ArcQuestPlayer data,
                                        QuestDefinition def,
                                        QuestRuntimeData runtime) {
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        CollectionQuestConfig config = def.getCollectionConfig();
        if (collectionData == null || config == null) {
            return 0;
        }
        boolean revealAll = config.isRevealAllEntriesByDefault();
        Set<ResourceLocation> completedQuests = data.getCompletedQuestLocations();
        Set<String> flags = data.getAllFlags();
        int changed = 0;
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null || collectionData.isVisible(phaseId)) continue;
            boolean visible = revealAll || CollectionVisibilityResolver.shouldBeVisible(player, completedQuests, flags, data, entryConfig);
            if (visible) {
                markEntryVisible(runtime, collectionData, phase, entryConfig, phaseId, true);
                GUIDE_UNLOCK_SERVICE.grantAll(player, phase.getGuidesToGrantOnEnter());
                changed++;
            }
        }
        return changed;
    }

    private static void markEntryVisible(QuestRuntimeData runtime,
                                         CollectionRuntimeData collectionData,
                                         PhaseDefinition phase,
                                         CollectionEntryConfig entryConfig,
                                         String phaseId,
                                         boolean discovered) {
        collectionData.markVisible(phaseId);
        if (discovered) {
            collectionData.markDiscovered(phaseId);
        }
        collectionData.markUpdated(phaseId, entryConfig.getCategoryId(),
                CoreProcessors.get().time().realTimeMillis());
        runtime.activatePhase(phaseId, Math.max(0, phase.getObjectives().size()));
    }

    public static int incrementEntry(ServerPlayer player,
                                     ArcQuestPlayer data,
                                     QuestDefinition def,
                                     QuestRuntimeData runtime,
                                     String phaseId,
                                     int amount) {
        return incrementEntryWithResult(player, data, def, runtime, phaseId, amount).getNextCount();
    }

    public static CollectionEntryUpdateResult incrementEntryWithResult(ServerPlayer player,
                                                                       ArcQuestPlayer data,
                                                                       QuestDefinition def,
                                                                       QuestRuntimeData runtime,
                                                                       String phaseId,
                                                                       int amount) {
        if (amount <= 0) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.INVALID_AMOUNT, phaseId);
        }
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (collectionData == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.NO_COLLECTION_DATA, phaseId);
        }
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.PHASE_NOT_FOUND, phaseId);
        }
        CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
        if (entryConfig == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.ENTRY_CONFIG_MISSING, phaseId);
        }

        boolean wasActive = runtime.isPhaseActive(phaseId);
        int max = entryConfig.getMaxCount() > 0 ? entryConfig.getMaxCount() : entryConfig.getCompletionTarget();
        int next = collectionData.incrementEntryCount(phaseId, amount, max);
        collectionData.markDiscovered(phaseId);
        collectionData.markVisible(phaseId);
        runtime.activatePhase(phaseId, Math.max(0, phase.getObjectives().size()));
        if (!wasActive) GUIDE_UNLOCK_SERVICE.grantAll(player, phase.getGuidesToGrantOnEnter());
        collectionData.markUpdated(phaseId, entryConfig.getCategoryId(),
                CoreProcessors.get().time().realTimeMillis());
        boolean entryCompleted = evaluateEntryCompletion(player, data, def, runtime, phaseId);
        boolean questCompleted = runtime.getState() == QuestState.COMPLETED;
        return CollectionEntryUpdateResult.ok(phaseId, next, entryCompleted, questCompleted);
    }

    public static boolean discoverEntry(QuestDefinition def,
                                        QuestRuntimeData runtime,
                                        String phaseId) {
        return discoverEntryWithResult(def, runtime, phaseId).isChanged();
    }

    public static CollectionEntryUpdateResult discoverEntryWithResult(QuestDefinition def,
                                                                      QuestRuntimeData runtime,
                                                                      String phaseId) {
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (collectionData == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.NO_COLLECTION_DATA, phaseId);
        }
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.PHASE_NOT_FOUND, phaseId);
        }
        CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
        if (entryConfig == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.ENTRY_CONFIG_MISSING, phaseId);
        }
        boolean wasVisible = collectionData.isVisible(phaseId);
        boolean wasDiscovered = collectionData.isDiscovered(phaseId);
        markEntryVisible(runtime, collectionData, phase, entryConfig, phaseId, true);
        if (wasVisible && wasDiscovered) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.NOT_CHANGED, phaseId);
        }
        return CollectionEntryUpdateResult.ok(phaseId, collectionData.getEntryCount(phaseId), false, runtime.getState() == QuestState.COMPLETED);
    }

    public static CollectionEntryUpdateResult discoverEntryWithResult(ServerPlayer player,
                                                                      ArcQuestPlayer data,
                                                                      QuestDefinition def,
                                                                      QuestRuntimeData runtime,
                                                                      String phaseId) {
        boolean wasActive = runtime.isPhaseActive(phaseId);
        CollectionEntryUpdateResult result = discoverEntryWithResult(def, runtime, phaseId);
        PhaseDefinition phase = def.getPhase(phaseId);
        if (result.isChanged() && !wasActive && phase != null) {
            GUIDE_UNLOCK_SERVICE.grantAll(player, phase.getGuidesToGrantOnEnter());
        }
        return result;
    }

    public static boolean addUniqueProgress(ServerPlayer player,
                                            ArcQuestPlayer data,
                                            QuestDefinition def,
                                            QuestRuntimeData runtime,
                                            String phaseId,
                                            String uniqueKey) {
        return addUniqueProgressWithResult(player, data, def, runtime, phaseId, uniqueKey).isChanged();
    }

    public static CollectionEntryUpdateResult addUniqueProgressWithResult(ServerPlayer player,
                                                                          ArcQuestPlayer data,
                                                                          QuestDefinition def,
                                                                          QuestRuntimeData runtime,
                                                                          String phaseId,
                                                                          String uniqueKey) {
        if (uniqueKey == null || uniqueKey.isEmpty()) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.INVALID_UNIQUE_KEY, phaseId);
        }
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (collectionData == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.NO_COLLECTION_DATA, phaseId);
        }
        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.PHASE_NOT_FOUND, phaseId);
        }
        CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
        if (entryConfig == null) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.ENTRY_CONFIG_MISSING, phaseId);
        }
        boolean added = collectionData.addUniqueKey(phaseId, uniqueKey);
        if (!added) {
            return CollectionEntryUpdateResult.unchanged(CollectionEntryUpdateResult.Status.DUPLICATE_UNIQUE_KEY, phaseId);
        }
        boolean wasActive = runtime.isPhaseActive(phaseId);
        collectionData.markDiscovered(phaseId);
        collectionData.markVisible(phaseId);
        runtime.activatePhase(phaseId, Math.max(0, phase.getObjectives().size()));
        if (!wasActive) GUIDE_UNLOCK_SERVICE.grantAll(player, phase.getGuidesToGrantOnEnter());
        int next = collectionData.incrementEntryCount(phaseId, 1, entryConfig.getMaxCount() > 0 ? entryConfig.getMaxCount() : entryConfig.getCompletionTarget());
        collectionData.markUpdated(phaseId, entryConfig.getCategoryId(),
                CoreProcessors.get().time().realTimeMillis());
        boolean entryCompleted = evaluateEntryCompletion(player, data, def, runtime, phaseId);
        boolean questCompleted = runtime.getState() == QuestState.COMPLETED;
        return CollectionEntryUpdateResult.ok(phaseId, next, entryCompleted, questCompleted);
    }

    public static boolean evaluateEntryCompletion(ServerPlayer player,
                                                  ArcQuestPlayer data,
                                                  QuestDefinition def,
                                                  QuestRuntimeData runtime,
                                                  String phaseId) {
        PhaseDefinition phase = def.getPhase(phaseId);
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (phase == null || collectionData == null) {
            return false;
        }
        CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
        if (entryConfig == null) {
            return false;
        }
        int target = Math.max(1, entryConfig.getCompletionTarget());
        int count = collectionData.getEntryCount(phaseId);
        if (!CollectionCompletionEvaluator.isEntryCompleted(runtime, phaseId, entryConfig, count)) {
            return false;
        }

        runtime.completePhase(phaseId);
        GUIDE_UNLOCK_SERVICE.grantAll(player, phase.getGuidesToGrantOnComplete());
        evaluateRewardUnlocks(player, data, def, runtime, phaseId);
        evaluateCategoryStates(player, data, def, runtime, entryConfig.getCategoryId());
        evaluateQuestState(player, data, def, runtime);
        return true;
    }

    public static boolean evaluateQuestState(ServerPlayer player,
                                             ArcQuestPlayer data,
                                             QuestDefinition def,
                                             QuestRuntimeData runtime) {
        if (runtime.getState() == QuestState.COMPLETED) {
            return false;
        }
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (config == null || collectionData == null) {
            return false;
        }

        boolean completed;
        if (config.getQuestCompletionRules().isEmpty()) {
            completed = CollectionCompletionEvaluator.areAllCollectionEntriesCompleted(def, runtime);
        } else {
            CollectionRuleContext context = new CollectionRuleContext(player, def, runtime, collectionData, data, null);
            completed = CollectionRuleEvaluator.all(context, config.getQuestCompletionRules());
        }
        if (!completed) {
            return false;
        }

        runtime.setState(QuestState.COMPLETED);
        data.markCompleted(def.getId().toString());
        evaluateQuestRewardUnlocks(player, data, def, runtime);
        QuestSyncCoordinator.syncQuestStateAndPush(player, runtime);
        TrackedQuestService.onQuestTerminated(player);
        return true;
    }

    public static boolean evaluateCategoryStates(ServerPlayer player,
                                                 ArcQuestPlayer data,
                                                 QuestDefinition def,
                                                 QuestRuntimeData runtime,
                                                 String categoryId) {
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (config == null || collectionData == null || categoryId == null || categoryId.isEmpty()) return false;

        for (CollectionCategoryDefinition category : config.getCategories()) {
            if (!categoryId.equals(category.getCategoryId())) continue;
            CollectionRuleContext context = new CollectionRuleContext(player, def, runtime, collectionData, data, categoryId);
            CollectionCategorySnapshot snapshot = CollectionCategoryStateResolver.snapshot(context, categoryId);
            boolean completed = category.getCompletionRules().isEmpty()
                    ? snapshot.isCompleted()
                    : CollectionRuleEvaluator.all(context, category.getCompletionRules());
            if (completed) {
                evaluateRewardUnlocks(player, data, def, runtime, categoryId);
                return true;
            }
        }
        return false;
    }

    private static void evaluateQuestRewardUnlocks(ServerPlayer player,
                                                   ArcQuestPlayer data,
                                                   QuestDefinition def,
                                                   QuestRuntimeData runtime) {
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (config == null || collectionData == null) return;

        CollectionRuleContext questContext = new CollectionRuleContext(player, def, runtime, collectionData, data, null);
        String questOwnerId = def.getId().toString();
        for (CollectionRewardNode node : config.getQuestRewardNodes()) {
            CollectionRewardResolver.tryGrantRewardNode(player, collectionData, questContext, node, questOwnerId);
        }
    }

    public static void evaluateRewardUnlocks(ServerPlayer player,
                                             ArcQuestPlayer data,
                                             QuestDefinition def,
                                             QuestRuntimeData runtime,
                                             String ownerId) {
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (config == null || collectionData == null) return;

        CollectionRuleContext questContext = new CollectionRuleContext(player, def, runtime, collectionData, data, null);
        for (CollectionRewardNode node : config.getQuestRewardNodes()) {
            CollectionRewardResolver.tryGrantRewardNode(player, collectionData, questContext, node, ownerId);
        }
        for (CollectionCategoryDefinition category : config.getCategories()) {
            CollectionRuleContext categoryContext = new CollectionRuleContext(player, def, runtime, collectionData, data, category.getCategoryId());
            for (CollectionRewardNode node : category.getRewardNodes()) {
                CollectionRewardResolver.tryGrantRewardNode(player, collectionData, categoryContext, node, ownerId);
            }
        }
        PhaseDefinition phase = def.getPhase(ownerId);
        if (phase != null && phase.getCollectionEntryConfig() != null) {
            for (CollectionRewardNode node : phase.getCollectionEntryConfig().getRewardNodes()) {
                CollectionRewardResolver.tryGrantRewardNode(player, collectionData, questContext, node, ownerId);
            }
        }
    }

    public static boolean claimReward(ServerPlayer player,
                                      ArcQuestPlayer data,
                                      QuestDefinition def,
                                      QuestRuntimeData runtime,
                                      String rewardNodeId) {
        return claimRewardWithResult(player, data, def, runtime, rewardNodeId).isOk();
    }

    public static CollectionRewardClaimResult claimRewardWithResult(ServerPlayer player,
                                                                    ArcQuestPlayer data,
                                                                    QuestDefinition def,
                                                                    QuestRuntimeData runtime,
                                                                    String rewardNodeId) {
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (rewardNodeId == null || rewardNodeId.isEmpty()) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.INVALID_REWARD_ID, rewardNodeId);
        }
        if (config == null) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.NO_COLLECTION_CONFIG, rewardNodeId);
        }
        if (collectionData == null) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.NO_COLLECTION_DATA, rewardNodeId);
        }
        if (!collectionData.isRewardUnlocked(rewardNodeId)) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.NOT_UNLOCKED, rewardNodeId);
        }
        if (collectionData.isRewardClaimed(rewardNodeId)) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.ALREADY_CLAIMED, rewardNodeId);
        }

        CollectionRewardNode node = CollectionRewardResolver.findRewardNode(def, config, rewardNodeId);
        if (node == null) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.REWARD_NODE_NOT_FOUND, rewardNodeId);
        }
        if (node.getGrantMode() != EntryRewardGrantMode.MANUAL) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.NOT_MANUAL_REWARD, rewardNodeId);
        }
        CollectionRewardResolver.grantNodeRewards(player, collectionData, node);
        return CollectionRewardClaimResult.ok(rewardNodeId);
    }

}
