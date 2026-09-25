package org.arcadia.arc_quest.trade.demo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;

import java.util.LinkedHashMap;

/** 两端从同一份小型货架快照构造原生商店，不改变编辑器或数据包格式。 */
public final class RefreshingTestShopDefinition {
    public static final String SHOP_ID = "arc_quest:test_updates";
    private RefreshingTestShopDefinition() { }

    public static TradeShopDefinition create(RefreshingTradeCatalog catalog) {
        Item[] products = {Items.APPLE, Items.BREAD, Items.CARROT, Items.BAKED_POTATO,
                Items.OAK_LOG, Items.COBBLESTONE, Items.IRON_INGOT, Items.GLASS,
                Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.SHEARS};
        TradeCategory[] categories = {
                TradeCategory.ofTranslated("food", "arc_quest.trade.test.food", 0, 0x92C879),
                TradeCategory.ofTranslated("materials", "arc_quest.trade.test.materials", 1, 0xD6B277),
                TradeCategory.ofTranslated("tools", "arc_quest.trade.test.tools", 2, 0x86B5D8)};
        var shop = TradeShopBuilder.create(SHOP_ID)
                .displayName(Component.translatable("arc_quest.trade.test.title", catalog.round()))
                .description(Component.translatable("arc_quest.trade.test.description"));
        for (TradeCategory category : categories) shop.category(category);
        for (var listing : catalog.entries()) {
            Item product = products[listing.product()];
            shop.entry(TradeEntryBuilder.create("slot_" + listing.slot())
                    .displayName(product.getDescription())
                    .description(Component.translatable("arc_quest.trade.test.entry", listing.slot() + 1))
                    .category(categories[listing.product() / 4])
                    .costItem(Items.EMERALD, listing.price())
                    .rewardItem(product, listing.count()).sortOrder(listing.slot()));
        }
        return shop.build();
    }

    public static void remove() {
        if (!TradeRegistry.getDatapackSnapshot().containsKey(SHOP_ID)) return;
        var remaining = new LinkedHashMap<>(TradeRegistry.getDatapackSnapshot());
        remaining.remove(SHOP_ID);
        TradeRegistry.replaceDatapackSnapshot(remaining);
    }
}
