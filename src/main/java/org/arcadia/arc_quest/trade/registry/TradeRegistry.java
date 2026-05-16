package org.arcadia.arc_quest.trade.registry;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public final class TradeRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<String, TradeShopDefinition> codeShops = new LinkedHashMap<>();
    private static volatile Map<String, TradeShopDefinition> datapackShops = Map.of();
    private static boolean frozen = false;

    private TradeRegistry() {
    }

    public static void register(TradeShopDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("TradeRegistry is frozen — cannot register '" + definition.getShopId() + "'");
        }

        String id = definition.getShopId();
        if (codeShops.containsKey(id)) {
            throw new IllegalStateException("Duplicate trade shop ID: " + id);
        }
        codeShops.put(id, definition);
        LOGGER.info("[ArcQuest] Registered trade shop: {} ({} entries)",
                id, definition.getAllEntries().size());
    }

    public static void freeze() {
        frozen = true;
        codeShops = Collections.unmodifiableMap(new LinkedHashMap<>(codeShops));
        LOGGER.info("[ArcQuest] TradeRegistry frozen. Total code shops: {}", codeShops.size());
    }

    public static void replaceDatapack(Map<String, TradeShopDefinition> shops) {
        datapackShops = Collections.unmodifiableMap(new LinkedHashMap<>(shops));
        LOGGER.info("[ArcQuest] TradeRegistry datapack replaced. Total datapack shops: {}", shops.size());
    }

    public static void clearDatapack() {
        datapackShops = Map.of();
        LOGGER.info("[ArcQuest] TradeRegistry datapack cleared.");
    }

    @Nullable
    public static TradeShopDefinition get(String shopId) {
        TradeShopDefinition shop = datapackShops.get(shopId);
        if (shop != null) return shop;

        shop = codeShops.get(shopId);
        if (shop != null) return shop;

        if (!shopId.contains(":")) {
            String fullId = Arc_Quest.MOD_ID + ":" + shopId;
            shop = datapackShops.get(fullId);
            if (shop != null) return shop;
            return codeShops.get(fullId);
        }

        return null;
    }

    public static Collection<TradeShopDefinition> getAll() {
        Map<String, TradeShopDefinition> merged = new LinkedHashMap<>();
        merged.putAll(codeShops);
        merged.putAll(datapackShops);
        return Collections.unmodifiableCollection(merged.values());
    }

    public static Set<String> getAllIds() {
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(codeShops.keySet());
        ids.addAll(datapackShops.keySet());
        return Collections.unmodifiableSet(ids);
    }

    public static int size() {
        return codeShops.size() + datapackShops.size();
    }

    public static void clearAll() {
        codeShops.clear();
        datapackShops = Map.of();
        LOGGER.info("[ArcQuest] TradeRegistry cleared.");
    }
}
