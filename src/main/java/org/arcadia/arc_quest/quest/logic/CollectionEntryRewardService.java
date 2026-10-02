package org.arcadia.arc_quest.quest.logic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
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

    public record Knowledge(boolean discovered, boolean researched, Set<String> outcomes) {
        public Knowledge(boolean discovered, boolean researched) { this(discovered, researched, Set.of()); }
    }
    public static Knowledge knowledge(CollectionEntryDefinition entry, CollectionRecordState records) {
        return new Knowledge(records.isDiscovered(entry.getEntryId()), CollectionProgressProjector.researchComplete(entry, records),
                records.getRecord(entry.getEntryId()).getOutcomeIds());
    }
    private static boolean eligible(CollectionEntryRewardDefinition reward, Knowledge knowledge) {
        return switch (reward.trigger()) {
            case DISCOVERED -> knowledge.discovered();
            case RESEARCH_COMPLETE -> knowledge.researched();
            case OUTCOME -> knowledge.outcomes().contains(reward.outcomeId());
            case BINDING_COMPLETE -> false;
        };
    }

    /** Reconcile existing knowledge without inventing an AUTO event when a task is accepted or reopened. */
    public static void reconcilePermanent(ArcQuestPlayer data) {
        for (CollectionEntryDefinition entry : CollectionEntryRegistry.serverSnapshot().values()) {
            CollectionOutcomeMigration.migrate(data.getCollectionRecords(), entry);
            if (!entry.getRewards().isEmpty()) updatePermanent(null, data, entry, knowledge(entry, data.getCollectionRecords()));
        }
    }

    /** AUTO rewards run on an actual permanent fact transition; MANUAL eligibility is latched immediately. */
    public static void updatePermanent(ServerPlayer player, ArcQuestPlayer data, CollectionEntryDefinition entry, Knowledge before) {
        CollectionRecordState records = data.getCollectionRecords();
        Knowledge after = knowledge(entry, records);
        for (CollectionEntryRewardDefinition reward : entry.getRewards()) {
            if (reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE || !eligible(reward, after)) continue;
            if (records.getRecord(entry.getEntryId()).getRewardEntitlement(reward.rewardId()).isEmpty()) {
                records.snapshotReward(entry.getEntryId(), reward.rewardId(), permanentEntitlement(player, data, entry, reward));
            }
            records.unlockReward(entry.getEntryId(), reward.rewardId());
            if (player != null && reward.grantMode() == EntryRewardGrantMode.AUTO && !eligible(reward, before)
                    && !records.isRewardClaimed(entry.getEntryId(), reward.rewardId())) {
                var source = sourceRuntime(data, entry.getEntryId());
                String questId = source == null ? "" : source.getQuestId();
                if (prepareDelivery(player, data, entry, questId, "", "", "", reward)
                        && records.claimReward(entry.getEntryId(), reward.rewardId()))
                    deliverPrepared(player, data, entry, questId, "", "", "", reward);
            }
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
            Knowledge before = knowledge(entry, data.getCollectionRecords());
            if (CollectionOutcomeService.recordCompletion(data.getCollectionRecords(), run, entry, binding,
                    quest.getId().toString(), phase.getPhaseId())) {
                updatePermanent(player, data, entry, before);
                changed = true;
            }
            for (CollectionEntryRewardDefinition reward : rewards(entry, binding)) {
                if (reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE) continue;
                changed |= run.snapshotEntryReward(phase.getPhaseId(), binding.getBindingId(), reward.rewardId(),
                        CollectionRewardEntitlement.capture(reward, quest.getId().toString(), runtime.getFrozenDefinitionHash(),
                                phase.getPhaseId(), binding.getBindingId(), entry.getEntryId()));
                changed |= run.unlockEntryReward(phase.getPhaseId(), binding.getBindingId(), reward.rewardId());
                if (player != null && reward.grantMode() == EntryRewardGrantMode.AUTO
                        && !run.isEntryRewardClaimed(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())
                        && prepareDelivery(player, data, entry, quest.getId().toString(), run.getRunId(), phase.getPhaseId(), binding.getBindingId(), reward)
                        && run.claimEntryReward(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())) {
                    changed = true;
                    deliverPrepared(player, data, entry, quest.getId().toString(), run.getRunId(),
                            phase.getPhaseId(), binding.getBindingId(), reward);
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
        Map<String, CollectionEntryRewardDefinition> definitions = new LinkedHashMap<>();
        for (CollectionEntryRewardDefinition reward : rewards(entry, binding)) definitions.put(reward.rewardId(), reward);
        var record = records.getRecord(entry.getEntryId());
        for (String id : record.getRewardEntitlementIds()) {
            var entitlement = record.getRewardEntitlement(id);
            definitions.put(id, CollectionRewardEntitlement.presentationDefinition(entitlement));
        }
        if (run != null) for (String id : run.getEntryRewardIds(phase.getPhaseId(), binding.getBindingId())) {
            var entitlement = run.getEntryRewardEntitlement(phase.getPhaseId(), binding.getBindingId(), id);
            if (!entitlement.isEmpty()) definitions.put(id, CollectionRewardEntitlement.presentationDefinition(entitlement));
        }
        for (CollectionEntryRewardDefinition reward : definitions.values()) {
            boolean bindingScope = reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE;
            boolean unlocked = bindingScope ? run != null && run.isEntryRewardUnlocked(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())
                    : records.isRewardUnlocked(entry.getEntryId(), reward.rewardId()) || eligible(reward, knowledge);
            boolean claimed = bindingScope ? run != null && run.isEntryRewardClaimed(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())
                    : records.isRewardClaimed(entry.getEntryId(), reward.rewardId());
            result.add(new CollectionEntryRewardProgress(reward, unlocked, claimed, bindingScope && run != null ? run.getRunId() : "",
                    bindingScope ? phase.getPhaseId() : "", bindingScope ? binding.getBindingId() : "",
                    bindingScope ? run != null && run.isEntryRewardDeliveryPending(phase.getPhaseId(), binding.getBindingId(), reward.rewardId()) : record.isDeliveryPending(reward.rewardId())));
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
            CollectionRuntimeData priorRun = prior.getCollectionData();
            for (String oldPhase : priorRun.getSheetPhaseIds()) for (String oldBinding : priorRun.getFrozenBindingIds(oldPhase))
                for (String rewardId : priorRun.getEntryRewardIds(oldPhase, oldBinding)) {
                    var entitlement = priorRun.getEntryRewardEntitlement(oldPhase, oldBinding, rewardId);
                    if (entitlement.isEmpty() || !binding.getEntryId().toString().equals(entitlement.getString("EntryId"))
                            || !priorRun.isEntryRewardUnlocked(oldPhase, oldBinding, rewardId)
                            || priorRun.isEntryRewardClaimed(oldPhase, oldBinding, rewardId) && !priorRun.isEntryRewardDeliveryPending(oldPhase, oldBinding, rewardId)) continue;
                    result.add(new CollectionEntryRewardProgress(CollectionRewardEntitlement.presentationDefinition(entitlement), true,
                            priorRun.isEntryRewardClaimed(oldPhase, oldBinding, rewardId), priorRun.getRunId(), oldPhase, oldBinding,
                            priorRun.isEntryRewardDeliveryPending(oldPhase, oldBinding, rewardId)));
                }
            for (CollectionEntryRewardProgress row : project(quest, phase, prior, records, binding)) {
                if (row.definition().trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE && (row.canClaim() || row.deliveryPending())
                        && result.stream().noneMatch(existing -> existing.sourceRunId().equals(row.sourceRunId())
                        && existing.sourcePhaseId().equals(row.sourcePhaseId()) && existing.sourceBindingId().equals(row.sourceBindingId())
                        && existing.definition().rewardId().equals(row.definition().rewardId()))) result.add(row);
            }
        }
        return List.copyOf(result);
    }

    public static QuestRejectCodeDictionary.Code claim(ServerPlayer player, ArcQuestPlayer data, String questId, String runId,
                                                       String phaseId, String bindingId, String rewardId) {
        QuestDefinition quest = QuestRegistry.getServerDefinition(ResourceLocation.tryParse(questId));
        QuestRuntimeData selected = resolveRuntime(data, questId, runId);
        if (selected == null && (runId == null || runId.isBlank())) {
            selected = data.getActiveQuest(questId);
            if (selected == null) selected = data.getCollectionArchives().get(questId);
        }
        try { if (selected != null && player != null) quest = CollectionRunDefinitions.resolve(player.getServer(), selected, quest); }
        catch (CollectionRunDefinitionStore.UnsupportedSnapshotException unavailable) { return QuestRejectCodeDictionary.Code.COLLECTION_DEFINITION_UNAVAILABLE; }
        if (quest == null || !quest.hasCollectionSheets()) return QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND;
        if (!knownQuest(data, questId)) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
        PhaseDefinition phase = quest.getPhase(phaseId);
        EntryRequirementBinding binding = phase == null || !phase.hasCollectionSheet() ? null : phase.getCollectionSheet().getBinding(bindingId);
        if (binding == null) return QuestRejectCodeDictionary.Code.PHASE_NOT_FOUND;
        CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
        if (entry == null || !org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.entryRevealed(entry, data.getCollectionRecords()))
            return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED;
        CompoundTag entitlement = runId == null || runId.isBlank()
                ? data.getCollectionRecords().getRecord(entry.getEntryId()).getRewardEntitlement(rewardId)
                : selected == null || selected.getCollectionData() == null ? new CompoundTag()
                : selected.getCollectionData().getEntryRewardEntitlement(phaseId, bindingId, rewardId);
        CollectionEntryRewardDefinition reward = entitlement.isEmpty() ? rewards(entry, binding).stream()
                .filter(r -> r.rewardId().equals(rewardId)).sorted(Comparator.comparingInt(r ->
                        (runId == null || runId.isBlank()) == (r.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE) ? 0 : 1)).findFirst().orElse(null)
                : CollectionRewardEntitlement.restore(entitlement, player == null ? null : CollectionRunDefinitionStore.get(player.getServer()));
        if (reward == null) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NODE_NOT_FOUND;
        if (reward.grantMode() != EntryRewardGrantMode.MANUAL) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_MANUAL;
        if (reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE) {
            if (runId == null || runId.isBlank()) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
            QuestRuntimeData runtime = resolveRuntime(data, questId, runId);
            if (runtime == null || runtime.getCollectionData() == null) return QuestRejectCodeDictionary.Code.NOT_ACTIVE;
            CollectionRuntimeData run = runtime.getCollectionData();
            if (!run.getFrozenBindingIds(phaseId).contains(bindingId) || !run.isBindingComplete(phaseId, bindingId)
                    || !run.isEntryRewardUnlocked(phaseId, bindingId, rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED;
            if (run.isEntryRewardClaimed(phaseId, bindingId, rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED;
            if (!prepareDelivery(player, data, entry, questId, runId, phaseId, bindingId, reward))
                return QuestRejectCodeDictionary.Code.COLLECTION_DELIVERY_UNAVAILABLE;
            if (!run.claimEntryReward(phaseId, bindingId, rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED;
        } else {
            CollectionRecordState records = data.getCollectionRecords();
            if (!records.isRewardUnlocked(entry.getEntryId(), rewardId) && !eligible(reward, knowledge(entry, records)))
                return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_NOT_UNLOCKED;
            records.unlockReward(entry.getEntryId(), rewardId);
            if (records.isRewardClaimed(entry.getEntryId(), rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED;
            if (!prepareDelivery(player, data, entry, questId, "", phaseId, bindingId, reward))
                return QuestRejectCodeDictionary.Code.COLLECTION_DELIVERY_UNAVAILABLE;
            if (!records.claimReward(entry.getEntryId(), rewardId)) return QuestRejectCodeDictionary.Code.COLLECTION_REWARD_ALREADY_CLAIMED;
        }
        deliverPrepared(player, data, entry, questId,
                reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE ? runId : "", phaseId, bindingId, reward);
        data.getCollectionArchives().pruneSettledPending(questId);
        return QuestRejectCodeDictionary.Code.OK;
    }

    private static boolean prepareDelivery(ServerPlayer player, ArcQuestPlayer data, CollectionEntryDefinition entry,
                                          String questId, String runId, String phaseId, String bindingId, CollectionEntryRewardDefinition reward) {
        try {
            CollectionRewardDelivery.prepare(player, data, entry.getEntryId(), questId, runId, phaseId, bindingId, reward);
            return true;
        } catch (RuntimeException unavailable) {
            ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE, "Collection reward preparation failed; claim remains available: {}", reward.rewardId(), unavailable);
            return false;
        }
    }

    private static void deliverPrepared(ServerPlayer player, ArcQuestPlayer data, CollectionEntryDefinition entry,
                                        String questId, String runId, String phaseId, String bindingId, CollectionEntryRewardDefinition reward) {
        try { CollectionRewardDelivery.grant(player, data, entry.getEntryId(), questId, runId, phaseId, bindingId, reward); }
        catch (RuntimeException unavailable) {
            // Preparation already committed this grant. Keep its pending receipt instead of reopening a consumed claim.
            ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE, "Prepared collection reward delivery paused; authorization retained: {}", reward.rewardId(), unavailable);
        }
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
        return data.isQuestActive(questId) || data.isQuestCompleted(questId) || data.isQuestFailed(questId)
                || !data.getCollectionArchives().allRuns(questId).isEmpty();
    }

    private static QuestRuntimeData sourceRuntime(ArcQuestPlayer data, ResourceLocation entryId) {
        for (QuestRuntimeData runtime : data.getAllActiveQuests().values()) {
            var quest = QuestRegistry.getServerDefinition(ResourceLocation.tryParse(runtime.getQuestId()));
            if (quest != null && quest.hasCollectionSheets() && quest.getCollectionConfig().getEntry(entryId) != null) return runtime;
        }
        return null;
    }
    private static CompoundTag permanentEntitlement(ServerPlayer player, ArcQuestPlayer data, CollectionEntryDefinition entry,
                                                    CollectionEntryRewardDefinition reward) {
        QuestRuntimeData source = sourceRuntime(data, entry.getEntryId());
        String questId = source == null ? "" : source.getQuestId(), hash = source == null ? "" : source.getFrozenDefinitionHash();
        if (hash.isBlank() && player != null) {
            for (ResourceLocation id : QuestRegistry.getAllIds()) {
                var candidate = QuestRegistry.getServerDefinition(id);
                if (candidate == null || !candidate.hasCollectionSheets() || candidate.getCollectionConfig().getEntry(entry.getEntryId()) == null
                        || !CollectionRunDefinitionStore.capability(candidate).supported()) continue;
                hash = CollectionRunDefinitionStore.get(player.server).freezeRegistered(candidate); questId = candidate.getId().toString(); break;
            }
        }
        return CollectionRewardEntitlement.capture(reward, questId, hash, "", "", entry.getEntryId());
    }

    /** Public previews disclose presentation only; executable reward callbacks stay on the server. */
    public static Set<String> disclosedRewardIds(ArcQuestPlayer data, QuestDefinition quest, CollectionEntryDefinition entry) {
        if (!knownQuest(data, quest.getId().toString())) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        CollectionRecordState records = data.getCollectionRecords();
        Knowledge knowledge = knowledge(entry, records);
        var record = records.getRecord(entry.getEntryId());
        for (String id : record.getRewardEntitlementIds())
            if (record.isRewardUnlocked(id) || record.isRewardClaimed(id)) ids.add(id);
        for (CollectionEntryRewardDefinition reward : entry.getRewards()) {
            if (reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE
                    && (records.isRewardUnlocked(entry.getEntryId(), reward.rewardId()) || records.isRewardClaimed(entry.getEntryId(), reward.rewardId())
                    || eligible(reward, knowledge) || publicPreview(entry, reward))) ids.add(reward.rewardId());
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

    public static List<CollectionEntryRewardDefinition> rewards(CollectionEntryDefinition entry, EntryRequirementBinding binding) {
        List<CollectionEntryRewardDefinition> result = new ArrayList<>(entry.getRewards());
        result.addAll(binding.getRewards());
        return List.copyOf(result);
    }

    public static boolean publicPreview(CollectionEntryDefinition entry, CollectionEntryRewardDefinition reward) {
        return reward.previewVisibility() == CollectionRewardPreviewVisibility.PUBLIC
                && entry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT;
    }

    public static Set<String> disclosedBindingRewardIds(ArcQuestPlayer data, QuestDefinition quest,
            PhaseDefinition phase, EntryRequirementBinding binding) {
        CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
        if (entry == null || !knownQuest(data, quest.getId().toString())
                || !org.arcadia.arc_quest.data.sync.CollectionContentDisclosure.entryRevealed(entry, data.getCollectionRecords())) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        List<QuestRuntimeData> runtimes = new ArrayList<>(data.getCollectionArchives().allRuns(quest.getId().toString()));
        QuestRuntimeData current = data.getActiveQuest(quest.getId().toString());
        if (current != null) runtimes.add(current);
        for (CollectionEntryRewardDefinition reward : binding.getRewards()) {
            if (publicPreview(entry, reward)) result.add(reward.rewardId());
            for (QuestRuntimeData runtime : runtimes) {
                CollectionRuntimeData run = runtime.getCollectionData();
                if (run != null && (run.isEntryRewardUnlocked(phase.getPhaseId(), binding.getBindingId(), reward.rewardId())
                        || run.isEntryRewardClaimed(phase.getPhaseId(), binding.getBindingId(), reward.rewardId()))) result.add(reward.rewardId());
            }
        }
        return Set.copyOf(result);
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
            for (PhaseDefinition phase : quest.getAllPhases()) if (phase.hasCollectionSheet())
                for (EntryRequirementBinding binding : phase.getCollectionSheet().getBindings())
                    for (String reward : disclosedBindingRewardIds(data, quest, phase, binding))
                        keys.add(id + "/" + phase.getPhaseId() + "/" + binding.getBindingId() + "/" + reward);
        }
        return Set.copyOf(keys);
    }

}
