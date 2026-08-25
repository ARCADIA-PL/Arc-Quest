package org.arcadia.arc_quest.trade.gacha.builder;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeText;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.GachaPool;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.PityConfig;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 抽奖商店构建器。
 * <p>
 * 使用示例：
 * <pre>{@code
 * GachaShop构建器.create("arc_quest:mystery_gacha", Component.literal("神秘宝箱"))
 *     .drawCost(ItemTradeOffer.of(Items.DIAMOND, 10))
 *     .cooldown(CooldownType.SECONDS, 3600)
 *     .addItem("common_sword", swordStack, 50, GachaItem.Rarity.COMMON)
 *     .addItem("rare_armor", armorStack, 10, GachaItem.Rarity.RARE)
 *     .pitySystem(50, GachaItem.Rarity.EPIC, true)
 *     .buildAndRegister();
 * }</pre>
 */
public class GachaShopBuilder {

    private final String shopId;
    private final TradeText displayName;
    private final List<TradeCategory> categories = new ArrayList<>();
    private final LinkedHashMap<String, TradeEntry> entries = new LinkedHashMap<>();
    private final List<GachaItem> poolItems = new ArrayList<>();
    @Nullable
    private TradeText description;
    @Nullable
    private ICondition openCondition;
    private boolean simpleMode = false;
    private int themeColor = 0xFFFFFFFF;
    @Nullable
    private SoundEvent openSound;
    @Nullable
    private SoundEvent closeSound;
    // 抽奖特有配置
    private final List<ITradeOffer> drawCosts = new ArrayList<>();
    @Nullable
    private ResourceLocation drawCostIcon;  // 自定义成本图标
    private CooldownType cooldownType = CooldownType.NONE;
    private long cooldownValue = 0;        // 改为 long 类型，对标 TradeEntryBuilder
    private int resetTimeTicks = 0;        // 游戏刻重置时间点
    @Nullable
    private ICondition drawCondition;  // 抽奖执行条件（类似 canBuy）
    private int maxDraws = -1;         // 最大抽奖次数（-1 为无限）
    @Nullable
    private ICondition resetCondition; // 次数重置条件
    private boolean resetOnLimitReachedByCoolDown = true; // 达到限购后是否通过冷却自动重置（默认true）
    private boolean resetPityOnEarlyTrigger = true; // 保底前提前抽中是否重置保底进度（默认true）
    private PityConfig pityConfig;
    // === 抽奖失败音效配置（对标 TradeEntry构建器）===
    @Nullable
    private SoundEvent drawCooldownSound;      // 冷却中音效
    @Nullable
    private SoundEvent drawLimitReachedSound;  // 达到限购音效
    @Nullable
    private SoundEvent drawConditionFailSound; // 条件不满足音效
    @Nullable
    private SoundEvent drawFailSound;          // 通用失败音效
    // 稀有度配置映射
    @Nullable
    private Map<GachaItem.Rarity, RarityConfigData> rarityConfigs;

    private GachaShopBuilder(String shopId, TradeText displayName) {
        this.shopId = shopId;
        this.displayName = displayName;
    }

    /**
     * 创建 构建器，使用完整 ResourceLocation（推荐）。
     */
    public static GachaShopBuilder create(ResourceLocation id, Component displayName) {
        return new GachaShopBuilder(id.toString(), TradeText.component(displayName));
    }

    public static GachaShopBuilder create(ResourceLocation id, TradeText displayName) {
        return new GachaShopBuilder(id.toString(), displayName);
    }

    /**
     * 创建 构建器，使用字符串 ID（自动解析命名空间）。
     */
    public static GachaShopBuilder create(String id, Component displayName) {
        String shopId;
        if (id.contains(":")) {
            shopId = id;
        } else {
            shopId = Arc_Quest.MOD_ID + ":" + id;
        }
        return new GachaShopBuilder(shopId, TradeText.component(displayName));
    }

    // === 基础商店配置（代理到 TradeShop构建器）===

    public static GachaShopBuilder create(String id, TradeText displayName) {
        String shopId;
        if (id.contains(":")) {
            shopId = id;
        } else {
            shopId = Arc_Quest.MOD_ID + ":" + id;
        }
        return new GachaShopBuilder(shopId, displayName);
    }

    public GachaShopBuilder description(@Nullable Component description) {
        this.description = description == null ? null : TradeText.component(description);
        return this;
    }

    public GachaShopBuilder description(@Nullable TradeText description) {
        this.description = description;
        return this;
    }

    public GachaShopBuilder category(TradeCategory category) {
        categories.add(category);
        return this;
    }

    public GachaShopBuilder entry(String entryId, TradeEntry entry) {
        entries.put(entryId, entry);
        return this;
    }

    public GachaShopBuilder openCondition(@Nullable ICondition condition) {
        openCondition = condition;
        return this;
    }

    public GachaShopBuilder simpleMode(boolean simpleMode) {
        this.simpleMode = simpleMode;
        return this;
    }

    public GachaShopBuilder themeColor(int themeColor) {
        this.themeColor = themeColor;
        return this;
    }

    public GachaShopBuilder openSound(@Nullable SoundEvent sound) {
        openSound = sound;
        return this;
    }

    // === 抽奖特有配置 ===

    public GachaShopBuilder closeSound(@Nullable SoundEvent sound) {
        closeSound = sound;
        return this;
    }

    /**
     * 添加抽奖成本（支持多次调用以添加多个成本项）。
     */
    public GachaShopBuilder drawCost(ITradeOffer cost) {
        drawCosts.add(cost);
        return this;
    }

    /**
     * 设置抽奖成本的自定义图标。
     * <p>
     * 对标 TradeEntry 的 costIcon 功能。
     *
     * @param icon 图标资源位置
     */
    public GachaShopBuilder drawCostIcon(@Nullable ResourceLocation icon) {
        drawCostIcon = icon;
        return this;
    }

    /**
     * 设置冷却时间（秒）。
     */
    public GachaShopBuilder cooldown(long seconds) {
        cooldownType = CooldownType.SECONDS;
        cooldownValue = Math.max(0, seconds);
        return this;
    }

    /**
     * 设置游戏日冷却。
     */
    public GachaShopBuilder cooldownGameDay() {
        cooldownType = CooldownType.GAME_DAY;
        cooldownValue = 1;
        return this;
    }

    /**
     * 设置游戏刻冷却（指定重置时间点）。
     * <p>
     * 当游戏刻达到指定时间点时，冷却将重置。
     * 例如：设置为 6000 表示每天游戏刻 6000 时（约早上 5 点）重置。
     *
     * @param resetTick 重置时间点（0-24000）
     */
    public GachaShopBuilder cooldownGameTick(int resetTick) {
        cooldownType = CooldownType.GAME_TICK;
        cooldownValue = 0;
        resetTimeTicks = Math.max(0, Math.min(resetTick, 24000));
        return this;
    }

    /**
     * 设置抽奖执行条件（类似商店的 canBuy）。
     */
    public GachaShopBuilder drawCondition(@Nullable ICondition condition) {
        drawCondition = condition;
        return this;
    }

    /**
     * 设置最大抽奖次数（限购）。
     *
     * @param maxDraws 最大次数，-1 表示无限
     */
    public GachaShopBuilder maxDraws(int maxDraws) {
        this.maxDraws = maxDraws;
        return this;
    }

    /**
     * 设置抽奖次数重置条件。
     */
    public GachaShopBuilder resetCondition(@Nullable ICondition condition) {
        resetCondition = condition;
        return this;
    }

    /**
     * 使用冷却机制自动恢复限购。
     * <p>
     * 【对标商店系统】这会自动设置一个基于冷却时间的重置条件。
     * 实际逻辑在 GachaSession.checkAndResetDrawCount() 中处理：
     * 1. 先检查冷却是否过期（shouldResetByCooldown）
     * 2. 再检查自定义条件（如果配置了）
     */
    public GachaShopBuilder drawResetByCooldown() {
        // 标记为需要基于冷却恢复，实际逻辑在 GachaSession 中处理
        resetCondition = (player, completedQuests, flags, variables) -> false; // 占位
        return this;
    }

    /**
     * 设置达到限购后是否通过冷却自动重置。
     * <p>
     * - true（默认）：达到限购后记录冷却，冷却过期自动重置（类似每日刷新）
     * - false：达到限购后永久锁定，只能通过自定义条件重置
     *
     * @param resetOnLimitReached 是否自动重置
     */
    public GachaShopBuilder resetOnLimitReached(boolean resetOnLimitReached) {
        resetOnLimitReachedByCoolDown = resetOnLimitReached;
        return this;
    }

    // === 抽奖失败音效配置（对标 TradeEntry构建器）===

    /**
     * 设置保底前提前抽中指定物品/品质时是否重置保底进度。
     * <p>
     * - true（默认）：抽中后重置保底计数（鼓励继续抽）
     * - false：抽中后保留保底计数（防止浪费保底机会）
     *
     * @param resetPityOnEarlyTrigger 是否重置保底进度
     */
    public GachaShopBuilder resetPityOnEarlyTrigger(boolean resetPityOnEarlyTrigger) {
        this.resetPityOnEarlyTrigger = resetPityOnEarlyTrigger;
        return this;
    }

    /**
     * 设置抽奖冷却中的音效。
     */
    public GachaShopBuilder drawCooldownSound(@Nullable SoundEvent sound) {
        drawCooldownSound = sound;
        return this;
    }

    /**
     * 设置抽奖冷却中的音效（支持 Holder.Reference）。
     */
    public GachaShopBuilder drawCooldownSound(Holder.Reference<SoundEvent> sound) {
        drawCooldownSound = sound.value();
        return this;
    }

    /**
     * 设置达到抽奖上限的音效。
     */
    public GachaShopBuilder drawLimitReachedSound(@Nullable SoundEvent sound) {
        drawLimitReachedSound = sound;
        return this;
    }

    /**
     * 设置达到抽奖上限的音效（支持 Holder.Reference）。
     */
    public GachaShopBuilder drawLimitReachedSound(Holder.Reference<SoundEvent> sound) {
        drawLimitReachedSound = sound.value();
        return this;
    }

    /**
     * 设置抽奖条件不满足的音效。
     */
    public GachaShopBuilder drawConditionFailSound(@Nullable SoundEvent sound) {
        drawConditionFailSound = sound;
        return this;
    }

    /**
     * 设置抽奖条件不满足的音效（支持 Holder.Reference）。
     */
    public GachaShopBuilder drawConditionFailSound(Holder.Reference<SoundEvent> sound) {
        drawConditionFailSound = sound.value();
        return this;
    }

    /**
     * 设置抽奖通用失败音效。
     */
    public GachaShopBuilder drawFailSound(@Nullable SoundEvent sound) {
        drawFailSound = sound;
        return this;
    }

    /**
     * 设置抽奖通用失败音效（支持 Holder.Reference）。
     */
    public GachaShopBuilder drawFailSound(Holder.Reference<SoundEvent> sound) {
        drawFailSound = sound.value();
        return this;
    }

    /**
     * 添加抽奖项（基础版本，sortOrder 默认为权重值）。
     */
    public GachaShopBuilder addItem(String itemId, ItemStack rewardStack,
                                    int weight, GachaItem.Rarity rarity) {
        return addItem(itemId, rewardStack, new ItemTradeOffer(rewardStack.getItem(), rewardStack.getCount(), false),
                weight, rarity, true, 1, 1, null, null, -1, null, weight);
    }

    /**
     * 添加抽奖项（随机数量范围，sortOrder 默认为权重值）。
     *
     * @param minCount 最小数量
     * @param maxCount 最大数量
     */
    public GachaShopBuilder addItem(String itemId, ItemStack rewardStack,
                                    int weight, GachaItem.Rarity rarity,
                                    int minCount, int maxCount) {
        return addItem(itemId, rewardStack, new ItemTradeOffer(rewardStack.getItem(), 1, false),
                weight, rarity, true, minCount, maxCount, null, null, -1, null, weight);
    }

    /**
     * 添加抽奖项（使用 ITradeOffer，支持任意类型奖励，固定数量，sortOrder 默认为权重值）。
     */
    public GachaShopBuilder addItem(String itemId, ItemStack rewardStack, ITradeOffer reward,
                                    int weight, GachaItem.Rarity rarity) {
        return addItem(itemId, rewardStack, reward, weight, rarity, true, 1, 1, null, null, -1, null, weight);
    }

    /**
     * 添加抽奖项（使用 ITradeOffer，随机数量范围，sortOrder 默认为权重值）。
     */
    public GachaShopBuilder addItem(String itemId, ItemStack rewardStack, ITradeOffer reward,
                                    int weight, GachaItem.Rarity rarity,
                                    int minCount, int maxCount) {
        return addItem(itemId, rewardStack, reward, weight, rarity, true, minCount, maxCount, null, null, -1, null, weight);
    }

    /**
     * 添加抽奖项（完整参数，sortOrder 默认为权重值）。
     *
     * @param itemId            奖池项唯一 ID
     * @param rewardStack       奖励物品堆叠
     * @param reward            奖励 Offer
     * @param countsTowardsPity 是否计入保底计数
     * @param minCount          最小奖励数量
     * @param maxCount          最大奖励数量
     */
    public GachaShopBuilder addItem(String itemId, ItemStack rewardStack, ITradeOffer reward,
                                    int weight, GachaItem.Rarity rarity,
                                    boolean countsTowardsPity,
                                    int minCount, int maxCount) {
        return addItem(itemId, rewardStack, reward, weight, rarity, countsTowardsPity, minCount, maxCount, null, null, -1, null, weight);
    }

    /**
     * 添加抽奖项（带可见性条件，sortOrder 默认为权重值）。
     *
     * @param itemId            奖池项唯一 ID
     * @param rewardStack       奖励物品堆叠
     * @param reward            奖励 Offer
     * @param countsTowardsPity 是否计入保底计数
     * @param minCount          最小奖励数量
     * @param maxCount          最大奖励数量
     * @param visibleCondition  可见性条件（null 表示始终可见）
     */
    public GachaShopBuilder addItem(String itemId, ItemStack rewardStack, ITradeOffer reward,
                                    int weight, GachaItem.Rarity rarity,
                                    boolean countsTowardsPity,
                                    int minCount, int maxCount,
                                    @Nullable ICondition visibleCondition) {
        return addItem(itemId, rewardStack, reward, weight, rarity, countsTowardsPity, minCount, maxCount, visibleCondition, null, -1, null, weight);
    }

    /**
     * 添加抽奖项（完整配置，对标 TradeEntry构建器）。
     *
     * @param itemId            奖池项唯一 ID
     * @param rewardStack       奖励物品堆叠
     * @param reward            奖励 Offer
     * @param weight            权重
     * @param rarity            稀有度
     * @param countsTowardsPity 是否计入保底计数
     * @param minCount          最小奖励数量
     * @param maxCount          最大奖励数量
     * @param visibleCondition  可见性条件
     * @param rewardIcon        自定义奖励图标（null 使用默认物品图标）
     * @param themeColor        主题色（-1 使用稀有度/商店默认）
     * @param drawSuccessSound  抽中音效（null 使用稀有度默认）
     * @param sortOrder         排序顺序（默认为权重值）
     */
    public GachaShopBuilder addItem(String itemId, ItemStack rewardStack, ITradeOffer reward,
                                    int weight, GachaItem.Rarity rarity,
                                    boolean countsTowardsPity,
                                    int minCount, int maxCount,
                                    @Nullable ICondition visibleCondition,
                                    @Nullable ResourceLocation rewardIcon,
                                    int themeColor,
                                    @Nullable SoundEvent drawSuccessSound,
                                    int sortOrder) {
        poolItems.add(new GachaItem(itemId, rewardStack, reward, weight, rarity, countsTowardsPity, minCount, maxCount, visibleCondition, rewardIcon, themeColor, drawSuccessSound, sortOrder));
        return this;
    }

    /**
     * 配置保底系统（简化版，按稀有度）。
     */
    public GachaShopBuilder pitySystem(int threshold, GachaItem.Rarity guaranteedRarity,
                                       boolean resetOnTrigger) {
        pityConfig = new PityConfig(threshold, guaranteedRarity, resetOnTrigger);
        return this;
    }

    /**
     * 配置保底系统（简化版，指定物品 ID）。
     */
    public GachaShopBuilder pitySystem(int threshold, String guaranteedItemId,
                                       boolean resetOnTrigger) {
        pityConfig = new PityConfig(threshold, guaranteedItemId, resetOnTrigger);
        return this;
    }

    /**
     * 配置保底系统（完整版，支持冷却和条件）。
     *
     * @param threshold          保底触发阈值
     * @param guaranteedItemId   保底指定的抽奖项 ID（null 则按稀有度）
     * @param guaranteedRarity   保底稀有度（当 guaranteedItemId 为 null 时使用）
     * @param resetCooldownType  重置冷却类型
     * @param resetCooldownValue 重置冷却值
     * @param resetCondition     重置条件（满足时重置计数）
     * @param resetOnTrigger     触发后是否重置计数
     */
    public GachaShopBuilder pitySystem(int threshold,
                                       @Nullable String guaranteedItemId,
                                       @Nullable GachaItem.Rarity guaranteedRarity,
                                       CooldownType resetCooldownType,
                                       int resetCooldownValue,
                                       @Nullable ICondition resetCondition,
                                       boolean resetOnTrigger) {
        pityConfig = new PityConfig(
                threshold, guaranteedItemId, guaranteedRarity,
                resetCooldownType, resetCooldownValue, resetCondition, resetOnTrigger
        );
        return this;
    }

    /**
     * 配置稀有度的默认主题色和抽中音效（对标 TradeEntry 的 themeColor 和 purchaseSuccessSound）。
     * <p>
     * 使用示例：
     * <pre>{@code
     * .rarityConfig(GachaItem.Rarity.COMMON, 0xFFAAAAAA, SoundEvents.ENTITY_ITEM_PICKUP)
     * .rarityConfig(GachaItem.Rarity.RARE, 0xFF55FFFF, SoundEvents.ENTITY_PLAYER_LEVELUP)
     * .rarityConfig(GachaItem.Rarity.LEGENDARY, 0xFFFFAA00, SoundEvents.ENTITY_ENDER_DRAGON_DEATH)
     * }</pre>
     *
     * @param rarity           稀有度
     * @param themeColor       主题色（ARGB）
     * @param drawSuccessSound 抽中音效
     */
    public GachaShopBuilder rarityConfig(GachaItem.Rarity rarity, int themeColor, @Nullable SoundEvent drawSuccessSound) {
        // 存储配置，在 buildAndRegister 时应用到 GachaShopDefinition
        if (rarityConfigs == null) {
            rarityConfigs = new HashMap<>();
        }
        rarityConfigs.put(rarity, new RarityConfigData(themeColor, drawSuccessSound));
        return this;
    }

    /**
     * 构建并注册商店。
     */
    public GachaShopDefinition buildAndRegister() {
        if (drawCosts.isEmpty()) {
            throw new IllegalStateException("Gacha shop '" + shopId + "' must have at least one draw cost");
        }
        if (poolItems.isEmpty()) {
            throw new IllegalStateException("Gacha shop '" + shopId + "' must have at least one pool item");
        }

        GachaPool pool = new GachaPool(poolItems);
        GachaShopDefinition shop = new GachaShopDefinition(
                shopId, displayName, description, categories, entries,
                openCondition, simpleMode, themeColor, openSound, closeSound,
                pool, drawCosts, cooldownType, cooldownValue, resetTimeTicks,
                drawCondition, maxDraws, resetCondition, resetOnLimitReachedByCoolDown, resetPityOnEarlyTrigger, pityConfig,
                drawCooldownSound, drawLimitReachedSound, drawConditionFailSound, drawFailSound
        );

        // 应用稀有度配置
        if (rarityConfigs != null) {
            for (var entry : rarityConfigs.entrySet()) {
                shop.setRarityConfig(entry.getKey(), entry.getValue().themeColor, entry.getValue().drawSuccessSound);
            }
        }

        // 注册底层的 TradeShopDefinition 到 TradeRegistry
        TradeRegistry.register(shop.getShopDefinition());

        // 注册抽奖商店到 GachaRegistry
        GachaRegistry.register(shop);

        return shop;
    }

    /**
     * 稀有度配置数据（内部类）。
     */
    private static class RarityConfigData {
        final int themeColor;
        @Nullable
        final SoundEvent drawSuccessSound;

        RarityConfigData(int themeColor, @Nullable SoundEvent drawSuccessSound) {
            this.themeColor = themeColor;
            this.drawSuccessSound = drawSuccessSound;
        }
    }
}
