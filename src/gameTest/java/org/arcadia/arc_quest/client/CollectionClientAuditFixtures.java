package org.arcadia.arc_quest.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.ArrayList;
import java.util.List;

/** Menu-only definitions are included exclusively in the opt-in acceptance source set. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CollectionClientAuditFixtures {
    public static final String QUEST = "arc_quest:collection_client_audit";
    public static final String PHASE = "field";
    public static final ResourceLocation TAG = ResourceLocation.parse("arc_quest:collection_audit_logs");
    public static final ResourceLocation LOGS_ENTRY = ResourceLocation.parse("arc_quest:audit_logs");
    public static final String DISCOVERY_REWARD = "logs_discovery", RESEARCH_REWARD = "logs_research", BINDING_REWARD = "logs_investigation";
    public static final String NODE_LOCKED = "survey_locked", NODE_UNLOCKED = "survey_available", NODE_CLAIMED = "survey_received";
    private CollectionClientAuditFixtures() {}

    @SubscribeEvent public static void register(ArcQuestRegistrationEvent.Quest event) {
        if (!Boolean.getBoolean("arc_quest.collection.audit")) return;
        var iron = CollectionEntryBuilder.create("arc_quest:audit_iron").category("materials").displayName("Iron specimen")
                .description("A known record still requires this survey's actual collection objectives.")
                .item(Items.IRON_INGOT).image("image", ResourceLocation.parse("minecraft:textures/item/iron_ingot.png"), 16, 16, "A guide-style specimen image")
                .text("long_readability", "A specimen may contain several paragraphs, long requirement labels and a reward list. Text must stay separate from the draggable scrollbar. ".repeat(24))
                .relatedItem(Items.IRON_NUGGET).build();
        var skeleton = CollectionEntryBuilder.create("arc_quest:audit_skeleton").category("mobs").displayName("Skeleton patrol")
                .description("Two-dimensional head portrait; this objective needs three defeats.")
                .entity(EntityType.SKELETON).relatedItem(Items.BONE).build();
        var logs = CollectionEntryBuilder.create(LOGS_ENTRY).category("materials").displayName("Any logs")
                .itemTag(TAG).description("The icon, tooltip and query freeze on the same hovered tag candidate.")
                .discoveryReward(DISCOVERY_REWARD, new ItemReward(Items.EMERALD, 2))
                .researchReward(RESEARCH_REWARD, new ItemReward(Items.DIAMOND, 1))
                .bindingReward(BINDING_REWARD, new ItemReward(Items.GOLD_INGOT, 3)).build();
        var entries = new ArrayList<>(List.of(iron, skeleton, logs));
        var sheet = CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("iron", iron.getEntryId()).objective("iron"))
                .binding(EntryRequirementBuilder.create("skeleton", skeleton.getEntryId()).objective("skeleton"))
                .binding(EntryRequirementBuilder.create("logs", logs.getEntryId()).objective("logs"));
        var phase = PhaseBuilder.create(PHASE).displayName("Field survey").autoAdvanceOnComplete(false)
                .reward(new ItemReward(Items.AMETHYST_SHARD, 2))
                .objective(ObjectiveBuilder.collect(Items.IRON_INGOT, 5).id("iron").display("Collect five iron ingots"))
                .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 3).id("skeleton").display("Defeat three skeletons").iconItem(Items.IRON_SWORD))
                .objective(ObjectiveBuilder.collectTag(TAG, 8).id("logs").display("Collect any eight logs"));
        for (int i = 0; i < 40; i++) {
            String id = "extra_" + i;
            var entry = CollectionEntryBuilder.create("arc_quest:audit_" + id).category("materials")
                    .displayName("Sample " + i).item(Items.COAL).build();
            entries.add(entry);
            phase.objective(ObjectiveBuilder.collect(Items.COAL, 1).id(id).display("Acquire sample " + i));
            sheet.binding(EntryRequirementBuilder.create(id, entry.getEntryId()).objective(id));
        }
        var hidden = CollectionEntryBuilder.create("arc_quest:audit_hidden").category("mobs").displayName("Secret hidden specimen")
                .entity(EntityType.CREEPER).visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build();
        entries.add(hidden);
        phase.objective(ObjectiveBuilder.kill(EntityType.CREEPER, 1).id("hidden").display("Hidden creature"));
        sheet.binding(EntryRequirementBuilder.create("hidden", hidden.getEntryId()).objective("hidden").optional());
        phase.collectionSheet(sheet);
        var categories = List.of(new CollectionCategoryDefinition("materials", QuestText.literal("Materials"), null, 0, List.of(), List.of(), List.of()),
                new CollectionCategoryDefinition("mobs", QuestText.literal("Creatures"), null, 1, List.of(), List.of(), List.of()));
        var rewardNodes = List.of(
                new CollectionRewardNode(NODE_LOCKED, RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                        List.of(new ItemReward(Items.REDSTONE, 1)), List.of(), QUEST),
                new CollectionRewardNode(NODE_UNLOCKED, RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                        List.of(new ItemReward(Items.EMERALD, 1)), List.of(), QUEST),
                new CollectionRewardNode(NODE_CLAIMED, RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                        List.of(new ItemReward(Items.LAPIS_LAZULI, 1)), List.of(), QUEST));
        var config = new CollectionQuestConfig(categories, List.of(), rewardNodes, null, null, false, true, true, entries);
        var definition = QuestBuilder.create(QUEST).displayName("Field journal / native acceptance")
                .description("Specimens, collapsible details and actual task progress share the quest journal.")
                .category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION).themeColor(0x85C6AE)
                .collectionConfig(config).canBeAutoTrack(false).reward(new ItemReward(Items.DIAMOND, 2)).phase(phase).build();
        org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore.registerCodeDefinitionFactory(definition.getId(), "client-audit-v1", () -> definition, true);
        event.register(definition);
    }
}
