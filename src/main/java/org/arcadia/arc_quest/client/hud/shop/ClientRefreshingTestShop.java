package org.arcadia.arc_quest.client.hud.shop;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.trade.demo.RefreshingTradeCatalog;
import org.arcadia.arc_quest.trade.demo.RefreshingTestShopDefinition;
import org.arcadia.arc_quest.trade.network.S2CTestTradeShopPacket;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientRefreshingTestShop {
    private static RefreshingTradeCatalog catalog;
    private ClientRefreshingTestShop() { }

    public static void accept(S2CTestTradeShopPacket packet) {
        catalog = packet.catalog().entries().isEmpty() ? null : packet.catalog();
        if (catalog == null) RefreshingTestShopDefinition.remove();
        else restore();
    }

    public static void restore() {
        if (catalog != null) TradeRegistry.registerDatapack(RefreshingTestShopDefinition.create(catalog));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (catalog != null) RefreshingTestShopDefinition.remove();
        catalog = null;
    }
}
