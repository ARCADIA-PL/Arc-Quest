package org.com.arc_quest.trade.registry;

import com.mojang.logging.LogUtils;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * 全局交易商店注册表（线程安全）。
 * <p>
 * 所有 {@link TradeShopDefinition} 在 commonSetup 阶段注册。
 * 模式与 {@link org.com.arc_quest.dialogue.registry.DialogueRegistry} 一致。
 */
public final class TradeRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, TradeShopDefinition> REGISTRY = new LinkedHashMap<>();

    private TradeRegistry() {}

    /**
     * 注册商店定义。
     *
     * @throws IllegalStateException ID 重复
     */
    public static void register(TradeShopDefinition definition) {
        String id = definition.getShopId();
        if (REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate trade shop ID: " + id);
        }
        REGISTRY.put(id, definition);
        LOGGER.info("[ArcQuest] Registered trade shop: {} ({} entries)",
                id, definition.getAllEntries().size());
    }

    @Nullable
    public static TradeShopDefinition get(String shopId) {
        return REGISTRY.get(shopId);
    }

    public static Collection<TradeShopDefinition> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static Set<String> getAllIds() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    public static int size() {
        return REGISTRY.size();
    }

    public static void clearAll() {
        REGISTRY.clear();
        LOGGER.info("[ArcQuest] TradeRegistry cleared.");
    }
}
