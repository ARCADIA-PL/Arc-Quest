package org.arcadia.arc_quest.trade.gacha.registry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.registry.LayeredRegistrySnapshot;
import org.arcadia.arc_quest.data.registry.RegistrySourceInfo;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class GachaRegistry {
    private static Map<String, GachaShopDefinition> codeShops = new LinkedHashMap<>();
    private static volatile LayeredRegistrySnapshot<String, GachaShopDefinition> snapshot =
            LayeredRegistrySnapshot.empty(codeShops);
    private static boolean frozen;

    private GachaRegistry() {
    }

    public static void register(GachaShopDefinition definition) {
        registerCode(definition);
    }

    public static synchronized void registerCode(GachaShopDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("GachaRegistry is frozen - cannot register '" + definition.getShopId() + "'");
        }
        String id = definition.getShopId();
        if (codeShops.containsKey(id)) {
            throw new IllegalStateException("Duplicate gacha shop ID: " + id);
        }
        codeShops.put(id, definition);
        rebuildSnapshot(snapshot.datapack());
        ArcQuestLog.info(ArcQuestLog.Category.GACHA, "Registered code gacha shop: {} ({} pool items)",
                id, definition.getGachaPool().getAllItems().size());
    }

    public static synchronized void registerDatapack(GachaShopDefinition definition) {
        Map<String, GachaShopDefinition> datapack = new LinkedHashMap<>(snapshot.datapack());
        datapack.put(definition.getShopId(), definition);
        rebuildSnapshot(datapack);
    }

    public static synchronized void freeze() {
        if (frozen) return;
        frozen = true;
        codeShops = Collections.unmodifiableMap(new LinkedHashMap<>(codeShops));
        rebuildSnapshot(snapshot.datapack());
        ArcQuestLog.info(ArcQuestLog.Category.GACHA, "GachaRegistry frozen. code={}, datapack={}, merged={}",
                codeSize(), datapackSize(), size());
    }

    public static void replaceDatapack(Map<String, GachaShopDefinition> shops) {
        replaceDatapackSnapshot(shops);
    }

    public static synchronized void replaceDatapackSnapshot(Map<String, GachaShopDefinition> shops) {
        rebuildSnapshot(shops);
        ArcQuestLog.info(ArcQuestLog.Category.GACHA, "Gacha datapack snapshot replaced. datapack={}, merged={}", datapackSize(), size());
    }

    public static Map<String, GachaShopDefinition> getDatapackSnapshot() {
        return snapshot.datapack();
    }

    public static synchronized void clearDatapack() {
        rebuildSnapshot(Map.of());
        ArcQuestLog.info(ArcQuestLog.Category.GACHA, "GachaRegistry datapack cleared.");
    }

    @Nullable
    public static GachaShopDefinition get(String shopId) {
        if (shopId == null || shopId.isBlank()) return null;
        GachaShopDefinition definition = snapshot.merged().get(shopId);
        if (definition == null && !shopId.contains(":")) {
            definition = snapshot.merged().get(Arc_Quest.MOD_ID + ":" + shopId);
        }
        return definition;
    }

    @Nullable
    public static GachaShopDefinition getCodeDefinition(String shopId) {
        if (shopId == null || shopId.isBlank()) return null;
        GachaShopDefinition definition = codeShops.get(shopId);
        if (definition == null && !shopId.contains(":")) definition = codeShops.get(Arc_Quest.MOD_ID + ":" + shopId);
        return definition;
    }

    @Nullable
    public static RegistrySourceInfo getSourceInfo(String shopId) {
        if (shopId == null || shopId.isBlank()) return null;
        RegistrySourceInfo source = snapshot.sources().get(shopId);
        if (source == null && !shopId.contains(":")) source = snapshot.sources().get(Arc_Quest.MOD_ID + ":" + shopId);
        return source;
    }

    public static Collection<GachaShopDefinition> getAllShops() {
        return snapshot.merged().values();
    }

    public static Set<String> getAllIds() {
        return snapshot.merged().keySet();
    }

    public static int size() {
        return snapshot.merged().size();
    }

    public static int codeSize() {
        return codeShops.size();
    }

    public static int datapackSize() {
        return snapshot.datapack().size();
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static synchronized void clearAll() {
        codeShops = new LinkedHashMap<>();
        frozen = false;
        snapshot = LayeredRegistrySnapshot.empty(codeShops);
        ArcQuestLog.info(ArcQuestLog.Category.GACHA, "GachaRegistry cleared.");
    }

    private static void rebuildSnapshot(Map<String, GachaShopDefinition> datapack) {
        LayeredRegistrySnapshot<String, GachaShopDefinition> next = LayeredRegistrySnapshot.create(codeShops, datapack);
        next.sources().forEach((id, source) -> {
            if (source.ignoredReason() != null) {
                ArcQuestLog.warn(ArcQuestLog.Category.GACHA, "Datapack gacha shop '{}' ignored because code-defined shop has priority.", id);
            }
        });
        snapshot = next;
    }
}
