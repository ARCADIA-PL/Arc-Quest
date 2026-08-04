package org.arcadia.arc_quest.trade.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;
import org.slf4j.Logger;

/**
 * 示例交易商店注册 —— 展示交易系统的各种能力。
 * <p>
 * 展示如何使用 ArcQuestAPI 进行商店注册（Lib 模组标准实践）。
 */
public final class TradeContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TradeContent() {
    }

    public static void registerAll() {
        LOGGER.info("[ArcQuest] Registering trade shops...");

        registerBlacksmithShop();
        registerMerchantShop();
        registerWanderingTraderShop();
        registerMysteriousMerchantShop();
        registerQuickSupplies();

        LOGGER.info("[ArcQuest] Total registered trade shops: {}", TradeRegistry.size());
    }

    /**
     * 示例1：铁匠铺 —— 完整交易窗口，含分类
     */
    private static void registerBlacksmithShop() {
        TradeCategory weapons = TradeCategory.ofTranslatedColor("weapons", "arc_quest.trade.category.weapons", 0, ChatFormatting.RED);
        TradeCategory armor = TradeCategory.ofTranslatedColor("armor", "arc_quest.trade.category.armor", 1, ChatFormatting.AQUA);
        TradeCategory tools = TradeCategory.ofTranslatedColor("tools", "arc_quest.trade.category.tools", 2, ChatFormatting.GREEN);

        ArcQuestAPI.registerTradeShop(
                TradeShopBuilder.create("arc_quest:blacksmith_shop")
                        .displayName(Component.translatable("arc_quest.trade.shop.blacksmith_shop.name"))
                        .description(Component.translatable("arc_quest.trade.shop.blacksmith_shop.desc"))
                        .category(weapons)
                        .category(armor)
                        .category(tools)
                        .entry(TradeEntryBuilder.create("iron_sword")
                                .displayName(Component.translatable("arc_quest.trade.entry.iron_sword.name"))
                                .costItem(Items.EMERALD, 5)
                                .rewardItem(Items.IRON_SWORD, 1)
                                .category(weapons)
                                .sortOrder(1))
                        .entry(TradeEntryBuilder.create("diamond_sword")
                                .displayName(Component.translatable("arc_quest.trade.entry.diamond_sword.name"))
                                .costItem(Items.EMERALD, 20)
                                .costItem(Items.DIAMOND, 2)
                                .rewardItem(Items.DIAMOND_SWORD, 1)
                                .category(weapons)
                                .maxPurchases(2)
                                .purchaseResetByCooldown()
                                .cooldownGameTick(0)
                                .sortOrder(2))
                        .entry(TradeEntryBuilder.create("iron_chestplate")
                                .displayName(Component.translatable("arc_quest.trade.entry.iron_chestplate.name"))
                                .costItem(Items.EMERALD, 12)
                                .rewardItem(Items.IRON_CHESTPLATE, 1)
                                .category(armor)
                                .sortOrder(1))
                        .entry(TradeEntryBuilder.create("iron_helmet")
                                .displayName(Component.translatable("arc_quest.trade.entry.iron_helmet.name"))
                                .costItem(Items.EMERALD, 8)
                                .rewardItem(Items.IRON_HELMET, 1)
                                .category(armor)
                                .sortOrder(2))
                        .entry(TradeEntryBuilder.create("iron_pickaxe")
                                .displayName(Component.translatable("arc_quest.trade.entry.iron_pickaxe.name"))
                                .costItem(Items.EMERALD, 6)
                                .rewardItem(Items.IRON_PICKAXE, 1)
                                .category(tools)
                                .cooldownGameDay())
                        // 示例1：高级物品 - 可见性和购买资格使用相同条件
                        .entry(TradeEntryBuilder.create("netherite_sword")
                                .displayName(Component.literal("§6下界合金剑"))
                                .description(Component.literal("§7需要拥有村庄英雄效果"))
                                .costItem(Items.NETHERITE_INGOT, 3)
                                .costItem(Items.DIAMOND, 5)
                                .rewardItem(Items.NETHERITE_SWORD, 1)
                                .category(weapons)
                                .maxPurchases(1)
                                // condition() 同时设置可见性和购买资格
                                .condition((serverPlayer, completedQuests, flags, variables) -> {
                                    return serverPlayer != null && serverPlayer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
                                })
                                .sortOrder(3))
                        // 示例2：特殊商品 - 可见性和购买资格分离
                        .entry(TradeEntryBuilder.create("mystery_box")
                                .displayName(Component.literal("§d神秘宝箱"))
                                .description(Component.literal("§7需拥有村庄英雄效果时可见，达到10级后可购买"))
                                .costItem(Items.EMERALD, 15)
                                .rewardItem(Items.DIAMOND, 3)
                                .category(tools)
                                .visibleCondition((serverPlayer, completedQuests, flags, variables) -> {
                                    return serverPlayer != null && serverPlayer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
                                })
                                .canBuyCondition((serverPlayer, completedQuests, flags, variables) -> {
                                    return serverPlayer != null && serverPlayer.experienceLevel >= 10;
                                })
                                .cooldownGameDay()
                                .maxPurchases(2)
                                .sortOrder(4))
                        .build()
        );
    }

    /**
     * 旅行商人 —— 物资供应商 (匹配 epic_merchant)
     */
    private static void registerMerchantShop() {
        TradeCategory supplies = TradeCategory.ofTranslatedColor("supplies", "arc_quest.trade.category.supplies", 0, ChatFormatting.GREEN);

        ArcQuestAPI.registerTradeShop(
                TradeShopBuilder.create("arc_quest:merchant_shop")
                        .displayName(Component.translatable("arc_quest.trade.shop.merchant_shop.name"))
                        .description(Component.translatable("arc_quest.trade.shop.merchant_shop.desc"))
                        .category(supplies)
                        .entry(TradeEntryBuilder.create("merchant_food_pack")
                                .displayName(Component.translatable("arc_quest.trade.entry.merchant_food_pack.name"))
                                .description(Component.translatable("arc_quest.trade.entry.merchant_food_pack.desc"))
                                .costItem(Items.EMERALD, 3)
                                .rewardItem(Items.BREAD, 4)
                                .rewardItem(Items.BAKED_POTATO, 2)
                                .category(supplies))
                        .entry(TradeEntryBuilder.create("merchant_torch_bundle")
                                .displayName(Component.translatable("arc_quest.trade.entry.merchant_torch_bundle.name"))
                                .description(Component.translatable("arc_quest.trade.entry.merchant_torch_bundle.desc"))
                                .costItem(Items.EMERALD, 5)
                                .rewardItem(Items.TORCH, 32)
                                .category(supplies))
                        .build()
        );
    }

    /**
     * 流浪商人 —— 稀有商品 (匹配 epic_wandering_trader)
     */
    private static void registerWanderingTraderShop() {
        TradeCategory rare = TradeCategory.ofTranslatedColor("rare", "arc_quest.trade.category.rare", 0, ChatFormatting.AQUA);

        ArcQuestAPI.registerTradeShop(
                TradeShopBuilder.create("arc_quest:wandering_trader_shop")
                        .displayName(Component.translatable("arc_quest.trade.shop.wandering_trader_shop.name"))
                        .description(Component.translatable("arc_quest.trade.shop.wandering_trader_shop.desc"))
                        .category(rare)
                        .entry(TradeEntryBuilder.create("trader_exotic_plant")
                                .displayName(Component.translatable("arc_quest.trade.entry.trader_exotic_plant.name"))
                                .description(Component.translatable("arc_quest.trade.entry.trader_exotic_plant.desc"))
                                .costItem(Items.EMERALD, 8)
                                .rewardItem(Items.WARPED_FUNGUS, 1)
                                .rewardItem(Items.CRIMSON_FUNGUS, 1)
                                .category(rare)
                                .cooldownGameDay())
                        .entry(TradeEntryBuilder.create("trader_dye_set")
                                .displayName(Component.translatable("arc_quest.trade.entry.trader_dye_set.name"))
                                .description(Component.translatable("arc_quest.trade.entry.trader_dye_set.desc"))
                                .costItem(Items.EMERALD, 6)
                                .rewardItem(Items.LAPIS_LAZULI, 4)
                                .rewardItem(Items.PINK_DYE, 4)
                                .category(rare))
                        .build()
        );
    }

    /**
     * 神秘商人 —— 深夜禁忌宝库 (匹配 epic_mysterious_merchant)
     * <p>
     * 注意：可见性控制已在对话树 (epic_mysterious_merchant) 的选项条件中处理，
     * 此处仅负责定义商品内容、价格及购买限制。
     */
    private static void registerMysteriousMerchantShop() {
        TradeCategory rare = TradeCategory.ofTranslatedColor("rare", "arc_quest.trade.category.rare", 0, ChatFormatting.LIGHT_PURPLE);

        ArcQuestAPI.registerTradeShop(
                TradeShopBuilder.create("arc_quest:mysterious_merchant_shop")
                        .displayName(Component.translatable("arc_quest.trade.shop.mysterious_merchant_shop.name"))
                        .description(Component.translatable("arc_quest.trade.shop.mysterious_merchant_shop.desc"))
                        .category(rare)
                        .entry(TradeEntryBuilder.create("mystery_netherite")
                                .displayName(Component.translatable("arc_quest.trade.entry.mystery_netherite.name"))
                                .description(Component.translatable("arc_quest.trade.entry.mystery_netherite.desc"))
                                .costItem(Items.DIAMOND, 10)
                                .costItem(Items.GOLD_INGOT, 16)
                                .rewardItem(Items.NETHERITE_INGOT, 1)
                                .category(rare)
                                .maxPurchases(1))
                        .entry(TradeEntryBuilder.create("mystery_totem")
                                .displayName(Component.translatable("arc_quest.trade.entry.mystery_totem.name"))
                                .description(Component.translatable("arc_quest.trade.entry.mystery_totem.desc"))
                                .costItem(Items.EMERALD, 32)
                                .rewardItem(Items.TOTEM_OF_UNDYING, 1)
                                .category(rare)
                                .maxPurchases(1)
                                .cooldownGameDay())
                        .build()
        );
    }

    /**
     * 快速补给 —— 简易交易弹窗示例 (匹配 Village Guard)
     */
    private static void registerQuickSupplies() {
        ArcQuestAPI.registerTradeShop(
                TradeShopBuilder.create("arc_quest:quick_supplies")
                        .displayName(Component.translatable("arc_quest.trade.shop.quick_supplies.name"))
                        .simpleMode() // 开启简易模式
                        .entry(TradeEntryBuilder.create("qs_bread")
                                .displayName(Component.translatable("arc_quest.trade.entry.qs_bread.name"))
                                .costItem(Items.EMERALD, 1)
                                .rewardItem(Items.BREAD, 4))
                        .entry(TradeEntryBuilder.create("qs_potion")
                                .displayName(Component.translatable("arc_quest.trade.entry.qs_potion.name"))
                                .costItem(Items.EMERALD, 3)
                                .rewardEffect(MobEffects.HEAL.value(), 1))
                        .build()
        );
    }
}
