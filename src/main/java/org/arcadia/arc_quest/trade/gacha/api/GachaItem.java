package org.arcadia.arc_quest.trade.gacha.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.QuestConditionContext;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽奖项定义。
 * <p>
 * 支持动态权重调整（通过条件系统）和任意类型的奖励（ITradeOffer）。
 */
public class GachaItem {

    private final String itemId;          // 奖池项唯一 ID
    private final ItemStack itemStack;    // 奖励物品堆叠
    private final ITradeOffer reward;     // 奖励 Offer
    private final int baseWeight;
    private final Rarity rarity;
    private final boolean countsTowardsPity;
    private final List<WeightModifier> weightModifiers;
    private final int minCount;  // 最小数量
    private final int maxCount;  // 最大数量（如果 == minCount 则固定数量）
    private final int sortOrder; // 排序顺序（默认为权重值）
    @Nullable
    private final ICondition visibleCondition;  // 可见性条件（控制是否参与抽取）

    // === 视觉与音效配置（对标 TradeEntry）===
    @Nullable
    private final ResourceLocation rewardIcon;  // 自定义奖励图标
    private final int themeColor;  // -1 表示使用商店默认，否则为 ARGB 颜色值
    @Nullable
    private final SoundEvent drawSuccessSound;  // 抽中时的音效

    public GachaItem(String itemId, ItemStack itemStack, ITradeOffer reward, int baseWeight,
                     Rarity rarity, boolean countsTowardsPity) {
        this(itemId, itemStack, reward, baseWeight, rarity, countsTowardsPity, 1, 1, null, null, -1, null, baseWeight);
    }

    /**
     * 完整构造函数。
     *
     * @param itemId           奖池项唯一 ID
     * @param itemStack        奖励物品堆叠
     * @param minCount         最小奖励数量
     * @param maxCount         最大奖励数量（如果 == minCount 则固定数量）
     * @param visibleCondition 可见性条件（null 表示始终可见）
     * @param rewardIcon       自定义奖励图标（null 使用默认物品图标）
     * @param themeColor       主题色（-1 使用商店默认，否则为 ARGB）
     * @param drawSuccessSound 抽中音效（null 使用稀有度默认音效）
     * @param sortOrder        排序顺序（默认为权重值）
     */
    public GachaItem(String itemId, ItemStack itemStack, ITradeOffer reward, int baseWeight,
                     Rarity rarity, boolean countsTowardsPity,
                     int minCount, int maxCount,
                     @Nullable ICondition visibleCondition,
                     @Nullable ResourceLocation rewardIcon,
                     int themeColor,
                     @Nullable SoundEvent drawSuccessSound,
                     int sortOrder) {
        if (itemId == null || itemId.isEmpty()) {
            throw new IllegalArgumentException("GachaItem ID cannot be null or empty");
        }
        if (minCount <= 0 || maxCount < minCount) {
            throw new IllegalArgumentException("Invalid count range: min=" + minCount + ", max=" + maxCount);
        }
        this.itemId = itemId;
        this.itemStack = itemStack;
        this.reward = reward;
        this.baseWeight = baseWeight;
        this.rarity = rarity;
        this.countsTowardsPity = countsTowardsPity;
        weightModifiers = new ArrayList<>();
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.visibleCondition = visibleCondition;
        this.rewardIcon = rewardIcon;
        this.themeColor = themeColor;
        this.drawSuccessSound = drawSuccessSound;
        this.sortOrder = sortOrder;
    }

    /**
     * 添加权重修改器（通过 ICondition）。
     * <p>
     * 使用 ICondition 接口，与对话系统的条件系统保持一致。
     *
     * @param condition   条件实例
     * @param weightDelta 权重增量（可为负数）
     */
    public GachaItem addWeightModifier(ICondition condition, int weightDelta) {
        if (condition != null) {
            weightModifiers.add(new WeightModifier(condition, weightDelta));
        }
        return this;
    }

    /**
     * 计算当前有效权重（基础权重 + 所有匹配的修改器）。
     */
    public int getEffectiveWeight(ArcQuestPlayer data) {
        return getEffectiveWeight(null, data);
    }

    /**
     * 计算当前有效权重（基础权重 + 所有匹配的修改器）。
     *
     * @param player 玩家实体（用于 ICondition）
     * @param data    玩家能力数据
     */
    public int getEffectiveWeight(ServerPlayer player, ArcQuestPlayer data) {
        int effective = baseWeight;
        for (WeightModifier modifier : weightModifiers) {
            if (modifier.matches(player, data)) {
                effective += modifier.getWeightDelta();
            }
        }
        return Math.max(0, effective);
    }

    /**
     * 计算实际奖励数量（在 minCount 和 maxCount 之间随机）。
     */
    public int calculateActualCount() {
        if (minCount == maxCount) {
            return minCount;
        }
        return ThreadLocalRandom.current().nextInt(minCount, maxCount + 1);
    }

    // 相关处理说明。
    public String getItemId() {
        return itemId;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public ITradeOffer getReward() {
        return reward;
    }

    public int getBaseWeight() {
        return baseWeight;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public boolean countsTowardsPity() {
        return countsTowardsPity;
    }

    public List<WeightModifier> getWeightModifiers() {
        return weightModifiers;
    }

    public int getMinCount() {
        return minCount;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    @Nullable
    public ICondition getVisibleCondition() {
        return visibleCondition;
    }

    // 相关处理说明。
    @Nullable
    public ResourceLocation getRewardIcon() {
        return rewardIcon;
    }

    public int getThemeColor() {
        return themeColor;
    }

    @Nullable
    public SoundEvent getDrawSuccessSound() {
        return drawSuccessSound;
    }

    /**
     * 检查当前玩家是否可以看到此抽奖项（服务端）。
     *
     * @param player 玩家对象（不可为 null）
     * @param data    玩家能力数据
     */
    public boolean isVisible(ServerPlayer player, ArcQuestPlayer data) {
        if (visibleCondition == null) return true;

        var completedQuests = data.getCompletedQuestLocations();
        return CoreProcessors.get().conditions().evaluate(visibleCondition,
                new QuestConditionContext(
                        player, completedQuests, data.getAllFlags(), data.getAllVariables()));
    }

    /**
     * 检查当前玩家是否可以看到此抽奖项（客户端）。
     *
     * @param data 玩家能力数据
     */
    public boolean isVisibleClient(ArcQuestPlayer data) {
        if (visibleCondition == null) return true;

        var completedQuests = data.getCompletedQuestLocations();
        return CoreProcessors.get().conditions().evaluate(visibleCondition,
                new QuestConditionContext(
                        null, completedQuests, data.getAllFlags(), data.getAllVariables()));
    }

    /**
     * 稀有度枚举。
     */
    public enum Rarity {
        COMMON(0, "common"),
        UNCOMMON(1, "uncommon"),
        RARE(2, "rare"),
        EPIC(3, "epic"),
        LEGENDARY(4, "legendary");

        private final int level;
        private final String name;

        Rarity(int level, String name) {
            this.level = level;
            this.name = name;
        }

        public int getLevel() {
            return level;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 权重修改器（支持动态调整概率）。
     */
    public static class WeightModifier {
        private final ICondition condition;
        private final int weightDelta;

        public WeightModifier(ICondition condition, int weightDelta) {
            this.condition = condition;
            this.weightDelta = weightDelta;
        }

        public boolean matches(ServerPlayer player, ArcQuestPlayer data) {
            if (player == null || data == null) return false;
            var completedQuests = data.getCompletedQuestLocations();
            return CoreProcessors.get().conditions().evaluate(condition,
                    new QuestConditionContext(
                            player, completedQuests, data.getAllFlags(), data.getAllVariables()));
        }

        public int getWeightDelta() {
            return weightDelta;
        }
    }
}
