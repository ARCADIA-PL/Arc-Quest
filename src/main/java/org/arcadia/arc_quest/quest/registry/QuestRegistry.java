package org.arcadia.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTypeIndex;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public final class QuestRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<ResourceLocation, QuestDefinition> CODE_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, QuestDefinition> DATAPACK_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, QuestDefinition> MERGED_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, QuestSourceInfo> SOURCE_INFO = new LinkedHashMap<>();
    private static volatile ObjectiveTypeIndex objectiveTypeIndex = ObjectiveTypeIndex.empty();
    private static final Object2ObjectOpenHashMap<String, ResourceLocation> rlCache = new Object2ObjectOpenHashMap<>();
    private static boolean frozen = false;
    private static int datapackLoadOrder = 0;

    private QuestRegistry() {
    }

    public static void register(QuestDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("QuestRegistry is frozen - cannot register '" + definition.getId() + "' after commonSetup");
        }
        ResourceLocation id = definition.getId();
        if (CODE_REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate quest ID: " + id);
        }
        CODE_REGISTRY.put(id, definition);
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] Registered code quest: {} ({}, {} phases)", id, definition.getCategory().getId(), definition.getPhaseIds().size());
    }

    public static void registerDatapack(QuestDefinition definition, String sourceId) {
        ResourceLocation id = definition.getId();
        DATAPACK_REGISTRY.put(id, definition);
        datapackLoadOrder++;
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] Registered datapack quest: {} from {}", id, sourceId);
    }

    public static void clearDatapack() {
        int previous = DATAPACK_REGISTRY.size();
        DATAPACK_REGISTRY = new LinkedHashMap<>();
        rlCache.clear();
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] Cleared {} datapack quest(s) before reload.", previous);
    }

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
                LOGGER.warn("[ArcQuest] Duplicate quest id '{}' from datapack source '{}' ignored because code-defined quest has priority.", entry.getKey(), sourceId);
                mergedSources.put(entry.getKey(), new QuestSourceInfo(QuestSourceType.CODE, "code", order++, "datapack_ignored_due_to_code_priority"));
            } else {
                mergedSources.put(entry.getKey(), new QuestSourceInfo(QuestSourceType.CODE, "code", order++, null));
            }
            merged.put(entry.getKey(), entry.getValue());
        }
        MERGED_REGISTRY = Collections.unmodifiableMap(merged);
        SOURCE_INFO = Collections.unmodifiableMap(mergedSources);
        objectiveTypeIndex = ObjectiveTypeIndex.build(merged);
        QuestRegistryMergeResult result = new QuestRegistryMergeResult(CODE_REGISTRY.size(), DATAPACK_REGISTRY.size(), MERGED_REGISTRY.size());
        LOGGER.info("[ArcQuest] Quest registry rebuilt. code={}, datapack={}, merged={}", result.codeCount(), result.datapackCount(), result.mergedCount());
        return result;
    }

    public static void freeze() {
        frozen = true;
        CODE_REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(CODE_REGISTRY));
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] QuestRegistry frozen. Total quests: {}", MERGED_REGISTRY.size());
        validateCrossReferences();
    }

    @Nullable
    public static QuestDefinition get(ResourceLocation id) {
        return MERGED_REGISTRY.get(id);
    }

    @Nullable
    public static QuestDefinition get(String questId) {
        ResourceLocation location = rlCache.get(questId);
        if (location == null) {
            if (questId.contains(":")) location = ResourceLocation.tryParse(questId);
            else location = ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, questId);
            if (location != null) rlCache.put(questId, location);
        }
        return location != null ? MERGED_REGISTRY.get(location) : null;
    }

    public static ObjectiveTypeIndex getObjectiveIndex() {
        return objectiveTypeIndex;
    }

    public static QuestDefinition getOrThrow(ResourceLocation id) {
        QuestDefinition def = MERGED_REGISTRY.get(id);
        if (def == null) throw new NoSuchElementException("Unknown quest: " + id);
        return def;
    }

    public static QuestDefinition getOrThrow(String questId) {
        QuestDefinition def = get(questId);
        if (def == null) throw new NoSuchElementException("Unknown quest: " + questId);
        return def;
    }

    public static Map<ResourceLocation, QuestDefinition> getDatapackSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(DATAPACK_REGISTRY));
    }

    public static Collection<QuestDefinition> getAll() {
        return Collections.unmodifiableCollection(MERGED_REGISTRY.values());
    }

    public static Set<ResourceLocation> getAllIds() {
        return Collections.unmodifiableSet(MERGED_REGISTRY.keySet());
    }

    public static List<QuestDefinition> getByCategory(QuestCategory category) {
        return MERGED_REGISTRY.values().stream()
                .filter(q -> q.getCategory().equals(category))
                .sorted(Comparator.comparingInt(QuestDefinition::getSortOrder))
                .collect(Collectors.toList());
    }

    public static int size() {
        return MERGED_REGISTRY.size();
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
                        LOGGER.warn("[ArcQuest] Quest '{}' requires unknown quest '{}' as prerequisite", quest.getId(), ref);
                        warnings++;
                    }
                }
            }
        }
        if (warnings > 0) LOGGER.warn("[ArcQuest] Cross-reference validation: {} warning(s)", warnings);
    }

    public record QuestTextBundle(Component questDisplayName, Component questDescription, Component phaseDisplayName,
                                  Component phaseDescription) {
    }
}
