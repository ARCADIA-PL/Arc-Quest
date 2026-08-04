package org.arcadia.arc_quest.guide.registry;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.registry.RegistrySourceInfo;
import org.arcadia.arc_quest.data.registry.RegistrySourceType;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public final class GuideRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object2ObjectOpenHashMap<String, ResourceLocation> RL_CACHE = new Object2ObjectOpenHashMap<>();
    private static Map<ResourceLocation, GuideDefinition> CODE_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, GuideDefinition> DATAPACK_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, GuideDefinition> MERGED_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, GuideSourceInfo> SOURCE_INFO = new LinkedHashMap<>();
    private static boolean frozen = false;

    private GuideRegistry() {
    }

    public static synchronized void register(GuideDefinition definition) {
        registerCode(definition);
    }

    public static synchronized void registerCode(GuideDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("GuideRegistry is frozen - cannot register '" + definition.getId() + "' after commonSetup");
        }
        ResourceLocation id = definition.getId();
        if (CODE_REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate guide ID: " + id);
        }
        CODE_REGISTRY.put(id, definition);
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] Registered code guide: {} ({}, {} pages)", id, definition.getCategory().getId(), definition.getPageCount());
    }

    public static synchronized void registerDatapack(GuideDefinition definition, String sourceId) {
        DATAPACK_REGISTRY.put(definition.getId(), definition);
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] Registered datapack guide: {} from {}", definition.getId(), sourceId);
    }

    public static synchronized void clearDatapack() {
        int previous = DATAPACK_REGISTRY.size();
        DATAPACK_REGISTRY = new LinkedHashMap<>();
        RL_CACHE.clear();
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] Cleared {} datapack guide(s) before reload.", previous);
    }

    public static synchronized void replaceDatapackSnapshot(Map<ResourceLocation, GuideDefinition> definitions) {
        DATAPACK_REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        RL_CACHE.clear();
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] Guide datapack snapshot replaced. datapack={}, merged={}",
                DATAPACK_REGISTRY.size(), MERGED_REGISTRY.size());
    }

    public static synchronized Map<ResourceLocation, GuideDefinition> getDatapackSnapshot() {
        return Map.copyOf(DATAPACK_REGISTRY);
    }

    public static synchronized GuideRegistryMergeResult rebuildMergedRegistry() {
        Map<ResourceLocation, GuideDefinition> merged = new LinkedHashMap<>(DATAPACK_REGISTRY);
        Map<ResourceLocation, GuideSourceInfo> mergedSources = new LinkedHashMap<>();
        int order = 0;
        for (Map.Entry<ResourceLocation, GuideDefinition> entry : DATAPACK_REGISTRY.entrySet()) {
            mergedSources.put(entry.getKey(), new GuideSourceInfo(GuideSourceType.DATAPACK, entry.getKey().toString(), order++, null));
        }
        for (Map.Entry<ResourceLocation, GuideDefinition> entry : CODE_REGISTRY.entrySet()) {
            if (merged.containsKey(entry.getKey())) {
                GuideSourceInfo datapackInfo = mergedSources.get(entry.getKey());
                String sourceId = datapackInfo != null ? datapackInfo.sourceId() : "unknown";
                LOGGER.warn("[ArcQuest] Duplicate guide id '{}' from datapack source '{}' ignored because code-defined guide has priority.", entry.getKey(), sourceId);
                mergedSources.put(entry.getKey(), new GuideSourceInfo(GuideSourceType.CODE, "code", order++, "datapack_ignored_due_to_code_priority"));
            } else {
                mergedSources.put(entry.getKey(), new GuideSourceInfo(GuideSourceType.CODE, "code", order++, null));
            }
            merged.put(entry.getKey(), entry.getValue());
        }
        MERGED_REGISTRY = Collections.unmodifiableMap(merged);
        SOURCE_INFO = Collections.unmodifiableMap(mergedSources);
        return new GuideRegistryMergeResult(CODE_REGISTRY.size(), DATAPACK_REGISTRY.size(), MERGED_REGISTRY.size());
    }

    public static synchronized void freeze() {
        frozen = true;
        CODE_REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(CODE_REGISTRY));
        rebuildMergedRegistry();
        LOGGER.info("[ArcQuest] GuideRegistry frozen. Total guides: {}", MERGED_REGISTRY.size());
    }

    @Nullable
    public static synchronized GuideDefinition get(ResourceLocation id) {
        return MERGED_REGISTRY.get(id);
    }

    @Nullable
    public static synchronized GuideDefinition getCodeDefinition(ResourceLocation id) {
        return CODE_REGISTRY.get(id);
    }

    @Nullable
    public static synchronized GuideDefinition get(String guideId) {
        ResourceLocation location = RL_CACHE.get(guideId);
        if (location == null) {
            if (guideId.contains(":")) location = ResourceLocation.tryParse(guideId);
            else location = ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, guideId);
            if (location != null) RL_CACHE.put(guideId, location);
        }
        return location != null ? MERGED_REGISTRY.get(location) : null;
    }

    public static synchronized GuideDefinition getOrThrow(ResourceLocation id) {
        GuideDefinition def = MERGED_REGISTRY.get(id);
        if (def == null) throw new NoSuchElementException("Unknown guide: " + id);
        return def;
    }

    public static synchronized Collection<GuideDefinition> getAll() {
        return Collections.unmodifiableCollection(MERGED_REGISTRY.values());
    }

    public static synchronized Set<ResourceLocation> getAllIds() {
        return Collections.unmodifiableSet(MERGED_REGISTRY.keySet());
    }

    public static synchronized List<GuideDefinition> getByCategory(GuideCategory category) {
        return getByCategory(category.getId());
    }

    public static synchronized List<GuideDefinition> getByCategory(ResourceLocation categoryId) {
        return MERGED_REGISTRY.values().stream()
                .filter(guide -> guide.getCategory().getId().equals(categoryId))
                .sorted(Comparator.comparingInt(GuideDefinition::getSortOrder).thenComparing(guide -> guide.getId().toString()))
                .collect(Collectors.toList());
    }

    public static synchronized List<GuideCategory> getAllCategories() {
        Map<ResourceLocation, GuideCategory> categories = new LinkedHashMap<>();
        for (GuideDefinition guide : MERGED_REGISTRY.values()) {
            categories.putIfAbsent(guide.getCategory().getId(), guide.getCategory());
        }
        return categories.values().stream()
                .sorted(Comparator.comparingInt(GuideCategory::getSortOrder).thenComparing(category -> category.getId().toString()))
                .collect(Collectors.toList());
    }

    public static synchronized int size() {
        return MERGED_REGISTRY.size();
    }

    public static synchronized int codeSize() {
        return CODE_REGISTRY.size();
    }

    public static synchronized int datapackSize() {
        return DATAPACK_REGISTRY.size();
    }

    public static synchronized boolean isFrozen() {
        return frozen;
    }

    @Nullable
    public static synchronized GuideSourceInfo getSourceInfo(ResourceLocation id) {
        return SOURCE_INFO.get(id);
    }

    @Nullable
    public static synchronized RegistrySourceInfo getUnifiedSourceInfo(ResourceLocation id) {
        GuideSourceInfo source = SOURCE_INFO.get(id);
        if (source == null) return null;
        return new RegistrySourceInfo(RegistrySourceType.valueOf(source.sourceType().name()),
                source.sourceId(), source.loadOrder(), source.ignoredReason());
    }
}
