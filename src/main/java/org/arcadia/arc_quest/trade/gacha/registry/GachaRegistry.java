package org.arcadia.arc_quest.trade.gacha.registry;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局抽奖商店注册表（线程安全）。
 * <p>
 * 所有 {@link GachaShopDefinition} 在 commonSetup 阶段注册。
 * 使用 ConcurrentHashMap 确保多线程读取安全。
 */
public final class GachaRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, GachaShopDefinition> REGISTRY = new ConcurrentHashMap<>();

    private GachaRegistry() {
    }

    /**
     * 注册抽奖商店定义。
     *
     * @throws IllegalStateException ID 重复
     */
    public static void register(GachaShopDefinition definition) {
        String id = definition.getShopId();
        if (REGISTRY.containsKey(id)) {
            throw new IllegalStateException("Duplicate gacha shop ID: " + id);
        }
        REGISTRY.put(id, definition);
        LOGGER.info("[ArcQuest] Registered gacha shop: {} ({} pool items)",
                id, definition.getGachaPool().getAllItems().size());
    }

    /**
     * 获取抽奖商店定义（支持智能命名空间解析）。
     *
     * @param shopId 商店 ID
     * @return 抽奖商店定义，未找到返回 null
     */
    @Nullable
    public static GachaShopDefinition get(String shopId) {
        // 1. 直接查找
        GachaShopDefinition def = REGISTRY.get(shopId);
        if (def != null) {
            return def;
        }

        // 2. 如果没有命名空间，尝试添加 arc_quest: 前缀
        if (!shopId.contains(":")) {
            def = REGISTRY.get(Arc_Quest.MOD_ID + ":" + shopId);
        }

        return def;
    }

    /**
     * 获取所有已注册的抽奖商店。
     */
    public static Collection<GachaShopDefinition> getAllShops() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * 清空注册表（仅用于测试）。
     */
    public static void clear() {
        REGISTRY.clear();
    }
}
