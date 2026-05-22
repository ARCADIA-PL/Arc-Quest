package org.arcadia.arc_quest.guide.registry;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GuideCategoryRegistry {

    private static Map<ResourceLocation, GuideCategory> CODE_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, GuideCategory> DATAPACK_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, GuideCategory> MERGED_REGISTRY = new LinkedHashMap<>();
    private static Map<ResourceLocation, GuideSourceInfo> SOURCE_INFO = new LinkedHashMap<>();
    private static final Object2ObjectOpenHashMap<String, ResourceLocation> RL_CACHE = new Object2ObjectOpenHashMap<>();
    private static boolean frozen = false;

    private GuideCategoryRegistry() {
    }

    public static synchronized void register(GuideCategory category) {
        if (frozen) {
            throw new IllegalStateException("GuideCategoryRegistry is frozen - cannot register '" + category.getId() + "' after commonSetup");
        }
        ResourceLocation id = category.getId();
        if (CODE_REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate guide category id: " + id);
        }
        CODE_REGISTRY.put(id, category);
        rebuildMergedRegistry();
    }

    public static synchronized void registerDatapack(GuideCategory category, String sourceId) {
        DATAPACK_REGISTRY.put(category.getId(), category);
        rebuildMergedRegistry();
    }

    public static synchronized void clearDatapack() {
        DATAPACK_REGISTRY = new LinkedHashMap<>();
        RL_CACHE.clear();
        rebuildMergedRegistry();
    }

    public static synchronized GuideRegistryMergeResult rebuildMergedRegistry() {
        Map<ResourceLocation, GuideCategory> merged = new LinkedHashMap<>(DATAPACK_REGISTRY);
        Map<ResourceLocation, GuideSourceInfo> mergedSources = new LinkedHashMap<>();
        int order = 0;
        for (Map.Entry<ResourceLocation, GuideCategory> entry : DATAPACK_REGISTRY.entrySet()) {
            mergedSources.put(entry.getKey(), new GuideSourceInfo(GuideSourceType.DATAPACK, entry.getKey().toString(), order++, null));
        }
        for (Map.Entry<ResourceLocation, GuideCategory> entry : CODE_REGISTRY.entrySet()) {
            if (merged.containsKey(entry.getKey())) {
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
    }

    @Nullable
    public static synchronized GuideCategory get(ResourceLocation id) {
        return MERGED_REGISTRY.get(id);
    }

    @Nullable
    public static synchronized GuideCategory get(String categoryId) {
        ResourceLocation location = RL_CACHE.get(categoryId);
        if (location == null) {
            if (categoryId.contains(":")) location = ResourceLocation.tryParse(categoryId);
            else location = ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, categoryId);
            if (location != null) RL_CACHE.put(categoryId, location);
        }
        return location != null ? MERGED_REGISTRY.get(location) : null;
    }

    public static synchronized Collection<GuideCategory> all() {
        return Collections.unmodifiableList(new ArrayList<>(MERGED_REGISTRY.values()));
    }

    public static synchronized List<GuideCategory> allSorted() {
        return MERGED_REGISTRY.values().stream()
                .sorted(Comparator.comparingInt(GuideCategory::getSortOrder).thenComparing(category -> category.getId().toString()))
                .toList();
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
}
