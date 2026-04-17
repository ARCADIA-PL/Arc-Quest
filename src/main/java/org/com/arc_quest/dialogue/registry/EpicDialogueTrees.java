package org.com.arc_quest.dialogue.registry;

import net.minecraft.network.chat.Component;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.DialogueCondition;
import org.com.arc_quest.dialogue.builder.DialogueTreeBuilder;

import java.util.List;

/**
 * 史诗主线任务配套对话树 —— 展示与 EpicMainlineDemo 的深度联动。
 */
public final class EpicDialogueTrees {

    private EpicDialogueTrees() {
    }

    public static void registerAll() {
        registerVillageElder();
        registerBlacksmith();
        registerMerchant();
        registerWanderingTrader(); //使用 sayIf() 的示例

        // 绑定 NPC
        DialogueRegistry.INSTANCE.bindNpc("village_elder", "epic_village_elder");
        DialogueRegistry.INSTANCE.bindNpc("blacksmith", "epic_blacksmith");
        DialogueRegistry.INSTANCE.bindNpc("merchant", "epic_merchant");
        DialogueRegistry.INSTANCE.bindNpc("wandering_trader", "epic_wandering_trader"); // ← 新增
    }

    /**
     * 村庄长老 —— 任务发布者
     * 
     * <p>新增条件类型示例（对话历史状态检查）：
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
        DialogueTreeBuilder.create("epic_village_elder")
                .npc(Component.translatable("dialogue.epic_village_elder.npc_name").getString())

                // ═══════════════════════════════════════════
                // 起始节点 - 使用 sayIf() 动态显示文本
                // ═══════════════════════════════════════════
                .node("start")
                
                // 情况1：完全新手
                .sayIf(
                    new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                    Component.translatable("dialogue.epic_village_elder.start.newcomer").getString()
                )
                
                // 情况2：序章进行中 - gather_wood 阶段
                .sayIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"),
                    Component.translatable("dialogue.epic_village_elder.start.phase_gather_wood").getString()
                )
                
                // 情况3：序章进行中 - talk_villager 阶段
                .sayIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "talk_villager"),
                    Component.translatable("dialogue.epic_village_elder.start.phase_talk_villager").getString()
                )
                
                // 情况4：序章进行中 - defend_village 阶段
                .sayIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "defend_village"),
                    Component.translatable("dialogue.epic_village_elder.start.phase_defend").getString()
                )
                
                // 情况5：序章已完成，但第一章未完成
                .sayIf(
                    new DialogueCondition.All(List.of(
                        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                    )),
                    Component.translatable("dialogue.epic_village_elder.start.prologue_done").getString()
                )
                
                // 情况6：第一章已完成，等待分支选择
                .sayIf(
                    new DialogueCondition.All(List.of(
                        new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_branch_choice"))
                    )),
                    Component.translatable("dialogue.epic_village_elder.start.chapter1_done").getString()
                )
                
                // 情况7：已完成终章（传奇英雄）
                .sayIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                    Component.translatable("dialogue.epic_village_elder.start.legendary").getString()
                )
                
                // 默认文本（兜底）
                .say(Component.translatable("dialogue.epic_village_elder.start.default").getString())
                
                // 选项 - 根据任务状态显示不同选项
                .choiceIf(
                    new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                    Component.translatable("dialogue.epic_village_elder.start.choice_intro").getString(),
                    c -> c.goTo("intro_story")
                )
                
                .choiceIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "talk_villager"),
                    Component.translatable("dialogue.epic_village_elder.start.choice_accept_mission").getString(),
                    c -> c
                        .notifyInteract("arc_quest:village_elder")
                        .close()
                )
                
                .choiceIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "defend_village"),
                    Component.translatable("dialogue.epic_village_elder.start.choice_encourage").getString(),
                    c -> c
                        .giveItem("minecraft:potion{Potion:\"minecraft:strength\"}", 1)
                        .close()
                )
                
                .choiceIf(
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
                    new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                    Component.translatable("dialogue.epic_village_elder.start.choice_legendary").getString(),
                    c -> c
                        .giveXp(100)
                        .presetHeroOfVillage()  // ← 使用预设动作
                        .close()
                )
                
                // 默认选项（无条件显示）
                .choice(Component.translatable("dialogue.epic_village_elder.start.choice_default").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 介绍故事背景
                // ═══════════════════════════════════════════
                .node("intro_story")
                .say(Component.translatable("dialogue.epic_village_elder.intro_story.text").getString())
                .choice(Component.translatable("dialogue.epic_village_elder.intro_story.choice_accept").getString(),
                        c -> c
                            .startQuest("arc_quest:epic_prologue")
                            .goTo("prologue_accepted"))
                .choice(Component.translatable("dialogue.epic_village_elder.intro_story.choice_decline").getString(),
                        c -> c.goTo("decline"))

                // ═══════════════════════════════════════════
                // 序章已接受
                // ═══════════════════════════════════════════
                .node("prologue_accepted")
                .say(Component.translatable("dialogue.epic_village_elder.prologue_accepted.text").getString())
                .choice(Component.translatable("dialogue.epic_village_elder.prologue_accepted.choice_ok").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 第一章已接受
                // ═══════════════════════════════════════════
                .node("chapter1_accepted")
                .say(Component.translatable("dialogue.epic_village_elder.chapter1_accepted.text").getString())
                .choice(Component.translatable("dialogue.epic_village_elder.chapter1_accepted.choice_ok").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 分支选择介绍
                // ═══════════════════════════════════════════
                .node("branch_introduction")
                .say(Component.translatable("dialogue.epic_village_elder.branch_introduction.text").getString())
                .choice(Component.translatable("dialogue.epic_village_elder.branch_introduction.choice_combat").getString(),
                        c -> c.goTo("explain_combat"))
                .choice(Component.translatable("dialogue.epic_village_elder.branch_introduction.choice_exploration").getString(),
                        c -> c.goTo("explain_exploration"))
                .choice(Component.translatable("dialogue.epic_village_elder.branch_introduction.choice_think").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 解释战斗路线
                // ═══════════════════════════════════════════
                .node("explain_combat")
                .say(Component.translatable("dialogue.epic_village_elder.explain_combat.text").getString())
                .choice(Component.translatable("dialogue.epic_village_elder.explain_combat.choice_confirm").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 解释探索路线
                // ═══════════════════════════════════════════
                .node("explain_exploration")
                .say(Component.translatable("dialogue.epic_village_elder.explain_exploration.text").getString())
                .choice(Component.translatable("dialogue.epic_village_elder.explain_exploration.choice_confirm").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 拒绝节点
                // ═══════════════════════════════════════════
                .node("decline")
                .say(Component.translatable("dialogue.epic_village_elder.decline.text").getString())
                .choice(Component.translatable("dialogue.epic_village_elder.decline.choice1").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                .buildAndRegister();

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_village_elder");
    }

    /**
     * 铁匠 —— 装备升级商人
     */
    private static void registerBlacksmith() {
        DialogueTreeBuilder.create("epic_blacksmith")
                .npc(Component.translatable("dialogue.epic_blacksmith.npc_name").getString())

                .node("start")
                
                // 使用 sayIf() 根据任务状态显示不同文本
                .sayIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                    Component.translatable("dialogue.epic_blacksmith.start.early").getString()
                )
                .sayIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_blacksmith.start.mid").getString()
                )
                .sayIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_blacksmith.start.late").getString()
                )
                .sayIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                    Component.translatable("dialogue.epic_blacksmith.start.legendary").getString()
                )
                .say(Component.translatable("dialogue.epic_blacksmith.start.default").getString())
                
                // 选项 - 使用预设动作
                .choiceIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                    Component.translatable("dialogue.epic_blacksmith.start.choice_buy_stone_sword").getString(),
                    c -> c.presetStoneSword().close()  // ← 使用预设动作
                )
                
                .choiceIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_blacksmith.start.choice_buy_iron_gear").getString(),
                    c -> c.presetIronArmorSet().close()  // ← 使用预设动作
                )
                
                .choiceIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_blacksmith.start.choice_buy_diamond_gear").getString(),
                    c -> c.presetDiamondArmorSet().close()  // ← 使用预设动作
                )
                
                .choiceIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                    Component.translatable("dialogue.epic_blacksmith.start.choice_buy_netherite").getString(),
                    c -> c.presetNetheriteIngot().close()  // ← 使用预设动作
                )
                
                .choice(Component.translatable("dialogue.epic_blacksmith.start.choice_leave").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                .buildAndRegister();

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_blacksmith");
    }

    /**
     * 商人 —— 物资供应商
     */
    private static void registerMerchant() {
        DialogueTreeBuilder.create("epic_merchant")
                .npc(Component.translatable("dialogue.epic_merchant.npc_name").getString())

                .node("start")
                
                // 使用 sayIf() 根据任务阶段显示不同文本
                .sayIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"),
                    Component.translatable("dialogue.epic_merchant.start.phase_gather").getString()
                )
                .sayIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "defend_village"),
                    Component.translatable("dialogue.epic_merchant.start.phase_defend").getString()
                )
                .sayIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_merchant.start.phase_chapter1").getString()
                )
                .say(Component.translatable("dialogue.epic_merchant.start.default").getString())
                
                // 选项 - 使用预设动作
                .choiceIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"),
                    Component.translatable("dialogue.epic_merchant.start.choice_buy_food").getString(),
                    c -> c.presetBread().close()  // ← 使用预设动作
                )
                
                .choiceIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "defend_village"),
                    Component.translatable("dialogue.epic_merchant.start.choice_buy_potions").getString(),
                    c -> c.giveItem("minecraft:potion{Potion:\"minecraft:healing\"}", 2).close()
                )
                
                .choiceIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_merchant.start.choice_buy_torches").getString(),
                    c -> c.presetTorches().close()  // ← 使用预设动作
                )
                
                .choice(Component.translatable("dialogue.epic_merchant.start.choice_browse").getString(),
                        c -> c.goTo("shop_menu"))
                .choice(Component.translatable("dialogue.epic_merchant.start.choice_leave").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                .node("shop_menu")
                .say(Component.translatable("dialogue.epic_merchant.shop_menu.text").getString())
                .choice(Component.translatable("dialogue.epic_merchant.shop_menu.choice_food").getString(),
                        c -> c.presetBread().presetTorches().close())  // ← 使用预设动作
                .choice(Component.translatable("dialogue.epic_merchant.shop_menu.choice_tools").getString(),
                        c -> c.giveItem("minecraft:iron_pickaxe", 1).giveItem("minecraft:iron_axe", 1).close())
                .choice(Component.translatable("dialogue.epic_merchant.shop_menu.choice_back").getString(),
                        c -> c.goTo("start"))

                .buildAndRegister();

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_merchant");
    }

    /**
     * 流浪商人 —— 使用 sayIf() 的示例 NPC
     * <p>
     * 展示如何根据任务状态动态显示不同的对话文本。
     */
    private static void registerWanderingTrader() {
        DialogueTreeBuilder.create("epic_wandering_trader")
                .npc(Component.translatable("dialogue.epic_wandering_trader.npc_name").getString())

                // ═══════════════════════════════════════════
                // 起始节点 - 使用 sayIf() 动态显示文本
                // ═══════════════════════════════════════════
                .node("start")
                
                // 情况1：完全新手
                .sayIf(
                    new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_prologue")),
                    Component.translatable("dialogue.epic_wandering_trader.start.newbie").getString()
                )
                
                // 情况2：序章进行中
                .sayIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_prologue"),
                    Component.translatable("dialogue.epic_wandering_trader.start.prologue_active").getString()
                )
                
                // 情况3：序章完成，第一章未开始
                .sayIf(
                    new DialogueCondition.All(List.of(
                        new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                        new DialogueCondition.Not(new DialogueCondition.HasQuest("arc_quest:epic_chapter1"))
                    )),
                    Component.translatable("dialogue.epic_wandering_trader.start.chapter1_ready").getString()
                )
                
                // 情况4：第一章进行中
                .sayIf(
                    new DialogueCondition.QuestActive("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_wandering_trader.start.chapter1_active").getString()
                )
                
                // 情况5：已完成终章（传奇英雄）
                .sayIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_finale"),
                    Component.translatable("dialogue.epic_wandering_trader.start.legendary").getString()
                )
                
                // 默认文本（兜底）
                .say(Component.translatable("dialogue.epic_wandering_trader.start.default").getString())
                
                // 选项
                .choice(Component.translatable("dialogue.epic_wandering_trader.start.choice_shop").getString(),
                        c -> c.goTo("shop_menu"))
                .choice(Component.translatable("dialogue.epic_wandering_trader.start.choice_rumors").getString(),
                        c -> c.goTo("rumors"))
                .choice(Component.translatable("dialogue.epic_wandering_trader.start.choice_bye").getString(),
                        DialogueTreeBuilder.ChoiceBuilder::close)

                // ═══════════════════════════════════════════
                // 商店菜单
                // ═══════════════════════════════════════════
                .node("shop_menu")
                .say(Component.translatable("dialogue.epic_wandering_trader.shop_menu.text").getString())
                .choice(Component.translatable("dialogue.epic_wandering_trader.shop_menu.choice_supplies").getString(),
                        c -> c.presetTorches().presetBread().goTo("purchase_complete"))
                .choice(Component.translatable("dialogue.epic_wandering_trader.shop_menu.choice_equipment").getString(),
                        c -> c.goTo("equipment_menu"))
                .choice(Component.translatable("dialogue.epic_wandering_trader.shop_menu.choice_back").getString(),
                        c -> c.goTo("start"))

                // ═══════════════════════════════════════════
                // 装备菜单
                // ═══════════════════════════════════════════
                .node("equipment_menu")
                .say(Component.translatable("dialogue.epic_wandering_trader.equipment_menu.text").getString())
                
                // 根据任务阶段显示不同装备
                .choiceIf(
                    new DialogueCondition.QuestPhase("arc_quest:epic_prologue", "gather_wood"),
                    Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_stone").getString(),
                    c -> c.presetStoneSword().goTo("purchase_complete")
                )
                .choiceIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_prologue"),
                    Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_iron").getString(),
                    c -> c.presetIronArmorSet().goTo("purchase_complete")
                )
                .choiceIf(
                    new DialogueCondition.QuestCompleted("arc_quest:epic_chapter1"),
                    Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_diamond").getString(),
                    c -> c.presetDiamondArmorSet().goTo("purchase_complete")
                )
                .choice(Component.translatable("dialogue.epic_wandering_trader.equipment_menu.choice_back").getString(),
                        c -> c.goTo("shop_menu"))

                // ═══════════════════════════════════════════
                // 购买完成
                // ═══════════════════════════════════════════
                .node("purchase_complete")
                .say(Component.translatable("dialogue.epic_wandering_trader.purchase_complete.text").getString())
                .choice(Component.translatable("dialogue.epic_wandering_trader.purchase_complete.choice_continue").getString(),
                        c -> c.goTo("start"))

                // ═══════════════════════════════════════════
                // 传闻
                // ═══════════════════════════════════════════
                .node("rumors")
                .say(Component.translatable("dialogue.epic_wandering_trader.rumors.text").getString())
                .choice(Component.translatable("dialogue.epic_wandering_trader.rumors.choice_back").getString(),
                        c -> c.goTo("start"))

                .buildAndRegister();

        Arc_quest.LOGGER.info("[ArcQuest] Registered epic dialogue: epic_wandering_trader (with sayIf)");
    }
}
