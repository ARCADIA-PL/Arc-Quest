// ═══════════════════════════════════════════════════════════
// 文件: org/com/arc_quest/data/ArcQuestZHLangProvider.java
// ═══════════════════════════════════════════════════════════
package org.com.arc_quest.data;

import net.minecraft.data.PackOutput;

/**
 * 中文语言文件生成器。
 * <p>
 * 翻译基调：日式轻小说 / 二次元风格，带有中二气息的修饰语。
 */
public class ArcQuestZHLangProvider extends ArcQuestLangProvider {

    public ArcQuestZHLangProvider(PackOutput output) {
        super(output, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        addScreenTitles();
        addGuiTranslations();
        addHudTranslations();
        addToastTranslations();
        addCommandTranslations();
        addDialogueTranslations();

        // ── 任务链 ──
        addPrologueQuest();
        addChapter1Quest();
        addChapter2CombatQuest();
        addChapter2ExplorationQuest();
        addFinaleQuest();
        addBranchChoiceQuest();
    }

    // ═══════════════════════════════════════════════════════
    //  屏幕标题
    // ═══════════════════════════════════════════════════════

    private void addScreenTitles() {
        add("gui.arc_quest.journal.title", "任务日志");
    }

    // ═══════════════════════════════════════════════════════
    //  GUI 界面文本
    // ═══════════════════════════════════════════════════════

    private void addGuiTranslations() {
        // ── Journal Tabs ──
        addGuiTab("journal", "active", "进行中");
        addGuiTab("journal", "completed", "已完成");
        addGuiTab("journal", "failed", "已失败");

        // ── Journal Sections ──
        addGuiSection("journal", "current_phase", "▸ 当前阶段: %s");
        addGuiSection("journal", "completed_phases", "▸ 已通过阶段");
        addGuiSection("journal", "choose_path", "▸ 选择你的道路");

        // ── Journal Labels ──
        addGuiLabel("journal", "no_phases_completed",
                "§7尚无已完成的阶段。");
        addGuiLabel("journal", "quest_completed",
                "§a✔ 该任务已完成。");
        addGuiLabel("journal", "quest_failed",
                "§c✘ 该任务已失败。");
        addGuiLabel("journal", "select_quest", "选择一个任务");
        addGuiLabel("journal", "objective_complete_prefix", "§a✔ ");
        addGuiLabel("journal", "objective_active_prefix", "§f○ ");

        // ── Journal Buttons ──
        addGuiButton("journal", "track", "⊕ 追踪");
        addGuiButton("journal", "tracked", "§a⊕ 追踪中");
        addGuiButton("journal", "abandon", "✘ 放弃");
        addGuiButton("journal", "restart", "↻ 重新开始");
    }

    // ═══════════════════════════════════════════════════════
    //  HUD 文本
    // ═══════════════════════════════════════════════════════

    private void addHudTranslations() {
        addHudText("new_phase", "ARC QUEST // 新阶段");
        addHudText("phase_prefix", "▸ %s");
        addHudText("objective_complete_prefix", "§a✔ ");
        addHudText("objective_active_prefix", "§7○ ");
    }

    // ═══════════════════════════════════════════════════════
    //  Toast 提示
    // ═══════════════════════════════════════════════════════

    private void addToastTranslations() {
        addToast("branch_available", "分支可用: %s");
        addToast("quest_accepted", "§a任务已接受");
        addToast("quest_completed", "§2任务已完成");
        addToast("quest_failed", "§c任务失败");
        addToast("phase_advanced", "阶段推进");
    }

    // ═══════════════════════════════════════════════════════
    //  命令反馈
    // ═══════════════════════════════════════════════════════

    private void addCommandTranslations() {
        // ── give ──
        addCommandFeedback("give", "success",
                "已将任务 §e%1$s§f 授予 %2$s。");
        addCommandFeedback("give", "error.already_active",
                "任务 §e%1$s§f 在 %2$s 处已为激活状态。");
        addCommandFeedback("give", "error.already_completed",
                "任务 §e%1$s§f 已完成且不可重复。");
        addCommandFeedback("give", "error.failed",
                "启动任务 '%1$s' 失败。");

        // ── complete ──
        addCommandFeedback("complete", "success",
                "已强制完成 %2$s 的任务 §e%1$s§f。");
        addCommandFeedback("complete", "error.not_active",
                "任务 '%1$s' 未处于激活状态。");

        // ── fail ──
        addCommandFeedback("fail", "success",
                "已强制失败 %2$s 的任务 §e%1$s§f。");

        // ── reset ──
        addCommandFeedback("reset", "success",
                "已重置 %2$s 的任务 §e%1$s§f。");

        // ── phase ──
        addCommandFeedback("phase", "success",
                "已设置任务 §e%1$s§f → 阶段 §d%2$s§f，目标: %3$s。");
        addCommandFeedback("phase", "error.not_found",
                "在任务 '%2$s' 中未找到阶段 '%1$s'。");
        addCommandFeedback("phase", "error.wrong_state",
                "任务 '%1$s' 当前状态为 %2$s，无法切换阶段。");
        addCommandFeedback("phase", "error.failed",
                "激活任务 '%1$s' 失败。");

        // ── progress ──
        addCommandFeedback("progress", "success",
                "已设置任务 §e%3$s§f 的目标[%1$s] = %2$s。");
        addCommandFeedback("progress", "error.out_of_range",
                "目标索引 %1$s 超出范围（最大 %2$s）。");

        // ── list ──
        addCommandFeedback("list", "header",
                "§6═══ %1$s 的任务列表 ═══");
        addCommandFeedback("list", "no_quests",
                "§7  暂无任务。");
        addCommandFeedback("list", "specify_player",
                "请指定玩家: /arcquest list <player>");
        addCommandFeedback("list", "no_capability",
                "§c  未找到 Capability。");
        addCommandFeedback("list", "completed_history",
                "§2  完成历史: §f%1$s");

        // ── dialogue ──
        addCommandFeedback("dialogue", "success",
                "已为 %2$s 启动对话 §e%1$s§f。");
        addCommandFeedback("dialogue", "error.not_found",
                "对话 '%1$s' 未找到。");

        // ── resetall ──
        addCommandFeedback("resetall", "success",
                "已清除 %1$s 的所有任务数据。");

        // ── reload ──
        addCommandFeedback("reload", "success",
                "已触发数据包重载，定义将刷新。");

        // ── registry ──
        addCommandFeedback("registry", "header",
                "§6═══ ArcQuest 注册表 ═══");
        addCommandFeedback("registry", "quests_header",
                "§e  任务 (%1$s):");
        addCommandFeedback("registry", "dialogues_header",
                "§e  对话 (%1$s):");

        // ── debug ──
        addCommandFeedback("debug", "header",
                "§6═══ 调试: %1$s ═══");
        addCommandFeedback("debug", "display",
                "§e  显示名: §f%1$s");
        addCommandFeedback("debug", "phases",
                "§e  阶段数: §f%1$s");
        addCommandFeedback("debug", "repeatable",
                "§e  可重复: §f%1$s");
        addCommandFeedback("debug", "runtime_header",
                "§a  --- 玩家运行时 ---");
        addCommandFeedback("debug", "runtime_state",
                "§a  状态: %1$s");
        addCommandFeedback("debug", "runtime_phase",
                "§a  阶段: %1$s");
        addCommandFeedback("debug", "runtime_obj",
                "§a  目标[%1$s]: %2$s");
        addCommandFeedback("debug", "no_runtime",
                "§7  该玩家无此任务的运行时数据。");

        // ── 通用错误 ──
        addCommandFeedback("error", "invalid_id",
                "无效的任务ID格式: '%1$s'。");
        addCommandFeedback("error", "not_in_registry",
                "任务 '%1$s' 未在注册表中找到。");
        addCommandFeedback("error", "no_capability",
                "无法访问 %1$s 的任务数据，Capability 未附加。");
    }

    // ═══════════════════════════════════════════════════════
    //  对话系统文本
    // ═══════════════════════════════════════════════════════

    private void addDialogueTranslations() {
        // ── Screen Titles ──
        add("screen.phase_choice.title", "阶段选择");
        add("screen.dialogue.title", "对话");

        // ── Phase Choice Panel ──
        add("screen.phase_choice.choose_path", "选择你的道路");

        // ── Dialogue Hints ──
        add("dialogue.speaker_prefix", "▸ ");
        add("dialogue.hint.close", "[点击或按空格键关闭]");
        add("dialogue.hint.loading", "...");
        add("dialogue.hint.continue", "[点击继续]");

        // ── Test Villager Dialogue ──
        add("dialogue.test_villager.npc_name", "村民");
        add("dialogue.test_villager.start.text",
                "§f你好，冒险者！村庄最近不太平……");
        add("dialogue.test_villager.start.choice1",
                "发生了什么事？");
        add("dialogue.test_villager.start.choice2",
                "再见");
        add("dialogue.test_villager.ask_problem.text",
                "§f僵尸在夜晚袭击村庄，我们需要帮助！\n"
                        + "你能收集一些物资吗？");
        add("dialogue.test_villager.ask_problem.choice1",
                "我会帮忙的！");
        add("dialogue.test_villager.ask_problem.choice2",
                "我现在没空");
        add("dialogue.test_villager.decline.text",
                "§f好吧……如果你改变主意，随时来找我。");
        add("dialogue.test_villager.decline.choice1",
                "再见");
    }

    // ═══════════════════════════════════════════════════════
    //  序章・崩坏的日常
    // ═══════════════════════════════════════════════════════

    private void addPrologueQuest() {
        addQuest("epic_prologue",
                "§6§l序章・崩坏的日常",
                "命运的齿轮开始转动——不死者的大军已经兵临城下。\n\n"
                        + "在一切化为灰烬之前，拿起武器，成为这片土地最后的守护者吧！");

        // ── Phase: gather_wood ──
        addPhase("epic_prologue", "gather_wood", "武装自己");
        addObjective("epic_prologue", "gather_wood", 0,
                "收集§6橡木原木§r (0/5)");

        // ── Phase: talk_villager ──
        addPhase("epic_prologue", "talk_villager", "寻求建议");
        addObjective("epic_prologue", "talk_villager", 0,
                "与§b村庄长老§r交谈");

        // ── Phase: craft_sword ──
        addPhase("epic_prologue", "craft_sword", "打造武器");
        addObjective("epic_prologue", "craft_sword", 0,
                "收集§f铁锭§r (0/3)");
        addObjective("epic_prologue", "craft_sword", 1,
                "收集§6木棍§r (0/2)");

        // ── Phase: defend_village ──
        addPhase("epic_prologue", "defend_village", "——保卫家园！");
        addObjective("epic_prologue", "defend_village", 0,
                "击退来袭的§c僵尸§r (0/10)");
        addObjective("epic_prologue", "defend_village", 1,
                "驱逐§e沙漠尸鬼§r (0/3)");

        // ── Phase: gather_food ──
        addPhase("epic_prologue", "gather_food", "筹备物资");
        addObjective("epic_prologue", "gather_food", 0,
                "收集§6面包§r (0/10)");
        addObjective("epic_prologue", "gather_food", 1,
                "收集§c熟牛肉§r (0/5)");
    }

    // ═══════════════════════════════════════════════════════
    //  第壹章・深渊的低语
    // ═══════════════════════════════════════════════════════

    private void addChapter1Quest() {
        addQuest("epic_chapter1",
                "§6§l第壹章・深渊的低语",
                "矿洞深处传来不属于这个世界的诡异回响……\n\n"
                        + "是谁在黑暗中窃窃私语？鼓起勇气，向深渊进发吧。");

        // ── Phase: prepare_tools ──
        addPhase("epic_chapter1", "prepare_tools", "准备装备");
        addObjective("epic_chapter1", "prepare_tools", 0,
                "收集§8煤炭§r (0/16)");
        addObjective("epic_chapter1", "prepare_tools", 1,
                "收集§6木棍§r (0/8)");

        // ── Phase: explore_depths ──
        addPhase("epic_chapter1", "explore_depths", "探索深处");
        addObjective("epic_chapter1", "explore_depths", 0,
                "清除§c洞穴蜘蛛§r (0/8)");
        addObjective("epic_chapter1", "explore_depths", 1,
                "收集§f蛛丝§r (0/10)");

        // ── Phase: find_resources ──
        addPhase("epic_chapter1", "find_resources", "开采矿脉");
        addObjective("epic_chapter1", "find_resources", 0,
                "采集§f铁矿石§r (0/8)");
        addObjective("epic_chapter1", "find_resources", 1,
                "采集§6金矿石§r (0/4)");

        // ── Phase: boss_fight ──
        addPhase("epic_chapter1", "boss_fight", "矿洞守卫");
        addObjective("epic_chapter1", "boss_fight", 0,
                "击败§4凋灵骷髅§r");
    }

    // ═══════════════════════════════════════════════════════
    //  第贰章A・烈焰的试炼
    // ═══════════════════════════════════════════════════════

    private void addChapter2CombatQuest() {
        addQuest("epic_chapter2_combat",
                "§c§l第贰章A・烈焰的试炼",
                "你选择了以钢铁与鲜血铸就的道路——直捣下界要塞的心脏！\n\n"
                        + "愿烈焰淬炼你的传说，或将你彻底吞噬。");

        // ── Phase: build_portal ──
        addPhase("epic_chapter2_combat", "build_portal", "打开通往炼狱的大门");
        addObjective("epic_chapter2_combat", "build_portal", 0,
                "收集§5黑曜石§r (0/10)");
        addObjective("epic_chapter2_combat", "build_portal", 1,
                "获得§f打火石§r");

        // ── Phase: enter_nether ──
        addPhase("epic_chapter2_combat", "enter_nether", "踏入下界");
        addObjective("epic_chapter2_combat", "enter_nether", 0,
                "击败§6烈焰人§r (0/5)");
        addObjective("epic_chapter2_combat", "enter_nether", 1,
                "收集§6烈焰棒§r (0/7)");

        // ── Phase: assault_fortress ──
        addPhase("epic_chapter2_combat", "assault_fortress", "烈焰突袭");
        addObjective("epic_chapter2_combat", "assault_fortress", 0,
                "击败§4凋灵骷髅§r (0/5)");
        addObjective("epic_chapter2_combat", "assault_fortress", 1,
                "收集§8下界砖§r (0/16)");
    }

    // ═══════════════════════════════════════════════════════
    //  第贰章B・深海的遗言
    // ═══════════════════════════════════════════════════════

    private void addChapter2ExplorationQuest() {
        addQuest("epic_chapter2_exploration",
                "§b§l第贰章B・深海的遗言",
                "你选择了追寻古老文明的道路——海底神殿正发出微弱的呼唤。\n\n"
                        + "波涛之下沉睡着被遗忘的力量，等待着被唤醒。");

        // ── Phase: prepare_underwater ──
        addPhase("epic_chapter2_exploration", "prepare_underwater", "潜水准备");
        addObjective("epic_chapter2_exploration", "prepare_underwater", 0,
                "收集§f玻璃§r (0/8)");
        addObjective("epic_chapter2_exploration", "prepare_underwater", 1,
                "收集§e沙子§r (0/16)");

        // ── Phase: dive_ocean ──
        addPhase("epic_chapter2_exploration", "dive_ocean", "深渊潜行");
        addObjective("epic_chapter2_exploration", "dive_ocean", 0,
                "击败§3守卫者§r (0/5)");
        addObjective("epic_chapter2_exploration", "dive_ocean", 1,
                "收集§b海晶砂粒§r (0/10)");

        // ── Phase: solve_puzzle ──
        addPhase("epic_chapter2_exploration", "solve_puzzle", "亘古的谜题");
        addObjective("epic_chapter2_exploration", "solve_puzzle", 0,
                "收集§b海晶碎片§r (0/16)");
        addObjective("epic_chapter2_exploration", "solve_puzzle", 1,
                "收集§e海绵§r (0/2)");

        // ── Phase: defeat_guardian ──
        addPhase("epic_chapter2_exploration", "defeat_guardian", "神殿的守护者");
        addObjective("epic_chapter2_exploration", "defeat_guardian", 0,
                "击败§3远古守卫者§r");
    }

    // ═══════════════════════════════════════════════════════
    //  终章・世界尽头的咏叹
    // ═══════════════════════════════════════════════════════

    private void addFinaleQuest() {
        addQuest("epic_finale",
                "§5§l终章・世界尽头的咏叹",
                "所有的道路在此汇聚——审判之刻已至。\n\n"
                        + "直面末影龙，将你的名字刻入永恒的星辰之中！");

        // ── Phase: prepare_ritual ──
        addPhase("epic_finale", "prepare_ritual", "末日准备");
        addObjective("epic_finale", "prepare_ritual", 0,
                "收集§5末影珍珠§r (0/16)");
        addObjective("epic_finale", "prepare_ritual", 1,
                "收集§6烈焰粉§r (0/8)");

        // ── Phase: craft_eyes ──
        addPhase("epic_finale", "craft_eyes", "锻造「虚空之眼」");
        addObjective("epic_finale", "craft_eyes", 0,
                "合成§5末影之眼§r (0/12)");

        // ── Phase: find_stronghold ──
        addPhase("epic_finale", "find_stronghold", "寻找遗迹要塞");
        addObjective("epic_finale", "find_stronghold", 0,
                "击败§7蠹虫§r (0/10)");
        addObjective("epic_finale", "find_stronghold", 1,
                "收集§f末地石§r (0/32)");

        // ── Phase: activate_portal ──
        addPhase("epic_finale", "activate_portal", "开启「末路之门」");
        addObjective("epic_finale", "activate_portal", 0,
                "放置§5末影之眼§r (0/12)");

        // ── Phase: enter_end ──
        addPhase("epic_finale", "enter_end", "踏足终末之地");
        addObjective("epic_finale", "enter_end", 0,
                "击败§5末影人§r (0/20)");

        // ── Phase: destroy_crystals ──
        addPhase("epic_finale", "destroy_crystals", "破除结界守护");
        addObjective("epic_finale", "destroy_crystals", 0,
                "清场——击败§5末影人§r (0/5)");

        // ── Phase: dragon_fight ──
        addPhase("epic_finale", "dragon_fight", "——终结之刻！");
        addObjective("epic_finale", "dragon_fight", 0,
                "击败§5末影龙§r");

        // ── Phase: return_home ──
        addPhase("epic_finale", "return_home", "英雄归来");
        addObjective("epic_finale", "return_home", 0,
                "获得§5龙首§r");
    }

    // ═══════════════════════════════════════════════════════
    //  抉择之章・命运的分岔路
    // ═══════════════════════════════════════════════════════

    private void addBranchChoiceQuest() {
        addQuest("epic_branch_choice",
                "§e§l抉择之章・命运的分岔路",
                "前方出现了岔路——每条道路都通往截然不同的命运。\n\n"
                        + "慎重选择吧，因为命运不会给你第二次机会。");

        // ── Phase: gather_info ──
        addPhase("epic_branch_choice", "gather_info", "收集情报");
        addObjective("epic_branch_choice", "gather_info", 0,
                "收集§a绿宝石§r (0/10)");
        addObjective("epic_branch_choice", "gather_info", 1,
                "收集§f书籍§r (0/5)");

        // ── Choices ──
        addQuestChoice("epic_branch_choice", "gather_info", 0,
                "§c我要挑战烈焰堡垒！燃烧吧，我的战魂！");
        addQuestChoice("epic_branch_choice", "gather_info", 1,
                "§b我想探索深海遗迹……那里一定藏着古老的秘密。");
        addQuestChoice("epic_branch_choice", "gather_info", 2,
                "§6§l【隐藏】全、都、要！这就是我的觉悟！");

        // ── Phase: path_combat ──
        addPhase("epic_branch_choice", "path_combat", "踏上征途");
        addObjective("epic_branch_choice", "path_combat", 0,
                "收集§4下界岩§r (0/32)");
        addObjective("epic_branch_choice", "path_combat", 1,
                "收集§6熔岩桶§r (0/2)");

        // ── Phase: path_exploration ──
        addPhase("epic_branch_choice", "path_exploration", "深海之旅");
        addObjective("epic_branch_choice", "path_exploration", 0,
                "收集§a海带§r (0/32)");
        addObjective("epic_branch_choice", "path_exploration", 1,
                "收集§f鳕鱼§r (0/10)");

        // ── Phase: path_both ──
        addPhase("epic_branch_choice", "path_both", "双修之路・觉醒");
        addObjective("epic_branch_choice", "path_both", 0,
                "击败§6烈焰人§r以证明实力 (0/3)");
        addObjective("epic_branch_choice", "path_both", 1,
                "收集§b海晶碎片§r (0/5)");
    }
}