package org.arcadia.arc_quest.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
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
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionQuestConfigBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionSheetBuilder;
import org.arcadia.arc_quest.quest.builder.EntryRequirementBuilder;
import org.arcadia.arc_quest.quest.api.QuestMode;
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
    static final String ATLAS_ID = "arc_quest:jei_atlas_client_audit";
    static final ResourceLocation ATLAS_GEM = ResourceLocation.parse("arc_quest:jei_atlas_gem");
    static final String COLLECTION_ID = "arc_quest:jei_collection_client_audit";
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
        var specimen = CollectionEntryBuilder.create("arc_quest:jei_collection_skeleton")
                .category("creatures").displayName("Skeleton specimen").entity(EntityType.SKELETON)
                .iconItem(Items.IRON_SWORD)
                .image("portrait", ResourceLocation.parse("minecraft:textures/item/iron_sword.png"), 16, 16,
                        "The configured item icon supports recipes and uses even for a creature objective.").build();
        var collectionDefinition = QuestBuilder.create(COLLECTION_ID).displayName("JEI collection acceptance")
                .mode(QuestMode.COLLECTION).canBeAutoTrack(false)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("creatures", "Creatures").entry(specimen).build())
                .phase(PhaseBuilder.create("survey").displayName("Creature survey").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 9999).id("skeleton").iconItem(Items.IRON_SWORD))
                        .collectionSheet(CollectionSheetBuilder.create().binding(
                                EntryRequirementBuilder.create("skeleton", specimen.getEntryId()).objective("skeleton"))))
                .build();
        org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.registerCodeDefinitionFactory(collectionDefinition.getId(), "jei-client-audit-v1", () -> collectionDefinition, true);
        event.register(collectionDefinition);
        var subset = new org.arcadia.arc_quest.quest.api.StackMatchSpec(
                org.arcadia.arc_quest.quest.api.StackMatchSpec.Mode.NBT_SUBSET, "");
        var epic = new org.arcadia.arc_quest.quest.api.CollectionSubjectSpec(ResourceLocation.parse("arc_quest:item_stack"),
                ResourceLocation.parse("minecraft:paper"), 1, "{gem:\"audit:royalty\",rarity:\"audit:epic\"}", subset, java.util.Map.of());
        var rare = new org.arcadia.arc_quest.quest.api.CollectionSubjectSpec(ResourceLocation.parse("arc_quest:item_stack"),
                ResourceLocation.parse("minecraft:paper"), 1, "{gem:\"audit:royalty\",rarity:\"audit:rare\"}", subset, java.util.Map.of());
        var gem = CollectionEntryBuilder.create(ATLAS_GEM).category("gems").displayName("Audit gem subject")
                .item(Items.PAPER).presentation(new org.arcadia.arc_quest.quest.api.CollectionEntryPresentation(null,
                        java.util.List.of(new org.arcadia.arc_quest.quest.api.CollectionVariantDefinition("epic",
                                        org.arcadia.arc_quest.quest.api.QuestText.literal("Epic"), epic, "", org.arcadia.arc_quest.quest.api.CollectionContentReveal.ALWAYS),
                                new org.arcadia.arc_quest.quest.api.CollectionVariantDefinition("rare",
                                        org.arcadia.arc_quest.quest.api.QuestText.literal("Rare"), rare, "", org.arcadia.arc_quest.quest.api.CollectionContentReveal.ALWAYS)),
                        "epic", java.util.List.of())).build();
        var atlasResource = ResourceLocation.parse("arc_quest:jei_audit_atlas");
        var layout = new org.arcadia.arc_quest.quest.api.CollectionAtlasDefinition(1, atlasResource, null,
                java.util.List.of(new org.arcadia.arc_quest.quest.api.CollectionAtlasDefinition.Category("gems", java.util.List.of(
                        new org.arcadia.arc_quest.quest.api.CollectionAtlasDefinition.Group("gems",
                                org.arcadia.arc_quest.quest.api.QuestText.literal("Audit gems"), 3, 0, 0,
                                java.util.List.of(new org.arcadia.arc_quest.quest.api.CollectionAtlasDefinition.Node(ATLAS_GEM, 0, 0)))))));
        var sourceSet = new org.arcadia.arc_quest.quest.api.CollectionSourceSetDefinition(1,
                ResourceLocation.parse("arc_quest:jei_audit_sources"), java.util.List.of(
                        new org.arcadia.arc_quest.quest.api.CollectionSourceSetDefinition.Relation(ResourceLocation.parse("arc_quest:jei_audit_drop"),
                                specimen.getEntryId(), gem.getEntryId(), java.util.List.of("epic", "rare"), "Exploration",
                                org.arcadia.arc_quest.quest.api.QuestText.literal("Verified audit source; no guaranteed reward"), 0)));
        var atlasQuest = QuestBuilder.create(ATLAS_ID).displayName("JEI atlas acceptance").mode(QuestMode.COLLECTION)
                .canBeAutoTrack(false).collectionConfig(CollectionQuestConfigBuilder.create().category("gems", "Gems")
                        .entry(gem).journalMode(org.arcadia.arc_quest.quest.api.CollectionPresentationMode.ATLAS).build()
                        .withAtlas(atlasResource, layout).withSourceSets(java.util.List.of(sourceSet)))
                .phase(PhaseBuilder.create("gems").displayName("Gem atlas").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.collect(Items.PAPER, 9999).id("gem"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("gem", gem.getEntryId()).objective("gem"))))
                .build();
        org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.registerCodeDefinitionFactory(atlasQuest.getId(),
                "jei-atlas-audit-v1", () -> atlasQuest, true);
        event.register(atlasQuest);

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
