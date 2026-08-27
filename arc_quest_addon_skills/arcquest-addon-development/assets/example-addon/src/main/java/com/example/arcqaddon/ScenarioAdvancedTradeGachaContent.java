package com.example.arcqaddon;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.builder.GachaShopBuilder;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;

public final class ScenarioAdvancedTradeGachaContent {
    public static final ResourceLocation TRADE_ID = id("expedition_exchange");
    public static final ResourceLocation GACHA_ID = id("expedition_crate");
    public static final String ACCESS_FLAG = "example_arcq_addon:expedition_access";
    public static final String PREMIUM_FLAG = "example_arcq_addon:premium_exchange";

    private ScenarioAdvancedTradeGachaContent() {
    }

    public static void registerTrade(ArcQuestRegistrationEvent.Trade event) {
        TradeCategory supplies = TradeCategory.ofTranslated(
                "expedition", "trade.example_arcq_addon.category.expedition");
        event.register(TradeShopBuilder.create(TRADE_ID)
                .displayName(Component.translatable(
                        "trade.example_arcq_addon.expedition_exchange.title"))
                .description(Component.translatable(
                        "trade.example_arcq_addon.expedition_exchange.description"))
                .openCondition(ICondition.flagSet(ACCESS_FLAG))
                .category(supplies)
                .entry(TradeEntryBuilder.create("daily_food")
                        .displayName(Component.translatable(
                                "trade.example_arcq_addon.expedition_exchange.daily_food"))
                        .category(supplies)
                        .costItem(Items.EMERALD, 2)
                        .rewardItem(Items.COOKED_BEEF, 8)
                        .maxPurchases(1)
                        .cooldownGameDay()
                        .purchaseResetByCooldown())
                .entry(TradeEntryBuilder.create("premium_tool")
                        .displayName(Component.translatable(
                                "trade.example_arcq_addon.expedition_exchange.premium_tool"))
                        .category(supplies)
                        .costItem(Items.DIAMOND, 2)
                        .rewardItem(Items.DIAMOND_PICKAXE, 1)
                        .visibleCondition(ICondition.flagSet(PREMIUM_FLAG))
                        .canBuyCondition(ICondition.flagSet(ACCESS_FLAG)
                                .and(ICondition.flagSet(PREMIUM_FLAG)))
                        .maxPurchases(1))
                .build());
    }

    public static void registerGacha(ArcQuestRegistrationEvent.Gacha event) {
        GachaShopBuilder.create(
                        GACHA_ID,
                        Component.translatable("gacha.example_arcq_addon.expedition_crate.title"))
                .description(Component.translatable(
                        "gacha.example_arcq_addon.expedition_crate.description"))
                .openCondition(ICondition.flagSet(ACCESS_FLAG))
                .drawCondition(ICondition.flagSet(ACCESS_FLAG))
                .drawCost(new ItemTradeOffer(Items.EMERALD, 4, true))
                .addItem("food", new ItemStack(Items.COOKED_BEEF, 6),
                        70, GachaItem.Rarity.COMMON)
                .addItem("iron", new ItemStack(Items.IRON_INGOT, 4),
                        25, GachaItem.Rarity.UNCOMMON)
                .addItem("diamond", new ItemStack(Items.DIAMOND, 2),
                        5, GachaItem.Rarity.RARE)
                .pitySystem(12, GachaItem.Rarity.RARE, true)
                .resetPityOnEarlyTrigger(true)
                .maxDraws(3)
                .cooldownGameDay()
                .drawResetByCooldown()
                .buildAndRegister();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, path);
    }
}
