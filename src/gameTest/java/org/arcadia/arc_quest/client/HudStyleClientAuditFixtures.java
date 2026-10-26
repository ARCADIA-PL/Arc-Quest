package org.arcadia.arc_quest.client;

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
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.builder.*;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.builder.GachaShopBuilder;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;

/** Definitions exist only in the opt-in, disconnected rendering audit process. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HudStyleClientAuditFixtures {
    public static final String ID = "arc_quest:hud_style_audit";
    private static boolean enabled() { return Boolean.getBoolean("arc_quest.hud.style.audit"); }
    @SubscribeEvent public static void quests(ArcQuestRegistrationEvent.Quest event) {
        if (!enabled()) return;
        event.register(QuestBuilder.create(ID).displayName("探索者的第一步 / First expedition")
                .description("收集旅途中的物资，准备下一次探索。\nCollect supplies and prepare for the next expedition.")
                .canBeAutoTrack(false).phase(PhaseBuilder.create("supplies").displayName("准备物资")
                        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 16).id("logs").display("收集橡木 / Collect oak logs"))
                        .objective(ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1).id("table").display("制作工作台 / Craft a workbench"))
                        .objective(ObjectiveBuilder.offer(Items.EMERALD, 5).id("offer").display("提交绿宝石 / Offer emeralds"))).build());
    }
    @SubscribeEvent public static void guides(ArcQuestRegistrationEvent.Guide event) {
        if (!enabled()) return;
        event.register(GuideBuilder.create(ID).title("野外生存指南 / Field guide").icon(Items.BOOK)
                .summary("准备工具与补给，开始新的旅程。")
                .page(GuidePageBuilder.create().description("木材是旅程的起点。收集橡木，制作工作台，然后准备装备。\n\nGather wood, craft a workbench and prepare your equipment."))
                .page(GuidePageBuilder.create().description("使用任务日志查看目标，使用追踪面板掌握当前进度。"))
                .build());
    }
    @SubscribeEvent public static void trades(ArcQuestRegistrationEvent.Trade event) {
        if (!enabled()) return;
        var supplies = TradeCategory.of("supplies", "旅行补给");
        var builder = TradeShopBuilder.create(ID).displayName("旅行商人 / Supplies").category(supplies);
        var items = new net.minecraft.world.item.Item[]{Items.IRON_SWORD, Items.BREAD, Items.TORCH, Items.IRON_PICKAXE, Items.APPLE};
        for (int i = 0; i < items.length; i++) builder.entry(TradeEntryBuilder.create("entry_" + i)
                .category(supplies).displayName(items[i].getDescription()).rewardItem(items[i], i + 1));
        event.register(builder.build());
    }
    @SubscribeEvent public static void gacha(ArcQuestRegistrationEvent.Gacha event) {
        if (!enabled()) return;
        var builder = GachaShopBuilder.create(ID + "_gacha", Component.literal("冒险者补给箱 / Expedition cache"))
                .drawCost(new ItemTradeOffer(Items.EMERALD, 1, false));
        var items = new net.minecraft.world.item.Item[]{Items.APPLE, Items.IRON_INGOT, Items.DIAMOND, Items.GOLDEN_APPLE, Items.NETHERITE_INGOT};
        int[] colors = {0xFFAAAAAA, 0xFF85C6AE, 0xFF56C8FF, 0xFFB76EFF, 0xFFFFD166};
        for (int i = 0; i < items.length; i++) {
            var rarity = GachaItem.Rarity.values()[i];
            builder.rarityConfig(rarity, colors[i], null);
            builder.addItem("reward_" + i, new ItemStack(items[i]), 10, rarity);
        }
        builder.buildAndRegister();
    }
}
