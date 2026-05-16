package org.arcadia.arc_quest.trade.gacha.registry;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public final class GachaRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<String, GachaShopDefinition> codeShops = new LinkedHashMap<>();
    private static volatile Map<String, GachaShopDefinition> datapackShops = Map.of();

    private GachaRegistry() {
    }

    public static void register(GachaShopDefinition definition) {
        String id = definition.getShopId();
        if (codeShops.containsKey(id)) {
            throw new IllegalStateException("Duplicate gacha shop ID: " + id);
        }
        codeShops.put(id, definition);
        LOGGER.info("[ArcQuest] Registered gacha shop: {} ({} pool items)",
                id, definition.getGachaPool().getAllItems().size());
    }

    public static void replaceDatapack(Map<String, GachaShopDefinition> shops) {
        datapackShops = Collections.unmodifiableMap(new LinkedHashMap<>(shops));
        LOGGER.info("[ArcQuest] GachaRegistry datapack replaced. Total datapack shops: {}", shops.size());
    }

    public static void clearDatapack() {
        datapackShops = Map.of();
        LOGGER.info("[ArcQuest] GachaRegistry datapack cleared.");
    }

    @Nullable
    public static GachaShopDefinition get(String shopId) {
        GachaShopDefinition def = codeShops.get(shopId);
        if (def != null) return def;

        def = datapackShops.get(shopId);
        if (def != null) return def;

        if (!shopId.contains(":")) {
            def = codeShops.get(Arc_Quest.MOD_ID + ":" + shopId);
            if (def != null) return def;
            return datapackShops.get(Arc_Quest.MOD_ID + ":" + shopId);
        }

        return null;
    }

    public static Collection<GachaShopDefinition> getAllShops() {
        Map<String, GachaShopDefinition> merged = new LinkedHashMap<>();
        merged.putAll(datapackShops);
        merged.putAll(codeShops);
        return Collections.unmodifiableCollection(merged.values());
    }

    public static int codeSize() {
        return codeShops.size();
    }

    public static int datapackSize() {
        return datapackShops.size();
    }
}
