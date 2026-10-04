package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.api.rule.collection.AllEntriesCompleteRule;
import org.arcadia.arc_quest.quest.api.rule.collection.CompletedEntryCountRule;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.List;

/** Playable field handbook, consumable supply orders and a parallel camp expedition. */
public final class CollectionFieldDemos {
    public static final ResourceLocation FIELD = id("field_compendium_demo");
    public static final ResourceLocation RENEWABLE = id("renewable_survey_demo");
    public static final ResourceLocation PARALLEL = id("parallel_expedition_demo");
    public static final ResourceLocation ZOMBIE = id("codex/zombie");
    public static final ResourceLocation SKELETON = id("codex/skeleton");
    public static final ResourceLocation SPIDER = id("codex/spider");
    public static final ResourceLocation COW = id("codex/cow");
    public static final ResourceLocation IRON = id("codex/iron_ingot");
    public static final ResourceLocation COAL = id("codex/coal");
    public static final ResourceLocation LOGS = id("codex/logs");
    public static final ResourceLocation BONE = id("codex/bone");
    public static final ResourceLocation FLESH = id("codex/rotten_flesh");
    public static final ResourceLocation STRING = id("codex/string");
    public static final ResourceLocation WHEAT = id("codex/wheat");
    public static final ResourceLocation TORCH = id("codex/torch");
    public static final ResourceLocation WORKBENCH = id("codex/crafting_table");
    private static final ResourceLocation LOG_TAG = ResourceLocation.parse("minecraft:logs");
    private static final ResourceLocation PLANK_TAG = ResourceLocation.parse("minecraft:planks");
    private CollectionFieldDemos() {}

    public static void registerAll() {
        var entries = entries();
        entries.forEach(CollectionEntryRegistry::register);
        ArcQuestAPI.registerQuest(field(entries));
        ArcQuestAPI.registerQuest(renewable(entries));
        ArcQuestAPI.registerQuest(parallel(entries));
    }

    public static List<CollectionEntryDefinition> entries() {
        var zombie = CollectionEntryBuilder.create(ZOMBIE).category("living").displayName(t("entry.zombie.name")).entity(EntityType.ZOMBIE)
                .description(t("entry.zombie.description"))
                .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_defeat").display(t("entry.zombie.discovery")))
                .outcome("anatomy", t("entry.zombie.outcome.anatomy"))
                .migrateResearchStep(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5).id("anatomy").display(t("entry.zombie.legacy.anatomy")).build(), "anatomy")
                .discoveryReward("zombie_first_record", new ItemReward(Items.COAL, 1))
                .outcomeReward("anatomy", "zombie_anatomy", new ItemReward(Items.IRON_NUGGET, 3))
                .relatedItem(Items.ROTTEN_FLESH).relatedItem(Items.SHIELD)
                .text("field_notes", t("entry.zombie.content.field_notes"))
                .image("habitat", id("textures/gui/collection/field_notes.png"), 240, 120, t("entry.zombie.caption.habitat"))
                .content(outcomeNotes("anatomy_notes", "anatomy", t("entry.zombie.content.anatomy_notes")))
                .build();
        var skeleton = CollectionEntryBuilder.create(SKELETON).category("living").displayName(t("entry.skeleton.name")).entity(EntityType.SKELETON)
                .description(t("entry.skeleton.description"))
                .discover(ObjectiveBuilder.kill(EntityType.SKELETON, 1).id("first_defeat").display(t("entry.skeleton.discovery")))
                .outcome("combat", t("entry.skeleton.outcome.combat"))
                .migrateResearchStep(ObjectiveBuilder.kill(EntityType.SKELETON, 3).id("ranged_behavior").display(t("entry.skeleton.legacy.ranged_behavior")).build(), "combat")
                .relatedItem(Items.BONE).relatedItem(Items.ARROW).relatedItem(Items.SHIELD)
                .text("notes", t("entry.skeleton.content.notes"))
                .content(outcomeNotes("combat_notes", "combat", t("entry.skeleton.content.combat_notes")))
                .sortOrder(1).build();
        var spider = CollectionEntryBuilder.create(SPIDER).category("living").displayName(t("entry.spider.name")).entity(EntityType.SPIDER)
                .description(t("entry.spider.description"))
                .publicClue(t("entry.spider.clue"))
                .discover(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("first_defeat").display(t("entry.spider.discovery")))
                .outcome("samples", t("entry.spider.outcome.samples"))
                .migrateResearchStep(ObjectiveBuilder.kill(EntityType.SPIDER, 3).id("climbing_behavior").display(t("entry.spider.legacy.climbing_behavior")).build(), "samples")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .relatedItem(Items.STRING).relatedItem(Items.FISHING_ROD).relatedItem(Items.BOW)
                .text("notes", t("entry.spider.content.notes"))
                .content(outcomeNotes("silk_notes", "samples", t("entry.spider.content.silk_notes")))
                .sortOrder(2).build();
        var cow = CollectionEntryBuilder.create(COW).category("living").displayName(t("entry.cow.name")).entity(EntityType.COW)
                .description(t("entry.cow.description"))
                .discover(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("first_contact").display(t("entry.cow.discovery")))
                .outcome("dairy", t("entry.cow.outcome.dairy"))
                .outcomeReward("dairy", "cow_dairy_bucket", new ItemReward(Items.BUCKET, 1))
                .relatedItem(Items.MILK_BUCKET).relatedItem(Items.WHEAT).relatedItem(Items.LEATHER)
                .text("notes", t("entry.cow.content.notes"))
                .content(outcomeNotes("dairy_notes", "dairy", t("entry.cow.content.dairy_notes")))
                .sortOrder(3).build();
        var iron = CollectionEntryBuilder.create(IRON).category("materials").displayName(t("entry.iron.name")).item(Items.IRON_INGOT)
                .description(t("entry.iron.description"))
                .discover(ObjectiveBuilder.collect(Items.IRON_INGOT, 1).id("first_sample").display(t("entry.iron.discovery")))
                .outcome("preparation", t("entry.iron.outcome.preparation"))
                .migrateResearchStep(ObjectiveBuilder.craft(Items.IRON_INGOT, 2).id("nugget_refining").display(t("entry.iron.legacy.nugget_refining")).build(), "preparation")
                .relatedItem(Items.IRON_PICKAXE).relatedItem(Items.BUCKET).relatedItem(Items.RAW_IRON)
                .text("notes", t("entry.iron.content.notes"))
                .image("material_notes", id("textures/gui/collection/mineral_notes.png"), 240, 120, t("entry.iron.caption.material_notes"))
                .content(outcomeNotes("tool_notes", "preparation", t("entry.iron.content.tool_notes")))
                .sortOrder(4).build();
        var coal = CollectionEntryBuilder.create(COAL).category("materials").displayName(t("entry.coal.name")).item(Items.COAL)
                .description(t("entry.coal.description"))
                .discover(ObjectiveBuilder.collect(Items.COAL, 1).id("first_sample").display(t("entry.coal.discovery")))
                .outcome("fuel_samples", t("entry.coal.outcome.fuel_samples"))
                .migrateResearchStep(ObjectiveBuilder.collect(Items.COAL, 5).id("fuel_samples").display(t("entry.coal.legacy.fuel_samples")).build(), "fuel_samples")
                .relatedItem(Items.TORCH).relatedItem(Items.FURNACE).relatedItem(Items.CHARCOAL)
                .text("notes", t("entry.coal.content.notes"))
                .content(outcomeNotes("fuel_notes", "fuel_samples", t("entry.coal.content.fuel_notes")))
                .sortOrder(5).build();
        var logs = CollectionEntryBuilder.create(LOGS).category("materials").displayName(t("entry.logs.name")).itemTag(LOG_TAG)
                .description(t("entry.logs.description"))
                .discover(ObjectiveBuilder.collectTag(LOG_TAG, 1).id("first_sample").display(t("entry.logs.discovery")))
                .outcome("wood_samples", t("entry.logs.outcome.wood_samples"))
                .migrateResearchStep(ObjectiveBuilder.collectTag(LOG_TAG, 8).id("wood_samples").display(t("entry.logs.legacy.wood_samples")).build(), "wood_samples")
                .relatedItem(Items.OAK_PLANKS).relatedItem(Items.CRAFTING_TABLE).relatedItem(Items.STICK)
                .text("notes", t("entry.logs.content.notes"))
                .content(outcomeNotes("wood_notes", "wood_samples", t("entry.logs.content.wood_notes")))
                .sortOrder(6).build();
        var bone = CollectionEntryBuilder.create(BONE).category("materials").displayName(t("entry.bone.name")).item(Items.BONE)
                .description(t("entry.bone.description"))
                .discover(ObjectiveBuilder.collect(Items.BONE, 1).id("first_sample").display(t("entry.bone.discovery")))
                .outcome("cultivation", t("entry.bone.outcome.cultivation"))
                .relatedItem(Items.BONE_MEAL).relatedItem(Items.WHITE_DYE)
                .text("notes", t("entry.bone.content.notes"))
                .content(outcomeNotes("cultivation_notes", "cultivation", t("entry.bone.content.cultivation_notes")))
                .sortOrder(7).build();
        var flesh = CollectionEntryBuilder.create(FLESH).category("materials").displayName(t("entry.flesh.name")).item(Items.ROTTEN_FLESH)
                .discover(ObjectiveBuilder.collect(Items.ROTTEN_FLESH, 1).id("first_sample").display(t("entry.flesh.discovery")))
                .text("notes", t("entry.flesh.content.notes"))
                .sortOrder(8).build();
        var string = CollectionEntryBuilder.create(STRING).category("materials").displayName(t("entry.string.name")).item(Items.STRING)
                .description(t("entry.string.description"))
                .discover(ObjectiveBuilder.collect(Items.STRING, 1).id("first_sample").display(t("entry.string.discovery")))
                .relatedItem(Items.FISHING_ROD).relatedItem(Items.BOW)
                .text("notes", t("entry.string.content.notes"))
                .sortOrder(9).build();
        var wheat = CollectionEntryBuilder.create(WHEAT).category("materials").displayName(t("entry.wheat.name")).item(Items.WHEAT)
                .description(t("entry.wheat.description"))
                .discover(ObjectiveBuilder.collect(Items.WHEAT, 1).id("first_sample").display(t("entry.wheat.discovery")))
                .relatedItem(Items.BREAD).relatedItem(Items.WHEAT_SEEDS)
                .text("notes", t("entry.wheat.content.notes"))
                .sortOrder(10).build();
        var torch = CollectionEntryBuilder.create(TORCH).category("equipment").displayName(t("entry.torch.name")).item(Items.TORCH)
                .description(t("entry.torch.description"))
                .discover(ObjectiveBuilder.collect(Items.TORCH, 1).id("first_sample").display(t("entry.torch.discovery")))
                .relatedItem(Items.COAL).relatedItem(Items.CHARCOAL).relatedItem(Items.STICK)
                .text("notes", t("entry.torch.content.notes"))
                .sortOrder(11).build();
        var workbench = CollectionEntryBuilder.create(WORKBENCH).category("equipment").displayName(t("entry.workbench.name")).item(Items.CRAFTING_TABLE)
                .description(t("entry.workbench.description"))
                .discover(ObjectiveBuilder.collect(Items.CRAFTING_TABLE, 1).id("first_sample").display(t("entry.workbench.discovery")))
                .relatedItem(Items.OAK_PLANKS).relatedItem(Items.CHEST)
                .text("notes", t("entry.workbench.content.notes"))
                .sortOrder(12).build();
        return List.of(zombie, skeleton, spider, cow, iron, coal, logs, bone, flesh, string, wheat, torch, workbench);
    }

    public static QuestDefinition field(List<CollectionEntryDefinition> entries) {
        entries = entriesFor(entries, ZOMBIE, SKELETON, SPIDER, COW, IRON, COAL, LOGS, BONE);
        return base(FIELD, t("quest.field_compendium_demo.title"), t("quest.field_compendium_demo.description"), entries)
                .collectionConfig(fieldMilestones(entries)).sortOrder(8990)
                .phase(PhaseBuilder.create("survey").displayName(t("phase.field_compendium_demo.survey.name"))
                        .description(t("phase.field_compendium_demo.survey.description"))
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("zombie_defeats").display(t("objective.field_compendium_demo.survey.zombie_defeats")))
                        .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2).id("zombie_samples").display(t("objective.field_compendium_demo.survey.zombie_samples")))
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 2).id("skeleton_defeats").display(t("objective.field_compendium_demo.survey.skeleton_defeats")))
                        .objective(ObjectiveBuilder.offer(Items.ARROW, 2).id("arrow_samples").display(t("objective.field_compendium_demo.survey.arrow_samples")))
                        .objective(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("spider_defeat").display(t("objective.field_compendium_demo.survey.spider_defeat")))
                        .objective(ObjectiveBuilder.offer(Items.STRING, 2).id("spider_samples").display(t("objective.field_compendium_demo.survey.spider_samples")))
                        .objective(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("cow_contact").display(t("objective.field_compendium_demo.survey.cow_contact")))
                        .objective(ObjectiveBuilder.offer(Items.MILK_BUCKET, 1).id("milk_sample").display(t("objective.field_compendium_demo.survey.milk_sample")))
                        .objective(ObjectiveBuilder.craft(Items.IRON_PICKAXE, 1).id("iron_crafting").display(t("objective.field_compendium_demo.survey.iron_crafting")))
                        .objective(ObjectiveBuilder.offer(Items.COAL, 5).id("coal_samples").display(t("objective.field_compendium_demo.survey.coal_samples")))
                        .objective(ObjectiveBuilder.craft(Items.TORCH, 4).id("lighting_crafting").display(t("objective.field_compendium_demo.survey.lighting_crafting")))
                        .objective(ObjectiveBuilder.offerTag(LOG_TAG, 8).id("log_samples").display(t("objective.field_compendium_demo.survey.log_samples")))
                        .objective(ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1).id("bench_crafting").display(t("objective.field_compendium_demo.survey.bench_crafting")))
                        .objective(ObjectiveBuilder.craft(Items.BONE_MEAL, 3).id("bone_processing").display(t("objective.field_compendium_demo.survey.bone_processing")))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("zombie", ZOMBIE).objectives("zombie_defeats", "zombie_samples")
                                        .recordOutcome("anatomy").reward("zombie_investigation", new ItemReward(Items.EMERALD, 1)))
                                .binding(EntryRequirementBuilder.create("skeleton", SKELETON).objectives("skeleton_defeats", "arrow_samples").recordOutcome("combat"))
                                .binding(EntryRequirementBuilder.create("spider", SPIDER).objectives("spider_defeat", "spider_samples").recordOutcome("samples"))
                                .binding(EntryRequirementBuilder.create("cow", COW).objectives("cow_contact", "milk_sample").recordOutcome("dairy"))
                                .binding(EntryRequirementBuilder.create("iron", IRON).objective("iron_crafting").recordOutcome("preparation"))
                                .binding(EntryRequirementBuilder.create("coal", COAL).objectives("coal_samples", "lighting_crafting").recordOutcome("fuel_samples"))
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objectives("log_samples", "bench_crafting").recordOutcome("wood_samples")
                                        .reward("logs_investigation", EntryRewardGrantMode.AUTO, new ItemReward(Items.STICK, 2)))
                                .binding(EntryRequirementBuilder.create("bone", BONE).objective("bone_processing").recordOutcome("cultivation")))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.EMERALD, 2)).build();
    }

    public static QuestDefinition renewable(List<CollectionEntryDefinition> entries) {
        entries = entriesFor(entries, LOGS, COAL, IRON, BONE, STRING, WHEAT);
        return base(RENEWABLE, t("quest.renewable_survey_demo.title"), t("quest.renewable_survey_demo.description"), entries)
                .collectionConfig(config(entries).repeatCooldownTicks(1200).build()).sortOrder(8991).repeatable()
                .phase(PhaseBuilder.create("round").displayName(t("phase.renewable_survey_demo.round.name"))
                        .description(t("phase.renewable_survey_demo.round.description"))
                        .objective(ObjectiveBuilder.offerTag(LOG_TAG, 8).id("log_action").display(t("objective.renewable_survey_demo.round.log_action")))
                        .objective(ObjectiveBuilder.offer(Items.COAL, 4).id("coal_action").display(t("objective.renewable_survey_demo.round.coal_action")))
                        .objective(ObjectiveBuilder.offer(Items.IRON_INGOT, 1).id("iron_action").display(t("objective.renewable_survey_demo.round.iron_action")))
                        .objective(ObjectiveBuilder.offer(Items.BONE, 3).id("bone_action").display(t("objective.renewable_survey_demo.round.bone_action")))
                        .objective(ObjectiveBuilder.offer(Items.STRING, 3).id("string_action").display(t("objective.renewable_survey_demo.round.string_action")))
                        .objective(ObjectiveBuilder.offer(Items.WHEAT, 8).id("wheat_action").display(t("objective.renewable_survey_demo.round.wheat_action")))
                        .collectionSheet(CollectionSheetBuilder.create().quota(3)
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objective("log_action"))
                                .binding(EntryRequirementBuilder.create("coal", COAL).objective("coal_action"))
                                .binding(EntryRequirementBuilder.create("iron", IRON).objective("iron_action"))
                                .binding(EntryRequirementBuilder.create("bone", BONE).objective("bone_action"))
                                .binding(EntryRequirementBuilder.create("string", STRING).objective("string_action"))
                                .binding(EntryRequirementBuilder.create("wheat", WHEAT).objective("wheat_action")))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.EMERALD, 2)).build();
    }

    public static QuestDefinition parallel(List<CollectionEntryDefinition> entries) {
        entries = entriesFor(entries, ZOMBIE, SKELETON, COW, IRON, LOGS, TORCH, WORKBENCH);
        ICondition both = ICondition.phaseCompleteCurrentRun(PARALLEL.toString(), "wildlife")
                .and(ICondition.phaseCompleteCurrentRun(PARALLEL.toString(), "materials"));
        return base(PARALLEL, t("quest.parallel_expedition_demo.title"), t("quest.parallel_expedition_demo.description"), entries)
                .sortOrder(8992)
                .phase(PhaseBuilder.create("preparation").displayName(t("phase.parallel_expedition_demo.preparation.name"))
                        .description(t("phase.parallel_expedition_demo.preparation.description"))
                        .objective(ObjectiveBuilder.possess(Items.CRAFTING_TABLE, 1).id("prepare_bench").display(t("objective.parallel_expedition_demo.preparation.prepare_bench")))
                        .objective(ObjectiveBuilder.possess(Items.TORCH, 8).id("prepare_lighting").display(t("objective.parallel_expedition_demo.preparation.prepare_lighting")))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("workbench", WORKBENCH).objective("prepare_bench"))
                                .binding(EntryRequirementBuilder.create("torch", TORCH).objective("prepare_lighting")))
                        .thenGoTo("wildlife").thenGoTo("materials"))
                .phase(PhaseBuilder.create("wildlife").displayName(t("phase.parallel_expedition_demo.wildlife.name"))
                        .description(t("phase.parallel_expedition_demo.wildlife.description"))
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("zombie_action").display(t("objective.parallel_expedition_demo.wildlife.zombie_action")))
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 1).id("skeleton_action").display(t("objective.parallel_expedition_demo.wildlife.skeleton_action")))
                        .objective(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("cow_action").display(t("objective.parallel_expedition_demo.wildlife.cow_action")))
                        .collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("zombie", ZOMBIE).objective("zombie_action"))
                                .binding(EntryRequirementBuilder.create("skeleton", SKELETON).objective("skeleton_action"))
                                .binding(EntryRequirementBuilder.create("cow", COW).objective("cow_action")))
                        .thenGoTo("report"))
                .phase(PhaseBuilder.create("materials").displayName(t("phase.parallel_expedition_demo.materials.name"))
                        .description(t("phase.parallel_expedition_demo.materials.description"))
                        .objective(ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1).id("bench_action").display(t("objective.parallel_expedition_demo.materials.bench_action")))
                        .objective(ObjectiveBuilder.craft(Items.TORCH, 4).id("torch_action").display(t("objective.parallel_expedition_demo.materials.torch_action")))
                        .objective(ObjectiveBuilder.craft(Items.IRON_PICKAXE, 1).id("iron_action").display(t("objective.parallel_expedition_demo.materials.iron_action")))
                        .collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("workbench", WORKBENCH).objective("bench_action"))
                                .binding(EntryRequirementBuilder.create("torch", TORCH).objective("torch_action"))
                                .binding(EntryRequirementBuilder.create("iron", IRON).objective("iron_action")))
                        .thenGoTo("report"))
                .phase(PhaseBuilder.create("report").displayName(t("phase.parallel_expedition_demo.report.name")).enterWhen(both)
                        .description(t("phase.parallel_expedition_demo.report.description"))
                        .objective(ObjectiveBuilder.offerTag(PLANK_TAG, 16).id("submit_planks").display(t("objective.parallel_expedition_demo.report.submit_planks")))
                        .objective(ObjectiveBuilder.offer(Items.TORCH, 8).id("submit_torches").display(t("objective.parallel_expedition_demo.report.submit_torches")))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objective("submit_planks"))
                                .binding(EntryRequirementBuilder.create("torch", TORCH).objective("submit_torches")))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.DIAMOND, 1)).build();
    }

    private static CollectionQuestConfig fieldMilestones(List<CollectionEntryDefinition> entries) {
        var livingReward = new CollectionRewardNode("field_living_complete", RewardScope.CATEGORY, EntryRewardGrantMode.MANUAL,
                List.of(new ItemReward(Items.EMERALD, 1)), List.of(new AllEntriesCompleteRule()), "living");
        var config = CollectionQuestConfigBuilder.create()
                .category(new CollectionCategoryDefinition("living", t("category.living"), null, 0,
                        List.of(), List.of(livingReward), List.of()))
                .category("materials", t("category.materials"))
                .reward(new CollectionRewardNode("field_three_samples", RewardScope.QUEST, EntryRewardGrantMode.AUTO,
                        List.of(new ItemReward(Items.COAL, 1)), List.of(new CompletedEntryCountRule(3)), FIELD.toString()));
        entries.forEach(config::entry);
        return config.build();
    }

    private static CollectionQuestConfigBuilder config(List<CollectionEntryDefinition> entries) {
        var config = CollectionQuestConfigBuilder.create();
        var categories = entries.stream().map(CollectionEntryDefinition::getCategoryId).collect(java.util.stream.Collectors.toSet());
        if (categories.contains("living")) config.category("living", t("category.living"));
        if (categories.contains("materials")) config.category("materials", t("category.materials"));
        if (categories.contains("equipment")) config.category("equipment", t("category.equipment"));
        entries.forEach(config::entry);
        return config;
    }

    private static List<CollectionEntryDefinition> entriesFor(List<CollectionEntryDefinition> entries, ResourceLocation... ids) {
        var selected = java.util.Set.of(ids);
        var result = entries.stream().filter(entry -> selected.contains(entry.getEntryId())).toList();
        if (result.size() != selected.size()) throw new IllegalArgumentException("Missing collection demo entries: " + selected);
        return result;
    }

    private static CollectionContentBlock outcomeNotes(String blockId, String outcomeId, QuestText notes) {
        return new CollectionContentBlock(blockId, notes, null, QuestText.literal(""),
                CollectionMediaFit.CONTAIN, false, CollectionContentReveal.OUTCOME, outcomeId);
    }

    private static QuestBuilder base(ResourceLocation id, QuestText title, QuestText description, List<CollectionEntryDefinition> entries) {
        return QuestBuilder.create(id).category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName(title).description(description).themeColor(0x85C6AE).collectionConfig(config(entries).build());
    }
    private static QuestText t(String suffix) { return QuestText.translatable("arc_quest.collection.demo." + suffix); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, path); }
}
