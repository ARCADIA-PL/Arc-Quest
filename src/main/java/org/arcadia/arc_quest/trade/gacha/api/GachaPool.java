package org.arcadia.arc_quest.trade.gacha.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 抽奖奖池。
 * <p>
 * 管理所有可抽取的物品及其权重，支持动态权重计算和随机抽取。
 */
public class GachaPool {

    private final List<GachaItem> items;
    private final Map<GachaItem.Rarity, List<GachaItem>> itemsByRarity;

    public GachaPool(List<GachaItem> items) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));

        // 按稀有度分组
        this.itemsByRarity = new EnumMap<>(GachaItem.Rarity.class);
        for (GachaItem.Rarity rarity : GachaItem.Rarity.values()) {
            itemsByRarity.put(rarity, new ArrayList<>());
        }
        for (GachaItem item : items) {
            itemsByRarity.get(item.getRarity()).add(item);
        }
    }

    /**
     * 获取所有抽奖项（不可变列表）。
     */
    public List<GachaItem> getItems() {
        return items;
    }

    /**
     * 获取所有抽奖项的副本（可变列表）。
     * <p>
     * 用于需要修改列表的场景，不会影响原始数据。
     */
    public List<GachaItem> getItemsCopy() {
        return new ArrayList<>(items);
    }

    /**
     * 计算总有效权重（考虑动态修改器）。
     */
    public int calculateTotalWeight(IQuestCapability cap) {
        return calculateTotalWeight(null, cap);
    }

    /**
     * 计算总有效权重（考虑动态修改器）。
     *
     * @param player 玩家实体（用于 ICondition）
     * @param cap    玩家能力数据
     */
    public int calculateTotalWeight(ServerPlayer player, IQuestCapability cap) {
        int total = 0;
        for (GachaItem item : items) {
            total += item.getEffectiveWeight(player, cap);
        }
        return total;
    }

    /**
     * 执行一次抽奖（根据条件过滤可见项）。
     *
     * @param cap 玩家能力数据（用于计算动态权重）
     * @return 抽中的物品，如果奖池为空则返回 null
     */
    public GachaItem draw(IQuestCapability cap) {
        return draw(null, cap);
    }

    /**
     * 执行一次抽奖（根据条件过滤可见项）。
     *
     * @param player 玩家实体（用于 ICondition）
     * @param cap    玩家能力数据（用于计算动态权重）
     * @return 抽中的物品，如果奖池为空则返回 null
     */
    public GachaItem draw(ServerPlayer player, IQuestCapability cap) {
        if (items.isEmpty()) {
            return null;
        }

        // 过滤出可见的抽奖项
        List<GachaItem> visibleItems = items.stream()
                .filter(item -> item.isVisible(player, cap))
                .collect(Collectors.toList());

        if (visibleItems.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (GachaItem item : visibleItems) {
            totalWeight += item.getEffectiveWeight(player, cap);
        }

        if (totalWeight <= 0) {
            return null;
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (GachaItem item : visibleItems) {
            int effectiveWeight = item.getEffectiveWeight(player, cap);
            cumulative += effectiveWeight;
            if (random < cumulative) {
                return item;
            }
        }

        // 理论上不会到达这里
        return visibleItems.get(visibleItems.size() - 1);
    }

    /**
     * 从指定稀有度中随机抽取（根据条件过滤可见项）。
     */
    public GachaItem drawFromRarity(GachaItem.Rarity rarity, IQuestCapability cap) {
        return drawFromRarity(rarity, null, cap);
    }

    /**
     * 从指定稀有度中随机抽取（根据条件过滤可见项）。
     *
     * @param rarity 稀有度
     * @param player 玩家实体（用于 ICondition）
     * @param cap    玩家能力数据
     */
    public GachaItem drawFromRarity(GachaItem.Rarity rarity,
                                    ServerPlayer player,
                                    IQuestCapability cap) {
        List<GachaItem> candidates = itemsByRarity.getOrDefault(rarity, Collections.emptyList());
        if (candidates.isEmpty()) {
            return null;
        }

        // 过滤出可见的抽奖项
        List<GachaItem> visibleCandidates = candidates.stream()
                .filter(item -> item.isVisible(player, cap))
                .collect(Collectors.toList());

        if (visibleCandidates.isEmpty()) {
            return null;
        }

        // 计算该稀有度的总权重
        int totalWeight = 0;
        for (GachaItem item : visibleCandidates) {
            totalWeight += item.getEffectiveWeight(player, cap);
        }

        if (totalWeight <= 0) {
            return null;
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (GachaItem item : visibleCandidates) {
            int effectiveWeight = item.getEffectiveWeight(cap);
            cumulative += effectiveWeight;
            if (random < cumulative) {
                return item;
            }
        }

        return visibleCandidates.get(visibleCandidates.size() - 1);
    }

    /**
     * 根据奖池项 ID 获取抽奖项。
     */
    @Nullable
    public GachaItem getItemById(String itemId) {
        for (GachaItem item : items) {
            if (item.getItemId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    public List<GachaItem> getAllItems() {
        return items;
    }

    public List<GachaItem> getItemsByRarity(GachaItem.Rarity rarity) {
        return itemsByRarity.getOrDefault(rarity, Collections.emptyList());
    }

    /**
     * 获取可见的抽奖项列表（用于 HUD 预览，客户端）。
     */
    public List<GachaItem> getVisibleItems(IQuestCapability cap) {
        return items.stream()
                .filter(item -> item.isVisibleClient(cap))
                .collect(Collectors.toList());
    }

    /**
     * 计算指定抽奖项的抽取概率（百分比，客户端）。
     *
     * @param itemId 抽奖项 ID
     * @param cap    玩家能力数据
     * @return 概率百分比（0-100），如果物品不存在或不可见则返回 0
     */
    public double getDrawProbability(String itemId, IQuestCapability cap) {
        GachaItem targetItem = getItemById(itemId);
        if (targetItem == null || !targetItem.isVisibleClient(cap)) {
            return 0.0;
        }

        // 过滤出所有可见项（客户端）
        List<GachaItem> visibleItems = items.stream()
                .filter(item -> item.isVisibleClient(cap))
                .collect(Collectors.toList());

        if (visibleItems.isEmpty()) {
            return 0.0;
        }

        // 计算总权重
        int totalWeight = 0;
        for (GachaItem item : visibleItems) {
            totalWeight += item.getEffectiveWeight(cap);
        }

        if (totalWeight <= 0) {
            return 0.0;
        }

        // 计算目标项的概率
        int itemWeight = targetItem.getEffectiveWeight(cap);
        return (itemWeight * 100.0) / totalWeight;
    }
}
