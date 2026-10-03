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
        var zombie = CollectionEntryBuilder.create(ZOMBIE).category("living").displayName("僵尸").entity(EntityType.ZOMBIE)
                .description("夜间与阴暗处的常见威胁。把战斗经过与腐肉样本放在同一份记录中。")
                .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_defeat").display("首次亲手击败 1 只僵尸"))
                .outcome("anatomy", "腐肉与行为对照")
                .migrateResearchStep(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5).id("anatomy").build(), "anatomy")
                .discoveryReward("zombie_first_record", new ItemReward(Items.COAL, 1))
                .outcomeReward("anatomy", "zombie_anatomy", new ItemReward(Items.IRON_NUGGET, 3))
                .relatedItem(Items.ROTTEN_FLESH).relatedItem(Items.SHIELD)
                .text("field_notes", "白天会在阳光下燃烧，但水中或遮蔽处仍需小心。保持退路，别让多只僵尸把你围在角落。")
                .image("habitat", id("textures/gui/collection/field_notes.png"), 240, 120, "林缘夜巡：利用开阔地观察，避免在树下失去视线。")
                .content(outcomeNotes("anatomy_notes", "anatomy", "腐肉样本已经归档。腐肉可用于繁殖和治疗狼，也能卖给牧师村民；直接食用可能带来饥饿效果。"))
                .build();
        var skeleton = CollectionEntryBuilder.create(SKELETON).category("living").displayName("骷髅").entity(EntityType.SKELETON)
                .description("弓箭让它在远处也有威胁。带回箭样本，并记录两次交锋。")
                .discover(ObjectiveBuilder.kill(EntityType.SKELETON, 1).id("first_defeat").display("首次亲手击败 1 只骷髅"))
                .outcome("combat", "弓箭威胁记录")
                .migrateResearchStep(ObjectiveBuilder.kill(EntityType.SKELETON, 3).id("ranged_behavior").build(), "combat")
                .relatedItem(Items.BONE).relatedItem(Items.ARROW).relatedItem(Items.SHIELD)
                .text("notes", "盾牌能够挡住正面箭矢。利用树木或地形接近，切勿长时间站在没有掩体的直线上。")
                .content(outcomeNotes("combat_notes", "combat", "样本中的箭可以直接作弹药。骨头可制成骨粉，骨粉既能辅助种植，也能制作白色染料。"))
                .sortOrder(1).build();
        var spider = CollectionEntryBuilder.create(SPIDER).category("living").displayName("蜘蛛").entity(EntityType.SPIDER)
                .description("会攀爬墙面的八足生物。矮墙不能完全隔开它。")
                .publicClue("夜间寻找能攀爬墙面的八足生物；击败一只，再从调查面板交付两份线。")
                .discover(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("first_defeat").display("首次亲手击败 1 只蜘蛛"))
                .outcome("samples", "蛛丝用途记录")
                .migrateResearchStep(ObjectiveBuilder.kill(EntityType.SPIDER, 3).id("climbing_behavior").build(), "samples")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .relatedItem(Items.STRING).relatedItem(Items.FISHING_ROD).relatedItem(Items.BOW)
                .text("notes", "蜘蛛在昏暗时会主动攻击，明亮环境中的普通蜘蛛通常不会先动手。调查无需判断攀爬动作，只需击败与交付线样本。")
                .content(outcomeNotes("silk_notes", "samples", "三根线可用于制作弓，两根线可用于制作钓鱼竿；四根线还能制成白色羊毛。"))
                .sortOrder(2).build();
        var cow = CollectionEntryBuilder.create(COW).category("living").displayName("牛").entity(EntityType.COW)
                .description("草原上的牧场动物，可提供牛奶、皮革和食物。小麦可以用来吸引与繁殖它。")
                .discover(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("first_contact").display("与牛互动"))
                .outcome("dairy", "牧场补给记录")
                .outcomeReward("dairy", "cow_dairy_bucket", new ItemReward(Items.BUCKET, 1))
                .relatedItem(Items.MILK_BUCKET).relatedItem(Items.WHEAT).relatedItem(Items.LEATHER)
                .text("notes", "手持空桶对牛使用可取得牛奶；小麦可以吸引成年牛并用于繁殖。调查面板的牛奶交付会消耗整只牛奶桶。")
                .content(outcomeNotes("dairy_notes", "dairy", "牛奶可以清除玩家的状态效果，包括有益效果。牛奶桶已交付，首次档案奖励会返还一个空桶。"))
                .sortOrder(3).build();
        var iron = CollectionEntryBuilder.create(IRON).category("materials").displayName("铁锭").item(Items.IRON_INGOT)
                .description("冶炼得到的金属材料，用于制作铁镐、水桶等实用工具。")
                .discover(ObjectiveBuilder.collect(Items.IRON_INGOT, 1).id("first_sample").display("首次获得 1 块铁锭"))
                .outcome("preparation", "铁工具制备记录")
                .migrateResearchStep(ObjectiveBuilder.craft(Items.IRON_INGOT, 2).id("nugget_refining").build(), "preparation")
                .relatedItem(Items.IRON_PICKAXE).relatedItem(Items.BUCKET).relatedItem(Items.RAW_IRON)
                .text("notes", "粗铁需要冶炼成铁锭。铁镐由三块铁锭与两根木棍合成，可开采钻石矿石；调查只统计接取后的实际合成。")
                .image("material_notes", id("textures/gui/collection/mineral_notes.png"), 240, 120, "金属样本：从粗铁冶炼到铁工具。")
                .content(outcomeNotes("tool_notes", "preparation", "铁镐已完成制备，保留在你的背包中。开采钻石矿石至少需要铁级别的镐，普通石镐不能收获钻石。"))
                .sortOrder(4).build();
        var coal = CollectionEntryBuilder.create(COAL).category("materials").displayName("煤炭").item(Items.COAL)
                .description("既是熔炉燃料，也是火把材料。可从煤矿石中开采，或通过其他途径取得。")
                .discover(ObjectiveBuilder.collect(Items.COAL, 1).id("first_sample").display("首次获得 1 份煤炭"))
                .outcome("fuel_samples", "燃料与照明记录")
                .migrateResearchStep(ObjectiveBuilder.collect(Items.COAL, 5).id("fuel_samples").build(), "fuel_samples")
                .relatedItem(Items.TORCH).relatedItem(Items.FURNACE).relatedItem(Items.CHARCOAL)
                .text("notes", "一块煤炭可在熔炉中熔炼八件物品。煤炭或木炭配合一根木棍可合成四根火把；调查交付的煤炭会被消耗。")
                .content(outcomeNotes("fuel_notes", "fuel_samples", "照明已经制备完成。火把能减少适宜敌对生物生成的阴暗位置；存量燃料和本次合成是不同要求。"))
                .sortOrder(5).build();
        var logs = CollectionEntryBuilder.create(LOGS).category("materials").displayName("原木").itemTag(LOG_TAG)
                .description("木板、木棍和工作台的基础原料。原木类调查接受标签内的任意木种。")
                .discover(ObjectiveBuilder.collectTag(LOG_TAG, 1).id("first_sample").display("首次获得任意原木 1 份"))
                .outcome("wood_samples", "木材加工记录")
                .migrateResearchStep(ObjectiveBuilder.collectTag(LOG_TAG, 8).id("wood_samples").build(), "wood_samples")
                .relatedItem(Items.OAK_PLANKS).relatedItem(Items.CRAFTING_TABLE).relatedItem(Items.STICK)
                .text("notes", "原木可加工成木板。任意四块可用于配方的木板可以组成工作台；木材样本交付不要求每个木种分别集齐。")
                .content(outcomeNotes("wood_notes", "wood_samples", "木材加工完成。工作台提供完整的三乘三合成网格，制作铁镐等工具需要它；本次制作出的工作台无需交付。"))
                .sortOrder(6).build();
        var bone = CollectionEntryBuilder.create(BONE).category("materials").displayName("骨头").item(Items.BONE)
                .description("每根骨头可加工为三份骨粉，是种植、染色与驯狼的实用材料。")
                .discover(ObjectiveBuilder.collect(Items.BONE, 1).id("first_sample").display("首次获得 1 根骨头"))
                .outcome("cultivation", "骨粉用途记录")
                .relatedItem(Items.BONE_MEAL).relatedItem(Items.WHITE_DYE)
                .text("notes", "骷髅是常见骨头来源。每根骨头可合成三份骨粉；骨粉对不同植物的效果不同，并非所有作物都能使用。")
                .content(outcomeNotes("cultivation_notes", "cultivation", "骨粉制作完成。可用于小麦等作物催熟；也能对草地使用，生成草与花。"))
                .sortOrder(7).build();
        var flesh = CollectionEntryBuilder.create(FLESH).category("materials").displayName("腐肉").item(Items.ROTTEN_FLESH)
                .discover(ObjectiveBuilder.collect(Items.ROTTEN_FLESH, 1).id("first_sample").display("首次获得 1 份腐肉"))
                .text("notes", "腐肉适合作为僵尸调查样本。饲喂狼不会触发玩家食用腐肉时的饥饿效果。")
                .sortOrder(8).build();
        var string = CollectionEntryBuilder.create(STRING).category("materials").displayName("线").item(Items.STRING)
                .description("为营地工具制作准备的柔韧材料。")
                .discover(ObjectiveBuilder.collect(Items.STRING, 1).id("first_sample").display("首次获得 1 份线"))
                .relatedItem(Items.FISHING_ROD).relatedItem(Items.BOW)
                .text("notes", "蜘蛛掉落与蜘蛛网都是常见线来源。补给委托接受已有库存，但每次交付都会实际消耗材料。")
                .sortOrder(9).build();
        var wheat = CollectionEntryBuilder.create(WHEAT).category("materials").displayName("小麦").item(Items.WHEAT)
                .description("营地的粮食储备，也能用于牛羊繁殖。")
                .discover(ObjectiveBuilder.collect(Items.WHEAT, 1).id("first_sample").display("首次获得 1 份小麦"))
                .relatedItem(Items.BREAD).relatedItem(Items.WHEAT_SEEDS)
                .text("notes", "成熟小麦可以收获小麦与种子。三份小麦可合成一个面包；补给交付要求的是小麦本身。")
                .sortOrder(10).build();
        var torch = CollectionEntryBuilder.create(TORCH).category("equipment").displayName("火把").item(Items.TORCH)
                .description("进入野外前备齐照明，调查期间再制备一批。")
                .discover(ObjectiveBuilder.collect(Items.TORCH, 1).id("first_sample").display("首次获得 1 根火把"))
                .relatedItem(Items.COAL).relatedItem(Items.CHARCOAL).relatedItem(Items.STICK)
                .text("notes", "准备阶段只检查背包中当前是否持有，不消耗火把。材料调查要求新的制作，最终建站才会交付八根火把。")
                .sortOrder(11).build();
        var workbench = CollectionEntryBuilder.create(WORKBENCH).category("equipment").displayName("工作台").item(Items.CRAFTING_TABLE)
                .description("一张便携工作台，供调查途中制作工具。")
                .discover(ObjectiveBuilder.collect(Items.CRAFTING_TABLE, 1).id("first_sample").display("首次获得 1 张工作台"))
                .relatedItem(Items.OAK_PLANKS).relatedItem(Items.CHEST)
                .text("notes", "准备阶段要求工作台在背包里，放在地上的工作台不计入当前持有。已有装备可以用于准备，进入调查后制作要求从零开始。")
                .sortOrder(12).build();
        return List.of(zombie, skeleton, spider, cow, iron, coal, logs, bone, flesh, string, wheat, torch, workbench);
    }

    public static QuestDefinition field(List<CollectionEntryDefinition> entries) {
        entries = entriesFor(entries, ZOMBIE, SKELETON, SPIDER, COW, IRON, COAL, LOGS, BONE);
        return base(FIELD, "荒野手册 · 从样本到用途", "完成八项小型调查，把夜间威胁、牧场补给和材料加工写进自己的手册。制作只计接取后的动作，样本在详情中交付。", entries)
                .collectionConfig(fieldMilestones(entries)).sortOrder(8990)
                .phase(PhaseBuilder.create("survey").displayName("野外调查")
                        .description("八项全部完成。可以先做材料与牧场调查，再在夜间完成敌对生物调查；每份深入资料只随对应调查完成而公开。")
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("zombie_defeats").display("亲手击败 3 只僵尸"))
                        .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2).id("zombie_samples").display("交付 2 份腐肉样本（消耗）"))
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 2).id("skeleton_defeats").display("亲手击败 2 只骷髅"))
                        .objective(ObjectiveBuilder.offer(Items.ARROW, 2).id("arrow_samples").display("交付 2 支箭样本（消耗）"))
                        .objective(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("spider_defeat").display("亲手击败 1 只蜘蛛"))
                        .objective(ObjectiveBuilder.offer(Items.STRING, 2).id("spider_samples").display("交付 2 份线样本（消耗）"))
                        .objective(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("cow_contact").display("在本次调查中与牛互动"))
                        .objective(ObjectiveBuilder.offer(Items.MILK_BUCKET, 1).id("milk_sample").display("交付 1 桶牛奶（消耗牛奶桶）"))
                        .objective(ObjectiveBuilder.craft(Items.IRON_PICKAXE, 1).id("iron_crafting").display("在本次调查中合成 1 把铁镐"))
                        .objective(ObjectiveBuilder.offer(Items.COAL, 5).id("coal_samples").display("交付 5 份煤炭样本（消耗）"))
                        .objective(ObjectiveBuilder.craft(Items.TORCH, 4).id("lighting_crafting").display("在本次调查中合成 4 根火把"))
                        .objective(ObjectiveBuilder.offerTag(LOG_TAG, 8).id("log_samples").display("交付任意原木共 8 份（可混用，消耗）"))
                        .objective(ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1).id("bench_crafting").display("在本次调查中合成 1 张工作台"))
                        .objective(ObjectiveBuilder.craft(Items.BONE_MEAL, 3).id("bone_processing").display("在本次调查中合成 3 份骨粉"))
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
        return base(RENEWABLE, "营地补给 · 轮值委托", "六种补给任选三种交付。所有交付都会消耗物品；交齐即结束本轮候选，确认领取 2 颗绿宝石。接取间隔为 60 秒游戏时间。", entries)
                .collectionConfig(config(entries).repeatCooldownTicks(1200).build()).sortOrder(8991).repeatable()
                .phase(PhaseBuilder.create("round").displayName("本轮补给单")
                        .description("准备你方便取得的三种补给即可。已有库存可交付，但旧调查完成记录不能替代本次交付；未选补给无需补齐。")
                        .objective(ObjectiveBuilder.offerTag(LOG_TAG, 8).id("log_action").display("交付任意原木共 8 份（可混用，消耗）"))
                        .objective(ObjectiveBuilder.offer(Items.COAL, 4).id("coal_action").display("交付 4 份煤炭（消耗）"))
                        .objective(ObjectiveBuilder.offer(Items.IRON_INGOT, 1).id("iron_action").display("交付 1 块铁锭（消耗）"))
                        .objective(ObjectiveBuilder.offer(Items.BONE, 3).id("bone_action").display("交付 3 根骨头（消耗）"))
                        .objective(ObjectiveBuilder.offer(Items.STRING, 3).id("string_action").display("交付 3 份线（消耗）"))
                        .objective(ObjectiveBuilder.offer(Items.WHEAT, 8).id("wheat_action").display("交付 8 份小麦（消耗）"))
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
        return base(PARALLEL, "营地踏勘 · 建站计划", "备齐装备后同时开展夜间威胁与器材制备。两线各任选两项，最后交付照明和建材，确认领取 1 颗钻石。", entries)
                .sortOrder(8992)
                .phase(PhaseBuilder.create("preparation").displayName("出发前检查")
                        .description("背包持有 1 张工作台和 8 根火把即可。检查不消耗装备，已有库存也可使用；地上的工作台不计入背包持有。")
                        .objective(ObjectiveBuilder.possess(Items.CRAFTING_TABLE, 1).id("prepare_bench").display("背包持有 1 张工作台（不消耗）"))
                        .objective(ObjectiveBuilder.possess(Items.TORCH, 8).id("prepare_lighting").display("背包持有 8 根火把（不消耗）"))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("workbench", WORKBENCH).objective("prepare_bench"))
                                .binding(EntryRequirementBuilder.create("torch", TORCH).objective("prepare_lighting")))
                        .thenGoTo("wildlife").thenGoTo("materials"))
                .phase(PhaseBuilder.create("wildlife").displayName("周边生物踏勘")
                        .description("僵尸、骷髅、牛三项任选两项。只记本阶段开始后的行动；这份短途踏勘不会代替手册中的深入调查。")
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("zombie_action").display("在本次踏勘中击败 1 只僵尸"))
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 1).id("skeleton_action").display("在本次踏勘中击败 1 只骷髅"))
                        .objective(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("cow_action").display("在本次踏勘中与牛互动"))
                        .collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("zombie", ZOMBIE).objective("zombie_action"))
                                .binding(EntryRequirementBuilder.create("skeleton", SKELETON).objective("skeleton_action"))
                                .binding(EntryRequirementBuilder.create("cow", COW).objective("cow_action")))
                        .thenGoTo("report"))
                .phase(PhaseBuilder.create("materials").displayName("营地器材制备")
                        .description("工作台、火把、铁镐三项任选两项。准备阶段已持有的器材不算新制备；制作完成的物品留在背包。")
                        .objective(ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1).id("bench_action").display("本阶段合成 1 张工作台"))
                        .objective(ObjectiveBuilder.craft(Items.TORCH, 4).id("torch_action").display("本阶段合成 4 根火把"))
                        .objective(ObjectiveBuilder.craft(Items.IRON_PICKAXE, 1).id("iron_action").display("本阶段合成 1 把铁镐"))
                        .collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("workbench", WORKBENCH).objective("bench_action"))
                                .binding(EntryRequirementBuilder.create("torch", TORCH).objective("torch_action"))
                                .binding(EntryRequirementBuilder.create("iron", IRON).objective("iron_action")))
                        .thenGoTo("report"))
                .phase(PhaseBuilder.create("report").displayName("照明与建材交付").enterWhen(both)
                        .description("两条调查都结束后交付建站材料。任意木板可混用，共 16 份；再交付 8 根火把，最后确认回报。")
                        .objective(ObjectiveBuilder.offerTag(PLANK_TAG, 16).id("submit_planks").display("交付任意木板共 16 份（可混用，消耗）"))
                        .objective(ObjectiveBuilder.offer(Items.TORCH, 8).id("submit_torches").display("交付 8 根火把（消耗）"))
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
                .category(new CollectionCategoryDefinition("living", QuestText.literal("野外生物"), null, 0,
                        List.of(), List.of(livingReward), List.of()))
                .category("materials", "材料与加工")
                .reward(new CollectionRewardNode("field_three_samples", RewardScope.QUEST, EntryRewardGrantMode.AUTO,
                        List.of(new ItemReward(Items.COAL, 1)), List.of(new CompletedEntryCountRule(3)), FIELD.toString()));
        entries.forEach(config::entry);
        return config.build();
    }

    private static CollectionQuestConfigBuilder config(List<CollectionEntryDefinition> entries) {
        var config = CollectionQuestConfigBuilder.create();
        var categories = entries.stream().map(CollectionEntryDefinition::getCategoryId).collect(java.util.stream.Collectors.toSet());
        if (categories.contains("living")) config.category("living", "野外生物");
        if (categories.contains("materials")) config.category("materials", "材料与加工");
        if (categories.contains("equipment")) config.category("equipment", "营地器材");
        entries.forEach(config::entry);
        return config;
    }

    private static List<CollectionEntryDefinition> entriesFor(List<CollectionEntryDefinition> entries, ResourceLocation... ids) {
        var selected = java.util.Set.of(ids);
        var result = entries.stream().filter(entry -> selected.contains(entry.getEntryId())).toList();
        if (result.size() != selected.size()) throw new IllegalArgumentException("Missing collection demo entries: " + selected);
        return result;
    }

    private static CollectionContentBlock outcomeNotes(String blockId, String outcomeId, String notes) {
        return new CollectionContentBlock(blockId, QuestText.literal(notes), null, QuestText.literal(""),
                CollectionMediaFit.CONTAIN, false, CollectionContentReveal.OUTCOME, outcomeId);
    }

    private static QuestBuilder base(ResourceLocation id, String title, String description, List<CollectionEntryDefinition> entries) {
        return QuestBuilder.create(id).category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName(title).description(description).themeColor(0x85C6AE).collectionConfig(config(entries).build());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, path); }
}
