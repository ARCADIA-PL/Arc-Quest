package org.arcadia.arc_quest.quest.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.reward.FlagReward;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.reward.VariableReward;

/**
 * 史诗级主线任务链 DEMO —— 基于 Minecraft 原版内容。
 * <p>
 * 展示如何使用 ArcQuestAPI 进行任务注册（Lib 模组标准实践）。
 */
public final class EpicMainlineDemo {

    private EpicMainlineDemo() {
    }

    public static void registerAll() {
        registerPrologue();
        registerChapter1();
        registerChapter2BranchA();
        registerChapter2BranchB();
        registerFinale();
    }

    /**
     * 序章：村庄危机
     */
    private static void registerPrologue() {
        ArcQuestAPI.registerQuest(
                QuestBuilder.create("arc_quest:epic_prologue")
                        .category(QuestCategory.ARCHON)
                        .chapterShop("arc_quest:blacksmith_shop")
                        .displayName(Component.translatable("arc_quest.quest.epic_prologue.title"))
                        .description(Component.translatable("arc_quest.quest.epic_prologue.desc"))
                        .icon(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "textures/gui/quest/prologue.png"))
                        .sortOrder(0)
                        .acquisitionSplash(
                                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "textures/gui/splash/prologue_acquire.png"),
                                1f
                        )
                        .completionSplash(
                                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "textures/gui/splash/prologue_complete.png"),
                                1f
                        )
                        .themeColor(ChatFormatting.GOLD)

                        // 阶段 1：收集木材制作武器
                        .phase(PhaseBuilder.create("arc_quest:gather_wood")
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.gather_wood"))
                                .setFlagOnEnter("arc_quest:prologue_started")
                                .objective(ObjectiveBuilder.offerTag(ItemTags.LOGS.location(), 5)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.gather_wood.0")))
                                .objective(ObjectiveBuilder.offerTag(ItemTags.LOGS.location(), 5)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.gather_wood.0")))
                                .objective(ObjectiveBuilder.offerTag(ItemTags.LOGS.location(), 5)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.gather_wood.0")))
                                .thenGoTo("arc_quest:talk_villager"))

                        // 阶段 2：与村民对话 -> 同时开启两条并行支线
                        .phase(PhaseBuilder.create("arc_quest:talk_villager")
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.talk_villager"))
                                .objective(ObjectiveBuilder.interact(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "village_elder"))
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.talk_villager.0")))
                                .setFlagOnComplete("arc_quest:elder_consulted")
                                .thenGoTo("arc_quest:scout_forest")
                                .thenGoTo("arc_quest:reinforce_gate")
                                .thenGoTo("arc_quest:reinforce_gate2"))

                        // 并行支线A：侦查森林
                        .phase(PhaseBuilder.create("arc_quest:scout_forest")
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.scout_forest"))
                                .intelScene("arc_quest:epic_prologue", "arc_quest:defend_village")
                                .objective(ObjectiveBuilder.collect(Items.STRING, 6)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.scout_forest.0")))
                                .setFlagOnComplete("arc_quest:forest_scouted")
                                // 手动转入汇合阶段（D）
                                .thenGoTo("arc_quest:craft_sword"))

                        // 并行支线B：加固村门
                        .phase(PhaseBuilder.create("arc_quest:reinforce_gate")
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.reinforce_gate"))
                                .objective(ObjectiveBuilder.offerTag(ItemTags.PLANKS.location(), 16)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.reinforce_gate.0")))
                                .objective(ObjectiveBuilder.offerTag(ItemTags.PLANKS.location(), 16)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.reinforce_gate.0")))
                                .objective(ObjectiveBuilder.offerTag(ItemTags.PLANKS.location(), 16)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.reinforce_gate.0")))
                                .setFlagOnComplete("arc_quest:gate_reinforced")
                                // 手动转入汇合阶段（D）
                                .thenGoTo("arc_quest:craft_sword"))

                        .phase(PhaseBuilder.create("arc_quest:reinforce_gate2")
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.reinforce_gate"))
                                .objective(ObjectiveBuilder.collect(Items.OAK_PLANKS, 16)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.reinforce_gate.0")))
                                .objective(ObjectiveBuilder.collect(Items.OAK_PLANKS, 16)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.reinforce_gate.0")))
                                .objective(ObjectiveBuilder.collect(Items.OAK_PLANKS, 16)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.reinforce_gate.0")))
                                .setFlagOnComplete("arc_quest:gate_reinforced")
                                // 手动转入汇合阶段（D）
                                .thenGoTo("arc_quest:craft_sword"))

                        // 汇合阶段（D）：制作铁剑
                        .phase(PhaseBuilder.create("arc_quest:craft_sword")
                                // 仅作为 condition 门禁，不自动跳转
                                .enterWhen(
                                        ICondition.flagSet("arc_quest:forest_scouted").and(ICondition.flagSet("arc_quest:gate_reinforced")),
                                        false
                                )
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.craft_sword"))
                                .objective(ObjectiveBuilder.collect(Items.IRON_INGOT, 3)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.craft_sword.0")))
                                .objective(ObjectiveBuilder.collect(Items.STICK, 2)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.craft_sword.1")))
                                .thenGoTo("arc_quest:defend_village"))

                        // 阶段 3：防御村庄（击杀僵尸）
                        .phase(PhaseBuilder.create("arc_quest:defend_village")
                                .intelScene("arc_quest:epic_prologue", "arc_quest:defend_village")
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.defend_village"))
                                .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 10)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.defend_village.0")))
                                .objective(ObjectiveBuilder.kill(EntityType.HUSK, 3)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.defend_village.1")).optional())
                                .setFlagOnComplete("arc_quest:village_defended")
                                .thenGoTo("arc_quest:gather_food"))

                        // 阶段 4：收集食物奖励村民
                        .phase(PhaseBuilder.create("arc_quest:gather_food")
                                .displayName(Component.translatable("arc_quest.phase.epic_prologue.gather_food"))
                                .objective(ObjectiveBuilder.collect(Items.BREAD, 10)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.gather_food.0")))
                                .objective(ObjectiveBuilder.collect(Items.COOKED_BEEF, 5)
                                        .display(Component.translatable("arc_quest.objective.epic_prologue.gather_food.1")))
                                .setFlagOnComplete("arc_quest:prologue_completed"))

                        // 奖励
                        .reward(new ItemReward(Items.IRON_SWORD, 1))
                        .reward(new ItemReward(Items.SHIELD, 1))
                        .reward(new ItemReward(Items.BREAD, 16))
                        .reward(VariableReward.add("arc_quest:village_reputation", 50))
                        .setFlagOnComplete("arc_quest:chapter1_unlocked")
                        .build()
        );
    }

    /**
     * 第一章：矿洞探险
     */
    private static void registerChapter1() {
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("arc_quest:epic_chapter1")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_chapter1.title"))
                .description(Component.translatable("arc_quest.quest.epic_chapter1.desc"))
                .sortOrder(1)
                .requiresQuest("arc_quest:epic_prologue")
                // 视觉配置
                .acquisitionSplash(
                        ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "textures/gui/splash/chapter1_acquire.png"),
                        1f
                )
                .completionSplash(
                        ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "textures/gui/splash/chapter1_complete.png"),
                        1f
                )
                .themeColor(ChatFormatting.BLUE)
                // 阶段 1：准备火把和工具
                .phase(PhaseBuilder.create("arc_quest:prepare_tools")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.prepare_tools"))
                        .objective(ObjectiveBuilder.collect(Items.COAL, 16).display(Component.translatable("arc_quest.objective.epic_chapter1.prepare_tools.0")))
                        .objective(ObjectiveBuilder.collect(Items.STICK, 8).display(Component.translatable("arc_quest.objective.epic_chapter1.prepare_tools.1")))
                        .thenGoTo("arc_quest:explore_depths"))
                // 阶段 2：探索矿洞（击杀洞穴蜘蛛）
                .phase(PhaseBuilder.create("arc_quest:explore_depths")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.explore_depths"))
                        .objective(ObjectiveBuilder.kill(EntityType.CAVE_SPIDER, 8).display(Component.translatable("arc_quest.objective.epic_chapter1.explore_depths.0")))
                        .objective(ObjectiveBuilder.collect(Items.STRING, 10).display(Component.translatable("arc_quest.objective.epic_chapter1.explore_depths.1")).hidden())
                        .thenGoTo("arc_quest:find_resources"))
                // 阶段 3：采集矿物
                .phase(PhaseBuilder.create("arc_quest:find_resources")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.find_resources"))
                        .objective(ObjectiveBuilder.collect(Items.IRON_ORE, 8).display(Component.translatable("arc_quest.objective.epic_chapter1.find_resources.0")))
                        .objective(ObjectiveBuilder.collect(Items.GOLD_ORE, 4).display(Component.translatable("arc_quest.objective.epic_chapter1.find_resources.1")))
                        .setFlagOnComplete("arc_quest:artifact_found")
                        .thenGoTo("arc_quest:boss_fight"))
                // 阶段 4：Boss 战（凋灵骷髅）
                .phase(PhaseBuilder.create("arc_quest:boss_fight")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.boss_fight"))
                        .objective(ObjectiveBuilder.kill(EntityType.WITHER_SKELETON, 1).display(Component.translatable("arc_quest.objective.epic_chapter1.boss_fight.0")))
                        .setFlagOnComplete("arc_quest:chapter1_boss_defeated"))
                // 奖励
                .reward(new ItemReward(Items.DIAMOND_PICKAXE, 1))
                .reward(new ItemReward(Items.TORCH, 32))
                .setFlagOnComplete("arc_quest:chapter1_completed")
                .setFlagOnComplete("arc_quest:chapter2_unlocked")
                .build()
        );
    }

    /**
     * 第二章分支A：战斗路线
     */
    private static void registerChapter2BranchA() {
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("arc_quest:epic_chapter2_combat")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_chapter2_combat.title"))
                .description(Component.translatable("arc_quest.quest.epic_chapter2_combat.desc"))
                .sortOrder(2)
                .requiresFlag("arc_quest:chapter2_unlocked")
                .requiresFlag("arc_quest:chose_combat_path")
                // 阶段 1：建造下界传送门
                .phase(PhaseBuilder.create("arc_quest:build_portal")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_combat.build_portal"))
                        .objective(ObjectiveBuilder.collect(Items.OBSIDIAN, 10).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.build_portal.0")))
                        .objective(ObjectiveBuilder.collect(Items.FLINT_AND_STEEL, 1).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.build_portal.1")))
                        .thenGoTo("arc_quest:enter_nether"))
                // 阶段 2：进入下界并收集资源
                .phase(PhaseBuilder.create("arc_quest:enter_nether")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_combat.enter_nether"))
                        .objective(ObjectiveBuilder.kill(EntityType.BLAZE, 5).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.enter_nether.0")))
                        .objective(ObjectiveBuilder.collect(Items.BLAZE_ROD, 7).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.enter_nether.1")))
                        .thenGoTo("arc_quest:assault_fortress"))
                // 阶段 3：突袭要塞
                .phase(PhaseBuilder.create("arc_quest:assault_fortress")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_combat.assault_fortress"))
                        .objective(ObjectiveBuilder.kill(EntityType.WITHER_SKELETON, 5).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.assault_fortress.0")))
                        .objective(ObjectiveBuilder.collect(Items.NETHER_BRICK, 16).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.assault_fortress.1")))
                        .setFlagOnComplete("arc_quest:combat_path_completed"))
                // 奖励
                .reward(new ItemReward(Items.NETHERITE_SCRAP, 2))
                .reward(new ItemReward(Items.FLINT_AND_STEEL, 1))
                .reward(VariableReward.add("arc_quest:combat_power", 100))
                .setFlagOnComplete("arc_quest:finale_unlocked")
                .build()
        );
    }

    /**
     * 第二章分支B：探索路线
     */
    private static void registerChapter2BranchB() {
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("arc_quest:epic_chapter2_exploration")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_chapter2_exploration.title"))
                .description(Component.translatable("arc_quest.quest.epic_chapter2_exploration.desc"))
                .sortOrder(2)
                .requiresFlag("arc_quest:chapter2_unlocked")
                .requiresFlag("arc_quest:chose_exploration_path")
                // 阶段 1：准备水下装备
                .phase(PhaseBuilder.create("arc_quest:prepare_underwater")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.prepare_underwater"))
                        .objective(ObjectiveBuilder.collect(Items.GLASS, 8).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.prepare_underwater.0")))
                        .objective(ObjectiveBuilder.collect(Items.SAND, 16).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.prepare_underwater.1")))
                        .thenGoTo("arc_quest:dive_ocean"))
                // 阶段 2：寻找海底神殿
                .phase(PhaseBuilder.create("arc_quest:dive_ocean")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.dive_ocean"))
                        .objective(ObjectiveBuilder.kill(EntityType.GUARDIAN, 5).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.dive_ocean.0")))
                        .objective(ObjectiveBuilder.collect(Items.PRISMARINE_CRYSTALS, 10).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.dive_ocean.1")))
                        .thenGoTo("arc_quest:solve_puzzle"))
                // 阶段 3：收集海晶材料
                .phase(PhaseBuilder.create("arc_quest:solve_puzzle")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.solve_puzzle"))
                        .objective(ObjectiveBuilder.collect(Items.PRISMARINE_SHARD, 16).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.solve_puzzle.0")))
                        .objective(ObjectiveBuilder.collect(Items.SPONGE, 2).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.solve_puzzle.1")))
                        .thenGoTo("arc_quest:defeat_guardian"))
                // 阶段 4：击败远古守卫者
                .phase(PhaseBuilder.create("arc_quest:defeat_guardian")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.defeat_guardian"))
                        .objective(ObjectiveBuilder.kill(EntityType.ELDER_GUARDIAN, 1).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.defeat_guardian.0")))
                        .setFlagOnComplete("arc_quest:exploration_path_completed"))
                // 奖励
                .reward(new ItemReward(Items.TRIDENT, 1))
                .reward(new ItemReward(Items.HEART_OF_THE_SEA, 1))
                .reward(VariableReward.add("arc_quest:exploration_wisdom", 100))
                .setFlagOnComplete("arc_quest:finale_unlocked")
                .build()
        );
    }

    /**
     * 终章：屠龙之战
     */
    private static void registerFinale() {
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("arc_quest:epic_finale")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_finale.title"))
                .description(Component.translatable("arc_quest.quest.epic_finale.desc"))
                .sortOrder(3)
                .requiresFlag("arc_quest:finale_unlocked")
                // 阶段 1：准备末影之眼
                .phase(PhaseBuilder.create("arc_quest:prepare_ritual")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.prepare_ritual"))
                        .objective(ObjectiveBuilder.collect(Items.ENDER_PEARL, 16).display(Component.translatable("arc_quest.objective.epic_finale.prepare_ritual.0")))
                        .objective(ObjectiveBuilder.collect(Items.BLAZE_POWDER, 8).display(Component.translatable("arc_quest.objective.epic_finale.prepare_ritual.1")))
                        .thenGoTo("arc_quest:craft_eyes"))
                // 阶段 2：合成末影之眼
                .phase(PhaseBuilder.create("arc_quest:craft_eyes")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.craft_eyes"))
                        .objective(ObjectiveBuilder.collect(Items.ENDER_EYE, 12).display(Component.translatable("arc_quest.objective.epic_finale.craft_eyes.0")))
                        .thenGoTo("arc_quest:find_stronghold"))
                // 阶段 3：寻找要塞
                .phase(PhaseBuilder.create("arc_quest:find_stronghold")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.find_stronghold"))
                        .objective(ObjectiveBuilder.kill(EntityType.SILVERFISH, 10).display(Component.translatable("arc_quest.objective.epic_finale.find_stronghold.0")))
                        .objective(ObjectiveBuilder.collect(Items.END_STONE, 32).display(Component.translatable("arc_quest.objective.epic_finale.find_stronghold.1")))
                        .thenGoTo("arc_quest:activate_portal"))
                // 阶段 4：激活末地传送门
                .phase(PhaseBuilder.create("arc_quest:activate_portal")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.activate_portal"))
                        .objective(ObjectiveBuilder.collect(Items.ENDER_EYE, 12).display(Component.translatable("arc_quest.objective.epic_finale.activate_portal.0")))
                        .thenGoTo("arc_quest:enter_end"))
                // 阶段 5：进入末地
                .phase(PhaseBuilder.create("arc_quest:enter_end")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.enter_end"))
                        .objective(ObjectiveBuilder.kill(EntityType.ENDERMAN, 20).display(Component.translatable("arc_quest.objective.epic_finale.enter_end.0")))
                        .thenGoTo("arc_quest:destroy_crystals"))
                // 阶段 6：摧毁末影水晶（通过击杀末影水晶实体）
                .phase(PhaseBuilder.create("arc_quest:destroy_crystals")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.destroy_crystals"))
                        .objective(ObjectiveBuilder.kill(EntityType.ENDERMAN, 5).display(Component.translatable("arc_quest.objective.epic_finale.destroy_crystals.0")))
                        .setFlagOnComplete("arc_quest:crystals_destroyed")
                        .thenGoTo("arc_quest:dragon_fight"))
                // 阶段 7：屠龙之战
                .phase(PhaseBuilder.create("arc_quest:dragon_fight")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.dragon_fight"))
                        .objective(ObjectiveBuilder.kill(EntityType.ENDER_DRAGON, 1).display(Component.translatable("arc_quest.objective.epic_finale.dragon_fight.0")))
                        .setFlagOnComplete("arc_quest:dragon_slayed"))
                // 阶段 8：凯旋归来
                .phase(PhaseBuilder.create("arc_quest:return_home")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.return_home"))
                        .objective(ObjectiveBuilder.collect(Items.DRAGON_HEAD, 1).display(Component.translatable("arc_quest.objective.epic_finale.return_home.0")))
                        .setFlagOnComplete("arc_quest:epic_completed"))
                // 终极奖励
                .reward(new ItemReward(Items.DRAGON_HEAD, 1))
                .reward(new ItemReward(Items.ELYTRA, 1))
                .reward(new ItemReward(Items.TOTEM_OF_UNDYING, 3))
                .reward(FlagReward.set("arc_quest:legendary_hero"))
                .reward(VariableReward.add("arc_quest:hero_level", 999))
                .build()
        );
    }

    /**
     * 分支选择任务
     */
    public static void registerBranchChoice() {
        ArcQuestAPI.registerQuest(
            QuestBuilder.create("arc_quest:epic_branch_choice")
                .category(QuestCategory.ADVENTURE)
                .displayName(Component.translatable("arc_quest.quest.epic_branch_choice.title"))
                .description(Component.translatable("arc_quest.quest.epic_branch_choice.desc"))
                .sortOrder(2)
                .requiresQuest("arc_quest:epic_chapter1")
                // 阶段 1：与村民交易获取线索
                .phase(PhaseBuilder.create("arc_quest:gather_info")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.gather_info"))
                        .objective(ObjectiveBuilder.collect(Items.EMERALD, 10).display(Component.translatable("arc_quest.objective.epic_branch_choice.gather_info.0")))
                        .objective(ObjectiveBuilder.collect(Items.BOOK, 5).display(Component.translatable("arc_quest.objective.epic_branch_choice.gather_info.1")))
                        .choice(Component.translatable("arc_quest.choice.epic_branch_choice.gather_info.0"), "arc_quest:chose_combat_path", "arc_quest:path_combat")
                        .choice(Component.translatable("arc_quest.choice.epic_branch_choice.gather_info.1"), "arc_quest:chose_exploration_path", "arc_quest:path_exploration")
                        .choice(Component.translatable("arc_quest.choice.epic_branch_choice.gather_info.2"), "arc_quest:chose_both_paths", "arc_quest:path_both",
                                ICondition.flagSet("arc_quest:village_reputation_high")))
                // 分支 A：战斗路线
                .phase(PhaseBuilder.create("arc_quest:path_combat")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.path_combat"))
                        .objective(ObjectiveBuilder.collect(Items.NETHERRACK, 32).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_combat.0")))
                        .objective(ObjectiveBuilder.collect(Items.LAVA_BUCKET, 2).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_combat.1")))
                        .setFlagOnComplete("arc_quest:chose_combat_path"))
                // 分支 B：探索路线
                .phase(PhaseBuilder.create("arc_quest:path_exploration")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.path_exploration"))
                        .objective(ObjectiveBuilder.collect(Items.KELP, 32).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_exploration.0")))
                        .objective(ObjectiveBuilder.collect(Items.COD, 10).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_exploration.1")))
                        .setFlagOnComplete("arc_quest:chose_exploration_path"))
                // 分支 C：隐藏路线（两条都走）
                .phase(PhaseBuilder.create("arc_quest:path_both")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.path_both"))
                        .objective(ObjectiveBuilder.kill(EntityType.BLAZE, 3).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_both.0")))
                        .objective(ObjectiveBuilder.collect(Items.PRISMARINE_SHARD, 5).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_both.1")))
                        .setFlagOnComplete("arc_quest:chose_combat_path")
                        .setFlagOnComplete("arc_quest:chose_exploration_path")
                        .reward(new ItemReward(Items.NETHER_STAR, 1)))
                .build()
        );
    }
}
