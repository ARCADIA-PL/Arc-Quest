package org.com.arc_quest.trade.registry;

import com.mojang.logging.LogUtils;
import org.com.arc_quest.Arc_quest;
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
    private static Map<String, TradeShopDefinition> REGISTRY = new LinkedHashMap<>();
    private static boolean frozen = false;

    private TradeRegistry() {}

    /**
     * 注册商店定义。
     *
     * @throws IllegalStateException ID 重复或注册表已冻结
     */
    public static void register(TradeShopDefinition definition) {
        if (frozen) {
            throw new IllegalStateException("TradeRegistry is frozen — cannot register '" + definition.getShopId() + "'");
        }
        
        String id = definition.getShopId();
        if (REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate trade shop ID: " + id);
        }
        REGISTRY.put(id, definition);
        LOGGER.info("[ArcQuest] Registered trade shop: {} ({} entries)",
                id, definition.getAllEntries().size());
    }
    
    /**
     * 冻结注册表。在 commonSetup 完成后调用。
     * <p>
     * 【并发安全】冻结后转换为不可变Map，确保多线程读取安全。
     */
    public static void freeze() {
        frozen = true;
        
        // 【并发防护】转换为线程安全的不可变Map
        REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(REGISTRY));
        
        LOGGER.info("[ArcQuest] TradeRegistry frozen. Total shops: {}", REGISTRY.size());
    }

    /**
     * 获取商店定义（支持智能命名空间解析）。
     * <p>
     * - 如果 shopId 包含 ":"，直接查找
     * - 如果不包含 ":"，尝试添加 "arc_quest:" 前缀后查找
     *
     * @param shopId 商店 ID（可以是 "blacksmith_shop" 或 "arc_quest:blacksmith_shop"）
     * @return 商店定义，未找到返回 null
     */
    @Nullable
    public static TradeShopDefinition get(String shopId) {
        // 1. 直接查找
        TradeShopDefinition shop = REGISTRY.get(shopId);
        if (shop != null) {
            return shop;
        }

        // 2. 如果不包含命名空间，尝试添加默认前缀
        if (!shopId.contains(":")) {
            String fullId = Arc_quest.MOD_ID + ":" + shopId;
            shop = REGISTRY.get(fullId);
            return shop;
        }

        return null;
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
