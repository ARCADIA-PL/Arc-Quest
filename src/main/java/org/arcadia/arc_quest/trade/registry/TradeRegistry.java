package org.arcadia.arc_quest.trade.registry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.registry.LayeredRegistrySnapshot;
import org.arcadia.arc_quest.data.registry.RegistrySourceInfo;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class TradeRegistry {
    private static Map<String, TradeShopDefinition> codeShops = new LinkedHashMap<>();
    private static volatile LayeredRegistrySnapshot<String, TradeShopDefinition> snapshot =
            LayeredRegistrySnapshot.empty(codeShops);
    private static boolean frozen;

    private TradeRegistry() {
    }

    public static void register(TradeShopDefinition definition) {
        registerCode(definition);
    }

    public static synchronized void registerCode(TradeShopDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("TradeRegistry is frozen - cannot register '" + definition.getShopId() + "'");
        }
        String id = definition.getShopId();
        if (codeShops.containsKey(id)) {
            throw new IllegalStateException("Duplicate trade shop ID: " + id);
        }
        codeShops.put(id, definition);
        rebuildSnapshot(snapshot.datapack());
        ArcQuestLog.info(ArcQuestLog.Category.TRADE, "Registered code trade shop: {} ({} entries)", id, definition.getAllEntries().size());
    }

    public static synchronized void registerDatapack(TradeShopDefinition definition) {
        Map<String, TradeShopDefinition> datapack = new LinkedHashMap<>(snapshot.datapack());
        datapack.put(definition.getShopId(), definition);
        rebuildSnapshot(datapack);
    }

    public static synchronized void freeze() {
        if (frozen) return;
        frozen = true;
        codeShops = Collections.unmodifiableMap(new LinkedHashMap<>(codeShops));
        rebuildSnapshot(snapshot.datapack());
        ArcQuestLog.info(ArcQuestLog.Category.TRADE, "TradeRegistry frozen. code={}, datapack={}, merged={}",
                codeSize(), datapackSize(), size());
    }

    public static void replaceDatapack(Map<String, TradeShopDefinition> shops) {
        replaceDatapackSnapshot(shops);
    }

    public static synchronized void replaceDatapackSnapshot(Map<String, TradeShopDefinition> shops) {
        rebuildSnapshot(shops);
        ArcQuestLog.info(ArcQuestLog.Category.TRADE, "Trade datapack snapshot replaced. datapack={}, merged={}", datapackSize(), size());
    }

    public static Map<String, TradeShopDefinition> getDatapackSnapshot() {
        return snapshot.datapack();
    }

    public static synchronized void clearDatapack() {
        rebuildSnapshot(Map.of());
        ArcQuestLog.info(ArcQuestLog.Category.TRADE, "TradeRegistry datapack cleared.");
    }

    @Nullable
    public static TradeShopDefinition get(String shopId) {
        if (shopId == null || shopId.isBlank()) return null;
        TradeShopDefinition definition = snapshot.merged().get(shopId);
        if (definition == null && !shopId.contains(":")) {
            definition = snapshot.merged().get(Arc_Quest.MOD_ID + ":" + shopId);
        }
        return definition;
    }

    @Nullable
    public static TradeShopDefinition getCodeDefinition(String shopId) {
        if (shopId == null || shopId.isBlank()) return null;
        TradeShopDefinition definition = codeShops.get(shopId);
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

    public static Collection<TradeShopDefinition> getAll() {
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
        ArcQuestLog.info(ArcQuestLog.Category.TRADE, "TradeRegistry cleared.");
    }

    private static void rebuildSnapshot(Map<String, TradeShopDefinition> datapack) {
        LayeredRegistrySnapshot<String, TradeShopDefinition> next = LayeredRegistrySnapshot.create(codeShops, datapack);
        logOverrides(next);
        snapshot = next;
    }

    private static void logOverrides(LayeredRegistrySnapshot<String, TradeShopDefinition> next) {
        next.sources().forEach((id, source) -> {
            if (source.ignoredReason() != null) {
                ArcQuestLog.warn(ArcQuestLog.Category.TRADE, "Datapack trade shop '{}' ignored because code-defined shop has priority.", id);
            }
        });
    }
}
