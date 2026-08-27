package com.example.arcqaddon;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.api.event.gacha.GachaEvents;
import org.arcadia.arc_quest.api.event.trade.TradeOpenedEvent;

@Mod.EventBusSubscriber(modid = ExampleArcQuestAddon.MOD_ID)
public final class ScenarioCommerceQuestBridge {
    private ScenarioCommerceQuestBridge() {
    }

    @SubscribeEvent
    public static void onTradeOpened(TradeOpenedEvent event) {
        if (!event.getShopId().equals(ScenarioAdvancedTradeGachaContent.TRADE_ID.toString())) {
            return;
        }
        ScenarioQuestProgressAdapter.incrementActiveObjective(
                event.getPlayer(),
                ScenarioParallelQuestContent.QUEST_ID,
                ScenarioParallelQuestContent.VISIT_MARKET_PHASE,
                0,
                1);
    }

    @SubscribeEvent
    public static void onGachaDrawn(GachaEvents.PostDrawEvent event) {
        if (!event.getShopId().equals(ScenarioAdvancedTradeGachaContent.GACHA_ID.toString())) {
            return;
        }
        event.getPlayerData().setFlag("example_arcq_addon:opened_expedition_crate");
    }
}
