package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.List;

/** Three playable sheets sharing permanent knowledge while keeping their run objectives separate. */
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
    private CollectionFieldDemos() {}

    public static void registerAll() {
        List<CollectionEntryDefinition> entries = entries();
        entries.forEach(CollectionEntryRegistry::register);
        ArcQuestAPI.registerQuest(field(entries));
        ArcQuestAPI.registerQuest(renewable(entries));
        ArcQuestAPI.registerQuest(parallel(entries));
    }

    public static List<CollectionEntryDefinition> entries() {
        var zombie = CollectionEntryBuilder.create(ZOMBIE).category("living").displayName("僵尸").entity(EntityType.ZOMBIE)
                .description("夜间活动的敌对生物。发现记录与这份委托的击败、提交目标分别保存。")
                .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_defeat"))
                .research(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5).id("anatomy").display("累计击败僵尸"))
                .relatedItem(Items.ROTTEN_FLESH)
                .text("field_notes", "击败僵尸可以获得腐肉。目录头像是二维图像；物品资料支持 JEI 查询。")
                .image("habitat", id("textures/gui/collection/field_notes.png"), 240, 120, "林地调查：保持距离，留意夜间活动。")
                .content(new CollectionContentBlock("anatomy_notes", QuestText.literal("研究记录：已经完成五次观察性击败，可以对照样本记录。"),
                        null, QuestText.literal(""), CollectionMediaFit.CONTAIN, false, CollectionContentReveal.RESEARCH_STEP, "anatomy"))
                .build();
        var skeleton = CollectionEntryBuilder.create(SKELETON).category("living").displayName("骷髅").entity(EntityType.SKELETON)
                .description("携带弓箭的敌对生物。留意掩体与距离。")
                .discover(ObjectiveBuilder.kill(EntityType.SKELETON, 1).id("first_defeat"))
                .research(ObjectiveBuilder.kill(EntityType.SKELETON, 3).id("ranged_behavior").display("累计击败骷髅"))
                .relatedItem(Items.BONE).relatedItem(Items.ARROW).text("notes", "骨头可以制作骨粉；箭可以用作远程武器的弹药。")
                .image("habitat", id("textures/gui/collection/field_notes.png"), 240, 120, "利用树木与地形作为掩体。")
                .sortOrder(1).build();
        var spider = CollectionEntryBuilder.create(SPIDER).category("living").displayName("蜘蛛").entity(EntityType.SPIDER)
                .description("能够攀爬方块的生物。发现前只显示未解锁线索。")
                .discover(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("first_defeat"))
                .research(ObjectiveBuilder.kill(EntityType.SPIDER, 3).id("climbing_behavior"))
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .relatedItem(Items.STRING).text("notes", "线是制作弓、钓鱼竿等工具的材料。无适配头像时保留文字，不使用实体模型。")
                .sortOrder(2).build();
        var cow = CollectionEntryBuilder.create(COW).category("living").displayName("牛").entity(EntityType.COW)
                .description("温和的草原生物。对牛进行一次右键互动即可留下发现记录。")
                .discover(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("first_contact").display("与牛互动"))
                .relatedItem(Items.LEATHER).relatedItem(Items.MILK_BUCKET).text("notes", "使用空桶可以取得牛奶。这个条目没有额外长期研究要求。")
                .sortOrder(3).build();
        var iron = CollectionEntryBuilder.create(IRON).category("materials").displayName("铁锭").item(Items.IRON_INGOT)
                .description("常用金属材料。拾取建立发现记录；合成研究只使用真实合成事件。")
                .discover(ObjectiveBuilder.collect(Items.IRON_INGOT, 1).id("first_sample"))
                .research(ObjectiveBuilder.craft(Items.IRON_INGOT, 2).id("nugget_refining").display("用铁粒合成铁锭"))
                .relatedItem(Items.IRON_NUGGET).relatedItem(Items.IRON_PICKAXE)
                .image("material_notes", id("textures/gui/collection/mineral_notes.png"), 240, 120, "冶炼与合成是不同动作；此范例检测铁粒合成铁锭。")
                .sortOrder(4).build();
        var coal = CollectionEntryBuilder.create(COAL).category("materials").displayName("煤炭").item(Items.COAL)
                .description("常见燃料，也可以制作火把。")
                .discover(ObjectiveBuilder.collect(Items.COAL, 1).id("first_sample"))
                .research(ObjectiveBuilder.collect(Items.COAL, 5).id("fuel_samples").display("累计获得煤炭样本"))
                .relatedItem(Items.TORCH).text("notes", "长期样本计数不因放弃或重新接取任务而清空。")
                .sortOrder(5).build();
        var logs = CollectionEntryBuilder.create(LOGS).category("materials").displayName("原木").itemTag(ResourceLocation.parse("minecraft:logs"))
                .description("任意 minecraft:logs 成员都可作为样本，图标按照实际 Tag 候选轮换。")
                .discover(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 1).id("first_sample"))
                .research(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 8).id("wood_samples").display("累计获得原木样本"))
                .relatedItem(Items.OAK_PLANKS).relatedItem(Items.CRAFTING_TABLE).text("notes", "任务提交会实际消耗原木。不同木种都计入同一个原木条目，不伪装成多个物种。")
                .sortOrder(6).build();
        var bone = CollectionEntryBuilder.create(BONE).category("materials").displayName("骨头").item(Items.BONE)
                .discover(ObjectiveBuilder.collect(Items.BONE, 1).id("first_sample"))
                .relatedItem(Items.BONE_MEAL).text("notes", "骨头可以制成骨粉。鼠标左键查看配方，右键查看用途。")
                .sortOrder(7).build();
        var flesh = CollectionEntryBuilder.create(FLESH).category("materials").displayName("腐肉").item(Items.ROTTEN_FLESH)
                .discover(ObjectiveBuilder.collect(Items.ROTTEN_FLESH, 1).id("first_sample"))
                .text("notes", "腐肉既可以作为调查样本，也可以由任务提交目标消耗。")
                .sortOrder(8).build();
        return List.of(zombie, skeleton, spider, cow, iron, coal, logs, bone, flesh);
    }

    public static QuestDefinition field(List<CollectionEntryDefinition> entries) {
        return base(FIELD, "荒野手册", "调查常见生物与材料。旧发现可以认可，本次击败、制作和提交要求需要实际完成。", entries)
                .sortOrder(8990)
                .phase(PhaseBuilder.create("survey").displayName("林地调查")
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("zombie_defeats"))
                        .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2).id("zombie_samples"))
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 2).id("skeleton_defeats"))
                        .objective(ObjectiveBuilder.craft(Items.IRON_INGOT, 1).id("iron_crafting").display("用铁粒合成铁锭"))
                        .objective(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 8).id("log_samples"))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("zombie", ZOMBIE).discovered().objectives("zombie_defeats", "zombie_samples"))
                                .binding(EntryRequirementBuilder.create("skeleton", SKELETON).discovered().objective("skeleton_defeats"))
                                .binding(EntryRequirementBuilder.create("spider", SPIDER).discovered())
                                .binding(EntryRequirementBuilder.create("cow", COW).discovered())
                                .binding(EntryRequirementBuilder.create("iron", IRON).discovered().objective("iron_crafting"))
                                .binding(EntryRequirementBuilder.create("coal", COAL).researchStep("fuel_samples"))
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objective("log_samples"))
                                .binding(EntryRequirementBuilder.create("bone", BONE).discovered()))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.EMERALD, 2)).build();
    }

    public static QuestDefinition renewable(List<CollectionEntryDefinition> entries) {
        return base(RENEWABLE, "轮值调查委托", "六个候选中任意完成三个。本轮行动重新计数，永久图鉴不会清空。", entries)
                .sortOrder(8991).repeatable()
                .phase(PhaseBuilder.create("round").displayName("本轮调查")
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 2).id("zombie_action"))
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 2).id("skeleton_action"))
                        .objective(ObjectiveBuilder.kill(EntityType.SPIDER, 1).id("spider_action"))
                        .objective(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 8).id("log_action"))
                        .objective(ObjectiveBuilder.collect(Items.COAL, 4).id("coal_action"))
                        .objective(ObjectiveBuilder.craft(Items.IRON_INGOT, 2).id("iron_action").display("用铁粒合成铁锭"))
                        .collectionSheet(CollectionSheetBuilder.create().quota(3)
                                .binding(EntryRequirementBuilder.create("zombie", ZOMBIE).objective("zombie_action"))
                                .binding(EntryRequirementBuilder.create("skeleton", SKELETON).objective("skeleton_action"))
                                .binding(EntryRequirementBuilder.create("spider", SPIDER).objective("spider_action"))
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objective("log_action"))
                                .binding(EntryRequirementBuilder.create("coal", COAL).objective("coal_action"))
                                .binding(EntryRequirementBuilder.create("iron", IRON).objective("iron_action")))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.EMERALD, 1)).build();
    }

    public static QuestDefinition parallel(List<CollectionEntryDefinition> entries) {
        ICondition both = ICondition.flagSet("collection_demo_wildlife_done").and(ICondition.flagSet("collection_demo_materials_done"));
        return base(PARALLEL, "营地联合调查", "准备燃料后同时开展生物与材料调查。两条调查都结束后提交营地样本。", entries)
                .sortOrder(8992)
                .phase(PhaseBuilder.create("preparation").displayName("调查准备")
                        .objective(ObjectiveBuilder.collect(Items.COAL, 1).id("prepare_fuel"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("fuel", COAL).objective("prepare_fuel")))
                        .thenGoTo("wildlife", "materials"))
                .phase(PhaseBuilder.create("wildlife").displayName("生物调查")
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 2).id("zombie_action"))
                        .objective(ObjectiveBuilder.kill(EntityType.SKELETON, 2).id("skeleton_action"))
                        .objective(ObjectiveBuilder.interact(ResourceLocation.parse("minecraft:cow")).id("cow_action"))
                        .collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("zombie", ZOMBIE).objective("zombie_action"))
                                .binding(EntryRequirementBuilder.create("skeleton", SKELETON).objective("skeleton_action"))
                                .binding(EntryRequirementBuilder.create("cow", COW).objective("cow_action")))
                        .setFlagOnComplete("collection_demo_wildlife_done").thenGoTo("report"))
                .phase(PhaseBuilder.create("materials").displayName("材料调查")
                        .objective(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 8).id("logs_action"))
                        .objective(ObjectiveBuilder.collect(Items.COAL, 4).id("coal_action"))
                        .objective(ObjectiveBuilder.craft(Items.IRON_INGOT, 1).id("iron_action").display("用铁粒合成铁锭"))
                        .collectionSheet(CollectionSheetBuilder.create().quota(2)
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objective("logs_action"))
                                .binding(EntryRequirementBuilder.create("coal", COAL).objective("coal_action"))
                                .binding(EntryRequirementBuilder.create("iron", IRON).objective("iron_action")))
                        .setFlagOnComplete("collection_demo_materials_done").thenGoTo("report"))
                .phase(PhaseBuilder.create("report").displayName("营地回报").enterWhen(both)
                        .objective(ObjectiveBuilder.offerTag(ResourceLocation.parse("minecraft:logs"), 4).id("submit_logs"))
                        .objective(ObjectiveBuilder.offer(Items.IRON_INGOT, 1).id("submit_iron"))
                        .collectionSheet(CollectionSheetBuilder.create()
                                .binding(EntryRequirementBuilder.create("logs", LOGS).objective("submit_logs"))
                                .binding(EntryRequirementBuilder.create("iron", IRON).objective("submit_iron")))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.DIAMOND, 1)).build();
    }

    private static QuestBuilder base(ResourceLocation id, String title, String description, List<CollectionEntryDefinition> entries) {
        var config = CollectionQuestConfigBuilder.create().category("living", "生物").category("materials", "材料");
        entries.forEach(config::entry);
        return QuestBuilder.create(id).category(QuestCategory.ADVENTURE).mode(QuestMode.COLLECTION)
                .displayName(title).description(description).themeColor(0x2C8A67).collectionConfig(config.build());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, path); }
}
