package org.arcadia.arc_quest.data.sync;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.CollectionQuestArchives;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.data.CollectionRewardEntitlement;
import org.arcadia.arc_quest.quest.logic.CollectionRunDefinitions;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.spec.*;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Recipient-authorized document projection; hidden identity and locked block bytes never enter a packet. */
public final class CollectionContentDisclosure {
    private CollectionContentDisclosure() {}

    public static DatapackContentSnapshot project(DatapackContentSnapshot source, CollectionRecordState records,
            Predicate<String> knownQuest, Function<String, QuestDefinition> definitions, ServerPlayer player) {
        return project(source, records, knownQuest, definitions, player, List.of());
    }

    public static DatapackContentSnapshot project(DatapackContentSnapshot source, CollectionRecordState records,
            Predicate<String> knownQuest, Function<String, QuestDefinition> definitions, ServerPlayer player,
            Collection<QuestDefinition> supplements) {
        return project(source, records, knownQuest, definitions, player, supplements, Map.of());
    }

    /** A selected active/archived run replaces its whole live document, never just selected fields. */
    public static DatapackContentSnapshot project(DatapackContentSnapshot source, CollectionRecordState records,
            Predicate<String> knownQuest, Function<String, QuestDefinition> definitions, ServerPlayer player,
            Collection<QuestDefinition> supplements, Map<String, QuestDefinition> selectedRuns) {
        return project(source, records, knownQuest, definitions, player, supplements, selectedRuns, Map.of());
    }

    public static DatapackContentSnapshot project(DatapackContentSnapshot source, CollectionRecordState records,
            Predicate<String> knownQuest, Function<String, QuestDefinition> definitions, ServerPlayer player,
            Collection<QuestDefinition> supplements, Map<String, QuestDefinition> selectedRuns,
            Map<String, QuestRuntimeData> selectedRuntimes) {
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        for (String json : source.documents(DatapackContentModule.QUEST)) documents.put(QuestSpecJsonReader.read(json).id, json);
        for (QuestDefinition quest : supplements) {
            if (quest.hasCollectionSheets() && !documents.containsKey(quest.getId().toString())) {
                documents.put(quest.getId().toString(), QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, player)));
            }
        }
        for (var selected : selectedRuns.entrySet()) {
            QuestDefinition quest = selected.getValue();
            if (quest == null) documents.remove(selected.getKey());
            else if (quest.hasCollectionSheets()) documents.put(selected.getKey(),
                    QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, player)));
        }
        List<String> quests = new ArrayList<>();
        for (String json : documents.values()) {
            QuestSpec spec = QuestSpecJsonReader.read(json);
            if (spec.collectionConfig == null || (spec.collectionConfig.entries.isEmpty() && spec.collectionConfig.entryIds.isEmpty())) {
                quests.add(json); continue;
            }
            QuestDefinition definition = definitions.apply(spec.id);
            QuestRuntimeData selectedRuntime = selectedRuntimes.get(spec.id);
            CollectionQuestConfig config = definition == null ? null : definition.getCollectionConfig();
            Map<String, CollectionEntrySpecData> raw = new LinkedHashMap<>();
            for (CollectionEntrySpecData entry : spec.collectionConfig.entries) raw.put(entry.entryId, entry);
            if (config != null) for (CollectionEntryDefinition entry : config.getEntries()) {
                raw.computeIfAbsent(entry.getEntryId().toString(), ignored -> CollectionDefinitionSpecExporter.entry(entry, player));
            }
            // Inline shared entries so compilation order cannot expose an unfiltered registry definition.
            spec.collectionConfig.entries = new ArrayList<>(raw.values());
            spec.collectionConfig.entryIds.clear();
            Set<String> revealedIds = new HashSet<>();
            for (CollectionEntrySpecData entry : spec.collectionConfig.entries) {
                ResourceLocation id = ResourceLocation.tryParse(entry.entryId);
                CollectionEntryDefinition runtime = id == null || config == null ? null : config.getEntry(id);
                boolean revealed = id != null && knownQuest.test(spec.id) && (records.isDiscovered(id)
                        || "VISIBLE_BY_DEFAULT".equals(entry.visibilityMode));
                if (!revealed) maskEntry(entry, knownQuest.test(spec.id));
                else {
                    revealedIds.add(entry.entryId);
                    ResourceLocation itemTag = ResourceLocation.tryParse(entry.itemTag);
                    if (itemTag != null && selectedRuntime != null && selectedRuntime.hasFrozenItemTag(itemTag))
                        entry.frozenTagMembers = selectedRuntime.getFrozenItemTagMembers(itemTag).stream().map(ResourceLocation::toString).sorted().toList();
                    entry.discoveryObjectives.forEach(objective -> discloseFrozenTagCandidates(objective, selectedRuntime));
                    entry.researchObjectives.forEach(objective -> discloseFrozenTagCandidates(objective, selectedRuntime));
                    Set<String> allowedBlocks = new HashSet<>();
                    if (runtime != null) for (CollectionContentBlock block : runtime.getContent()) {
                        if (CollectionProgressProjector.contentRevealed(runtime, block, records)) allowedBlocks.add(block.blockId());
                    }
                    // A definition removed/replaced during reload must fail closed for its private content.
                    entry.content.removeIf(block -> !allowedBlocks.contains(block.blockId));
                    entry.recordConditions.clear();
                    ArcQuestPlayer rewardData = player == null ? null : ArcQuestPlayerManager.get(player);
                    Set<String> allowedRewards = runtime == null ? Set.of() : rewardData == null
                            ? runtime.getRewards().stream().filter(reward -> reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE
                                    && (records.isRewardUnlocked(runtime.getEntryId(), reward.rewardId())
                                    || records.isRewardClaimed(runtime.getEntryId(), reward.rewardId())
                                    || CollectionEntryRewardService.publicPreview(runtime, reward)))
                                    .map(CollectionEntryRewardDefinition::rewardId).collect(java.util.stream.Collectors.toSet())
                            : CollectionEntryRewardService.disclosedRewardIds(rewardData, definition, runtime);
                    entry.rewards.removeIf(reward -> !allowedRewards.contains(reward.rewardId));
                    entry.rewards.forEach(CollectionContentDisclosure::presentationReward);
                    entry.legacyResearchObjectives.clear();
                    entry.legacyResearchOutcomeMappings.clear();
                }
            }
            for (PhaseSpec phase : spec.phases) {
                if (phase.collectionSheet == null) continue;
                Set<String> bound = new HashSet<>(), publicObjectives = new HashSet<>();
                for (EntryRequirementBindingSpecData binding : phase.collectionSheet.bindings) {
                    bound.addAll(binding.objectiveIds);
                    if (revealedIds.contains(binding.entryId)) publicObjectives.addAll(binding.objectiveIds);
                    CollectionEntryDefinition entry = config == null ? null : config.getEntry(ResourceLocation.tryParse(binding.entryId));
                    PhaseDefinition realPhase = definition == null ? null : definition.getPhase(phase.phaseId);
                    EntryRequirementBinding realBinding = realPhase == null || !realPhase.hasCollectionSheet() ? null
                            : realPhase.getCollectionSheet().getBinding(binding.bindingId);
                    ArcQuestPlayer rewardData = player == null ? null : ArcQuestPlayerManager.get(player);
                    Set<String> allowed = entry == null || realBinding == null || !revealedIds.contains(binding.entryId) ? Set.of()
                            : rewardData == null ? realBinding.getRewards().stream()
                                .filter(reward -> CollectionEntryRewardService.publicPreview(entry, reward))
                                .map(CollectionEntryRewardDefinition::rewardId).collect(java.util.stream.Collectors.toSet())
                            : CollectionEntryRewardService.disclosedBindingRewardIds(rewardData, definition, realPhase, realBinding);
                    binding.rewards.removeIf(reward -> !allowed.contains(reward.rewardId));
                    binding.rewards.forEach(CollectionContentDisclosure::presentationReward);
                }
                for (ObjectiveSpec objective : phase.objectives) {
                    if (bound.contains(objective.id) && !publicObjectives.contains(objective.id)) maskObjective(objective);
                    else discloseFrozenTagCandidates(objective, selectedRuntime);
                }
            }
            quests.add(QuestSpecJsonWriter.write(spec));
        }
        EnumMap<DatapackContentModule, List<String>> modules = new EnumMap<>(DatapackContentModule.class);
        modules.putAll(source.documents()); modules.put(DatapackContentModule.QUEST, quests);
        return new DatapackContentSnapshot(source.epoch(), modules, source.objectiveTypes());
    }

    public static boolean entryRevealed(CollectionEntryDefinition entry, CollectionRecordState records) {
        return records.isDiscovered(entry.getEntryId()) || entry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT;
    }

    /** Network-only copy: permanent server records and migration bookkeeping remain unchanged. */
    public static CompoundTag sanitizePlayerSnapshot(ArcQuestPlayer data) {
        return sanitizePlayerSnapshot(data, id -> recipientDefinition(data, id));
    }

    public static CompoundTag sanitizePlayerSnapshot(ArcQuestPlayer data, Function<String, QuestDefinition> definitions) {
        CompoundTag snapshot = data.serializeNBT();
        Set<String> known = knownQuests(data);
        snapshot.put(CollectionRecordState.ROOT_KEY,
                sanitizeRecordSnapshot(data, snapshot.getCompound(CollectionRecordState.ROOT_KEY), definitions));
        ListTag active = snapshot.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) sanitizeRuntimeSnapshot(active.getCompound(i),
                snapshotDefinition(active.getCompound(i), definitions), data.getCollectionRecords());
        ListTag archives = snapshot.getList(CollectionQuestArchives.ROOT_KEY, Tag.TAG_COMPOUND);
        ListTag authorizedArchives = new ListTag();
        for (int i = 0; i < archives.size(); i++) {
            CompoundTag runtime = archives.getCompound(i);
            String questId = runtime.getString("QuestId");
            if (!known.contains(questId)) continue;
            sanitizeRuntimeSnapshot(runtime, snapshotDefinition(runtime, definitions), data.getCollectionRecords());
            authorizedArchives.add(runtime);
        }
        snapshot.put(CollectionQuestArchives.ROOT_KEY, authorizedArchives);
        return snapshot;
    }

    /** Keeps the revision even when every changed record is private, preserving delta ordering. */
    public static CompoundTag sanitizeRecordSnapshot(ArcQuestPlayer data, CompoundTag records) {
        return sanitizeRecordSnapshot(data, records, id -> recipientDefinition(data, id));
    }

    public static CompoundTag sanitizeRecordSnapshot(ArcQuestPlayer data, CompoundTag records,
            Function<String, QuestDefinition> definitions) {
        Map<String, List<CollectionEntryDefinition>> authorized = new LinkedHashMap<>();
        for (String questId : knownQuests(data)) {
            QuestDefinition quest = definitions.apply(questId);
            if (quest == null || quest.getCollectionConfig() == null) continue;
            for (CollectionEntryDefinition entry : quest.getCollectionConfig().getEntries()) {
                if (entryRevealed(entry, data.getCollectionRecords())) authorized
                        .computeIfAbsent(entry.getEntryId().toString(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        CompoundTag sanitized = records.copy();
        sanitized.remove("MigratedLegacyEntries");
        sanitized.remove("LegacyRewardReceipts");
        sanitized.remove(CollectionRecordState.RESET_ENTRY_IDS_KEY);
        sanitized.remove(CollectionRecordState.ENTRY_GENERATIONS_KEY);
        sanitized.remove(CollectionRecordState.IMPORT_SUPPRESSED_KEY);
        CompoundTag entries = sanitized.getCompound("Entries");
        for (String id : new HashSet<>(entries.getAllKeys())) {
            List<CollectionEntryDefinition> definitionsForEntry = authorized.get(id);
            if (definitionsForEntry == null) { entries.remove(id); continue; }
            Set<String> allowed = new HashSet<>();
            if (data.getCollectionRecords().isDiscovered(ResourceLocation.parse(id))) allowed.add("entry");
            for (CollectionEntryDefinition definition : definitionsForEntry) for (CollectionContentBlock block : definition.getContent()) {
                if (CollectionProgressProjector.contentRevealed(definition, block, data.getCollectionRecords())) allowed.add(block.blockId());
            }
            CompoundTag record = entries.getCompound(id);
            CompoundTag outcomes = record.getCompound("Outcomes");
            for (String outcomeId : new HashSet<>(outcomes.getAllKeys())) {
                if (definitionsForEntry.stream().noneMatch(definition -> definition.getOutcome(outcomeId) != null)) outcomes.remove(outcomeId);
                else outcomes.getCompound(outcomeId).remove("Generation");
            }
            record.put("Outcomes", outcomes);
            Set<String> declaredSteps = new HashSet<>();
            for (CollectionEntryDefinition definition : definitionsForEntry) {
                definition.getDiscoveryObjectives().forEach(objective -> declaredSteps.add(CollectionProgressProjector.discoveryKey(objective.getObjectiveId())));
                definition.getResearchObjectives().forEach(objective -> declaredSteps.add(CollectionProgressProjector.researchKey(objective.getObjectiveId())));
            }
            CompoundTag steps = record.getCompound("Steps");
            for (String stepId : new HashSet<>(steps.getAllKeys())) if (!declaredSteps.contains(stepId)) steps.remove(stepId);
            record.put("Steps", steps);
            ListTag seen = record.getList("Seen", Tag.TAG_STRING), visibleSeen = new ListTag();
            for (int i = 0; i < seen.size(); i++) if (allowed.contains(seen.getString(i))) visibleSeen.add(seen.get(i).copy());
            record.put("Seen", visibleSeen);
            Set<String> rewardIds = definitionsForEntry.stream().flatMap(definition -> definition.getRewards().stream())
                    .filter(reward -> reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE)
                    .map(CollectionEntryRewardDefinition::rewardId).collect(java.util.stream.Collectors.toSet());
            Set<String> receiptIds = new HashSet<>();
            for (String key : List.of("UnlockedRewards", "ClaimedRewards")) {
                ListTag receipts = record.getList(key, Tag.TAG_STRING);
                for (int i = 0; i < receipts.size(); i++) receiptIds.add(receipts.getString(i));
            }
            CompoundTag entitled = record.getCompound("RewardEntitlements"), presentations = new CompoundTag();
            for (String rewardId : entitled.getAllKeys()) {
                CompoundTag entitlement = entitled.getCompound(rewardId);
                if (!receiptIds.contains(rewardId) || !validEntitlement(entitlement, rewardId, id, false)) continue;
                presentations.put(rewardId, CollectionRewardEntitlement.presentation(entitlement));
                // An earned old reward stays visible even after its provider renames or removes its ID.
                rewardIds.add(rewardId);
            }
            record.put("RewardEntitlements", presentations);
            for (String key : List.of("UnlockedRewards", "ClaimedRewards")) {
                ListTag raw = record.getList(key, Tag.TAG_STRING), visible = new ListTag();
                for (int i = 0; i < raw.size(); i++) if (rewardIds.contains(raw.getString(i))) visible.add(raw.get(i).copy());
                record.put(key, visible);
            }
        }
        sanitized.put("Entries", entries);
        return sanitized;
    }

    /** Clips an isolated runtime copy to the real sheet; old Collection fields remain compatible. */
    public static void sanitizeRuntimeSnapshot(CompoundTag runtime, QuestDefinition quest) {
        sanitizeRuntimeSnapshot(runtime, quest, null);
    }

    /** A recipient's hidden entries must not disclose even their reward receipt IDs. */
    public static void sanitizeRuntimeSnapshot(CompoundTag runtime, QuestDefinition quest, CollectionRecordState records) {
        runtime.remove(QuestRuntimeData.FROZEN_DEFINITION_HASH_KEY);
        runtime.remove(QuestRuntimeData.FROZEN_DEFINITION_VERSION_KEY);
        runtime.remove(QuestRuntimeData.FROZEN_ITEM_TAGS_COMPLETE_KEY);
        Set<String> allowedTags = new HashSet<>();
        if (quest != null && quest.getCollectionConfig() != null) {
            for (CollectionEntryDefinition entry : quest.getCollectionConfig().getEntries()) {
                boolean revealed = records == null ? entry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT : entryRevealed(entry, records);
                if (revealed && entry.getItemTag() != null) allowedTags.add(entry.getItemTag().toString());
            }
            for (PhaseDefinition phase : quest.getAllPhases()) {
                Set<String> bound = new HashSet<>(), revealedObjectives = new HashSet<>();
                if (phase.hasCollectionSheet()) for (EntryRequirementBinding binding : phase.getCollectionSheet().getBindings()) {
                    bound.addAll(binding.getObjectiveIds());
                    CollectionEntryDefinition entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
                    boolean revealed = entry != null && (records == null ? entry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT : entryRevealed(entry, records));
                    if (revealed) revealedObjectives.addAll(binding.getObjectiveIds());
                }
                for (ObjectiveEntry objective : phase.getObjectives()) if (!objective.isHidden()
                        && (!bound.contains(objective.getObjectiveId()) || revealedObjectives.contains(objective.getObjectiveId()))
                        && objective.hasTargetTag()) allowedTags.add(objective.getTargetTagResourceLocation().toString());
            }
        }
        CompoundTag frozenTags = runtime.getCompound(QuestRuntimeData.FROZEN_ITEM_TAGS_KEY);
        for (String id : new HashSet<>(frozenTags.getAllKeys())) if (!allowedTags.contains(id)) frozenTags.remove(id);
        if (frozenTags.isEmpty()) runtime.remove(QuestRuntimeData.FROZEN_ITEM_TAGS_KEY);
        else runtime.put(QuestRuntimeData.FROZEN_ITEM_TAGS_KEY, frozenTags);
        CompoundTag phases = runtime.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases");
        for (String phaseId : phases.getAllKeys()) {
            PhaseDefinition phase = quest == null ? null : quest.getPhase(phaseId);
            Set<String> boundEntries = new HashSet<>();
            if (phase != null && phase.hasCollectionSheet()) {
                phase.getCollectionSheet().getBindings().forEach(binding -> boundEntries.add(binding.getEntryId().toString()));
            }
            CompoundTag phaseData = phases.getCompound(phaseId);
            phaseData.remove("PendingOutcomes");
            ListTag baseline = phaseData.getList("DiscoveryBaseline", Tag.TAG_STRING), clipped = new ListTag();
            for (int i = 0; i < baseline.size(); i++) if (boundEntries.contains(baseline.getString(i))) clipped.add(StringTag.valueOf(baseline.getString(i)));
            phaseData.put("DiscoveryBaseline", clipped);
            CompoundTag bindingRewards = phaseData.getCompound("EntryRewards");
            for (String bindingId : new HashSet<>(bindingRewards.getAllKeys())) {
                EntryRequirementBinding binding = phase == null || !phase.hasCollectionSheet() ? null : phase.getCollectionSheet().getBinding(bindingId);
                CollectionEntryDefinition entry = binding == null || quest.getCollectionConfig() == null ? null : quest.getCollectionConfig().getEntry(binding.getEntryId());
                boolean revealed = entry != null && (records == null
                        ? entry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT : entryRevealed(entry, records));
                Set<String> declared = !revealed ? Set.of() : CollectionEntryRewardService.rewards(entry, binding).stream().filter(reward -> reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE)
                        .map(CollectionEntryRewardDefinition::rewardId).collect(java.util.stream.Collectors.toSet());
                CompoundTag rewards = bindingRewards.getCompound(bindingId);
                for (String id : new HashSet<>(rewards.getAllKeys())) {
                    CompoundTag receipt = rewards.getCompound(id);
                    CompoundTag entitlement = receipt.getCompound(receipt.contains("Entitlement", Tag.TAG_COMPOUND)
                            ? "Entitlement" : "EntitlementPresentation");
                    ResourceLocation entitledId = ResourceLocation.tryParse(entitlement.getString("EntryId"));
                    CollectionEntryDefinition entitledEntry = entitledId == null || quest == null || quest.getCollectionConfig() == null
                            ? null : quest.getCollectionConfig().getEntry(entitledId);
                    boolean entitledRevealed = entitledEntry != null && (records == null
                            ? entitledEntry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT : entryRevealed(entitledEntry, records));
                    boolean authorizedEntitlement = entitledRevealed && validEntitlement(entitlement, id, entitledId.toString(), true);
                    if ((!declared.contains(id) && !authorizedEntitlement)
                            || (!receipt.getBoolean("Unlocked") && !receipt.getBoolean("Claimed"))) rewards.remove(id);
                    else {
                        CompoundTag safeReceipt = new CompoundTag();
                        safeReceipt.putBoolean("Unlocked", receipt.getBoolean("Unlocked"));
                        safeReceipt.putBoolean("Claimed", receipt.getBoolean("Claimed"));
                        safeReceipt.putBoolean("DeliveryPending", receipt.getBoolean("DeliveryPending"));
                        if (authorizedEntitlement) safeReceipt.put("EntitlementPresentation", CollectionRewardEntitlement.presentation(entitlement));
                        rewards.put(id, safeReceipt);
                    }
                }
                if (rewards.isEmpty()) bindingRewards.remove(bindingId);
            }
            phaseData.put("EntryRewards", bindingRewards);
        }
    }

    private static Set<String> knownQuests(ArcQuestPlayer data) {
        Set<String> known = new LinkedHashSet<>(data.getAllActiveQuests().keySet());
        known.addAll(data.getCompletedQuests()); known.addAll(data.getFailedQuests());
        return known;
    }

    private static boolean validEntitlement(CompoundTag entitlement, String rewardId, String entryId, boolean binding) {
        if (!rewardId.equals(entitlement.getString("RewardId")) || !entryId.equals(entitlement.getString("EntryId"))) return false;
        try {
            CollectionEntryRewardTrigger trigger = CollectionEntryRewardTrigger.valueOf(entitlement.getString("Trigger"));
            EntryRewardGrantMode.valueOf(entitlement.getString("GrantMode"));
            CollectionRewardPreviewVisibility.valueOf(entitlement.getString("Preview"));
            return (trigger == CollectionEntryRewardTrigger.BINDING_COMPLETE) == binding;
        } catch (IllegalArgumentException exception) { return false; }
    }

    private static QuestDefinition serverDefinition(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? null : QuestRegistry.getServerDefinition(parsed);
    }

    /** This is the same default run chosen by the journal cache: active first, otherwise latest archive. */
    public static QuestRuntimeData preferredRuntime(ArcQuestPlayer data, String questId) {
        QuestRuntimeData active = data.getActiveQuest(questId);
        return active == null ? data.getCollectionArchives().get(questId) : active;
    }

    private static QuestDefinition recipientDefinition(ArcQuestPlayer data, String id) {
        QuestRuntimeData runtime = preferredRuntime(data, id);
        QuestDefinition live = serverDefinition(id);
        if (runtime == null) return live;
        try { return CollectionRunDefinitions.resolve(ServerLifecycleHooks.getCurrentServer(), runtime, live); }
        catch (RuntimeException exception) {
            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_NETWORK,
                    "Cannot disclose collection quest '{}'; restore its original definition provider: {}", id, exception.getMessage());
            return null;
        }
    }

    private static QuestDefinition snapshotDefinition(CompoundTag runtime, Function<String, QuestDefinition> definitions) {
        QuestDefinition fallback = definitions.apply(runtime.getString("QuestId"));
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || (!runtime.contains(QuestRuntimeData.FROZEN_DEFINITION_HASH_KEY, Tag.TAG_STRING)
                && !runtime.contains("CollectionData", Tag.TAG_COMPOUND) && (fallback == null || !fallback.hasCollectionSheets()))) return fallback;
        try { return CollectionRunDefinitions.resolve(server, QuestRuntimeData.deserializeNBT(runtime), fallback); }
        catch (RuntimeException exception) { return null; }
    }

    private static void maskEntry(CollectionEntrySpecData entry, boolean known) {
        // This hint is explicitly authored as public. No identity, objective or texture is used to derive it.
        if (!known || !HiddenPresentationMode.PLACEHOLDER.name().equals(entry.hiddenPresentationMode))
            entry.publicClue = QuestTextSpec.literal("");
        entry.displayName = QuestTextSpec.translatable("arc_quest.collection.entry.unknown");
        entry.description = QuestTextSpec.literal(""); entry.subjectKind = "CUSTOM";
        entry.subjectId = ""; entry.itemTag = ""; entry.icon = ObjectiveIcons.none();
        entry.frozenTagMembers = null;
        entry.relatedItems.clear(); entry.content.clear(); entry.recordConditions.clear();
        entry.rewards.clear();
        entry.outcomes.forEach(outcome -> outcome.displayName = QuestTextSpec.translatable("arc_quest.collection.entry.unknown"));
        entry.legacyResearchObjectives.clear();
        entry.legacyResearchOutcomeMappings.clear();
        // Preserve anonymous step IDs/count thresholds so the client's record-state math remains correct.
        entry.discoveryObjectives.forEach(CollectionContentDisclosure::maskObjective);
        entry.researchObjectives.forEach(CollectionContentDisclosure::maskObjective);
    }

    private static void maskObjective(ObjectiveSpec objective) {
        objective.type = "arc_quest:custom"; objective.targetId = "arc_quest:undisclosed";
        objective.displayText = QuestTextSpec.translatable("arc_quest.collection.requirement.unavailable");
        objective.icon = ObjectiveIcons.none(); objective.npcId = ""; objective.itemTag = "";
        objective.x = null; objective.y = null; objective.z = null; objective.radius = null;
        objective.extraData.clear(); objective.relatedMarks.clear(); objective.hidden = true;
        objective.countMode = ""; objective.countBase = null; objective.countPerLevel = null;
        objective.countMin = null; objective.countMax = null;
    }

    private static void discloseFrozenTagCandidates(ObjectiveSpec objective, QuestRuntimeData runtime) {
        if (objective.hidden || runtime == null) return;
        ResourceLocation tagId = ResourceLocation.tryParse(objective.extraData.getOrDefault("target_tag", ""));
        if (tagId == null && objective.itemTag != null) tagId = ResourceLocation.tryParse(objective.itemTag);
        if (tagId != null && runtime.hasFrozenItemTag(tagId)) objective.extraData.put(ObjectiveItemResolver.FROZEN_TAG_MEMBERS,
                ObjectiveItemResolver.encodeFrozenTagMembers(runtime.getFrozenItemTagMembers(tagId)));
    }

    private static void presentationReward(CollectionEntryRewardSpecData reward) {
        // The claim identifies a server reward by stable ID; the client needs only authorized item previews.
        reward.rewards.removeIf(component -> !"item".equals(component.type));
    }
}
