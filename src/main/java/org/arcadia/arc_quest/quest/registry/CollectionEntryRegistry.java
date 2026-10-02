package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import org.arcadia.arc_quest.quest.api.*;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Shared knowledge definitions. Snapshot replacement validates conflicts before publication. */
public final class CollectionEntryRegistry {
    private static final Map<ResourceLocation, CollectionEntryDefinition> CODE = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, CollectionEntryDefinition> entries = Map.of();
    private static volatile Map<OutcomeKey, List<CollectionOutcomeSource>> outcomeSources = Map.of();
    @Nullable private static volatile Map<ResourceLocation, CollectionEntryDefinition> clientEntries;
    private CollectionEntryRegistry() {}

    public static synchronized void register(CollectionEntryDefinition definition) {
        Objects.requireNonNull(definition, "collection entry");
        CollectionEntryDefinition previous = CODE.get(definition.getEntryId());
        checkConflict(previous, definition);
        checkConflict(entries.get(definition.getEntryId()), definition);
        if (previous == null) CODE.put(definition.getEntryId(), definition);
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> merged = new LinkedHashMap<>(entries);
        checkConflict(merged.get(definition.getEntryId()), definition);
        merged.putIfAbsent(definition.getEntryId(), definition);
        entries = Map.copyOf(merged);
    }

    /** Called with the successful, merged Quest snapshot after a datapack reload. */
    public static synchronized void replaceQuestSnapshot(Collection<QuestDefinition> quests) {
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> merged = new LinkedHashMap<>(CODE);
        for (QuestDefinition quest : quests) {
            CollectionQuestConfig config = quest.getCollectionConfig();
            if (config == null) continue;
            for (CollectionEntryDefinition definition : config.getEntries()) {
                checkConflict(merged.get(definition.getEntryId()), definition);
                merged.putIfAbsent(definition.getEntryId(), definition);
            }
        }
        Map<OutcomeKey, List<CollectionOutcomeSource>> sources = validateOutcomeSources(merged, quests);
        entries = Map.copyOf(merged);
        outcomeSources = sources;
    }

    public static synchronized void replaceClientPresentationSnapshot(Collection<QuestDefinition> quests) {
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> merged = new LinkedHashMap<>();
        for (QuestDefinition quest : quests) {
            if (quest.getCollectionConfig() == null) continue;
            for (CollectionEntryDefinition entry : quest.getCollectionConfig().getEntries()) {
                // The same shared entry can be redacted differently by its task context; keep the disclosed version.
                merged.merge(entry.getEntryId(), entry, (oldEntry, nextEntry) ->
                        disclosureScore(nextEntry) > disclosureScore(oldEntry) ? nextEntry : oldEntry);
            }
        }
        clientEntries = Map.copyOf(merged);
    }

    public static synchronized void clearClientPresentationSnapshot() { clientEntries = null; }
    private static int disclosureScore(CollectionEntryDefinition entry) {
        if (entry.getDisplayName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && "arc_quest.collection.entry.unknown".equals(text.getKey())) return -1;
        return (entry.getSubjectId() != null || entry.getItemTag() != null ? 4 : 0)
                + (entry.getIcon().mode() != org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec.Mode.NONE ? 2 : 0)
                + Math.min(8, entry.getContent().size());
    }
    private static Map<ResourceLocation, CollectionEntryDefinition> current() {
        var client = clientEntries;
        if (EffectiveSide.get() == LogicalSide.CLIENT) return client == null ? Map.of() : client;
        return entries;
    }

    @Nullable public static CollectionEntryDefinition get(ResourceLocation entryId) { return current().get(entryId); }
    @Nullable public static CollectionEntryDefinition get(String entryId) {
        ResourceLocation id = entryId == null ? null : ResourceLocation.tryParse(entryId);
        return id == null ? null : get(id);
    }
    public static Collection<CollectionEntryDefinition> all() { return current().values(); }
    public static Map<ResourceLocation, CollectionEntryDefinition> snapshot() { return current(); }
    @Nullable public static CollectionEntryDefinition getServerEntry(ResourceLocation entryId) { return entries.get(entryId); }
    public static Map<ResourceLocation, CollectionEntryDefinition> serverSnapshot() { return entries; }
    public static List<CollectionOutcomeSource> getOutcomeSources(ResourceLocation entryId, String outcomeId) {
        return outcomeSources.getOrDefault(outcomeKey(entryId, outcomeId), List.of());
    }
    public static synchronized void clear() { CODE.clear(); entries = Map.of(); clientEntries = null; outcomeSources = Map.of(); }

    private static OutcomeKey outcomeKey(ResourceLocation entryId, String outcomeId) { return new OutcomeKey(entryId, outcomeId); }

    /** All equivalent sources must opt in with recordOutcome; merely referencing the entry grants nothing. */
    private static Map<OutcomeKey, List<CollectionOutcomeSource>> validateOutcomeSources(
            Map<ResourceLocation, CollectionEntryDefinition> definitions, Collection<QuestDefinition> quests) {
        Map<OutcomeKey, List<CollectionOutcomeSource>> sources = new LinkedHashMap<>();
        Map<OutcomeKey, Set<OutcomeKey>> prerequisites = new LinkedHashMap<>();
        Set<ResourceLocation> referenced = new HashSet<>();
        for (QuestDefinition quest : quests) if (quest.getCollectionConfig() != null)
            for (CollectionEntryDefinition entry : quest.getCollectionConfig().getEntries()) referenced.add(entry.getEntryId());
        for (QuestDefinition quest : quests) for (PhaseDefinition phase : quest.getAllPhases()) {
            if (!phase.hasCollectionSheet()) continue;
            for (EntryRequirementBinding binding : phase.getCollectionSheet().getBindings()) {
                CollectionEntryDefinition entry = definitions.get(binding.getEntryId());
                if (entry == null) throw new IllegalArgumentException("Unknown outcome entry: " + binding.getEntryId());
                for (String outcomeId : binding.getOutcomeIds()) {
                    if (!entry.isUnifiedGameplay() || entry.getOutcome(outcomeId) == null)
                        throw new IllegalArgumentException("Unknown outcome source: " + outcomeKey(binding.getEntryId(), outcomeId));
                    OutcomeKey key = outcomeKey(binding.getEntryId(), outcomeId);
                    sources.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new CollectionOutcomeSource(quest.getId(), phase.getPhaseId(), binding.getBindingId()));
                    Set<OutcomeKey> requires = prerequisites.computeIfAbsent(key, ignored -> new HashSet<>());
                    for (var requirement : binding.getRecordRequirements()) if (requirement.type() == CollectionRecordRequirement.Type.OUTCOME)
                        requires.add(outcomeKey(binding.getEntryId(), requirement.stepId()));
                }
            }
        }
        for (var entry : definitions.values()) for (var outcome : entry.getOutcomes()) {
            if (!referenced.contains(entry.getEntryId())) continue; // Standalone code entries may precede their quests during setup.
            OutcomeKey key = outcomeKey(entry.getEntryId(), outcome.outcomeId());
            if (!sources.containsKey(key)) throw new IllegalArgumentException("Outcome has no explicit investigation source: " + key);
        }
        Set<OutcomeKey> visited = new HashSet<>(), visiting = new HashSet<>();
        for (OutcomeKey key : prerequisites.keySet()) validateAcyclic(key, prerequisites, visited, visiting);
        Map<OutcomeKey, List<CollectionOutcomeSource>> frozen = new LinkedHashMap<>();
        sources.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Map.copyOf(frozen);
    }

    private static void validateAcyclic(OutcomeKey key, Map<OutcomeKey, Set<OutcomeKey>> dependencies, Set<OutcomeKey> visited, Set<OutcomeKey> visiting) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) throw new IllegalArgumentException("Cyclic outcome investigation prerequisites: " + key);
        for (OutcomeKey dependency : dependencies.getOrDefault(key, Set.of())) validateAcyclic(dependency, dependencies, visited, visiting);
        visiting.remove(key); visited.add(key);
    }
    private record OutcomeKey(ResourceLocation entryId, String outcomeId) { }

    private static void checkConflict(CollectionEntryDefinition previous, CollectionEntryDefinition next) {
        if (previous != null && previous != next && !equivalent(previous, next)) {
            throw new IllegalArgumentException("Conflicting shared collection entry: " + next.getEntryId());
        }
    }

    /** Compare the immutable public definition rather than object identity of compiled objectives. */
    public static boolean equivalent(CollectionEntryDefinition left, CollectionEntryDefinition right) {
        return left.getEntryId().equals(right.getEntryId())
                && left.getCategoryId().equals(right.getCategoryId())
                && left.getDisplayName().equals(right.getDisplayName())
                && left.getDescription().equals(right.getDescription())
                && left.getPublicClue().equals(right.getPublicClue())
                && left.getSubjectKind() == right.getSubjectKind()
                && Objects.equals(left.getSubjectId(), right.getSubjectId())
                && Objects.equals(left.getItemTag(), right.getItemTag())
                && Objects.equals(left.getPresentationItemTagMembers(), right.getPresentationItemTagMembers())
                && left.getIcon().equals(right.getIcon())
                && left.getRelatedItems().equals(right.getRelatedItems())
                && left.getVisibilityMode() == right.getVisibilityMode()
                && left.getHiddenPresentationMode() == right.getHiddenPresentationMode()
                && left.getSortOrder() == right.getSortOrder()
                && left.isResearchAfterDiscovery() == right.isResearchAfterDiscovery()
                && left.getGameplayVersion() == right.getGameplayVersion()
                && sameOutcomes(left.getOutcomes(), right.getOutcomes())
                && left.getLegacyResearchOutcomeMappings().equals(right.getLegacyResearchOutcomeMappings())
                && sameObjectives(left.getLegacyResearchObjectives(), right.getLegacyResearchObjectives())
                && sameConditions(left, right)
                && sameObjectives(left.getDiscoveryObjectives(), right.getDiscoveryObjectives())
                && sameObjectives(left.getResearchObjectives(), right.getResearchObjectives())
                && sameRewards(left.getRewards(), right.getRewards())
                && sameContent(left.getContent(), right.getContent());
    }

    private static boolean sameRewards(List<CollectionEntryRewardDefinition> left, List<CollectionEntryRewardDefinition> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            var a = left.get(i); var b = right.get(i);
            if (!a.rewardId().equals(b.rewardId()) || a.trigger() != b.trigger() || a.grantMode() != b.grantMode()
                    || !a.outcomeId().equals(b.outcomeId()) || a.previewVisibility() != b.previewVisibility()
                    || a.rewards().size() != b.rewards().size()) return false;
            for (int n = 0; n < a.rewards().size(); n++) {
                IReward ar = a.rewards().get(n), br = b.rewards().get(n);
                if (ar.getClass() != br.getClass() || !ar.describe().equals(br.describe())) return false;
            }
        }
        return true;
    }

    private static boolean sameOutcomes(List<CollectionOutcomeDefinition> left, List<CollectionOutcomeDefinition> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++)
            if (!left.get(i).outcomeId().equals(right.get(i).outcomeId()) || !left.get(i).getDisplayName().equals(right.get(i).getDisplayName())) return false;
        return true;
    }

    private static boolean sameConditions(CollectionEntryDefinition left, CollectionEntryDefinition right) {
        if (left.getRecordConditions().isEmpty() && right.getRecordConditions().isEmpty()) return true;
        if (left.getRecordConditionSignature() != null && right.getRecordConditionSignature() != null) {
            return left.getRecordConditionSignature().equals(right.getRecordConditionSignature());
        }
        return left.getRecordConditions().equals(right.getRecordConditions());
    }

    private static boolean sameObjectives(List<ObjectiveEntry> left, List<ObjectiveEntry> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            ObjectiveEntry a = left.get(i), b = right.get(i);
            if (!a.getObjectiveId().equals(b.getObjectiveId()) || !a.getType().equals(b.getType())
                    || !a.getTargetId().equals(b.getTargetId()) || a.getRequiredCount() != b.getRequiredCount()
                    || !a.getDisplayText().equals(b.getDisplayText()) || a.isHidden() != b.isHidden()
                    || a.isOptional() != b.isOptional() || !a.getExtraData().equals(b.getExtraData())
                    || !a.getIcon().equals(b.getIcon()) || !a.getRelatedMarks().equals(b.getRelatedMarks())) return false;
        }
        return true;
    }

    private static boolean sameContent(List<CollectionContentBlock> left, List<CollectionContentBlock> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            var a = left.get(i); var b = right.get(i);
            if (!a.blockId().equals(b.blockId()) || !a.text().resolve(null, QuestTextContext.empty()).equals(b.text().resolve(null, QuestTextContext.empty()))
                    || !a.caption().resolve(null, QuestTextContext.empty()).equals(b.caption().resolve(null, QuestTextContext.empty()))
                    || a.fit() != b.fit() || a.zoomable() != b.zoomable() || a.reveal() != b.reveal()
                    || !a.revealStepId().equals(b.revealStepId())) return false;
            var am = a.media(); var bm = b.media();
            if (am == null || bm == null) { if (am != bm) return false; }
            else if (am.getType() != bm.getType() || !Objects.equals(am.getTexture(), bm.getTexture())
                    || !Objects.equals(am.getSceneId(), bm.getSceneId()) || am.getWidth() != bm.getWidth()
                    || am.getHeight() != bm.getHeight() || am.isAutoplay() != bm.isAutoplay() || am.isLoop() != bm.isLoop()) return false;
        }
        return true;
    }
}
