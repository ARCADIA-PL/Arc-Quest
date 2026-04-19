package org.com.arc_quest.trade.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import org.com.arc_quest.quest.api.ICondition;
import org.com.arc_quest.quest.condition.FlagSetCondition;
import org.com.arc_quest.quest.condition.QuestCompletedCondition;
import org.com.arc_quest.trade.api.TradeCategory;
import org.com.arc_quest.trade.builder.TradeEntryBuilder;
import org.com.arc_quest.trade.builder.TradeShopBuilder;
import org.slf4j.Logger;

/**
 * 示例交易商店注册 —— 展示交易系统的各种能力。
 */
public final class TradeContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TradeContent() {}

    public static void registerAll() {
        LOGGER.info("[ArcQuest] Registering trade shops...");

        registerBlacksmithShop();
        registerPotionShop();
        registerQuickTradePanel();

        LOGGER.info("[ArcQuest] Total registered trade shops: {}", TradeRegistry.size());
    }

    /**
     * 示例1：铁匠铺 —— 完整交易窗口，含分类
     */
    private static void registerBlacksmithShop() {
        TradeCategory weapons = TradeCategory.ofTranslatedColor("weapons", "arc_quest.trade.category.weapons", 0, ChatFormatting.RED);
        TradeCategory armor = TradeCategory.ofTranslatedColor("armor", "arc_quest.trade.category.armor", 1, ChatFormatting.AQUA);
        TradeCategory tools = TradeCategory.ofTranslatedColor("tools", "arc_quest.trade.category.tools", 2, ChatFormatting.GREEN);

        TradeShopBuilder.create("blacksmith_shop")
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
                // 示例：高级物品 - 需要完成任务或拥有flag
                .entry(TradeEntryBuilder.create("netherite_sword")
                        .displayName(Component.literal("§6下界合金剑"))
                        .description(Component.literal("§7需要完成新手任务或拥有VIP权限"))
                        .costItem(Items.NETHERITE_INGOT, 3)
                        .costItem(Items.DIAMOND, 5)
                        .rewardItem(Items.NETHERITE_SWORD, 1)
                        .category(weapons)
                        .maxPurchases(1)
                        .condition((serverPlayer, completedQuests, flags, variables) -> {
                            return serverPlayer.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
                        })
                        .sortOrder(3))
                .buildAndRegister();
    }

    /**
     * 示例2：药水商人 —— 售卖效果型交易物
     */
    private static void registerPotionShop() {
        TradeShopBuilder.create("potion_shop")
                .displayName(Component.translatable("arc_quest.trade.shop.potion_shop.name"))
                .description(Component.translatable("arc_quest.trade.shop.potion_shop.desc"))
                .entry(TradeEntryBuilder.create("strength_potion")
                        .displayName(Component.translatable("arc_quest.trade.entry.strength_potion.name"))
                        .description(Component.translatable("arc_quest.trade.entry.strength_potion.desc"))
                        .costItem(Items.EMERALD, 3)
                        .rewardEffect(MobEffects.DAMAGE_BOOST, 60)
                        .cooldown(120))
                .entry(TradeEntryBuilder.create("speed_potion")
                        .displayName(Component.translatable("arc_quest.trade.entry.speed_potion.name"))
                        .description(Component.translatable("arc_quest.trade.entry.speed_potion.desc"))
                        .costItem(Items.EMERALD, 4)
                        .rewardEffect(MobEffects.MOVEMENT_SPEED, 120)
                        .cooldown(180))
                .entry(TradeEntryBuilder.create("regen_potion")
                        .displayName(Component.translatable("arc_quest.trade.entry.regen_potion.name"))
                        .description(Component.translatable("arc_quest.trade.entry.regen_potion.desc"))
                        .costItem(Items.EMERALD, 8)
                        .costItem(Items.GOLD_INGOT, 2)
                        .rewardEffect(MobEffects.REGENERATION, 30, 1)
                        .cooldownGameDay()
                        .maxPurchases(3))
                .buildAndRegister();
    }

    /**
     * 示例3：快速交易弹窗 —— 简易模式
     */
    private static void registerQuickTradePanel() {
        TradeShopBuilder.create("quick_food_trade")
                .displayName(Component.translatable("arc_quest.trade.shop.quick_food_trade.name"))
                .simpleMode()
                .entry(TradeEntryBuilder.create("buy_bread")
                        .displayName(Component.translatable("arc_quest.trade.entry.buy_bread.name"))
                        .costItem(Items.EMERALD, 1)
                        .rewardItem(Items.BREAD, 4))
                .entry(TradeEntryBuilder.create("buy_steak")
                        .displayName(Component.translatable("arc_quest.trade.entry.buy_steak.name"))
                        .costItem(Items.EMERALD, 2)
                        .rewardItem(Items.COOKED_BEEF, 2))
                .entry(TradeEntryBuilder.create("buy_golden_apple")
                        .displayName(Component.translatable("arc_quest.trade.entry.buy_golden_apple.name"))
                        .costItem(Items.EMERALD, 10)
                        .costItem(Items.GOLD_INGOT, 4)
                        .rewardItem(Items.GOLDEN_APPLE, 1)
                        .cooldownGameDay())
                .buildAndRegister();
    }
}
