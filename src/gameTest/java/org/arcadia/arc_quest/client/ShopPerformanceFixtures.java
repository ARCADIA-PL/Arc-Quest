package org.arcadia.arc_quest.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;

/** Identical opt-in definitions for before/after runs; never used for actual purchases. */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ShopPerformanceFixtures {
    static final int[] COUNTS = {12, 120, 600};
    private ShopPerformanceFixtures() {}
    static String id(int count) { return "arc_quest:shop_performance_" + count; }
    @SubscribeEvent public static void trades(ArcQuestRegistrationEvent.Trade event) {
        if (!ShopPerformanceClientAudit.enabled()) return;
        for (int count : COUNTS) {
            var shop = TradeShopBuilder.create(id(count)).displayName(Component.literal("Shop benchmark / " + count + " entries"));
            for (int index = 0; index < count; index++) shop.entry(TradeEntryBuilder.create("entry_" + index)
                    .displayName(Component.literal("Benchmark item " + index))
                    .costItem(Items.EMERALD, 5).costItem(Items.DIAMOND, 2).rewardItem(Items.IRON_SWORD, 1));
            event.register(shop.build());
        }
    }
}
