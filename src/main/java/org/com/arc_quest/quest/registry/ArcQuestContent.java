package org.com.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
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
import org.slf4j.Logger;

/**
 * 所有任务内容在此注册。纯代码驱动，零 JSON。
 * 在 FMLCommonSetupEvent 中调用 {@link #registerAll()}.
 */
public final class ArcQuestContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestContent() {
    }

    public static void registerAll() {
        LOGGER.info("[ArcQuest] Registering quest content...");

        registerTutorialQuest();
        registerMainlineChapter1();
        registerBranchingDemoQuest();

        QuestRegistry.freeze();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Demo 1：新手教程 —— 找人 → 打3个怪 → 交物品
    // ═══════════════════════════════════════════════════════════════════

    private static void registerTutorialQuest() {
        QuestBuilder.create("tutorial_dawn")
                .category(QuestCategory.ARCHON)
                .displayName("黎明的起点")
                .description("与村庄长老交谈，开始你的冒险之旅。")
                .icon(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "textures/gui/quest/icon_dawn.png"))
                .sortOrder(0)

                // ── 阶段 1：找 NPC 对话 ──
                .phase(PhaseBuilder.create("find_elder")
                        .displayName("寻找长老")
                        .setFlagOnEnter("tutorial_started")
                        .objective(ObjectiveBuilder
                                .talk(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "elder_npc"))
                                .display("与村庄长老交谈"))
                        .thenGoTo("hunt_zombies"))

                // ── 阶段 2：击杀 3 只僵尸 ──
                .phase(PhaseBuilder.create("hunt_zombies")
                        .displayName("清除威胁")
                        .objective(ObjectiveBuilder
                                .kill(EntityType.ZOMBIE, 3)
                                .display("击杀村庄附近的僵尸 (0/3)"))
                        .objective(ObjectiveBuilder
                                .kill(EntityType.SKELETON, 1)
                                .display("击杀骷髅弓手")
                                .optional())                    // 隐藏的可选目标
                        .thenGoTo("deliver_proof"))

                // ── 阶段 3：上交物品 ──
                .phase(PhaseBuilder.create("deliver_proof")
                        .displayName("回报长老")
                        .objective(ObjectiveBuilder
                                .deliver(Items.ROTTEN_FLESH, 3,
                                        ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "elder_npc"))
                                .display("将 3 个腐肉交给长老"))
                        .setFlagOnComplete("tutorial_completed"))

                // ── 任务完成奖励 ──
                .reward(new ItemReward(Items.IRON_SWORD, 1))
                .reward(new ItemReward(Items.BREAD, 8))
                .reward(VariableReward.add("reputation", 10))
                .setFlagOnComplete("main_ch1_unlocked")

                .buildAndRegister();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Demo 2：主线第一章 —— 前置依赖演示
    // ═══════════════════════════════════════════════════════════════════

    private static void registerMainlineChapter1() {
        QuestBuilder.create("mainline_ch1")
                .category(QuestCategory.ARCHON)
                .displayName("暗影森林")
                .description("长老提到了森林深处的异变……")
                .sortOrder(1)

                // 前置条件：tutorial_dawn 必须已完成
                .requiresQuest("tutorial_dawn")

                .phase(PhaseBuilder.create("enter_forest")
                        .displayName("进入暗影森林")
                        .objective(ObjectiveBuilder
                                .reachLocation(
                                        ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "dark_forest_entrance"),
                                        -200, 64, 350, 15)
                                .display("前往暗影森林入口"))
                        .thenGoTo("investigate"))

                .phase(PhaseBuilder.create("investigate")
                        .displayName("调查异变")
                        .objective(ObjectiveBuilder
                                .interact(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "corrupted_shrine"))
                                .display("检查被腐化的祭坛"))
                        .objective(ObjectiveBuilder
                                .collect(Items.ENDER_PEARL, 2)
                                .display("收集末影珍珠 (0/2)"))
                        .thenGoTo("boss_fight"))

                .phase(PhaseBuilder.create("boss_fight")
                        .displayName("击败暗影守卫")
                        .objective(ObjectiveBuilder
                                .kill(EntityType.WITHER_SKELETON, 1)
                                .display("击败暗影守卫"))
                        .setFlagOnComplete("ch1_boss_defeated"))

                .reward(new ItemReward(Items.DIAMOND, 3))
                .reward(new CommandReward("give {player} minecraft:experience_bottle 16"))
                .setFlagOnComplete("main_ch2_unlocked")

                .buildAndRegister();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Demo 3：分支对话演示 —— 展示 choices 与 flag 驱动的分支
    // ═══════════════════════════════════════════════════════════════════

    private static void registerBranchingDemoQuest() {
        QuestBuilder.create("branching_demo")
                .category(QuestCategory.COMPANION)
                .displayName("岔路抉择")
                .description("你需要做出选择——每条路都有不同的结局。")
                .sortOrder(0)
                .requiresFlag("tutorial_completed")

                // ── 阶段 1：对话，弹出选择 ──
                .phase(PhaseBuilder.create("talk_crossroad")
                        .displayName("十字路口")
                        .objective(ObjectiveBuilder
                                .talk(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "guide_npc"))
                                .display("与向导交谈"))
                        // 玩家选择走哪条路
                        .choice(Component.literal("走左边的山路"),
                                "chose_mountain", "path_mountain")
                        .choice(Component.literal("走右边的河道"),
                                "chose_river", "path_river")
                        .choice(Component.literal("[隐藏] 穿过密林"),
                                "chose_forest", "path_forest",
                                new FlagSetCondition("ch1_boss_defeated")))  // 只有打过Boss的人才看得到

                // ── 分支 A：山路 ──
                .phase(PhaseBuilder.create("path_mountain")
                        .displayName("攀越山峰")
                        .objective(ObjectiveBuilder
                                .reachLocation(
                                        ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "mountain_peak"),
                                        100, 200, 100, 10)
                                .display("抵达山顶"))
                        .thenGoTo("ending"))

                // ── 分支 B：河道 ──
                .phase(PhaseBuilder.create("path_river")
                        .displayName("顺流而下")
                        .objective(ObjectiveBuilder
                                .collect(Items.COD, 5)
                                .display("在河中捕鱼 (0/5)"))
                        .thenGoTo("ending"))

                // ── 分支 C：密林（隐藏路线） ──
                .phase(PhaseBuilder.create("path_forest")
                        .displayName("穿越密林")
                        .objective(ObjectiveBuilder
                                .kill(EntityType.SPIDER, 5)
                                .display("清除挡路的蜘蛛 (0/5)"))
                        .reward(new ItemReward(Items.EMERALD, 5)) // 隐藏路线额外奖励
                        .thenGoTo("ending"))

                // ── 汇合结局 ──
                .phase(PhaseBuilder.create("ending")
                        .displayName("殊途同归")
                        .objective(ObjectiveBuilder
                                .talk(ResourceLocation.fromNamespaceAndPath(Arc_quest.MOD_ID, "guide_npc"))
                                .display("再次与向导交谈")))

                .reward(new ItemReward(Items.GOLDEN_APPLE, 1))
                .reward(FlagReward.set("branching_completed"))

                .buildAndRegister();
    }
}