package org.arcadia.arc_quest.quest.logic.profile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.quest.QuestAcceptedEvent;
import org.arcadia.arc_quest.api.event.quest.QuestStartedEvent;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.event.QuestChangeEvent;
import org.arcadia.arc_quest.quest.event.QuestEventBus;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionCategorySnapshot;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionCategoryStateResolver;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;

import java.util.LinkedHashSet;
import java.util.Set;

public final class CollectionQuestEngine {

    private CollectionQuestEngine() {
    }

    public static QuestRejectCodeDictionary.Code acceptQuest(ServerPlayer player,
                                                             IQuestCapability cap,
                                                             QuestDefinition def) {
        String questId = def.getId().toString();
        if (cap.isQuestActive(questId)) {
            return QuestRejectCodeDictionary.Code.ALREADY_ACTIVE;
        }
        if (cap.isQuestCompleted(questId) && !def.isRepeatable()) {
            return QuestRejectCodeDictionary.Code.ALREADY_COMPLETED_NOT_REPEATABLE;
        }

        PhaseDefinition firstPhase = def.getInitialPhase();
        if (firstPhase == null) {
            return QuestRejectCodeDictionary.Code.NO_INITIAL_PHASE;
        }

        long acceptedTick = player.getServer() != null ? player.getServer().getTickCount() : 0L;
        long acceptedRealMs = System.currentTimeMillis();
        long acceptedDayTime = player.level().getDayTime() % 24000L;

        QuestRuntimeData runtime = new QuestRuntimeData(
                questId,
                firstPhase.getPhaseId(),
                Math.max(0, firstPhase.getObjectives().size()),
                acceptedTick,
                acceptedRealMs,
                acceptedDayTime
        );

        CollectionRuntimeData collectionData = new CollectionRuntimeData();
        initializeQuest(player, cap, def, runtime, collectionData);
        runtime.setCollectionData(collectionData);
        cap.addActiveQuest(runtime);

        boolean flagsChanged = false;
        for (String flag : def.getFlagsToSetOnAccept()) {
            cap.setFlag(flag);
            flagsChanged = true;
        }

        QuestSyncCoordinator.syncQuestStateAndPush(player, runtime);
        if (flagsChanged) {
            QuestSyncCoordinator.syncFlagsVarsAndPush(player, cap);
        }

        QuestEventBus.fire(QuestChangeEvent.questAccepted(def.getId()));
        MinecraftForge.EVENT_BUS.post(new QuestAcceptedEvent(player, def.getId()));
        MinecraftForge.EVENT_BUS.post(new QuestStartedEvent(player, def.getId()));
        return QuestRejectCodeDictionary.Code.OK;
    }

    public static void initializeQuest(ServerPlayer player,
                                       IQuestCapability cap,
                                       QuestDefinition def,
                                       QuestRuntimeData runtime,
                                       CollectionRuntimeData collectionData) {
        CollectionQuestConfig config = def.getCollectionConfig();
        boolean revealAll = config != null && config.isRevealAllEntriesByDefault();
        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        Set<String> flags = cap.getAllFlags();

        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || !phase.hasCollectionEntryConfig()) {
                continue;
            }
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) {
                continue;
            }

            boolean visible = revealAll || shouldBeVisible(player, completedQuests, flags, cap, entryConfig);
            if (visible) {
                markEntryVisible(runtime, collectionData, phase, entryConfig, phaseId, true);
            }
        }

        if (!runtime.isPhaseActive(runtime.getCurrentPhaseId())) {
            runtime.activatePhase(def.getInitialPhaseId(), Math.max(0, firstObjectiveCount(def)));
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

    public static int refreshVisibility(ServerPlayer player,
                                        IQuestCapability cap,
                                        QuestDefinition def,
                                        QuestRuntimeData runtime) {
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        CollectionQuestConfig config = def.getCollectionConfig();
        if (collectionData == null || config == null) {
            return 0;
        }
        boolean revealAll = config.isRevealAllEntriesByDefault();
        Set<ResourceLocation> completedQuests = cap.getCompletedQuestLocations();
        Set<String> flags = cap.getAllFlags();
        int changed = 0;
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null || collectionData.isVisible(phaseId)) continue;
            boolean visible = revealAll || shouldBeVisible(player, completedQuests, flags, cap, entryConfig);
            if (visible) {
                markEntryVisible(runtime, collectionData, phase, entryConfig, phaseId, true);
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
        collectionData.markUpdated(phaseId, entryConfig.getCategoryId(), System.currentTimeMillis());
        runtime.activatePhase(phaseId, Math.max(0, phase.getObjectives().size()));
    }

    public static int incrementEntry(ServerPlayer player,
                                     IQuestCapability cap,
                                     QuestDefinition def,
                                     QuestRuntimeData runtime,
                                     String phaseId,
                                     int amount) {
        return incrementEntryWithResult(player, cap, def, runtime, phaseId, amount).getNextCount();
    }

    public static CollectionEntryUpdateResult incrementEntryWithResult(ServerPlayer player,
                                                                       IQuestCapability cap,
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

        int max = entryConfig.getMaxCount() > 0 ? entryConfig.getMaxCount() : entryConfig.getCompletionTarget();
        int next = collectionData.incrementEntryCount(phaseId, amount, max);
        collectionData.markDiscovered(phaseId);
        collectionData.markVisible(phaseId);
        runtime.activatePhase(phaseId, Math.max(0, phase.getObjectives().size()));
        collectionData.markUpdated(phaseId, entryConfig.getCategoryId(), System.currentTimeMillis());
        boolean entryCompleted = evaluateEntryCompletion(player, cap, def, runtime, phaseId);
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

    public static boolean addUniqueProgress(ServerPlayer player,
                                            IQuestCapability cap,
                                            QuestDefinition def,
                                            QuestRuntimeData runtime,
                                            String phaseId,
                                            String uniqueKey) {
        return addUniqueProgressWithResult(player, cap, def, runtime, phaseId, uniqueKey).isChanged();
    }

    public static CollectionEntryUpdateResult addUniqueProgressWithResult(ServerPlayer player,
                                                                          IQuestCapability cap,
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
        collectionData.markDiscovered(phaseId);
        collectionData.markVisible(phaseId);
        runtime.activatePhase(phaseId, Math.max(0, phase.getObjectives().size()));
        int next = collectionData.incrementEntryCount(phaseId, 1, entryConfig.getMaxCount() > 0 ? entryConfig.getMaxCount() : entryConfig.getCompletionTarget());
        collectionData.markUpdated(phaseId, entryConfig.getCategoryId(), System.currentTimeMillis());
        boolean entryCompleted = evaluateEntryCompletion(player, cap, def, runtime, phaseId);
        boolean questCompleted = runtime.getState() == QuestState.COMPLETED;
        return CollectionEntryUpdateResult.ok(phaseId, next, entryCompleted, questCompleted);
    }

    public static boolean evaluateEntryCompletion(ServerPlayer player,
                                                  IQuestCapability cap,
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
        if (count < target || runtime.isPhaseCompleted(phaseId)) {
            return false;
        }

        runtime.completePhase(phaseId);
        evaluateRewardUnlocks(player, cap, def, runtime, phaseId);
        evaluateCategoryStates(player, cap, def, runtime, entryConfig.getCategoryId());
        evaluateQuestState(player, cap, def, runtime);
        return true;
    }

    public static boolean evaluateQuestState(ServerPlayer player,
                                             IQuestCapability cap,
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
            completed = areAllCollectionEntriesCompleted(def, runtime);
        } else {
            CollectionRuleContext context = new CollectionRuleContext(player, def, runtime, collectionData, cap, null);
            completed = config.getQuestCompletionRules().stream().allMatch(rule -> rule.test(context));
        }
        if (!completed) {
            return false;
        }

        runtime.setState(QuestState.COMPLETED);
        cap.markCompleted(def.getId().toString());
        evaluateQuestRewardUnlocks(player, cap, def, runtime);
        QuestSyncCoordinator.syncQuestStateAndPush(player, runtime);
        return true;
    }

    private static boolean areAllCollectionEntriesCompleted(QuestDefinition def, QuestRuntimeData runtime) {
        LinkedHashSet<String> collectionPhaseIds = new LinkedHashSet<>();
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase != null && phase.hasCollectionEntryConfig()) {
                collectionPhaseIds.add(phaseId);
            }
        }
        if (collectionPhaseIds.isEmpty()) {
            return false;
        }
        for (String phaseId : collectionPhaseIds) {
            if (!runtime.isPhaseCompleted(phaseId)) {
                return false;
            }
        }
        return true;
    }


    public static boolean evaluateCategoryStates(ServerPlayer player,
                                                 IQuestCapability cap,
                                                 QuestDefinition def,
                                                 QuestRuntimeData runtime,
                                                 String categoryId) {
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (config == null || collectionData == null || categoryId == null || categoryId.isEmpty()) return false;

        for (CollectionCategoryDefinition category : config.getCategories()) {
            if (!categoryId.equals(category.getCategoryId())) continue;
            CollectionRuleContext context = new CollectionRuleContext(player, def, runtime, collectionData, cap, categoryId);
            CollectionCategorySnapshot snapshot = CollectionCategoryStateResolver.snapshot(context, categoryId);
            boolean completed = category.getCompletionRules().isEmpty()
                    ? snapshot.isCompleted()
                    : category.getCompletionRules().stream().allMatch(rule -> rule.test(context));
            if (completed) {
                evaluateRewardUnlocks(player, cap, def, runtime, categoryId);
                return true;
            }
        }
        return false;
    }

    private static void evaluateQuestRewardUnlocks(ServerPlayer player,
                                                   IQuestCapability cap,
                                                   QuestDefinition def,
                                                   QuestRuntimeData runtime) {
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (config == null || collectionData == null) return;

        CollectionRuleContext questContext = new CollectionRuleContext(player, def, runtime, collectionData, cap, null);
        String questOwnerId = def.getId().toString();
        for (CollectionRewardNode node : config.getQuestRewardNodes()) {
            tryGrantRewardNode(player, collectionData, questContext, node, questOwnerId);
        }
    }

    public static void evaluateRewardUnlocks(ServerPlayer player,
                                             IQuestCapability cap,
                                             QuestDefinition def,
                                             QuestRuntimeData runtime,
                                             String ownerId) {
        CollectionQuestConfig config = def.getCollectionConfig();
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (config == null || collectionData == null) return;

        CollectionRuleContext questContext = new CollectionRuleContext(player, def, runtime, collectionData, cap, null);
        for (CollectionRewardNode node : config.getQuestRewardNodes()) {
            tryGrantRewardNode(player, collectionData, questContext, node, ownerId);
        }
        for (CollectionCategoryDefinition category : config.getCategories()) {
            CollectionRuleContext categoryContext = new CollectionRuleContext(player, def, runtime, collectionData, cap, category.getCategoryId());
            for (CollectionRewardNode node : category.getRewardNodes()) {
                tryGrantRewardNode(player, collectionData, categoryContext, node, ownerId);
            }
        }
        PhaseDefinition phase = def.getPhase(ownerId);
        if (phase != null && phase.getCollectionEntryConfig() != null) {
            for (CollectionRewardNode node : phase.getCollectionEntryConfig().getRewardNodes()) {
                tryGrantRewardNode(player, collectionData, questContext, node, ownerId);
            }
        }
    }

    public static boolean claimReward(ServerPlayer player,
                                      IQuestCapability cap,
                                      QuestDefinition def,
                                      QuestRuntimeData runtime,
                                      String rewardNodeId) {
        return claimRewardWithResult(player, cap, def, runtime, rewardNodeId).isOk();
    }

    public static CollectionRewardClaimResult claimRewardWithResult(ServerPlayer player,
                                                                    IQuestCapability cap,
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

        CollectionRewardNode node = findRewardNode(def, config, rewardNodeId);
        if (node == null) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.REWARD_NODE_NOT_FOUND, rewardNodeId);
        }
        if (node.getGrantMode() != EntryRewardGrantMode.MANUAL) {
            return CollectionRewardClaimResult.rejected(CollectionRewardClaimResult.Status.NOT_MANUAL_REWARD, rewardNodeId);
        }
        grantNodeRewards(player, collectionData, node);
        return CollectionRewardClaimResult.ok(rewardNodeId);
    }

    private static CollectionRewardNode findRewardNode(QuestDefinition def,
                                                       CollectionQuestConfig config,
                                                       String rewardNodeId) {
        for (CollectionRewardNode node : config.getQuestRewardNodes()) {
            if (rewardNodeId.equals(node.getRewardNodeId())) return node;
        }
        for (CollectionCategoryDefinition category : config.getCategories()) {
            for (CollectionRewardNode node : category.getRewardNodes()) {
                if (rewardNodeId.equals(node.getRewardNodeId())) return node;
            }
        }
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || phase.getCollectionEntryConfig() == null) continue;
            for (CollectionRewardNode node : phase.getCollectionEntryConfig().getRewardNodes()) {
                if (rewardNodeId.equals(node.getRewardNodeId())) return node;
            }
        }
        return null;
    }

    private static void tryGrantRewardNode(ServerPlayer player,
                                           CollectionRuntimeData collectionData,
                                           CollectionRuleContext context,
                                           CollectionRewardNode node,
                                           String ownerId) {
        if (node == null || node.getRewardNodeId() == null) return;
        if (node.getOwnerId() != null && ownerId != null && !node.getOwnerId().equals(ownerId)) return;
        if (collectionData.isRewardClaimed(node.getRewardNodeId())) return;
        boolean unlocked = node.getUnlockRules().isEmpty() || node.getUnlockRules().stream().allMatch(rule -> rule.test(context));
        if (!unlocked) return;
        collectionData.markRewardUnlocked(node.getRewardNodeId());
        if (node.getGrantMode() == EntryRewardGrantMode.AUTO) {
            grantNodeRewards(player, collectionData, node);
        }
    }

    private static void grantNodeRewards(ServerPlayer player,
                                         CollectionRuntimeData collectionData,
                                         CollectionRewardNode node) {
        for (IReward reward : node.getRewards()) {
            reward.grant(player);
        }
        collectionData.markRewardClaimed(node.getRewardNodeId());
    }

    private static boolean shouldBeVisible(ServerPlayer player,
                                           Set<ResourceLocation> completedQuests,
                                           Set<String> flags,
                                           IQuestCapability cap,
                                           CollectionEntryConfig entryConfig) {
        return switch (entryConfig.getVisibilityMode()) {
            case VISIBLE_BY_DEFAULT -> true;
            case HIDDEN_BY_DEFAULT, DISCOVER_ONLY -> false;
            case CONDITIONAL -> entryConfig.getVisibilityConditions().stream()
                    .allMatch(condition -> condition.test(player, completedQuests, flags, cap.getAllVariables()));
        };
    }

    private static int firstObjectiveCount(QuestDefinition def) {
        PhaseDefinition first = def.getInitialPhase();
        return first != null ? first.getObjectives().size() : 0;
    }
}
