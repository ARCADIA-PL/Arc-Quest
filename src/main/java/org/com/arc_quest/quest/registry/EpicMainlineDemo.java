package org.com.arc_quest.quest.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.api.QuestCategory;
import org.com.arc_quest.quest.builder.ObjectiveBuilder;
import org.com.arc_quest.quest.builder.PhaseBuilder;
import org.com.arc_quest.quest.builder.QuestBuilder;
import org.com.arc_quest.quest.condition.FlagSetCondition;
import org.com.arc_quest.quest.reward.CommandReward;
import org.com.arc_quest.quest.reward.FlagReward;
import org.com.arc_quest.quest.reward.ItemReward;
import org.com.arc_quest.quest.reward.VariableReward;

/**
 * 史诗级主线任务链 DEMO —— 基于 Minecraft 原版内容。
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

    private static void registerPrologue() {
        QuestBuilder.create("epic_prologue")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_prologue.title"))
                .description(Component.translatable("arc_quest.quest.epic_prologue.desc"))
                .icon(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "textures/gui/quest/prologue.png"))
                .sortOrder(0)
                // 视觉配置
                .acquisitionSplash(
                        ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "textures/gui/splash/prologue_acquire.png"),
                        1f
                )
                .completionSplash(
                        ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "textures/gui/splash/prologue_complete.png"),
                        1f
                )
                .themeColor(ChatFormatting.GOLD)
                // 阶段 1：收集木材制作武器
                .phase(PhaseBuilder.create("gather_wood")
                        .displayName(Component.translatable("arc_quest.phase.epic_prologue.gather_wood"))
                        .setFlagOnEnter("prologue_started")
                        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 5).display(Component.translatable("arc_quest.objective.epic_prologue.gather_wood.0")))
                        .thenGoTo("talk_villager"))
                // 阶段 1.5：与村民对话测试
                .phase(PhaseBuilder.create("talk_villager")
                        .displayName(Component.translatable("arc_quest.phase.epic_prologue.talk_villager"))
                        .objective(ObjectiveBuilder.interact(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "village_elder")).display(Component.translatable("arc_quest.objective.epic_prologue.talk_villager.0")))
                        .setFlagOnComplete("elder_consulted")  // 标记已与长老交谈
                        .thenGoTo("craft_sword"))
                // 阶段 2：制作铁剑
                .phase(PhaseBuilder.create("craft_sword")
                        .displayName(Component.translatable("arc_quest.phase.epic_prologue.craft_sword"))
                        .objective(ObjectiveBuilder.collect(Items.IRON_INGOT, 3).display(Component.translatable("arc_quest.objective.epic_prologue.craft_sword.0")))
                        .objective(ObjectiveBuilder.collect(Items.STICK, 2).display(Component.translatable("arc_quest.objective.epic_prologue.craft_sword.1")))
                        .thenGoTo("defend_village"))
                // 阶段 3：防御村庄（击杀僵尸）
                .phase(PhaseBuilder.create("defend_village")
                        .displayName(Component.translatable("arc_quest.phase.epic_prologue.defend_village"))
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 10).display(Component.translatable("arc_quest.objective.epic_prologue.defend_village.0")))
                        .objective(ObjectiveBuilder.kill(EntityType.HUSK, 3).display(Component.translatable("arc_quest.objective.epic_prologue.defend_village.1")).optional())
                        .setFlagOnComplete("village_defended")
                        .thenGoTo("gather_food"))
                // 阶段 4：收集食物奖励村民
                .phase(PhaseBuilder.create("gather_food")
                        .displayName(Component.translatable("arc_quest.phase.epic_prologue.gather_food"))
                        .objective(ObjectiveBuilder.collect(Items.BREAD, 10).display(Component.translatable("arc_quest.objective.epic_prologue.gather_food.0")))
                        .objective(ObjectiveBuilder.collect(Items.COOKED_BEEF, 5).display(Component.translatable("arc_quest.objective.epic_prologue.gather_food.1")))
                        .setFlagOnComplete("prologue_completed")
                        .setFlagOnComplete("blacksmith_unlocked")  // 解锁铁匠特殊对话
                        .setFlagOnComplete("merchant_unlocked"))  // 解锁商人特殊对话
                // 奖励
                .reward(new ItemReward(Items.IRON_SWORD, 1))
                .reward(new ItemReward(Items.SHIELD, 1))
                .reward(new ItemReward(Items.BREAD, 16))
                .reward(VariableReward.add("village_reputation", 50))
                .setFlagOnComplete("chapter1_unlocked")
                .buildAndRegister();
    }

    private static void registerChapter1() {
        QuestBuilder.create("epic_chapter1")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_chapter1.title"))
                .description(Component.translatable("arc_quest.quest.epic_chapter1.desc"))
                .sortOrder(1)
                .requiresQuest("epic_prologue")
                // 视觉配置
                .acquisitionSplash(
                        ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "textures/gui/splash/chapter1_acquire.png"),
                        1f
                )
                .completionSplash(
                        ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "textures/gui/splash/chapter1_complete.png"),
                        1f
                )
                .themeColor(ChatFormatting.BLUE)
                // 阶段 1：准备火把和工具
                .phase(PhaseBuilder.create("prepare_tools")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.prepare_tools"))
                        .objective(ObjectiveBuilder.collect(Items.COAL, 16).display(Component.translatable("arc_quest.objective.epic_chapter1.prepare_tools.0")))
                        .objective(ObjectiveBuilder.collect(Items.STICK, 8).display(Component.translatable("arc_quest.objective.epic_chapter1.prepare_tools.1")))
                        .thenGoTo("explore_depths"))
                // 阶段 2：探索矿洞（击杀洞穴蜘蛛）
                .phase(PhaseBuilder.create("explore_depths")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.explore_depths"))
                        .objective(ObjectiveBuilder.kill(EntityType.CAVE_SPIDER, 8).display(Component.translatable("arc_quest.objective.epic_chapter1.explore_depths.0")))
                        .objective(ObjectiveBuilder.collect(Items.STRING, 10).display(Component.translatable("arc_quest.objective.epic_chapter1.explore_depths.1")).hidden())
                        .thenGoTo("find_resources"))
                // 阶段 3：采集矿物
                .phase(PhaseBuilder.create("find_resources")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.find_resources"))
                        .objective(ObjectiveBuilder.collect(Items.IRON_ORE, 8).display(Component.translatable("arc_quest.objective.epic_chapter1.find_resources.0")))
                        .objective(ObjectiveBuilder.collect(Items.GOLD_ORE, 4).display(Component.translatable("arc_quest.objective.epic_chapter1.find_resources.1")))
                        .setFlagOnComplete("artifact_found")
                        .thenGoTo("boss_fight"))
                // 阶段 4：Boss 战（凋灵骷髅）
                .phase(PhaseBuilder.create("boss_fight")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter1.boss_fight"))
                        .objective(ObjectiveBuilder.kill(EntityType.WITHER_SKELETON, 1).display(Component.translatable("arc_quest.objective.epic_chapter1.boss_fight.0")))
                        .setFlagOnComplete("chapter1_boss_defeated"))
                // 奖励
                .reward(new ItemReward(Items.DIAMOND_PICKAXE, 1))
                .reward(new ItemReward(Items.TORCH, 32))
                .reward(new CommandReward("give {player} minecraft:experience_bottle 32"))
                .setFlagOnComplete("chapter1_completed")
                .setFlagOnComplete("chapter2_unlocked")
                .buildAndRegister();
    }

    private static void registerChapter2BranchA() {
        QuestBuilder.create("epic_chapter2_combat")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_chapter2_combat.title"))
                .description(Component.translatable("arc_quest.quest.epic_chapter2_combat.desc"))
                .sortOrder(2)
                .requiresFlag("chapter2_unlocked")
                .requiresFlag("chose_combat_path")
                // 阶段 1：建造下界传送门
                .phase(PhaseBuilder.create("build_portal")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_combat.build_portal"))
                        .objective(ObjectiveBuilder.collect(Items.OBSIDIAN, 10).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.build_portal.0")))
                        .objective(ObjectiveBuilder.collect(Items.FLINT_AND_STEEL, 1).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.build_portal.1")))
                        .thenGoTo("enter_nether"))
                // 阶段 2：进入下界并收集资源
                .phase(PhaseBuilder.create("enter_nether")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_combat.enter_nether"))
                        .objective(ObjectiveBuilder.kill(EntityType.BLAZE, 5).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.enter_nether.0")))
                        .objective(ObjectiveBuilder.collect(Items.BLAZE_ROD, 7).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.enter_nether.1")))
                        .thenGoTo("assault_fortress"))
                // 阶段 3：突袭要塞
                .phase(PhaseBuilder.create("assault_fortress")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_combat.assault_fortress"))
                        .objective(ObjectiveBuilder.kill(EntityType.WITHER_SKELETON, 5).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.assault_fortress.0")))
                        .objective(ObjectiveBuilder.collect(Items.NETHER_BRICK, 16).display(Component.translatable("arc_quest.objective.epic_chapter2_combat.assault_fortress.1")))
                        .setFlagOnComplete("combat_path_completed"))
                // 奖励
                .reward(new ItemReward(Items.NETHERITE_SCRAP, 2))
                .reward(new ItemReward(Items.FLINT_AND_STEEL, 1))
                .reward(VariableReward.add("combat_power", 100))
                .setFlagOnComplete("finale_unlocked")
                .buildAndRegister();
    }

    private static void registerChapter2BranchB() {
        QuestBuilder.create("epic_chapter2_exploration")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_chapter2_exploration.title"))
                .description(Component.translatable("arc_quest.quest.epic_chapter2_exploration.desc"))
                .sortOrder(2)
                .requiresFlag("chapter2_unlocked")
                .requiresFlag("chose_exploration_path")
                // 阶段 1：准备水下装备
                .phase(PhaseBuilder.create("prepare_underwater")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.prepare_underwater"))
                        .objective(ObjectiveBuilder.collect(Items.GLASS, 8).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.prepare_underwater.0")))
                        .objective(ObjectiveBuilder.collect(Items.SAND, 16).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.prepare_underwater.1")))
                        .thenGoTo("dive_ocean"))
                // 阶段 2：寻找海底神殿
                .phase(PhaseBuilder.create("dive_ocean")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.dive_ocean"))
                        .objective(ObjectiveBuilder.kill(EntityType.GUARDIAN, 5).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.dive_ocean.0")))
                        .objective(ObjectiveBuilder.collect(Items.PRISMARINE_CRYSTALS, 10).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.dive_ocean.1")))
                        .thenGoTo("solve_puzzle"))
                // 阶段 3：收集海晶材料
                .phase(PhaseBuilder.create("solve_puzzle")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.solve_puzzle"))
                        .objective(ObjectiveBuilder.collect(Items.PRISMARINE_SHARD, 16).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.solve_puzzle.0")))
                        .objective(ObjectiveBuilder.collect(Items.SPONGE, 2).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.solve_puzzle.1")))
                        .thenGoTo("defeat_guardian"))
                // 阶段 4：击败远古守卫者
                .phase(PhaseBuilder.create("defeat_guardian")
                        .displayName(Component.translatable("arc_quest.phase.epic_chapter2_exploration.defeat_guardian"))
                        .objective(ObjectiveBuilder.kill(EntityType.ELDER_GUARDIAN, 1).display(Component.translatable("arc_quest.objective.epic_chapter2_exploration.defeat_guardian.0")))
                        .setFlagOnComplete("exploration_path_completed"))
                // 奖励
                .reward(new ItemReward(Items.TRIDENT, 1))
                .reward(new ItemReward(Items.HEART_OF_THE_SEA, 1))
                .reward(VariableReward.add("exploration_wisdom", 100))
                .setFlagOnComplete("finale_unlocked")
                .buildAndRegister();
    }

    private static void registerFinale() {
        QuestBuilder.create("epic_finale")
                .category(QuestCategory.ARCHON)
                .displayName(Component.translatable("arc_quest.quest.epic_finale.title"))
                .description(Component.translatable("arc_quest.quest.epic_finale.desc"))
                .sortOrder(3)
                .requiresFlag("finale_unlocked")
                // 阶段 1：准备末影之眼
                .phase(PhaseBuilder.create("prepare_ritual")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.prepare_ritual"))
                        .objective(ObjectiveBuilder.collect(Items.ENDER_PEARL, 16).display(Component.translatable("arc_quest.objective.epic_finale.prepare_ritual.0")))
                        .objective(ObjectiveBuilder.collect(Items.BLAZE_POWDER, 8).display(Component.translatable("arc_quest.objective.epic_finale.prepare_ritual.1")))
                        .thenGoTo("craft_eyes"))
                // 阶段 2：合成末影之眼
                .phase(PhaseBuilder.create("craft_eyes")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.craft_eyes"))
                        .objective(ObjectiveBuilder.collect(Items.ENDER_EYE, 12).display(Component.translatable("arc_quest.objective.epic_finale.craft_eyes.0")))
                        .thenGoTo("find_stronghold"))
                // 阶段 3：寻找要塞
                .phase(PhaseBuilder.create("find_stronghold")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.find_stronghold"))
                        .objective(ObjectiveBuilder.kill(EntityType.SILVERFISH, 10).display(Component.translatable("arc_quest.objective.epic_finale.find_stronghold.0")))
                        .objective(ObjectiveBuilder.collect(Items.END_STONE, 32).display(Component.translatable("arc_quest.objective.epic_finale.find_stronghold.1")))
                        .thenGoTo("activate_portal"))
                // 阶段 4：激活末地传送门
                .phase(PhaseBuilder.create("activate_portal")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.activate_portal"))
                        .objective(ObjectiveBuilder.collect(Items.ENDER_EYE, 12).display(Component.translatable("arc_quest.objective.epic_finale.activate_portal.0")))
                        .thenGoTo("enter_end"))
                // 阶段 5：进入末地
                .phase(PhaseBuilder.create("enter_end")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.enter_end"))
                        .objective(ObjectiveBuilder.kill(EntityType.ENDERMAN, 20).display(Component.translatable("arc_quest.objective.epic_finale.enter_end.0")))
                        .thenGoTo("destroy_crystals"))
                // 阶段 6：摧毁末影水晶（通过击杀末影水晶实体）
                .phase(PhaseBuilder.create("destroy_crystals")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.destroy_crystals"))
                        .objective(ObjectiveBuilder.kill(EntityType.ENDERMAN, 5).display(Component.translatable("arc_quest.objective.epic_finale.destroy_crystals.0")))
                        .setFlagOnComplete("crystals_destroyed")
                        .thenGoTo("dragon_fight"))
                // 阶段 7：屠龙之战
                .phase(PhaseBuilder.create("dragon_fight")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.dragon_fight"))
                        .objective(ObjectiveBuilder.kill(EntityType.ENDER_DRAGON, 1).display(Component.translatable("arc_quest.objective.epic_finale.dragon_fight.0")))
                        .setFlagOnComplete("dragon_slayed"))
                // 阶段 8：凯旋归来
                .phase(PhaseBuilder.create("return_home")
                        .displayName(Component.translatable("arc_quest.phase.epic_finale.return_home"))
                        .objective(ObjectiveBuilder.collect(Items.DRAGON_HEAD, 1).display(Component.translatable("arc_quest.objective.epic_finale.return_home.0")))
                        .setFlagOnComplete("epic_completed"))
                // 终极奖励
                .reward(new ItemReward(Items.DRAGON_HEAD, 1))
                .reward(new ItemReward(Items.ELYTRA, 1))
                .reward(new ItemReward(Items.TOTEM_OF_UNDYING, 3))
                .reward(new CommandReward("give {player} minecraft:netherite_ingot 8"))
                .reward(new CommandReward("give {player} minecraft:experience_bottle 128"))
                .reward(FlagReward.set("legendary_hero"))
                .reward(VariableReward.add("hero_level", 999))
                .buildAndRegister();
    }

    public static void registerBranchChoice() {
        QuestBuilder.create("epic_branch_choice")
                .category(QuestCategory.ADVENTURE)
                .displayName(Component.translatable("arc_quest.quest.epic_branch_choice.title"))
                .description(Component.translatable("arc_quest.quest.epic_branch_choice.desc"))
                .sortOrder(2)
                .requiresQuest("epic_chapter1")
                // 阶段 1：与村民交易获取线索
                .phase(PhaseBuilder.create("gather_info")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.gather_info"))
                        .objective(ObjectiveBuilder.collect(Items.EMERALD, 10).display(Component.translatable("arc_quest.objective.epic_branch_choice.gather_info.0")))
                        .objective(ObjectiveBuilder.collect(Items.BOOK, 5).display(Component.translatable("arc_quest.objective.epic_branch_choice.gather_info.1")))
                        .choice(Component.translatable("arc_quest.choice.epic_branch_choice.gather_info.0"), "chose_combat_path", "path_combat")
                        .choice(Component.translatable("arc_quest.choice.epic_branch_choice.gather_info.1"), "chose_exploration_path", "path_exploration")
                        .choice(Component.translatable("arc_quest.choice.epic_branch_choice.gather_info.2"), "chose_both_paths", "path_both",
                                new FlagSetCondition("village_reputation_high")))
                // 分支 A：战斗路线
                .phase(PhaseBuilder.create("path_combat")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.path_combat"))
                        .objective(ObjectiveBuilder.collect(Items.NETHERRACK, 32).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_combat.0")))
                        .objective(ObjectiveBuilder.collect(Items.LAVA_BUCKET, 2).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_combat.1")))
                        .setFlagOnComplete("chose_combat_path"))
                // 分支 B：探索路线
                .phase(PhaseBuilder.create("path_exploration")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.path_exploration"))
                        .objective(ObjectiveBuilder.collect(Items.KELP, 32).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_exploration.0")))
                        .objective(ObjectiveBuilder.collect(Items.COD, 10).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_exploration.1")))
                        .setFlagOnComplete("chose_exploration_path"))
                // 分支 C：隐藏路线（两条都走）
                .phase(PhaseBuilder.create("path_both")
                        .displayName(Component.translatable("arc_quest.phase.epic_branch_choice.path_both"))
                        .objective(ObjectiveBuilder.kill(EntityType.BLAZE, 3).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_both.0")))
                        .objective(ObjectiveBuilder.collect(Items.PRISMARINE_SHARD, 5).display(Component.translatable("arc_quest.objective.epic_branch_choice.path_both.1")))
                        .setFlagOnComplete("chose_combat_path")
                        .setFlagOnComplete("chose_exploration_path")
                        .reward(new ItemReward(Items.NETHER_STAR, 1)))
                .buildAndRegister();
    }
}
