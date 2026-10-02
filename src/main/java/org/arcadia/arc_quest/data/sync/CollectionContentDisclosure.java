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
        LinkedHashMap<String, String> documents = new LinkedHashMap<>();
        for (String json : source.documents(DatapackContentModule.QUEST)) documents.put(QuestSpecJsonReader.read(json).id, json);
        for (QuestDefinition quest : supplements) {
            if (quest.hasCollectionSheets() && !documents.containsKey(quest.getId().toString())) {
                documents.put(quest.getId().toString(), QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, player)));
            }
        }
        List<String> quests = new ArrayList<>();
        for (String json : documents.values()) {
            QuestSpec spec = QuestSpecJsonReader.read(json);
            if (spec.collectionConfig == null || (spec.collectionConfig.entries.isEmpty() && spec.collectionConfig.entryIds.isEmpty())) {
                quests.add(json); continue;
            }
            QuestDefinition definition = definitions.apply(spec.id);
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
                if (!revealed) maskEntry(entry);
                else {
                    revealedIds.add(entry.entryId);
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
                                    || records.isRewardClaimed(runtime.getEntryId(), reward.rewardId())))
                                    .map(CollectionEntryRewardDefinition::rewardId).collect(java.util.stream.Collectors.toSet())
                            : CollectionEntryRewardService.disclosedRewardIds(rewardData, definition, runtime);
                    entry.rewards.removeIf(reward -> !allowedRewards.contains(reward.rewardId));
                }
            }
            for (PhaseSpec phase : spec.phases) {
                if (phase.collectionSheet == null) continue;
                Set<String> bound = new HashSet<>(), publicObjectives = new HashSet<>();
                for (EntryRequirementBindingSpecData binding : phase.collectionSheet.bindings) {
                    bound.addAll(binding.objectiveIds);
                    if (revealedIds.contains(binding.entryId)) publicObjectives.addAll(binding.objectiveIds);
                }
                for (ObjectiveSpec objective : phase.objectives) {
                    if (bound.contains(objective.id) && !publicObjectives.contains(objective.id)) maskObjective(objective);
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
        return sanitizePlayerSnapshot(data, CollectionContentDisclosure::serverDefinition);
    }

    public static CompoundTag sanitizePlayerSnapshot(ArcQuestPlayer data, Function<String, QuestDefinition> definitions) {
        CompoundTag snapshot = data.serializeNBT();
        Set<String> known = knownQuests(data);
        snapshot.put(CollectionRecordState.ROOT_KEY,
                sanitizeRecordSnapshot(data, snapshot.getCompound(CollectionRecordState.ROOT_KEY), definitions));
        ListTag active = snapshot.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) sanitizeRuntimeSnapshot(active.getCompound(i),
                definitions.apply(active.getCompound(i).getString("QuestId")), data.getCollectionRecords());
        ListTag archives = snapshot.getList(CollectionQuestArchives.ROOT_KEY, Tag.TAG_COMPOUND);
        ListTag authorizedArchives = new ListTag();
        for (int i = 0; i < archives.size(); i++) {
            CompoundTag runtime = archives.getCompound(i);
            String questId = runtime.getString("QuestId");
            if (!known.contains(questId)) continue;
            sanitizeRuntimeSnapshot(runtime, definitions.apply(questId), data.getCollectionRecords());
            authorizedArchives.add(runtime);
        }
        snapshot.put(CollectionQuestArchives.ROOT_KEY, authorizedArchives);
        return snapshot;
    }

    /** Keeps the revision even when every changed record is private, preserving delta ordering. */
    public static CompoundTag sanitizeRecordSnapshot(ArcQuestPlayer data, CompoundTag records) {
        return sanitizeRecordSnapshot(data, records, CollectionContentDisclosure::serverDefinition);
    }

    public static CompoundTag sanitizeRecordSnapshot(ArcQuestPlayer data, CompoundTag records,
            Function<String, QuestDefinition> definitions) {
        Map<String, CollectionEntryDefinition> authorized = new LinkedHashMap<>();
        for (String questId : knownQuests(data)) {
            QuestDefinition quest = definitions.apply(questId);
            if (quest == null || quest.getCollectionConfig() == null) continue;
            for (CollectionEntryDefinition entry : quest.getCollectionConfig().getEntries()) {
                if (entryRevealed(entry, data.getCollectionRecords())) authorized.put(entry.getEntryId().toString(), entry);
            }
        }
        CompoundTag sanitized = records.copy();
        sanitized.remove("MigratedLegacyEntries");
        sanitized.remove("LegacyRewardReceipts");
        sanitized.remove(CollectionRecordState.RESET_ENTRY_IDS_KEY);
        CompoundTag entries = sanitized.getCompound("Entries");
        for (String id : new HashSet<>(entries.getAllKeys())) {
            CollectionEntryDefinition definition = authorized.get(id);
            if (definition == null) { entries.remove(id); continue; }
            Set<String> allowed = new HashSet<>();
            if (data.getCollectionRecords().isDiscovered(definition.getEntryId())) allowed.add("entry");
            for (CollectionContentBlock block : definition.getContent()) {
                if (CollectionProgressProjector.contentRevealed(definition, block, data.getCollectionRecords())) allowed.add(block.blockId());
            }
            CompoundTag record = entries.getCompound(id);
            Set<String> declaredSteps = new HashSet<>();
            definition.getDiscoveryObjectives().forEach(objective -> declaredSteps.add(CollectionProgressProjector.discoveryKey(objective.getObjectiveId())));
            definition.getResearchObjectives().forEach(objective -> declaredSteps.add(CollectionProgressProjector.researchKey(objective.getObjectiveId())));
            CompoundTag steps = record.getCompound("Steps");
            for (String stepId : new HashSet<>(steps.getAllKeys())) if (!declaredSteps.contains(stepId)) steps.remove(stepId);
            record.put("Steps", steps);
            ListTag seen = record.getList("Seen", Tag.TAG_STRING), visibleSeen = new ListTag();
            for (int i = 0; i < seen.size(); i++) if (allowed.contains(seen.getString(i))) visibleSeen.add(seen.get(i).copy());
            record.put("Seen", visibleSeen);
            Set<String> rewardIds = definition.getRewards().stream()
                    .filter(reward -> reward.trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE)
                    .map(CollectionEntryRewardDefinition::rewardId).collect(java.util.stream.Collectors.toSet());
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
        CompoundTag phases = runtime.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases");
        for (String phaseId : phases.getAllKeys()) {
            PhaseDefinition phase = quest == null ? null : quest.getPhase(phaseId);
            Set<String> boundEntries = new HashSet<>();
            if (phase != null && phase.hasCollectionSheet()) {
                phase.getCollectionSheet().getBindings().forEach(binding -> boundEntries.add(binding.getEntryId().toString()));
            }
            CompoundTag phaseData = phases.getCompound(phaseId);
            ListTag baseline = phaseData.getList("DiscoveryBaseline", Tag.TAG_STRING), clipped = new ListTag();
            for (int i = 0; i < baseline.size(); i++) if (boundEntries.contains(baseline.getString(i))) clipped.add(StringTag.valueOf(baseline.getString(i)));
            phaseData.put("DiscoveryBaseline", clipped);
            CompoundTag bindingRewards = phaseData.getCompound("EntryRewards");
            for (String bindingId : new HashSet<>(bindingRewards.getAllKeys())) {
                EntryRequirementBinding binding = phase == null || !phase.hasCollectionSheet() ? null : phase.getCollectionSheet().getBinding(bindingId);
                CollectionEntryDefinition entry = binding == null || quest.getCollectionConfig() == null ? null : quest.getCollectionConfig().getEntry(binding.getEntryId());
                boolean revealed = entry != null && (records == null
                        ? entry.getVisibilityMode() == VisibilityMode.VISIBLE_BY_DEFAULT : entryRevealed(entry, records));
                if (!revealed) { bindingRewards.remove(bindingId); continue; }
                Set<String> declared = entry.getRewards().stream().filter(reward -> reward.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE)
                        .map(CollectionEntryRewardDefinition::rewardId).collect(java.util.stream.Collectors.toSet());
                CompoundTag rewards = bindingRewards.getCompound(bindingId);
                for (String id : new HashSet<>(rewards.getAllKeys())) {
                    CompoundTag receipt = rewards.getCompound(id);
                    if (!declared.contains(id) || (!receipt.getBoolean("Unlocked") && !receipt.getBoolean("Claimed"))) rewards.remove(id);
                }
            }
            phaseData.put("EntryRewards", bindingRewards);
        }
    }

    private static Set<String> knownQuests(ArcQuestPlayer data) {
        Set<String> known = new LinkedHashSet<>(data.getAllActiveQuests().keySet());
        known.addAll(data.getCompletedQuests()); known.addAll(data.getFailedQuests());
        return known;
    }

    private static QuestDefinition serverDefinition(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? null : QuestRegistry.getServerDefinition(parsed);
    }

    private static void maskEntry(CollectionEntrySpecData entry) {
        entry.displayName = QuestTextSpec.translatable("arc_quest.collection.entry.unknown");
        entry.description = QuestTextSpec.literal(""); entry.subjectKind = "CUSTOM";
        entry.subjectId = ""; entry.itemTag = ""; entry.icon = ObjectiveIcons.none();
        entry.relatedItems.clear(); entry.content.clear(); entry.recordConditions.clear();
        entry.rewards.clear();
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
}
