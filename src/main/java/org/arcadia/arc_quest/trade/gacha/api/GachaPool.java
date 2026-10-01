package org.arcadia.arc_quest.trade.gacha.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
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
        itemsByRarity = new EnumMap<>(GachaItem.Rarity.class);
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
    public int calculateTotalWeight(ArcQuestPlayer data) {
        return calculateTotalWeight(null, data);
    }

    /**
     * 计算总有效权重（考虑动态修改器）。
     *
     * @param player 玩家实体（用于 ICondition）
     * @param data    玩家能力数据
     */
    public int calculateTotalWeight(ServerPlayer player, ArcQuestPlayer data) {
        long total = 0;
        for (GachaItem item : items) {
            total += Math.max(0, item.getEffectiveWeight(player, data));
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * 执行一次抽奖（根据条件过滤可见项）。
     *
     * @param data 玩家能力数据（用于计算动态权重）
     * @return 抽中的物品，如果奖池为空则返回 null
     */
    public GachaItem draw(ArcQuestPlayer data) {
        return draw(null, data);
    }

    /**
     * 执行一次抽奖（根据条件过滤可见项）。
     *
     * @param player 玩家实体（用于 ICondition）
     * @param data    玩家能力数据（用于计算动态权重）
     * @return 抽中的物品，如果奖池为空则返回 null
     */
    public GachaItem draw(ServerPlayer player, ArcQuestPlayer data) {
        return weighted(items, player, data).draw(bound -> ThreadLocalRandom.current().nextLong(bound));
    }

    /**
     * 从指定稀有度中随机抽取（根据条件过滤可见项）。
     */
    public GachaItem drawFromRarity(GachaItem.Rarity rarity, ArcQuestPlayer data) {
        return drawFromRarity(rarity, null, data);
    }

    /**
     * 从指定稀有度中随机抽取（根据条件过滤可见项）。
     *
     * @param rarity 稀有度
     * @param player 玩家实体（用于 ICondition）
     * @param data    玩家能力数据
     */
    public GachaItem drawFromRarity(GachaItem.Rarity rarity,
                                    ServerPlayer player,
                                    ArcQuestPlayer data) {
        return weighted(itemsByRarity.getOrDefault(rarity, List.of()), player, data)
                .draw(bound -> ThreadLocalRandom.current().nextLong(bound));
    }

    private static GachaWeightSnapshot weighted(List<GachaItem> candidates, ServerPlayer player, ArcQuestPlayer data) {
        return new GachaWeightSnapshot(candidates, item -> item.isVisible(player, data),
                item -> item.getEffectiveWeight(player, data));
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
    public List<GachaItem> getVisibleItems(ArcQuestPlayer data) {
        return items.stream()
                .filter(item -> item.isVisibleClient(data))
                .collect(Collectors.toList());
    }

    /**
     * 计算指定抽奖项的抽取概率（百分比，客户端）。
     *
     * @param itemId 抽奖项 ID
     * @param data    玩家能力数据
     * @return 概率百分比（0-100），如果物品不存在或不可见则返回 0
     */
    public double getDrawProbability(String itemId, ArcQuestPlayer data) {
        return new GachaWeightSnapshot(items, item -> item.isVisibleClient(data),
                item -> item.getEffectiveWeight(data)).probability(itemId);
    }
}
