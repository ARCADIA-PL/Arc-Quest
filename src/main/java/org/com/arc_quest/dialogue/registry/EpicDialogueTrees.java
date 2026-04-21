package org.com.arc_quest.dialogue.registry;

import net.minecraft.network.chat.Component;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.api.ArcQuestAPI;
import org.com.arc_quest.dialogue.api.DialogueCondition;
import org.com.arc_quest.dialogue.builder.DialogueTreeBuilder;

import java.util.List;

/**
 * 史诗主线任务配套对话树 —— 展示与 EpicMainlineDemo 的深度联动。
 * <p>
 * 展示如何使用 ArcQuestAPI 进行对话树注册（Lib 模组标准实践）。
 */
public final class EpicDialogueTrees {

    private EpicDialogueTrees() {
    }

    public static void registerAll() {
        registerVillageElder();
        registerBlacksmith();
        registerMerchant();
        registerWanderingTrader();
        registerVillageGuard();
        registerMysteriousMerchant();
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
                .npc(Component.translatable("dialogue.epic_village_elder.npc_name").getString())

                // ═══════════════════════════════════════════
                // 起始节点 - 使用 sayIf() 动态显示文本
                // ═══════════════════════════════════════════
                .node("start")

                // 情况1：完全新手
                .sayIf(
                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                        Component.translatable("dialogue.epic_village_elder.start.newcomer").getString(),
                        "arc_quest:newcomer"
                )

                // 情况2：序章进行中 - gather_wood 阶段
                .sayIf(
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                        Component.translatable("dialogue.epic_village_elder.start.phase_gather_wood").getString(),
                        "arc_quest:phase_gather_wood"
                )

                // 情况3：序章进行中 - talk_villager 阶段
                .sayIf(
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:talk_villager"),
                        Component.translatable("dialogue.epic_village_elder.start.phase_talk_villager").getString(),
                        "arc_quest:phase_talk_villager"
                )

                // 情况4：序章进行中 - defend_village 阶段
                .sayIf(
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                        Component.translatable("dialogue.epic_village_elder.start.phase_defend").getString(),
                        "arc_quest:phase_defend"
                )

                // 情况5：序章已完成，但第一章未完成
                .sayIf(
                        new DialogueCondition.All(List.of(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                                new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                        )),
                        Component.translatable("dialogue.epic_village_elder.start.prologue_done").getString(),
                        "arc_quest:prologue_done"
                )

                // 情况6：第一章已完成，等待分支选择
                .sayIf(
                        new DialogueCondition.All(List.of(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                                new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_branch_choice"))
                        )),
                        Component.translatable("dialogue.epic_village_elder.start.chapter1_done").getString(),
                        "arc_quest:chapter1_done"
                )

                // 情况7：已完成终章（传奇英雄）
                .sayIf(
                        new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                        Component.translatable("dialogue.epic_village_elder.start.legendary").getString(),
                        "arc_quest:legendary"
                )

                // 兜底文本
                .say(Component.translatable("dialogue.epic_village_elder.start.default").getString(), "arc_quest:elder_start_default")

                // 选项 - 根据任务状态显示不同选项
                .choiceIf(
                        "arc_quest:choice_intro",
                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                        Component.translatable("dialogue.epic_village_elder.start.choice_intro").getString(),
                        c -> c.goTo("intro_story")
                )

                .choiceIf(
                        "arc_quest:choice_accept_mission",
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:talk_villager"),
                        Component.translatable("dialogue.epic_village_elder.start.choice_accept_mission").getString(),
                        c -> c
                                .notifyInteract("arc_quest:village_elder")
                                .close()
                )

                .choiceIf(
                        "arc_quest:choice_encourage",
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                        Component.translatable("dialogue.epic_village_elder.start.choice_encourage").getString(),
                        c -> c
                                .giveItem("minecraft:potion{Potion:\"minecraft:strength\"}", 1)
                                .close()
                )

                .choiceIf(
                        "arc_quest:choice_start_chapter1",
                        new DialogueCondition.All(List.of(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                                new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                        )),
                        Component.translatable("dialogue.epic_village_elder.start.choice_start_chapter1").getString(),
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
                        Component.translatable("dialogue.epic_village_elder.start.choice_branch").getString(),
                        c -> c
                                .startQuest("arc_quest:epic_branch_choice")
                                .goTo("branch_introduction")
                )

                .choiceIf(
                        "arc_quest:choice_legendary",
                        new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                        Component.translatable("dialogue.epic_village_elder.start.choice_legendary").getString(),
                        c -> c
                                .giveXp(100)
                                .presetHeroOfVillage()  // ← 使用预设动作
                                .close()
                )

                // 无条件选项
                .choice("arc_quest:choice_default",
                        Component.translatable("dialogue.epic_village_elder.start.choice_default").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 介绍故事背景
                // ═══════════════════════════════════════════
                .node("intro_story")
                .say(Component.translatable("dialogue.epic_village_elder.intro_story.text").getString(), "arc_quest:intro_story_text")
                .choice("arc_quest:intro_story_choice_accept",
                        Component.translatable("dialogue.epic_village_elder.intro_story.choice_accept").getString(),
                        c -> c
                                .startQuest("arc_quest:epic_prologue")
                                .goTo("prologue_accepted"))
                .choice("arc_quest:intro_story_choice_decline",
                        Component.translatable("dialogue.epic_village_elder.intro_story.choice_decline").getString(),
                        c -> c.goTo("decline"))

                // ═══════════════════════════════════════════
                // 序章已接受
                // ═══════════════════════════════════════════
                .node("prologue_accepted")
                .say(Component.translatable("dialogue.epic_village_elder.prologue_accepted.text").getString(), "arc_quest:prologue_accepted_text")
                .choice("arc_quest:prologue_accepted_choice_ok",
                        Component.translatable("dialogue.epic_village_elder.prologue_accepted.choice_ok").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 第一章已接受
                // ═══════════════════════════════════════════
                .node("chapter1_accepted")
                .say(Component.translatable("dialogue.epic_village_elder.chapter1_accepted.text").getString(), "arc_quest:chapter1_accepted_text")
                .choice("arc_quest:chapter1_accepted_choice_ok",
                        Component.translatable("dialogue.epic_village_elder.chapter1_accepted.choice_ok").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 分支选择介绍
                // ═══════════════════════════════════════════
                .node("branch_introduction")
                .say(Component.translatable("dialogue.epic_village_elder.branch_introduction.text").getString(), "arc_quest:branch_intro_text")
                .choice("arc_quest:branch_intro_choice_combat",
                        Component.translatable("dialogue.epic_village_elder.branch_introduction.choice_combat").getString(),
                        c -> c.goTo("explain_combat"))
                .choice("arc_quest:branch_intro_choice_exploration",
                        Component.translatable("dialogue.epic_village_elder.branch_introduction.choice_exploration").getString(),
                        c -> c.goTo("explain_exploration"))
                .choice("arc_quest:branch_intro_choice_think",
                        Component.translatable("dialogue.epic_village_elder.branch_introduction.choice_think").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 解释战斗路线
                // ═══════════════════════════════════════════
                .node("explain_combat")
                .say(Component.translatable("dialogue.epic_village_elder.explain_combat.text").getString(), "arc_quest:explain_combat_text")
                .choice("arc_quest:explain_combat_choice_confirm",
                        Component.translatable("dialogue.epic_village_elder.explain_combat.choice_confirm").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 解释探索路线
                // ═══════════════════════════════════════════
                .node("explain_exploration")
                .say(Component.translatable("dialogue.epic_village_elder.explain_exploration.text").getString(), "arc_quest:explain_exploration_text")
                .choice("arc_quest:explain_exploration_choice_confirm",
                        Component.translatable("dialogue.epic_village_elder.explain_exploration.choice_confirm").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 拒绝节点
                // ═══════════════════════════════════════════
                .node("decline")
                .say(Component.translatable("dialogue.epic_village_elder.decline.text").getString(), "arc_quest:decline_text")
                .choice("arc_quest:decline_choice1",
                        Component.translatable("dialogue.epic_village_elder.decline.choice1").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                .build()
        );

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_village_elder");
    }

    /**
     * 铁匠 —— 装备升级商人
     */
    private static void registerBlacksmith() {
        ArcQuestAPI.registerDialogueTree(
            DialogueTreeBuilder.create("arc_quest:epic_blacksmith")
                .npc(Component.translatable("dialogue.epic_blacksmith.npc_name").getString())

                .node("start")

                // 使用 sayIf() 根据任务状态显示不同文本
                .sayIf(
                        new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                        Component.translatable("dialogue.epic_blacksmith.start.early").getString(),
                        "arc_quest:blacksmith_early"
                )
                .sayIf(
                        new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_blacksmith.start.mid").getString(),
                        "arc_quest:blacksmith_mid"
                )
                .sayIf(
                        new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_blacksmith.start.late").getString(),
                        "arc_quest:blacksmith_late"
                )
                .sayIf(
                        new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                        Component.translatable("dialogue.epic_blacksmith.start.legendary").getString(),
                        "arc_quest:blacksmith_legendary"
                )
                .say(Component.translatable("dialogue.epic_blacksmith.start.default").getString(), "arc_quest:blacksmith_default")

                // 选项 - 根据任务状态动态显示
                .choiceIf(
                        "arc_quest:blacksmith_choice_stone_sword",
                        new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                        Component.translatable("dialogue.epic_blacksmith.start.choice_buy_stone_sword").getString(),
                        c -> c.presetStoneSword().close()
                )

                .choiceIf(
                        "arc_quest:blacksmith_choice_iron_gear",
                        new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_blacksmith.start.choice_buy_iron_gear").getString(),
                        c -> c.presetIronArmorSet().close()
                )

                .choiceIf(
                        "arc_quest:blacksmith_choice_diamond_gear",
                        new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_blacksmith.start.choice_buy_diamond_gear").getString(),
                        c -> c.presetDiamondArmorSet().close()
                )

                .choiceIf(
                        "arc_quest:blacksmith_choice_netherite",
                        new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                        Component.translatable("dialogue.epic_blacksmith.start.choice_buy_netherite").getString(),
                        c -> c.presetNetheriteIngot().close()
                )

                // 链接到铁匠铺商店
                .choice("arc_quest:blacksmith_choice_open_shop",
                        Component.translatable("dialogue.epic_blacksmith.start.choice_open_shop").getString(),
                        c -> c.openTrade("arc_quest:blacksmith_shop"))

                .choice("arc_quest:blacksmith_choice_leave",
                        Component.translatable("dialogue.epic_blacksmith.start.choice_leave").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                .build()
        );

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_blacksmith");
    }

    /**
     * 商人 —— 物资供应商
     */
    private static void registerMerchant() {
        ArcQuestAPI.registerDialogueTree(
            DialogueTreeBuilder.create("arc_quest:epic_merchant")
                .npc(Component.translatable("dialogue.epic_merchant.npc_name").getString())

                .node("start")

                // 使用 sayIf() 根据任务阶段显示不同文本
                .sayIf(
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                        Component.translatable("dialogue.epic_merchant.start.phase_gather").getString(),
                        "arc_quest:merchant_phase_gather"
                )
                .sayIf(
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                        Component.translatable("dialogue.epic_merchant.start.phase_defend").getString(),
                        "arc_quest:merchant_phase_defend"
                )
                .sayIf(
                        new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_merchant.start.phase_chapter1").getString(),
                        "arc_quest:merchant_phase_chapter1"
                )
                .say(Component.translatable("dialogue.epic_merchant.start.default").getString(), "arc_quest:merchant_default")

                // 选项 - 使用预设动作
                .choiceIf(
                        "arc_quest:merchant_choice_food",
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                        Component.translatable("dialogue.epic_merchant.start.choice_buy_food").getString(),
                        c -> c.presetBread().close()
                )

                .choiceIf(
                        "arc_quest:merchant_choice_potions",
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:defend_village"),
                        Component.translatable("dialogue.epic_merchant.start.choice_buy_potions").getString(),
                        c -> c.giveItem("minecraft:potion{Potion:\"minecraft:healing\"}", 2).close()
                )

                .choiceIf(
                        "arc_quest:merchant_choice_torches",
                        new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_merchant.start.choice_buy_torches").getString(),
                        c -> c.presetTorches().close()
                )

                .choice("arc_quest:merchant_choice_browse",
                        Component.translatable("dialogue.epic_merchant.start.choice_browse").getString(),
                        c -> c.goTo("shop_menu"))
                .choice("arc_quest:merchant_choice_leave",
                        Component.translatable("dialogue.epic_merchant.start.choice_leave").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                .node("shop_menu")
                .say(Component.translatable("dialogue.epic_merchant.shop_menu.text").getString(), "arc_quest:merchant_shop_menu_text")
                .choice("arc_quest:shop_menu_choice_food",
                        Component.translatable("dialogue.epic_merchant.shop_menu.choice_food").getString(),
                        c -> c.presetBread().presetTorches().close())  // ← 使用预设动作
                .choice("arc_quest:shop_menu_choice_tools",
                        Component.translatable("dialogue.epic_merchant.shop_menu.choice_tools").getString(),
                        c -> c.giveItem("minecraft:iron_pickaxe", 1).giveItem("minecraft:iron_axe", 1).close())
                // 链接到旅行商人商店
                .choice("arc_quest:shop_menu_choice_open_shop",
                        Component.translatable("dialogue.epic_merchant.shop_menu.choice_open_shop").getString(),
                        c -> c.openTrade("arc_quest:merchant_shop"))
                .choice("arc_quest:shop_menu_choice_back",
                        Component.translatable("dialogue.epic_merchant.shop_menu.choice_back").getString(),
                        c -> c.goTo("start"))

                .build()
        );

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_merchant");
    }

    /**
     * 流浪商人 —— 使用 sayIf() 的示例 NPC
     * <p>
     * 展示如何根据任务状态动态显示不同的对话文本。
     */
    private static void registerWanderingTrader() {
        ArcQuestAPI.registerDialogueTree(
            DialogueTreeBuilder.create("arc_quest:epic_wandering_trader")
                .npc(Component.translatable("dialogue.epic_wandering_trader.npc_name").getString())

                // ═══════════════════════════════════════════
                // 起始节点 - 使用 sayIf() 动态显示文本
                // ═══════════════════════════════════════════
                .node("start")

                // 情况1：完全新手
                .sayIf(
                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                        Component.translatable("dialogue.epic_wandering_trader.start.newbie").getString(),
                        "arc_quest:wandering_newbie"
                )

                // 情况2：序章进行中
                .sayIf(
                        new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                        Component.translatable("dialogue.epic_wandering_trader.start.prologue_active").getString(),
                        "arc_quest:wandering_prologue_active"
                )

                // 情况3：序章完成，第一章未开始
                .sayIf(
                        new DialogueCondition.All(List.of(
                                new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                                new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                        )),
                        Component.translatable("dialogue.epic_wandering_trader.start.chapter1_ready").getString(),
                        "arc_quest:wandering_chapter1_ready"
                )

                // 情况4：第一章进行中
                .sayIf(
                        new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_wandering_trader.start.chapter1_active").getString(),
                        "arc_quest:wandering_chapter1_active"
                )

                // 情况5：已完成终章（传奇英雄）
                .sayIf(
                        new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                        Component.translatable("dialogue.epic_wandering_trader.start.legendary").getString(),
                        "arc_quest:wandering_legendary"
                )

                // 兜底文本
                .say(Component.translatable("dialogue.epic_wandering_trader.start.default").getString(), "arc_quest:wandering_default")

                // 选项
                .choice("arc_quest:wandering_choice_shop",
                        Component.translatable("dialogue.epic_wandering_trader.start.choice_shop").getString(),
                        c -> c.goTo("shop_menu"))
                .choice("arc_quest:wandering_choice_rumors",
                        Component.translatable("dialogue.epic_wandering_trader.start.choice_rumors").getString(),
                        c -> c.goTo("rumors"))
                .choice("arc_quest:wandering_choice_bye",
                        Component.translatable("dialogue.epic_wandering_trader.start.choice_bye").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 商店菜单
                // ═══════════════════════════════════════════
                .node("shop_menu")
                .say(Component.translatable("dialogue.epic_wandering_trader.shop_menu.text").getString(), "arc_quest:wandering_shop_menu_text")
                .choice("arc_quest:shop_menu_choice_supplies",
                        Component.translatable("dialogue.epic_wandering_trader.shop_menu.choice_supplies").getString(),
                        c -> c.presetTorches().presetBread().goTo("purchase_complete"))
                .choice("arc_quest:shop_menu_choice_equipment",
                        Component.translatable("dialogue.epic_wandering_trader.shop_menu.choice_equipment").getString(),
                        c -> c.goTo("equipment_menu"))
                // 链接到流浪商人商店
                .choice("arc_quest:shop_menu_choice_open_shop",
                        Component.translatable("dialogue.epic_wandering_trader.shop_menu.choice_open_shop").getString(),
                        c -> c.openTrade("arc_quest:wandering_trader_shop"))
                .choice("arc_quest:shop_menu_choice_back",
                        Component.translatable("dialogue.epic_wandering_trader.shop_menu.choice_back").getString(),
                        c -> c.goTo("start"))

                // ═══════════════════════════════════════════
                // 装备菜单
                // ═══════════════════════════════════════════
                .node("equipment_menu")
                .say(Component.translatable("dialogue.epic_wandering_trader.equipment_menu.text").getString(), "arc_quest:wandering_equipment_menu_text")

                // 根据任务阶段显示不同装备
                .choiceIf(
                        "arc_quest:equipment_choice_stone",
                        new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "arc_quest:gather_wood"),
                        Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_stone").getString(),
                        c -> c.presetStoneSword().goTo("purchase_complete")
                )
                .choiceIf(
                        "arc_quest:equipment_choice_iron",
                        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                        Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_iron").getString(),
                        c -> c.presetIronArmorSet().goTo("purchase_complete")
                )
                .choiceIf(
                        "arc_quest:equipment_choice_diamond",
                        new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                        Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_diamond").getString(),
                        c -> c.presetDiamondArmorSet().goTo("purchase_complete")
                )
                .choice("arc_quest:equipment_menu_choice_back",
                        Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_back").getString(),
                        c -> c.goTo("shop_menu"))

                // ═══════════════════════════════════════════
                // 购买完成
                // ═══════════════════════════════════════════
                .node("purchase_complete")
                .say(Component.translatable("dialogue.epic_wandering_trader.purchase_complete.text").getString(), "arc_quest:wandering_purchase_complete_text")
                .choice("arc_quest:purchase_complete_choice_continue",
                        Component.translatable("dialogue.epic_wandering_trader.purchase_complete.choice_continue").getString(),
                        c -> c.goTo("start"))

                // ═══════════════════════════════════════════
                // 传闻
                // ═══════════════════════════════════════════
                .node("rumors")
                .say(Component.translatable("dialogue.epic_wandering_trader.rumors.text").getString(), "arc_quest:wandering_rumors_text")
                .choice("arc_quest:rumors_choice_back",
                        Component.translatable("dialogue.epic_wandering_trader.rumors.choice_back").getString(),
                        c -> c.goTo("start"))

                .build()
        );

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_wandering_trader (with sayIf)");
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
                .npc(Component.translatable("dialogue.epic_village_guard.npc_name").getString())

                // ═══════════════════════════════════════════
                // 起始节点 - 根据时间段显示不同问候语
                // ═══════════════════════════════════════════
                .node("start")

                // 早晨问候（6:00-12:00）
                .sayIf(
                        new DialogueCondition.IsMorning(),
                        Component.translatable("dialogue.epic_village_guard.start.morning").getString(),
                        "arc_quest:guard_morning"
                )

                // 下午问候（12:00-18:00）
                .sayIf(
                        new DialogueCondition.IsAfternoon(),
                        Component.translatable("dialogue.epic_village_guard.start.afternoon").getString(),
                        "arc_quest:guard_afternoon"
                )

                // 夜晚问候（18:00-次日6:00）
                .sayIf(
                        new DialogueCondition.IsNight(),
                        Component.translatable("dialogue.epic_village_guard.start.night").getString(),
                        "arc_quest:guard_night"
                )

                // 兜底文本
                .say(Component.translatable("dialogue.epic_village_guard.start.default").getString(), "arc_quest:guard_default")

                // 选项1：首次见面介绍（一次性）
                .choiceIf(
                        "arc_quest:guard_choice_intro",
                        new DialogueCondition.Not(new DialogueCondition.NodeVisited("intro")),
                        Component.translatable("dialogue.epic_village_guard.start.choice_intro").getString(),
                        c -> c.goTo("intro")
                )

                // 选项2：每日任务（每天6:00重置）- 使用时间段条件
                .choiceIf(
                        "arc_quest:guard_choice_daily",
                        new DialogueCondition.IsMorning(),  // 只在早晨显示
                        Component.translatable("dialogue.epic_village_guard.start.choice_daily").getString(),
                        c -> {
                            c.goTo("daily_patrol");
                            c.cooldownGameTick(0);
                        }
                )

                // 选项3：训练请求（1小时冷却）
                .choice(
                        "arc_quest:guard_choice_training",
                        Component.translatable("dialogue.epic_village_guard.start.choice_training").getString(),
                        c -> {
                            c.cooldown(3600);
                            c.goTo("training");
                        }
                )

                // 选项4：闲聊
                .choice(
                        "arc_quest:guard_choice_chat",
                        Component.translatable("dialogue.epic_village_guard.start.choice_chat").getString(),
                        c -> c.goTo("chat")
                )

                // 选项5：快速补给（简易弹窗）
                .choice("arc_quest:guard_choice_quick_trade",
                        Component.translatable("dialogue.epic_village_guard.start.choice_quick_trade").getString(),
                        c -> c.openSimpleTrade("arc_quest:quick_supplies"))

                // 选项6：告别
                .choice(
                        "arc_quest:guard_choice_bye",
                        Component.translatable("dialogue.epic_village_guard.start.choice_bye").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close
                )

                // ═══════════════════════════════════════════
                // 首次介绍节点
                // ═══════════════════════════════════════════
                .node("intro")
                .say(Component.translatable("dialogue.epic_village_guard.intro.text").getString(), "arc_quest:guard_intro_text")
                .choice("arc_quest:intro_choice_ok",
                        Component.translatable("dialogue.epic_village_guard.intro.choice_ok").getString(),
                        c -> c.goTo("start"))

                // ═══════════════════════════════════════════
                // 每日巡逻任务（每天早上6点重置）
                // ═══════════════════════════════════════════
                .node("daily_patrol")
                .say(Component.translatable("dialogue.epic_village_guard.daily_patrol.text").getString(), "arc_quest:guard_daily_patrol_text")
                .choice("arc_quest:daily_patrol_choice_thanks",
                        Component.translatable("dialogue.epic_village_guard.daily_patrol.choice_thanks").getString(),
                        c -> {
                            c.giveItem("minecraft:bread", 3);
                            c.giveXp(10);
                            c.close();
                        })

                // ═══════════════════════════════════════════
                // 训练请求（1小时冷却）
                // ═══════════════════════════════════════════
                .node("training")
                .say(Component.translatable("dialogue.epic_village_guard.training.text").getString(), "arc_quest:guard_training_text")
                .choice("arc_quest:training_choice_thanks",
                        Component.translatable("dialogue.epic_village_guard.training.choice_thanks").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 闲聊节点（根据时间段显示不同内容）
                // ═══════════════════════════════════════════
                .node("chat")

                // 早晨闲聊
                .sayIf(
                        new DialogueCondition.IsMorning(),
                        Component.translatable("dialogue.epic_village_guard.chat.morning").getString(),
                        "arc_quest:chat_morning"
                )

                // 下午闲聊
                .sayIf(
                        new DialogueCondition.IsAfternoon(),
                        Component.translatable("dialogue.epic_village_guard.chat.afternoon").getString(),
                        "arc_quest:chat_afternoon"
                )

                // 夜晚闲聊
                .sayIf(
                        new DialogueCondition.IsNight(),
                        Component.translatable("dialogue.epic_village_guard.chat.night").getString(),
                        "arc_quest:chat_night"
                )

                .say(Component.translatable("dialogue.epic_village_guard.chat.default").getString(), "arc_quest:guard_chat_default")
                .choice("arc_quest:chat_choice_back",
                        Component.translatable("dialogue.epic_village_guard.chat.choice_back").getString(),
                        c -> c.goTo("start"))

                .build()
        );

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_village_guard (with time-based features)");
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
                .npc(Component.translatable("dialogue.epic_mysterious_merchant.npc_name").getString())

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
                        Component.translatable("dialogue.epic_mysterious_merchant.start.open").getString(),
                        "arc_quest:mysterious_open"
                )

                // 情况2：早晨关闭（6:00-12:00）
                .sayIf(
                        new DialogueCondition.All(List.of(
                                new DialogueCondition.Not(new DialogueCondition.GameTimeInRange(16000, 0)),
                                new DialogueCondition.IsMorning()
                        )),
                        Component.translatable("dialogue.epic_mysterious_merchant.start.closed_morning").getString(),
                        "arc_quest:mysterious_closed_morning"
                )

                // 情况3：下午关闭（12:00-18:00）
                .sayIf(
                        new DialogueCondition.All(List.of(
                                new DialogueCondition.Not(new DialogueCondition.GameTimeInRange(16000, 0)),
                                new DialogueCondition.IsAfternoon()
                        )),
                        Component.translatable("dialogue.epic_mysterious_merchant.start.closed_afternoon").getString(),
                        "arc_quest:mysterious_closed_afternoon"
                )

                // 情况4：傍晚/夜晚关闭（18:00-22:00）- 使用默认文本

                // 默认文本
                .say(Component.translatable("dialogue.epic_mysterious_merchant.start.default").getString(), "arc_quest:mysterious_default")

                // 选项1：购买商品（仅在深夜开放）
                .choiceIf(
                        "arc_quest:mysterious_choice_shop",
                        new DialogueCondition.GameTimeInRange(16000, 0),  // 跨天区间：22:00→次日6:00
                        Component.translatable("dialogue.epic_mysterious_merchant.start.choice_shop").getString(),
                        c -> {
                            c.goTo("shop");
                        }
                )

                // 选项2：询问身份（首次见面）
                .choiceIf(
                        "arc_quest:mysterious_choice_identity",
                        new DialogueCondition.Not(new DialogueCondition.DialogueCompleted("arc_quest:epic_mysterious_merchant")),
                        Component.translatable("dialogue.epic_mysterious_merchant.start.choice_identity").getString(),
                        c -> c.goTo("identity")
                )

                // 选项3：离开
                .choice("arc_quest:mysterious_choice_leave",
                        Component.translatable("dialogue.epic_mysterious_merchant.start.choice_leave").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 商店节点
                // ═══════════════════════════════════════════
                .node("shop")
                .say(Component.translatable("dialogue.epic_mysterious_merchant.shop.text").getString(), "arc_quest:mysterious_shop_text")

                // 链接到神秘商人商店（替代原本的直接给予物品）
                .choice(
                        "arc_quest:mysterious_shop_choice_rare",
                        Component.translatable("dialogue.epic_mysterious_merchant.shop.choice_rare").getString(),
                        c -> c.openTrade("arc_quest:mysterious_merchant_shop").close()
                )

                // 返回
                .choice("arc_quest:mysterious_shop_choice_back",
                        Component.translatable("dialogue.epic_mysterious_merchant.shop.choice_back").getString(),
                        c -> c.goTo("start"))

                // ═══════════════════════════════════════════
                // 身份揭示节点
                // ═══════════════════════════════════════════
                .node("identity")
                .say(Component.translatable("dialogue.epic_mysterious_merchant.identity.text").getString(), "arc_quest:mysterious_identity_text")
                .choice("arc_quest:identity_choice_back",
                        Component.translatable("dialogue.epic_mysterious_merchant.identity.choice_back").getString(),
                        c -> c.goTo("start"))

                .build()
        );

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_mysterious_merchant (with custom time ranges)");
    }
}
