package com.example.arcqaddon;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.builder.GachaShopBuilder;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;

public final class ExampleTradeGachaContent {
    public static final ResourceLocation TRADE_ID = id("field_shop");
    public static final ResourceLocation GACHA_ID = id("supply_draw");

    private ExampleTradeGachaContent() {
    }

    public static void registerTrade(ArcQuestRegistrationEvent.Trade event) {
        TradeCategory supplies = TradeCategory.ofTranslated(
                "supplies", "trade.example_arcq_addon.category.supplies");
        event.register(TradeShopBuilder.create(TRADE_ID)
                .displayName(Component.translatable("trade.example_arcq_addon.field_shop.title"))
                .description(Component.translatable(
                        "trade.example_arcq_addon.field_shop.description"))
                .category(supplies)
                .entry(TradeEntryBuilder.create("bread")
                        .displayName(Component.translatable(
                                "trade.example_arcq_addon.field_shop.bread"))
                        .category(supplies)
                        .costItem(Items.EMERALD, 1)
                        .rewardItem(Items.BREAD, 4)
                        .cooldown(5))
                .build());
    }

    public static void registerGacha(ArcQuestRegistrationEvent.Gacha event) {
        GachaShopBuilder.create(
                        GACHA_ID,
                        Component.translatable("gacha.example_arcq_addon.supply_draw.title"))
                .drawCost(new ItemTradeOffer(Items.EMERALD, 3, true))
                .addItem("iron", new ItemStack(Items.IRON_INGOT, 2),
                        80, GachaItem.Rarity.COMMON)
                .addItem("diamond", new ItemStack(Items.DIAMOND),
                        20, GachaItem.Rarity.RARE)
                .pitySystem(10, GachaItem.Rarity.RARE, true)
                .maxDraws(-1)
                .buildAndRegister();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, path);
    }
}
