package org.arcadia.arc_quest.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.arcadia.arc_quest.guide.builder.GuidePageBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.arcadia.arc_quest.trade.builder.TradeShopBuilder;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.builder.GachaShopBuilder;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;

/** Test-only definitions registered at the supported extension point, before registry freeze. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JeiClientAuditFixtures {
    static final String QUEST_ID = "arc_quest:jei_client_audit";
    static final ResourceLocation GUIDE_ID = ResourceLocation.parse("arc_quest:jei_client_audit_guide");
    static final String SHOP_ID = "arc_quest:jei_client_audit_shop";
    static final String GACHA_ID = "arc_quest:jei_client_audit_gacha";
    private JeiClientAuditFixtures() {}

    @SubscribeEvent
    public static void quests(ArcQuestRegistrationEvent.Quest event) {
        if (!JeiClientAuditGate.enabled()) return;
        event.register(QuestBuilder.create(QUEST_ID).displayName("JEI client acceptance quest")
                .canBeAutoTrack(false)
                .chapterTradeShop(SHOP_ID)
                .phase(PhaseBuilder.create("requirements").displayName("JEI requirements")
                        .objective(ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("collect"))
                        .objective(ObjectiveBuilder.offer(Items.GOLD_INGOT, 9999).id("offer"))
                        .reward(new ItemReward(Items.EMERALD, 2)))
                .reward(new ItemReward(Items.IRON_INGOT, 3)).build());
    }

    @SubscribeEvent
    public static void guides(ArcQuestRegistrationEvent.Guide event) {
        if (!JeiClientAuditGate.enabled()) return;
        event.register(GuideBuilder.create(GUIDE_ID).title("JEI client acceptance guide")
                .summary("An explicit item association for runtime acceptance")
                .icon(Items.DIAMOND).renderLargeIconOnIntro(true).unlockPopup(false).associatedItem(Items.DIAMOND, 0)
                .page(GuidePageBuilder.create().none().description("JEI source navigation reached this page."))
                .build());
    }

    @SubscribeEvent
    public static void trades(ArcQuestRegistrationEvent.Trade event) {
        if (!JeiClientAuditGate.enabled()) return;
        var shop = TradeShopBuilder.create(SHOP_ID).displayName(Component.literal("JEI client acceptance shop"))
                .entry(TradeEntryBuilder.create("iron_sword").displayName(Component.literal("Audit iron sword"))
                        .costItem(Items.EMERALD, 5).costItem(Items.DIAMOND, 2).rewardItem(Items.IRON_SWORD, 1));
        // Enough ordinary entries to exercise each native viewport's scroll clipping.
        for (int i = 0; i < 12; i++) shop.entry(TradeEntryBuilder.create("scroll_" + i)
                .displayName(Component.literal("Audit scroll row " + i))
                .costItem(Items.EMERALD, 3).costItem(Items.DIAMOND, 1).rewardItem(Items.IRON_AXE, 1));
        event.register(shop.build());
    }

    @SubscribeEvent
    public static void gacha(ArcQuestRegistrationEvent.Gacha event) {
        if (!JeiClientAuditGate.enabled()) return;
        GachaShopBuilder.create(GACHA_ID, Component.literal("JEI client acceptance prize pool"))
                .drawCost(new ItemTradeOffer(Items.EMERALD, 1, true))
                .addItem("gold_ingot", new ItemStack(Items.GOLD_INGOT), 1, GachaItem.Rarity.RARE)
                .buildAndRegister();
    }
}
