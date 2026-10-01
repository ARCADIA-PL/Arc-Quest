package org.arcadia.arc_quest.integration.jei;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProviders;
import org.arcadia.arc_quest.integration.jei.trade.TradeJeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.gacha.GachaJeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.quest.QuestJeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.guide.GuideJeiCatalogProvider;

public final class JeiCatalogBuiltins {
    private static boolean registered;
    private JeiCatalogBuiltins() {}
    public static synchronized void register() {
        if (registered) return;
        JeiCatalogProviders.register(ResourceLocation.fromNamespaceAndPath("arc_quest", "trade"), new TradeJeiCatalogProvider());
        JeiCatalogProviders.register(ResourceLocation.fromNamespaceAndPath("arc_quest", "gacha"), new GachaJeiCatalogProvider());
        JeiCatalogProviders.register(ResourceLocation.fromNamespaceAndPath("arc_quest", "quest"), new QuestJeiCatalogProvider());
        JeiCatalogProviders.register(ResourceLocation.fromNamespaceAndPath("arc_quest", "guide"), new GuideJeiCatalogProvider());
        registered = true;
    }
}
