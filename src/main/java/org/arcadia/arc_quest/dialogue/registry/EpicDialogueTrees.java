package org.arcadia.arc_quest.dialogue.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.Arc_Quest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.dialogue.api.DialogueCondition;
import org.arcadia.arc_quest.dialogue.api.DialogueText;
import org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder;

import java.util.List;

/**
 * 史诗主线任务配套对话树 —— 展示与 EpicMainlineDemo 的深度联动。
 * <p>
 * 展示如何使用 ArcQuestAPI 进行对话树注册（Lib 模组标准实践）。
 */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class EpicDialogueTrees {

    public static final DialogueText example = DialogueText.translatable(
            "dialogue.epic_village_elder.start.newcomer",
            DialogueText.DialogueArg.of((player, npc) -> npc.position()),
            DialogueText.DialogueArg.of((player, npc) -> player.getMainHandItem().getHoverName())
    );

    private EpicDialogueTrees() {
    }

    public static void registerAll() {
        registerVillageElder();
        registerBlacksmith();
        registerMerchant();
        registerWanderingTrader();
        registerVillageGuard();
        registerMysteriousMerchant();
        registerDiamondGuidePopupTest();
    }

    @SubscribeEvent
    public static void onDialogueRegistration(ArcQuestRegistrationEvent.Dialogue event) {
        registerAll();
    }

    /**
     * 村庄长老 —— 任务发布者
     *
     * <p>条件类型示例（对话历史状态检查）：
     * <pre>
     * // 1. 检查节点是否已被访问过（一次性节点）
     * .choiceIf(
     *     new DialogueCondition.Not(new DialogueCondition.NodeVisited("intro_story")),
     *     "介绍故事背景",
     *     c -> c.goTo("intro_story")
     * )
     *
     * // 2. 检查选项是否已被选择过（一次性选项）
     * .choiceIf(
     *     new DialogueCondition.Not(new DialogueCondition.ChoiceSelected("start:0")),
     *     "接受任务（仅限一次）",
     *     c -> c.startQuest("...").close()
     * )
     *
     * // 3. 检查对话树是否已完成（一次性对话）
     * .choiceIf(
     *     new DialogueCondition.Not(new DialogueCondition.DialogueCompleted("epic_village_elder")),
     *     "首次对话奖励",
     *     c -> c.giveItem("...", 1).close()
     * )
     *
     * // 4. 精确检查节点冷却状态（需指定冷却时间）
     * .choiceIf(
     *     new DialogueCondition.Not(new DialogueCondition.NodeOnCooldown("daily_quest", 86400)), // 24小时冷却
     *     "领取每日任务",
     *     c -> c.goTo("daily_quest_node")
     * )
     *
     * // 5. 精确检查选项冷却状态
     * .choiceIf(
     *     new DialogueCondition.Not(new DialogueCondition.ChoiceOnCooldown("start:hint", 3600)), // 1小时冷却
     *     "获取提示（1小时冷却）",
     *     c -> c.goTo("hint_node")
     * )
     *
     * // 6. 精确检查对话树冷却状态
     * .choiceIf(
     *     new DialogueCondition.Not(new DialogueCondition.DialogueOnCooldown("epic_village_elder", 7200)), // 2小时冷却
     *     "再次交谈（2小时后可用）",
     *     c -> c.goTo("repeat_chat")
     * )
     * </pre>
     */
    private static void registerVillageElder() {
        ArcQuestAPI.registerDialogueTree(
                DialogueTreeBuilder.create("arc_quest:epic_village_elder")
                        .npc(DialogueText.translatable("dialogue.epic_village_elder.npc_name"))

                        // ═══════════════════════════════════════════
                        // 起始节点 - 使用 sayIf() 动态显示文本
                        // ═══════════════════════════════════════════
                        .node("start")

                        // 情况1：完全新手
                        .sayIf(
                                new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                                DialogueText.translatable("dialogue.epic_village_elder.start.newcomer"),
                                "arc_quest:newcomer"
                        )

                        // 情况2：序章进行中 - gather_wood 阶段
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.phase_gather_wood"),
                                "arc_quest:phase_gather_wood"
                        )

                        // 情况3：序章进行中 - talk_villager 阶段
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:talk_villager"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.phase_talk_villager"),
                                "arc_quest:phase_talk_villager"
                        )

                        // 情况4：序章进行中 - scout_forest 并行阶段
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:scout_forest"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.phase_scout_forest"),
                                "arc_quest:phase_scout_forest"
                        )

                        // 情况5：序章进行中 - reinforce_gate 并行阶段
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:reinforce_gate"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.phase_reinforce_gate"),
                                "arc_quest:phase_reinforce_gate"
                        )

                        // 情况6：序章进行中 - craft_sword 汇合阶段
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:craft_sword"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.phase_craft_sword"),
                                "arc_quest:phase_craft_sword"
                        )

                        // 情况7：序章进行中 - defend_village 阶段
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.phase_defend"),
                                "arc_quest:phase_defend"
                        )

                        // 情况8：序章进行中 - gather_food 收尾阶段
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_food"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.phase_gather_food"),
                                "arc_quest:phase_gather_food"
                        )

                        // 情况5：序章已完成，但第一章未完成
                        .sayIf(
                                new DialogueCondition.All(List.of(
                                        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                                )),
                                DialogueText.translatable("dialogue.epic_village_elder.start.prologue_done"),
                                "arc_quest:prologue_done"
                        )

                        // 情况6：第一章已完成，等待分支选择
                        .sayIf(
                                new DialogueCondition.All(List.of(
                                        new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_branch_choice"))
                                )),
                                DialogueText.translatable("dialogue.epic_village_elder.start.chapter1_done"),
                                "arc_quest:chapter1_done"
                        )

                        // 情况7：已完成终章（传奇英雄）
                        .sayIf(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.legendary"),
                                "arc_quest:legendary"
                        )

                        // 兜底文本
                        .say(DialogueText.translatable("dialogue.epic_village_elder.start.default"), "arc_quest:elder_start_default")

                        // 选项 - 根据任务状态显示不同选项
                        .choiceIf(
                                "arc_quest:choice_intro",
                                new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_intro"),
                                c -> c.goTo("intro_story")
                        )

                        .choiceIf(
                                "arc_quest:choice_accept_mission",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:talk_villager"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_accept_mission"),
                                c -> c
                                        .notifyInteract("arc_quest:village_elder")
                                        .close()
                        )

                        .choiceIf(
                                "arc_quest:choice_scout_tip",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:scout_forest"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_scout_tip"),
                                c -> c.close()
                        )

                        .choiceIf(
                                "arc_quest:choice_reinforce_tip",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:reinforce_gate"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_reinforce_tip"),
                                c -> c.close()
                        )

                        .choiceIf(
                                "arc_quest:choice_craft_tip",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:craft_sword"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_craft_tip"),
                                c -> c.close()
                        )

                        .choiceIf(
                                "arc_quest:choice_encourage",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_encourage"),
                                c -> c
                                        .giveItem("minecraft:potion{Potion:\"minecraft:strength\"}", 1)
                                        .close()
                        )

                        .choiceIf(
                                "arc_quest:choice_food_tip",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_food"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_food_tip"),
                                c -> c.close()
                        )

                        .choiceIf(
                                "arc_quest:choice_start_chapter1",
                                new DialogueCondition.All(List.of(
                                        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                                )),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_start_chapter1"),
                                c -> c
                                        .startQuest("arc_quest:epic_chapter1")
                                        .goTo("chapter1_accepted")
                        )

                        .choiceIf(
                                "arc_quest:choice_branch",
                                new DialogueCondition.All(List.of(
                                        new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_branch_choice"))
                                )),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_branch"),
                                c -> c
                                        .startQuest("arc_quest:epic_branch_choice")
                                        .goTo("branch_introduction")
                        )

                        .choiceIf(
                                "arc_quest:choice_legendary",
                                new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_legendary"),
                                c -> c
                                        .giveXp(100)
                                        .presetHeroOfVillage()  // ← 使用预设动作
                                        .close()
                        )

                        // 无条件选项
                        .choice("arc_quest:choice_default",
                                DialogueText.translatable("dialogue.epic_village_elder.start.choice_default"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 介绍故事背景
                        // ═══════════════════════════════════════════
                        .node("intro_story")
                        .say(DialogueText.translatable("dialogue.epic_village_elder.intro_story.text"), "arc_quest:intro_story_text")
                        .choice("arc_quest:intro_story_choice_accept",
                                DialogueText.translatable("dialogue.epic_village_elder.intro_story.choice_accept"),
                                c -> c
                                        .startQuest("arc_quest:epic_prologue")
                                        .goTo("prologue_accepted"))
                        .choice("arc_quest:intro_story_choice_decline",
                                DialogueText.translatable("dialogue.epic_village_elder.intro_story.choice_decline"),
                                c -> c.goTo("decline"))

                        // ═══════════════════════════════════════════
                        // 序章已接受
                        // ═══════════════════════════════════════════
                        .node("prologue_accepted")
                        .say(DialogueText.translatable("dialogue.epic_village_elder.prologue_accepted.text"), "arc_quest:prologue_accepted_text")
                        .choice("arc_quest:prologue_accepted_choice_ok",
                                DialogueText.translatable("dialogue.epic_village_elder.prologue_accepted.choice_ok"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 第一章已接受
                        // ═══════════════════════════════════════════
                        .node("chapter1_accepted")
                        .say(DialogueText.translatable("dialogue.epic_village_elder.chapter1_accepted.text"), "arc_quest:chapter1_accepted_text")
                        .choice("arc_quest:chapter1_accepted_choice_ok",
                                DialogueText.translatable("dialogue.epic_village_elder.chapter1_accepted.choice_ok"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 分支选择介绍
                        // ═══════════════════════════════════════════
                        .node("branch_introduction")
                        .say(DialogueText.translatable("dialogue.epic_village_elder.branch_introduction.text"), "arc_quest:branch_intro_text")
                        .choice("arc_quest:branch_intro_choice_combat",
                                DialogueText.translatable("dialogue.epic_village_elder.branch_introduction.choice_combat"),
                                c -> c.goTo("explain_combat"))
                        .choice("arc_quest:branch_intro_choice_exploration",
                                DialogueText.translatable("dialogue.epic_village_elder.branch_introduction.choice_exploration"),
                                c -> c.goTo("explain_exploration"))
                        .choice("arc_quest:branch_intro_choice_think",
                                DialogueText.translatable("dialogue.epic_village_elder.branch_introduction.choice_think"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 解释战斗路线
                        // ═══════════════════════════════════════════
                        .node("explain_combat")
                        .say(DialogueText.translatable("dialogue.epic_village_elder.explain_combat.text"), "arc_quest:explain_combat_text")
                        .choice("arc_quest:explain_combat_choice_confirm",
                                DialogueText.translatable("dialogue.epic_village_elder.explain_combat.choice_confirm"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 解释探索路线
                        // ═══════════════════════════════════════════
                        .node("explain_exploration")
                        .say(DialogueText.translatable("dialogue.epic_village_elder.explain_exploration.text"), "arc_quest:explain_exploration_text")
                        .choice("arc_quest:explain_exploration_choice_confirm",
                                DialogueText.translatable("dialogue.epic_village_elder.explain_exploration.choice_confirm"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 拒绝节点
                        // ═══════════════════════════════════════════
                        .node("decline")
                        .say(DialogueText.translatable("dialogue.epic_village_elder.decline.text"), "arc_quest:decline_text")
                        .choice("arc_quest:decline_choice1",
                                DialogueText.translatable("dialogue.epic_village_elder.decline.choice1"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        .build()
        );

        Arc_Quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_village_elder");
    }

    private static void registerDiamondGuidePopupTest() {
        ArcQuestAPI.registerDialogueTree(
                DialogueTreeBuilder.create("arc_quest:diamond_guide_popup_test")
                        .npc(DialogueText.literal("测试员"))
                        .node("start")
                        .say(DialogueText.literal("请点击确认以测试对话中的弹窗"),
                                "arc_quest:diamond_guide_popup_test_start")
                        .choice("arc_quest:diamond_guide_popup_test_confirm",
                                DialogueText.of(((serverPlayer, entity, iDialogueNpc) -> Component.literal("确认").withStyle(ChatFormatting.AQUA))),
                                choice -> choice
                                        .unlockGuide("arc_quest:diamond_demo")
                                        .openGuide("arc_quest:diamond_demo", 0, false)
                                        .goTo("finished"))
                        .node("finished")
                        .say(DialogueText.literal("测试完毕"),
                                "arc_quest:diamond_guide_popup_test_finished")
                        .choice("arc_quest:diamond_guide_popup_test_end",
                                DialogueText.literal("结束对话"),
                                DialogueTreeBuilder.ChoiceBuilder::close)
                        .build()
        );
        Arc_Quest.LOGGER.info("[ArcQuest] Registered dialogue: diamond_guide_popup_test");
    }

    /**
     * 铁匠 —— 装备升级商人
     */
    private static void registerBlacksmith() {
        ArcQuestAPI.registerDialogueTree(
                DialogueTreeBuilder.create("arc_quest:epic_blacksmith")
                        .npc(DialogueText.translatable("dialogue.epic_blacksmith.npc_name"))

                        .node("start")

                        // 使用 sayIf() 根据任务状态显示不同文本
                        .sayIf(
                                new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.early"),
                                "arc_quest:blacksmith_early"
                        )
                        .sayIf(
                                new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.mid"),
                                "arc_quest:blacksmith_mid"
                        )
                        .sayIf(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.late"),
                                "arc_quest:blacksmith_late"
                        )
                        .sayIf(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.legendary"),
                                "arc_quest:blacksmith_legendary"
                        )
                        .say(DialogueText.translatable("dialogue.epic_blacksmith.start.default"), "arc_quest:blacksmith_default")

                        // 选项 - 根据任务状态动态显示
                        .choiceIf(
                                "arc_quest:blacksmith_choice_stone_sword",
                                new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.choice_buy_stone_sword"),
                                c -> c.presetStoneSword().close()
                        )

                        .choiceIf(
                                "arc_quest:blacksmith_choice_iron_gear",
                                new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.choice_buy_iron_gear"),
                                c -> c.presetIronArmorSet().close()
                        )

                        .choiceIf(
                                "arc_quest:blacksmith_choice_diamond_gear",
                                new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.choice_buy_diamond_gear"),
                                c -> c.presetDiamondArmorSet().close()
                        )

                        .choiceIf(
                                "arc_quest:blacksmith_choice_netherite",
                                new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                                DialogueText.translatable("dialogue.epic_blacksmith.start.choice_buy_netherite"),
                                c -> c.presetNetheriteIngot().close()
                        )

                        // 链接到铁匠铺商店
                        .choice("arc_quest:blacksmith_choice_open_shop",
                                DialogueText.translatable("dialogue.epic_blacksmith.start.choice_open_shop"),
                                c -> c.openTrade("arc_quest:blacksmith_shop"))

                        .choice("arc_quest:blacksmith_choice_leave",
                                DialogueText.translatable("dialogue.epic_blacksmith.start.choice_leave"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        .build()
        );

        Arc_Quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_blacksmith");
    }

    /**
     * 商人 —— 物资供应商
     */
    private static void registerMerchant() {
        ArcQuestAPI.registerDialogueTree(
                DialogueTreeBuilder.create("arc_quest:epic_merchant")
                        .npc(DialogueText.translatable("dialogue.epic_merchant.npc_name"))

                        .node("start")

                        // 使用 sayIf() 根据任务阶段显示不同文本
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                                DialogueText.translatable("dialogue.epic_merchant.start.phase_gather"),
                                "arc_quest:merchant_phase_gather"
                        )
                        .sayIf(
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                                DialogueText.translatable("dialogue.epic_merchant.start.phase_defend"),
                                "arc_quest:merchant_phase_defend"
                        )
                        .sayIf(
                                new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_merchant.start.phase_chapter1"),
                                "arc_quest:merchant_phase_chapter1"
                        )
                        .say(DialogueText.translatable("dialogue.epic_merchant.start.default"), "arc_quest:merchant_default")

                        // 选项 - 使用预设动作
                        .choiceIf(
                                "arc_quest:merchant_choice_food",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                                DialogueText.translatable("dialogue.epic_merchant.start.choice_buy_food"),
                                c -> c.presetBread().close()
                        )

                        .choiceIf(
                                "arc_quest:merchant_choice_potions",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                                DialogueText.translatable("dialogue.epic_merchant.start.choice_buy_potions"),
                                c -> c.giveItem("minecraft:potion{Potion:\"minecraft:healing\"}", 2).close()
                        )

                        .choiceIf(
                                "arc_quest:merchant_choice_torches",
                                new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_merchant.start.choice_buy_torches"),
                                c -> c.presetTorches().close()
                        )

                        .choice("arc_quest:merchant_choice_browse",
                                DialogueText.translatable("dialogue.epic_merchant.start.choice_browse"),
                                c -> c.goTo("shop_menu"))
                        .choice("arc_quest:merchant_choice_leave",
                                DialogueText.translatable("dialogue.epic_merchant.start.choice_leave"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        .node("shop_menu")
                        .say(DialogueText.translatable("dialogue.epic_merchant.shop_menu.text"), "arc_quest:merchant_shop_menu_text")
                        .choice("arc_quest:shop_menu_choice_food",
                                DialogueText.translatable("dialogue.epic_merchant.shop_menu.choice_food"),
                                c -> c.presetBread().presetTorches().close())  // ← 使用预设动作
                        .choice("arc_quest:shop_menu_choice_tools",
                                DialogueText.translatable("dialogue.epic_merchant.shop_menu.choice_tools"),
                                c -> c.giveItem("minecraft:iron_pickaxe", 1).giveItem("minecraft:iron_axe", 1).close())
                        // 链接到旅行商人商店
                        .choice("arc_quest:shop_menu_choice_open_shop",
                                DialogueText.translatable("dialogue.epic_merchant.shop_menu.choice_open_shop"),
                                c -> c.openTrade("arc_quest:merchant_shop"))
                        .choice("arc_quest:shop_menu_choice_back",
                                DialogueText.translatable("dialogue.epic_merchant.shop_menu.choice_back"),
                                c -> c.goTo("start"))

                        .build()
        );

        Arc_Quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_merchant");
    }

    /**
     * 流浪商人 —— 使用 sayIf() 的示例 NPC
     * <p>
     * 展示如何根据任务状态动态显示不同的对话文本。
     */
    private static void registerWanderingTrader() {
        ArcQuestAPI.registerDialogueTree(
                DialogueTreeBuilder.create("arc_quest:epic_wandering_trader")
                        .npc(DialogueText.translatable("dialogue.epic_wandering_trader.npc_name"))

                        // ═══════════════════════════════════════════
                        // 起始节点 - 使用 sayIf() 动态显示文本
                        // ═══════════════════════════════════════════
                        .node("start")

                        // 情况1：完全新手
                        .sayIf(
                                new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.newbie"),
                                "arc_quest:wandering_newbie"
                        )

                        // 情况2：序章进行中
                        .sayIf(
                                new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.prologue_active"),
                                "arc_quest:wandering_prologue_active"
                        )

                        // 情况3：序章完成，第一章未开始
                        .sayIf(
                                new DialogueCondition.All(List.of(
                                        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                                )),
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.chapter1_ready"),
                                "arc_quest:wandering_chapter1_ready"
                        )

                        // 情况4：第一章进行中
                        .sayIf(
                                new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.chapter1_active"),
                                "arc_quest:wandering_chapter1_active"
                        )

                        // 情况5：已完成终章（传奇英雄）
                        .sayIf(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.legendary"),
                                "arc_quest:wandering_legendary"
                        )

                        // 兜底文本
                        .say(DialogueText.translatable("dialogue.epic_wandering_trader.start.default"), "arc_quest:wandering_default")

                        // 选项
                        .choice("arc_quest:wandering_choice_shop",
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.choice_shop"),
                                c -> c.goTo("shop_menu"))
                        .choice("arc_quest:wandering_choice_rumors",
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.choice_rumors"),
                                c -> c.goTo("rumors"))
                        .choice("arc_quest:wandering_choice_bye",
                                DialogueText.translatable("dialogue.epic_wandering_trader.start.choice_bye"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 商店菜单
                        // ═══════════════════════════════════════════
                        .node("shop_menu")
                        .say(DialogueText.translatable("dialogue.epic_wandering_trader.shop_menu.text"), "arc_quest:wandering_shop_menu_text")
                        .choice("arc_quest:shop_menu_choice_supplies",
                                DialogueText.translatable("dialogue.epic_wandering_trader.shop_menu.choice_supplies"),
                                c -> c.presetTorches().presetBread().goTo("purchase_complete"))
                        .choice("arc_quest:shop_menu_choice_equipment",
                                DialogueText.translatable("dialogue.epic_wandering_trader.shop_menu.choice_equipment"),
                                c -> c.goTo("equipment_menu"))
                        // 链接到流浪商人商店
                        .choice("arc_quest:shop_menu_choice_open_shop",
                                DialogueText.translatable("dialogue.epic_wandering_trader.shop_menu.choice_open_shop"),
                                c -> c.openTrade("arc_quest:wandering_trader_shop"))
                        .choice("arc_quest:shop_menu_choice_back",
                                DialogueText.translatable("dialogue.epic_wandering_trader.shop_menu.choice_back"),
                                c -> c.goTo("start"))

                        // ═══════════════════════════════════════════
                        // 装备菜单
                        // ═══════════════════════════════════════════
                        .node("equipment_menu")
                        .say(DialogueText.translatable("dialogue.epic_wandering_trader.equipment_menu.text"), "arc_quest:wandering_equipment_menu_text")

                        // 根据任务阶段显示不同装备
                        .choiceIf(
                                "arc_quest:equipment_choice_stone",
                                new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                                DialogueText.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_stone"),
                                c -> c.presetStoneSword().goTo("purchase_complete")
                        )
                        .choiceIf(
                                "arc_quest:equipment_choice_iron",
                                new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                                DialogueText.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_iron"),
                                c -> c.presetIronArmorSet().goTo("purchase_complete")
                        )
                        .choiceIf(
                                "arc_quest:equipment_choice_diamond",
                                new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                                DialogueText.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_diamond"),
                                c -> c.presetDiamondArmorSet().goTo("purchase_complete")
                        )
                        .choice("arc_quest:equipment_menu_choice_back",
                                DialogueText.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_back"),
                                c -> c.goTo("shop_menu"))

                        // ═══════════════════════════════════════════
                        // 购买完成
                        // ═══════════════════════════════════════════
                        .node("purchase_complete")
                        .say(DialogueText.translatable("dialogue.epic_wandering_trader.purchase_complete.text"), "arc_quest:wandering_purchase_complete_text")
                        .choice("arc_quest:purchase_complete_choice_continue",
                                DialogueText.translatable("dialogue.epic_wandering_trader.purchase_complete.choice_continue"),
                                c -> c.goTo("start"))

                        // ═══════════════════════════════════════════
                        // 传闻
                        // ═══════════════════════════════════════════
                        .node("rumors")
                        .say(DialogueText.translatable("dialogue.epic_wandering_trader.rumors.text"), "arc_quest:wandering_rumors_text")
                        .choice("arc_quest:rumors_choice_back",
                                DialogueText.translatable("dialogue.epic_wandering_trader.rumors.choice_back"),
                                c -> c.goTo("start"))

                        .build()
        );

        Arc_Quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_wandering_trader (with sayIf)");
    }

    /**
     * 村庄守卫 - 展示游戏时间刻冷却、时间段条件、对话历史状态和权重的综合示例
     *
     * <p>功能展示：
     * <ul>
     *   <li> 使用 GAME_TICK 冷却类型（每天早上6点重置）</li>
     *   <li> 使用时间段条件（IsMorning、IsAfternoon、IsNight）</li>
     *   <li> 检查节点访问历史（NodeVisited）</li>
     *   <li> 使用权重控制选项优先级</li>
     * </ul>
     */
    private static void registerVillageGuard() {
        ArcQuestAPI.registerDialogueTree(
                DialogueTreeBuilder.create("arc_quest:epic_village_guard")
                        .npc(DialogueText.translatable("dialogue.epic_village_guard.npc_name"))

                        // ═══════════════════════════════════════════
                        // 起始节点 - 根据时间段显示不同问候语
                        // ═══════════════════════════════════════════
                        .node("start")

                        // 早晨问候（6:00-12:00）
                        .sayIf(
                                new DialogueCondition.IsMorning(),
                                DialogueText.translatable("dialogue.epic_village_guard.start.morning"),
                                "arc_quest:guard_morning"
                        )

                        // 下午问候（12:00-18:00）
                        .sayIf(
                                new DialogueCondition.IsAfternoon(),
                                DialogueText.translatable("dialogue.epic_village_guard.start.afternoon"),
                                "arc_quest:guard_afternoon"
                        )

                        // 夜晚问候（18:00-次日6:00）
                        .sayIf(
                                new DialogueCondition.IsNight(),
                                DialogueText.translatable("dialogue.epic_village_guard.start.night"),
                                "arc_quest:guard_night"
                        )

                        // 兜底文本
                        .say(DialogueText.translatable("dialogue.epic_village_guard.start.default"), "arc_quest:guard_default")

                        // 选项1：首次见面介绍（一次性）
                        .choiceIf(
                                "arc_quest:guard_choice_intro",
                                new DialogueCondition.Not(new DialogueCondition.NodeVisited("intro")),
                                DialogueText.translatable("dialogue.epic_village_guard.start.choice_intro"),
                                c -> c.goTo("intro")
                        )

                        // 选项2：每日任务（每天6:00重置）- 使用时间段条件
                        .choiceIf(
                                "arc_quest:guard_choice_daily",
                                new DialogueCondition.IsMorning(),  // 只在早晨显示
                                DialogueText.translatable("dialogue.epic_village_guard.start.choice_daily"),
                                c -> {
                                    c.goTo("daily_patrol");
                                    c.cooldownGameTick(0);
                                }
                        )

                        // 选项3：训练请求（1小时冷却）
                        .choice(
                                "arc_quest:guard_choice_training",
                                DialogueText.translatable("dialogue.epic_village_guard.start.choice_training"),
                                c -> {
                                    c.cooldown(3600);
                                    c.goTo("training");
                                }
                        )

                        // 选项4：闲聊
                        .choice(
                                "arc_quest:guard_choice_chat",
                                DialogueText.translatable("dialogue.epic_village_guard.start.choice_chat"),
                                c -> c.goTo("chat")
                        )

                        // 选项5：快速补给（简易弹窗）
                        .choice("arc_quest:guard_choice_quick_trade",
                                DialogueText.translatable("dialogue.epic_village_guard.start.choice_quick_trade"),
                                c -> c.openSimpleTrade("arc_quest:quick_supplies"))

                        // 选项6：告别
                        .choice(
                                "arc_quest:guard_choice_bye",
                                DialogueText.translatable("dialogue.epic_village_guard.start.choice_bye"),
                                DialogueTreeBuilder.ChoiceBuilder::close
                        )

                        // ═══════════════════════════════════════════
                        // 首次介绍节点
                        // ═══════════════════════════════════════════
                        .node("intro")
                        .say(DialogueText.translatable("dialogue.epic_village_guard.intro.text"), "arc_quest:guard_intro_text")
                        .choice("arc_quest:intro_choice_ok",
                                DialogueText.translatable("dialogue.epic_village_guard.intro.choice_ok"),
                                c -> c.goTo("start"))

                        // ═══════════════════════════════════════════
                        // 每日巡逻任务（每天早上6点重置）
                        // ═══════════════════════════════════════════
                        .node("daily_patrol")
                        .say(DialogueText.translatable("dialogue.epic_village_guard.daily_patrol.text"), "arc_quest:guard_daily_patrol_text")
                        .choice("arc_quest:daily_patrol_choice_thanks",
                                DialogueText.translatable("dialogue.epic_village_guard.daily_patrol.choice_thanks"),
                                c -> {
                                    c.giveItem("minecraft:bread", 3);
                                    c.giveXp(10);
                                    c.close();
                                })

                        // ═══════════════════════════════════════════
                        // 训练请求（1小时冷却）
                        // ═══════════════════════════════════════════
                        .node("training")
                        .say(DialogueText.translatable("dialogue.epic_village_guard.training.text"), "arc_quest:guard_training_text")
                        .choice("arc_quest:training_choice_thanks",
                                DialogueText.translatable("dialogue.epic_village_guard.training.choice_thanks"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 闲聊节点（根据时间段显示不同内容）
                        // ═══════════════════════════════════════════
                        .node("chat")

                        // 早晨闲聊
                        .sayIf(
                                new DialogueCondition.IsMorning(),
                                DialogueText.translatable("dialogue.epic_village_guard.chat.morning"),
                                "arc_quest:chat_morning"
                        )

                        // 下午闲聊
                        .sayIf(
                                new DialogueCondition.IsAfternoon(),
                                DialogueText.translatable("dialogue.epic_village_guard.chat.afternoon"),
                                "arc_quest:chat_afternoon"
                        )

                        // 夜晚闲聊
                        .sayIf(
                                new DialogueCondition.IsNight(),
                                DialogueText.translatable("dialogue.epic_village_guard.chat.night"),
                                "arc_quest:chat_night"
                        )

                        .say(DialogueText.translatable("dialogue.epic_village_guard.chat.default"), "arc_quest:guard_chat_default")
                        .choice("arc_quest:chat_choice_back",
                                DialogueText.translatable("dialogue.epic_village_guard.chat.choice_back"),
                                c -> c.goTo("start"))

                        .build()
        );

        Arc_Quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_village_guard (with time-based features)");
    }

    /**
     * 神秘商人 - 展示自定义时间区间和复杂条件的示例
     *
     * <p>功能展示：
     * <ul>
     *   <li> 使用 GameTimeInRange 自定义时间区间</li>
     *   <li> 跨天区间支持（如 22:00-凌晨6:00）</li>
     *   <li> 组合条件（AND/OR/NOT）</li>
     *   <li> 对话树级别的冷却配置</li>
     * </ul>
     */
    private static void registerMysteriousMerchant() {
        ArcQuestAPI.registerDialogueTree(
                DialogueTreeBuilder.create("arc_quest:epic_mysterious_merchant")
                        .npc(DialogueText.translatable("dialogue.epic_mysterious_merchant.npc_name"))

                        // ═══════════════════════════════════════════
                        // 起始节点 - 只在特定时间开放
                        // ═══════════════════════════════════════════
                        .node("start")

                        // 情况1：深夜时段（22:00-凌晨6:00）- 商店开放
                        .sayIf(
                                new DialogueCondition.All(List.of(
                                        //示范自定义条件
                                /*DialogueCondition.CustomCondition.create((serverPlayer, npc) -> {
                                    if (npc instanceof LivingEntity livingNpc) {
                                        return livingNpc.hasEffect(MobEffects.NIGHT_VISION);
                                    }
                                    return false;
                                }),*/
                                        new DialogueCondition.GameTimeInRange(16000, 0)

                                )),
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.start.open"),
                                "arc_quest:mysterious_open"
                        )

                        // 情况2：早晨关闭（6:00-12:00）
                        .sayIf(
                                new DialogueCondition.All(List.of(
                                        new DialogueCondition.Not(new DialogueCondition.GameTimeInRange(16000, 0)),
                                        new DialogueCondition.IsMorning()
                                )),
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.start.closed_morning"),
                                "arc_quest:mysterious_closed_morning"
                        )

                        // 情况3：下午关闭（12:00-18:00）
                        .sayIf(
                                new DialogueCondition.All(List.of(
                                        new DialogueCondition.Not(new DialogueCondition.GameTimeInRange(16000, 0)),
                                        new DialogueCondition.IsAfternoon()
                                )),
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.start.closed_afternoon"),
                                "arc_quest:mysterious_closed_afternoon"
                        )

                        // 情况4：傍晚/夜晚关闭（18:00-22:00）- 使用默认文本

                        // 默认文本
                        .say(DialogueText.translatable("dialogue.epic_mysterious_merchant.start.default"), "arc_quest:mysterious_default")

                        // 选项1：购买商品（仅在深夜开放）
                        .choiceIf(
                                "arc_quest:mysterious_choice_shop",
                                new DialogueCondition.GameTimeInRange(16000, 0),  // 跨天区间：22:00→次日6:00
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.start.choice_shop"),
                                c -> {
                                    c.goTo("shop");
                                }
                        )

                        // 选项2：询问身份（首次见面）
                        .choiceIf(
                                "arc_quest:mysterious_choice_identity",
                                new DialogueCondition.Not(new DialogueCondition.DialogueCompleted("arc_quest:epic_mysterious_merchant")),
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.start.choice_identity"),
                                c -> c.goTo("identity")
                        )

                        // 选项3：离开
                        .choice("arc_quest:mysterious_choice_leave",
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.start.choice_leave"),
                                DialogueTreeBuilder.ChoiceBuilder::close)

                        // ═══════════════════════════════════════════
                        // 商店节点
                        // ═══════════════════════════════════════════
                        .node("shop")
                        .say(DialogueText.translatable("dialogue.epic_mysterious_merchant.shop.text"), "arc_quest:mysterious_shop_text")

                        // 链接到神秘商人商店（替代原本的直接给予物品）
                        .choice(
                                "arc_quest:mysterious_shop_choice_rare",
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.shop.choice_rare"),
                                c -> c.openTrade("arc_quest:mysterious_merchant_shop").close()
                        )

                        // 返回
                        .choice("arc_quest:mysterious_shop_choice_back",
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.shop.choice_back"),
                                c -> c.goTo("start"))

                        // ═══════════════════════════════════════════
                        // 身份揭示节点
                        // ═══════════════════════════════════════════
                        .node("identity")
                        .say(DialogueText.translatable("dialogue.epic_mysterious_merchant.identity.text"), "arc_quest:mysterious_identity_text")
                        .choice("arc_quest:identity_choice_back",
                                DialogueText.translatable("dialogue.epic_mysterious_merchant.identity.choice_back"),
                                c -> c.goTo("start"))

                        .build()
        );

        Arc_Quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_mysterious_merchant (with custom time ranges)");
    }
}
