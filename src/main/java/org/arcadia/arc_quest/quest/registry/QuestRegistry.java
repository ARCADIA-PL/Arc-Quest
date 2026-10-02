package org.arcadia.arc_quest.quest.registry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.registry.RegistrySourceInfo;
import org.arcadia.arc_quest.data.registry.RegistrySourceType;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTypeIndex;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class QuestRegistry {
    private static Map<ResourceLocation, QuestDefinition> CODE_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, QuestDefinition> DATAPACK_REGISTRY = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, QuestDefinition> MERGED_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, QuestSourceInfo> SOURCE_INFO = new LinkedHashMap<>();
    private static volatile ObjectiveTypeIndex objectiveTypeIndex = ObjectiveTypeIndex.empty();
    @Nullable private static volatile Map<ResourceLocation, QuestDefinition> clientPresentationRegistry;
    private static volatile Map<ResourceLocation, QuestDefinition> clientStartupRegistry = Map.of();
    private static volatile ObjectiveTypeIndex clientStartupIndex = ObjectiveTypeIndex.empty();
    private static volatile ObjectiveTypeIndex clientPresentationIndex = ObjectiveTypeIndex.empty();
    private static final Map<String, ResourceLocation> rlCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean frozen = false;
    private static int datapackLoadOrder = 0;

    private QuestRegistry() {
    }

    public static void register(QuestDefinition definition) {
        registerCode(definition);
    }

    public static void registerCode(QuestDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("QuestRegistry is frozen - cannot register '" + definition.getId() + "' after commonSetup");
        }
        ResourceLocation id = definition.getId();
        if (CODE_REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate quest ID: " + id);
        }
        CODE_REGISTRY.put(id, definition);
        rebuildMergedRegistry();
        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "Registered code quest: {} ({}, {} phases)", id, definition.getCategory().getId(), definition.getPhaseIds().size());
    }

    public static void registerDatapack(QuestDefinition definition, String sourceId) {
        ResourceLocation id = definition.getId();
        DATAPACK_REGISTRY.put(id, definition);
        datapackLoadOrder++;
        rebuildMergedRegistry();
        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "Registered datapack quest: {} from {}", id, sourceId);
    }

    public static void clearDatapack() {
        int previous = DATAPACK_REGISTRY.size();
        DATAPACK_REGISTRY = new LinkedHashMap<>();
        rlCache.clear();
        rebuildMergedRegistry();
        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "Cleared {} datapack quest(s) before reload.", previous);
    }

    public static synchronized void replaceDatapackSnapshot(Map<ResourceLocation, QuestDefinition> definitions) {
        DATAPACK_REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        datapackLoadOrder = definitions.size();
        rlCache.clear();
        rebuildMergedRegistry();
        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "Quest datapack snapshot replaced. datapack={}, merged={}",
                DATAPACK_REGISTRY.size(), MERGED_REGISTRY.size());
    }

    public static synchronized Map<ResourceLocation, QuestDefinition> getDatapackSnapshot() {
        return Map.copyOf(DATAPACK_REGISTRY);
    }

    /** Per-player disclosure never replaces the integrated server's authoritative definitions. */
    public static synchronized void replaceClientPresentationSnapshot(Map<ResourceLocation, QuestDefinition> definitions) {
        Map<ResourceLocation, QuestDefinition> merged = new LinkedHashMap<>();
        CODE_REGISTRY.forEach((id, definition) -> {
            if (!definition.hasCollectionSheets()) merged.put(id, definition);
        });
        merged.putAll(definitions);
        Map<ResourceLocation, QuestDefinition> immutable = Collections.unmodifiableMap(merged);
        CollectionEntryRegistry.replaceClientPresentationSnapshot(immutable.values());
        clientPresentationIndex = ObjectiveTypeIndex.build(immutable);
        clientPresentationRegistry = immutable;
    }

    public static synchronized void clearClientPresentationSnapshot() {
        clientPresentationRegistry = null;
        clientPresentationIndex = ObjectiveTypeIndex.empty();
        CollectionEntryRegistry.clearClientPresentationSnapshot();
    }

    private static Map<ResourceLocation, QuestDefinition> currentRegistry() {
        Map<ResourceLocation, QuestDefinition> client = clientPresentationRegistry;
        if (EffectiveSide.get() == LogicalSide.CLIENT) return client != null ? client : clientStartupRegistry;
        return MERGED_REGISTRY;
    }

    /** Explicit accessor for authoring, synchronization and integrated-server isolation tests. */
    @Nullable public static QuestDefinition getServerDefinition(ResourceLocation id) { return MERGED_REGISTRY.get(id); }

    public static QuestRegistryMergeResult rebuildMergedRegistry() {
        Map<ResourceLocation, QuestDefinition> merged = new LinkedHashMap<>(DATAPACK_REGISTRY);
        Map<ResourceLocation, QuestSourceInfo> mergedSources = new LinkedHashMap<>();
        int order = 0;
        for (Map.Entry<ResourceLocation, QuestDefinition> entry : DATAPACK_REGISTRY.entrySet()) {
            mergedSources.put(entry.getKey(), new QuestSourceInfo(QuestSourceType.DATAPACK, entry.getKey().toString(), order++, null));
        }
        for (Map.Entry<ResourceLocation, QuestDefinition> entry : CODE_REGISTRY.entrySet()) {
            if (merged.containsKey(entry.getKey())) {
                QuestSourceInfo datapackInfo = mergedSources.get(entry.getKey());
                String sourceId = datapackInfo != null ? datapackInfo.sourceId() : "unknown";
                ArcQuestLog.warn(ArcQuestLog.Category.QUEST, "Duplicate quest id '{}' from datapack source '{}' ignored because code-defined quest has priority.", entry.getKey(), sourceId);
                mergedSources.put(entry.getKey(), new QuestSourceInfo(QuestSourceType.CODE, "code", order++, "datapack_ignored_due_to_code_priority"));
            } else {
                mergedSources.put(entry.getKey(), new QuestSourceInfo(QuestSourceType.CODE, "code", order++, null));
            }
            merged.put(entry.getKey(), entry.getValue());
        }
        // Validate all shared definitions before publishing a new merged content generation.
        CollectionEntryRegistry.replaceQuestSnapshot(merged.values());
        MERGED_REGISTRY = Collections.unmodifiableMap(merged);
        Map<ResourceLocation, QuestDefinition> startup = new LinkedHashMap<>();
        merged.forEach((id, definition) -> { if (!definition.hasCollectionSheets()) startup.put(id, definition); });
        clientStartupRegistry = Collections.unmodifiableMap(startup);
        clientStartupIndex = ObjectiveTypeIndex.build(startup);
        SOURCE_INFO = Collections.unmodifiableMap(mergedSources);
        objectiveTypeIndex = ObjectiveTypeIndex.build(merged);
        QuestRegistryMergeResult result = new QuestRegistryMergeResult(CODE_REGISTRY.size(), DATAPACK_REGISTRY.size(), MERGED_REGISTRY.size());
        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "Quest registry rebuilt. code={}, datapack={}, merged={}", result.codeCount(), result.datapackCount(), result.mergedCount());
        return result;
    }

    public static void freeze() {
        frozen = true;
        CODE_REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(CODE_REGISTRY));
        rebuildMergedRegistry();
        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "QuestRegistry frozen. Total quests: {}", MERGED_REGISTRY.size());
        validateCrossReferences();
    }

    @Nullable
    public static QuestDefinition get(ResourceLocation id) {
        return currentRegistry().get(id);
    }

    @Nullable
    public static QuestDefinition get(String questId) {
        ResourceLocation location = rlCache.get(questId);
        if (location == null) {
            if (questId.contains(":")) location = ResourceLocation.tryParse(questId);
            else location = ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, questId);
            if (location != null) rlCache.put(questId, location);
        }
        return location != null ? currentRegistry().get(location) : null;
    }

    public static ObjectiveTypeIndex getObjectiveIndex() {
        if (EffectiveSide.get() == LogicalSide.CLIENT) return clientPresentationRegistry != null ? clientPresentationIndex : clientStartupIndex;
        return objectiveTypeIndex;
    }

    public static QuestDefinition getOrThrow(ResourceLocation id) {
        QuestDefinition def = currentRegistry().get(id);
        if (def == null) throw new NoSuchElementException("Unknown quest: " + id);
        return def;
    }

    public static QuestDefinition getOrThrow(String questId) {
        QuestDefinition def = get(questId);
        if (def == null) throw new NoSuchElementException("Unknown quest: " + questId);
        return def;
    }

    public static Collection<QuestDefinition> getAll() {
        return Collections.unmodifiableCollection(currentRegistry().values());
    }

    public static Set<ResourceLocation> getAllIds() {
        return Collections.unmodifiableSet(currentRegistry().keySet());
    }

    public static List<QuestDefinition> getByCategory(QuestCategory category) {
        return currentRegistry().values().stream()
                .filter(q -> q.getCategory().equals(category))
                .sorted(Comparator.comparingInt(QuestDefinition::getSortOrder))
                .collect(Collectors.toList());
    }

    public static int size() {
        return currentRegistry().size();
    }

    public static int codeSize() {
        return CODE_REGISTRY.size();
    }

    public static int datapackSize() {
        return DATAPACK_REGISTRY.size();
    }

    @Nullable
    public static QuestSourceInfo getSourceInfo(ResourceLocation id) {
        return SOURCE_INFO.get(id);
    }

    @Nullable
    public static RegistrySourceInfo getUnifiedSourceInfo(ResourceLocation id) {
        QuestSourceInfo source = SOURCE_INFO.get(id);
        if (source == null) return null;
        return new RegistrySourceInfo(RegistrySourceType.valueOf(source.sourceType().name()),
                source.sourceId(), source.loadOrder(), source.ignoredReason());
    }

    @Nullable
    public static QuestTextBundle getQuestTextById(@Nullable ServerPlayer player, String questId, @Nullable String phaseId) {
        QuestDefinition quest = get(questId);
        if (quest == null) return null;
        String resolvedPhaseId = (phaseId == null || phaseId.isEmpty()) ? quest.getInitialPhaseId() : phaseId;
        PhaseDefinition phase = quest.getPhase(resolvedPhaseId);
        if (phase == null) {
            phase = quest.getInitialPhase();
            resolvedPhaseId = phase != null ? phase.getPhaseId() : resolvedPhaseId;
        }
        QuestTextContext ctx = new QuestTextContext(quest, phase, resolvedPhaseId, Map.of());
        Component questName = player != null ? quest.getDisplayName(player, ctx) : quest.getDisplayName();
        Component questDesc = player != null ? quest.getDescription(player, ctx) : quest.getDescription();
        Component phaseName = phase != null ? (player != null ? phase.getDisplayName(player, ctx) : phase.getDisplayName()) : Component.empty();
        Component phaseDesc = phase != null ? (player != null ? phase.getDescription(player, ctx) : phase.getDescription()) : Component.empty();
        return new QuestTextBundle(questName, questDesc, phaseName, phaseDesc);
    }

    public static boolean isFrozen() {
        return frozen;
    }

    private static void validateCrossReferences() {
        int warnings = 0;
        for (QuestDefinition quest : MERGED_REGISTRY.values()) {
            for (var cond : quest.getUnlockConditions()) {
                if (cond instanceof ICondition.WithRequiredQuest wrq) {
                    ResourceLocation ref = wrq.getRequiredQuestId();
                    if (!MERGED_REGISTRY.containsKey(ref)) {
                        ArcQuestLog.warn(ArcQuestLog.Category.QUEST, "Quest '{}' requires unknown quest '{}' as prerequisite", quest.getId(), ref);
                        warnings++;
                    }
                }
            }
        }
        if (warnings > 0) ArcQuestLog.warn(ArcQuestLog.Category.QUEST, "Cross-reference validation: {} warning(s)", warnings);
    }

    public record QuestTextBundle(Component questDisplayName, Component questDescription, Component phaseDisplayName,
                                  Component phaseDescription) {
    }
}
