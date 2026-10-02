package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.util.*;

/** Entry reward receipts remain separate from the legacy and modern quest/category milestone nodes. */
public final class CollectionEntryRewardService {
    private CollectionEntryRewardService() {}

    public record Knowledge(boolean discovered, boolean researched) {}
    public static Knowledge knowledge(CollectionEntryDefinition entry, CollectionRecordState records) {
        return new Knowledge(records.isDiscovered(entry.getEntryId()), CollectionProgressProjector.researchComplete(entry, records));
    }
    private static boolean eligible(CollectionEntryRewardDefinition reward, Knowledge knowledge) {
        return switch (reward.trigger()) {
            case DISCOVERED -> knowledge.discovered();
            case RESEARCH_COMPLETE -> knowledge.researched();
            case BINDING_COMPLETE -> false;
        };
    }

    /** Reconcile existing knowledge without inventing an AUTO event when a task is accepted or reopened. */
    public static void reconcilePermanent(ArcQuestPlayer data) {
        for (CollectionEntryDefinition entry : CollectionEntryRegistry.serverSnapshot().values())
            if (!entry.getRewards().isEmpty()) updatePermanent(null, data, entry, knowledge(entry, data.getCollectionRecords()));
    }

    /** AUTO rewards run on an actual permanent fact transition; MANUAL eligibility is latched immediately. */
    public static void updatePermanent(ServerPlayer player, ArcQuestPlayer data, CollectionEntryDefinition entry, Knowledge before) {
        CollectionRecordState records = data.getCollectionRecords();
        Knowledge after = knowledge(entry, records);
        for (CollectionEntryRewardDefinition reward : entry.getRewards()) {
            if (reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE || !eligible(reward, after)) continue;
            records.unlockReward(entry.getEntryId(), reward.rewardId());
            if (player != null && reward.grantMode() == EntryRewardGrantMode.AUTO && !eligible(reward, before)
                    && records.claimReward(entry.getEntryId(), reward.rewardId())) grant(player, reward);
        }
    }

    /** Called after the current binding outcomes are latched, before phase completion/archival. */
    public static boolean updateBindings(ServerPlayer player, ArcQuestPlayer data, QuestDefinition quest, PhaseDefinition phase,
                                      QuestRuntimeData runtime) {
        CollectionRuntimeData run = runtime.getCollectionData();
        if (run == null || !phase.hasCollectionSheet() || runtime.getState() != QuestState.ACTIVE) return false;
        boolean changed = false;
        for (EntryRequirementBinding binding : phase.getCollectionSheet().getBindings()) {
            if (!run.isBindingComplete(phase.getPhaseId(), binding.getBindingId())) continue;
            CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
            if (entry == null) continue;
            for (CollectionEntryRewardDefinition reward : entry.getRewards()) {
                if (reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE) continue;
                changed |= run.unlockEntryReward(phase.getPhaseId(), binding.getBindingId(), reward.rewardId());
                if (reward.grantMode() == EntryRewardGrantMode.AUTO
                        && run.claimEntryReward(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())) {
                    changed = true;
                    grant(player, reward);
                }
            }
        }
        return changed;
    }

    /** Pure client/server projection; the client receives only authorized definitions and receipts. */
    public static List<CollectionEntryRewardProgress> project(QuestDefinition quest, PhaseDefinition phase,
            QuestRuntimeData runtime, CollectionRecordState records, EntryRequirementBinding binding) {
        CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
        if (entry == null) return List.of();
        Knowledge knowledge = knowledge(entry, records);
        CollectionRuntimeData run = runtime == null ? null : runtime.getCollectionData();
        List<CollectionEntryRewardProgress> result = new ArrayList<>();
        for (CollectionEntryRewardDefinition reward : entry.getRewards()) {
            boolean bindingScope = reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE;
            boolean unlocked = bindingScope ? run != null && run.isEntryRewardUnlocked(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())
                    : records.isRewardUnlocked(entry.getEntryId(), reward.rewardId()) || eligible(reward, knowledge);
            boolean claimed = bindingScope ? run != null && run.isEntryRewardClaimed(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())
                    : records.isRewardClaimed(entry.getEntryId(), reward.rewardId());
            result.add(new CollectionEntryRewardProgress(reward, unlocked, claimed, bindingScope && run != null ? run.getRunId() : ""));
        }
        return List.copyOf(result);
    }

    /** Only unpaid previous investigations are appended; permanent rewards retain one lifetime row. */
    public static List<CollectionEntryRewardProgress> pendingPriorRewards(QuestDefinition quest, PhaseDefinition phase,
            EntryRequirementBinding binding, QuestRuntimeData current, CollectionRecordState records, CollectionQuestArchives archives) {
        if (archives == null) return List.of();
        String currentRun = current == null || current.getCollectionData() == null ? "" : current.getCollectionData().getRunId();
        List<CollectionEntryRewardProgress> result = new ArrayList<>();
        for (QuestRuntimeData prior : archives.pendingRuns(quest.getId().toString())) {
            if (prior.getCollectionData().getRunId().equals(currentRun)) continue;
            for (CollectionEntryRewardProgress row : project(quest, phase, prior, records, binding)) {
                if (row.definition().trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE && row.canClaim()) result.add(row);
            }
        }
        return List.copyOf(result);
    }

    public static QuestRejectCodeDictionary.Code claim(ServerPlayer player, ArcQuestPlayer data, String questId, String runId,
                                                       String phaseId, String bindingId, String rewardId) {
        QuestDefinition quest = QuestRegistry.getServerDefinition(ResourceLocation.tryParse(questId));
        if (quest == null || !quest.hasCollectionSheets()) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        if (!knownQuest(data, questId)) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        PhaseDefinition phase = quest.getPhase(phaseId);
        EntryRequirementBinding binding = phase == null || !phase.hasCollectionSheet() ? null : phase.getCollectionSheet().getBinding(bindingId);
        if (binding == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
        if (entry == null || !org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.entryRevealed(entry, data.getCollectionRecords()))
            return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED;
        CollectionEntryRewardDefinition reward = entry.getRewards().stream().filter(r -> r.rewardId().equals(rewardId)).findFirst().orElse(null);
        if (reward == null) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NODE_NOT_FOUND;
        if (reward.grantMode() != EntryRewardGrantMode.MANUAL) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_MANUAL;
        if (reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE) {
            QuestRuntimeData runtime = resolveRuntime(data, questId, runId);
            if (runtime == null || runtime.getCollectionData() == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
            CollectionRuntimeData run = runtime.getCollectionData();
            if (!run.getFrozenBindingIds(phaseId).contains(bindingId) || !run.isBindingComplete(phaseId, bindingId)
                    || !run.isEntryRewardUnlocked(phaseId, bindingId, rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED;
            if (!run.claimEntryReward(phaseId, bindingId, rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED;
        } else {
            CollectionRecordState records = data.getCollectionRecords();
            if (!records.isRewardUnlocked(entry.getEntryId(), rewardId) && !eligible(reward, knowledge(entry, records)))
                return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED;
            records.unlockReward(entry.getEntryId(), rewardId);
            if (!records.claimReward(entry.getEntryId(), rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED;
        }
        grant(player, reward);
        data.getCollectionArchives().pruneSettledPending(questId);
        return QuestRejectCodeDictionary.Code.OK;
    }

    public static QuestRuntimeData resolveRuntime(ArcQuestPlayer data, String questId, String runId) {
        if (runId == null || runId.isBlank()) return null;
        QuestRuntimeData active = data.getActiveQuest(questId);
        if (sameRun(active, runId)) return active;
        return data.getCollectionArchives().get(questId, runId);
    }
    private static boolean sameRun(QuestRuntimeData runtime, String runId) {
        return runtime != null && runtime.getCollectionData() != null && runId.equals(runtime.getCollectionData().getRunId());
    }
    public static boolean knownQuest(ArcQuestPlayer data, String questId) {
        return data.isQuestActive(questId) || data.isQuestCompleted(questId) || data.isQuestFailed(questId);
    }

    /** Eligible reward payloads only: locked item IDs and commands never enter presentation packets. */
    public static Set<String> disclosedRewardIds(ArcQuestPlayer data, QuestDefinition quest, CollectionEntryDefinition entry) {
        if (entry.getRewards().isEmpty()) return Set.of();
        if (!knownQuest(data, quest.getId().toString())) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        CollectionRecordState records = data.getCollectionRecords();
        Knowledge knowledge = knowledge(entry, records);
        for (CollectionEntryRewardDefinition reward : entry.getRewards()) {
            if (reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE
                    && (records.isRewardUnlocked(entry.getEntryId(), reward.rewardId()) || records.isRewardClaimed(entry.getEntryId(), reward.rewardId())
                    || eligible(reward, knowledge))) ids.add(reward.rewardId());
        }
        List<QuestRuntimeData> runtimes = new ArrayList<>(data.getCollectionArchives().allRuns(quest.getId().toString()));
        QuestRuntimeData active = data.getActiveQuest(quest.getId().toString());
        if (active != null) runtimes.add(active);
        for (QuestRuntimeData runtime : runtimes) {
            if (runtime == null || runtime.getCollectionData() == null) continue;
            CollectionRuntimeData run = runtime.getCollectionData();
            for (PhaseDefinition phase : quest.getAllPhases()) {
                if (!phase.hasCollectionSheet()) continue;
                for (EntryRequirementBinding binding : phase.getCollectionSheet().getBindings()) {
                    if (!entry.getEntryId().equals(binding.getEntryId())) continue;
                    for (CollectionEntryRewardDefinition reward : entry.getRewards())
                        if (reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE
                                && (run.isEntryRewardUnlocked(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())
                                || run.isEntryRewardClaimed(phase.getPhaseId(), binding.getBindingId(), reward.rewardId()))) ids.add(reward.rewardId());
                }
            }
        }
        return Set.copyOf(ids);
    }

    public static Set<String> disclosureKeys(ArcQuestPlayer data) {
        Set<String> keys = new LinkedHashSet<>();
        Set<String> known = new LinkedHashSet<>(data.getAllActiveQuests().keySet());
        known.addAll(data.getCompletedQuests()); known.addAll(data.getFailedQuests());
        for (String id : known) {
            QuestDefinition quest = QuestRegistry.getServerDefinition(ResourceLocation.tryParse(id));
            if (quest == null || !quest.hasCollectionSheets()) continue;
            for (CollectionEntryDefinition entry : quest.getCollectionConfig().getEntries())
                for (String reward : disclosedRewardIds(data, quest, entry)) keys.add(id + "/" + entry.getEntryId() + "/" + reward);
        }
        return Set.copyOf(keys);
    }

    private static void grant(ServerPlayer player, CollectionEntryRewardDefinition definition) {
        // The caller has recorded its at-most-once receipt in player data before arbitrary callback side effects.
        for (IReward reward : definition.rewards()) {
            try { reward.grant(player); }
            catch (Exception exception) {
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_PROGRESS, "Entry reward '{}' failed; receipt retained and later rewards continue", definition.rewardId(), exception);
            }
        }
    }
}
