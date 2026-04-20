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
        addEpicDialogueTranslations();
        addTradeTranslations();

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

        // ── 对话系统命令 ──
        add("arc_quest.command.dialogue.reset.all", "已重置玩家 %1$s 的所有对话进度");
        add("arc_quest.command.dialogue.reset.single", "已请求重置玩家 %2$s 的对话树 %1$s（功能开发中）");
        add("arc_quest.command.dialogue.status.header", "§e=== 对话状态 ===");
        add("arc_quest.command.dialogue.status.player", "§f玩家: %1$s");
        add("arc_quest.command.dialogue.status.work_in_progress", "§7(详细历史记录功能开发中...)");

        // ── 交易系统命令 ──
        add("arc_quest.command.trade.error.not_found", "未找到交易商店: %1$s");
        add("arc_quest.command.trade.open.success", "已为 %2$s 打开交易商店: %1$s");
        add("arc_quest.command.trade.simple.success", "已为 %2$s 打开简易交易: %1$s");
        add("arc_quest.command.trade.list.header", "§e=== 交易商店注册表 (%1$s) ===");
        add("arc_quest.command.trade.list.entry", "§f  %1$s §7- %2$s §8[%3$s entries%4$s]");
        add("arc_quest.command.trade.list.empty", "§7  (无已注册的交易商店)");
        add("arc_quest.command.trade.debug.header", "§e=== 交易商店调试: %1$s ===");
        add("arc_quest.command.trade.debug.name", "§f  名称: %1$s");
        add("arc_quest.command.trade.debug.description", "§7  描述: %1$s");
        add("arc_quest.command.trade.debug.mode.simple", "§f  模式: §a简易弹窗");
        add("arc_quest.command.trade.debug.mode.full", "§f  模式: §b完整窗口");
        add("arc_quest.command.trade.debug.categories", "§f  分类: §7%1$s 个");
        add("arc_quest.command.trade.debug.category_entry", "§d    %1$s §7- %2$s");
        add("arc_quest.command.trade.debug.entries", "§f  交易项: §7%1$s 个");
        add("arc_quest.command.trade.debug.entry_header", "§a    %1$s §7| %2$s");
        add("arc_quest.command.trade.debug.costs", "§7      成本: §c%1$s");
        add("arc_quest.command.trade.debug.rewards", "§7      奖励: §b%1$s");
        add("arc_quest.command.trade.debug.limit", "§7      限购: §e%1$s 次");
        add("arc_quest.command.trade.debug.cooldown", "§7      冷却: §e%1$s (%2$s)");
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

    /**
     * 交易系统翻译。
     */
    private void addTradeTranslations() {
        // ── 屏幕标题 ──
        addTradeScreenTitle("screen", "交易矩阵");
        addTradeScreenTitle("quick", "快速交易");

        // ── 通用标签 ──
        addTradeLabel("subtitle", "请选择商品");
        addTradeLabel("closed", "商店已关闭");
        addTradeLabel("unknown_shop", "未知商店");
        addTradeLabel("buy", "购买");
        addTradeLabel("sold_out", "已售罄");
        addTradeLabel("purchase", "购买");
        addTradeLabel("maxed", "已达上限");
        addTradeLabel("cooldown", "%d秒");

        // ── 分类名称 ──
        addTradeCategory("all", "全部");

        // ── 交易物描述模板 ──
        addTradeOfferTemplate("item", "%sx%d");
        addTradeOfferTemplate("effect", "%s%s %d秒");
        addTradeOfferTemplate("command", "命令: %s");
        addTradeOfferTemplate("flag.require", "需要: %s");
        addTradeOfferTemplate("flag.reward", "获得标记: %s");

        // ── 反馈消息 ──
        addTradeFeedback("success", "交易成功！");
        addTradeFeedback("error.cooldown", "冷却中，请等待 %d 秒");
        addTradeFeedback("error.limit", "已达到购买上限");
        addTradeFeedback("error.cannot_afford", "资源不足");
        addTradeFeedback("error.condition", "条件未满足");

        // ── 冷却提示文本 ──
        addTradeCooldownText("game_day", "明日重置");
        addTradeCooldownText("seconds_remaining", "%d秒后重置");
        addTradeCooldownText("ticks_remaining", "%dt后重置");

        // ── 交易商店 ──
        addTradeShop("blacksmith_shop", "铁匠铺", "出售各类武器、护甲和工具");
        addTradeShop("potion_shop", "药水商人", "出售各类药水效果");
        addTradeShop("quick_food_trade", "快速补给", null);
        addTradeShop("merchant_shop", "旅行商人的行囊", "周游各地的物资供应商");
        addTradeShop("wandering_trader_shop", "流浪商人的珍品", "每日更新的稀有商品");
        addTradeShop("mysterious_merchant_shop", "神秘商人的深夜小店", "仅在深夜开放的禁忌宝库");
        addTradeShop("quick_supplies", "守卫的快速补给", "不关闭对话的便捷购买");

        // ── 分类名称 ──
        addTradeCategory("weapons", "武器");
        addTradeCategory("armor", "护甲");
        addTradeCategory("tools", "工具");
        addTradeCategory("supplies", "物资");
        addTradeCategory("rare", "珍宝");

        // ── 交易项（铁匠铺） ──
        addTradeEntry("iron_sword", "铁剑", null);
        addTradeEntry("diamond_sword", "钻石剑", null);
        addTradeEntry("iron_chestplate", "铁胸甲", null);
        addTradeEntry("iron_helmet", "铁头盔", null);
        addTradeEntry("iron_pickaxe", "铁镐", null);

        // ── 交易项（药水商人） ──
        addTradeEntry("strength_potion", "力量药水", "给予力量 I 效果 60秒");
        addTradeEntry("speed_potion", "速度药水", "给予速度 I 效果 120秒");
        addTradeEntry("regen_potion", "生命恢复药水", "给予生命恢复 II 效果 30秒");

        // ── 交易项（快速补给） ──
        addTradeEntry("buy_bread", "面包 x4", null);
        addTradeEntry("buy_steak", "牛排 x2", null);
        addTradeEntry("buy_golden_apple", "金苹果", null);

        // ── 交易项（快速补给） ──
        addTradeEntry("qs_bread", "面包 x4", null);
        addTradeEntry("qs_potion", "治疗药水", null);

        // ── 交易项（旅行商人） ──
        addTradeEntry("merchant_food_pack", "应急口粮包", "包含面包与烤马铃薯");
        addTradeEntry("merchant_torch_bundle", "探险火把组", "深入矿洞的必备品");

        // ── 交易项（流浪商人） ──
        addTradeEntry("trader_exotic_plant", "异域植物种子", "来自远方的奇异种子");
        addTradeEntry("trader_dye_set", "染料大礼包", "五颜六色的创作素材");

        // ── 交易项（神秘商人） ──
        addTradeEntry("mystery_netherite", "下界合金锭", "蕴含毁灭力量的金属");
        addTradeEntry("mystery_totem", "不死图腾", "逆转生死的最后希望");
    }

    /**
     * 史诗主线对话翻译。
     */
    private void addEpicDialogueTranslations() {
        // ═══════════════════════════════════════════
        // 村庄长老
        // ═══════════════════════════════════════════
        add("dialogue.epic_village_elder.npc_name", "§6§l村庄长老");

        // 起始节点 - 多种状态
        add("dialogue.epic_village_elder.start.newcomer",
                "§f欢迎你，陌生的旅人。我是这片土地的守护者。\n"
                        + "如果你准备好了，我可以告诉你关于这个世界的真相……");
        add("dialogue.epic_village_elder.start.choice_intro",
                "请告诉我，发生了什么？");

        add("dialogue.epic_village_elder.start.phase_gather_wood",
                "§f冒险者，收集木材制作武器吧！\n"
                        + "僵尸大军随时可能来袭，我们必须做好准备。");
        add("dialogue.epic_village_elder.start.choice_wood_tip",
                "我会尽快完成的");

        add("dialogue.epic_village_elder.start.phase_talk_villager",
                "§f很好，你已经武装了自己。\n"
                        + "现在，让我告诉你接下来的计划……");
        add("dialogue.epic_village_elder.start.choice_accept_mission",
                "我准备好了，请下达命令！");

        add("dialogue.epic_village_elder.start.phase_defend",
                "§c§l警告！僵尸大军正在逼近！\n"
                        + "§f拿起你的武器，保卫我们的家园！\n"
                        + "§e（获得力量药水 x1）");
        add("dialogue.epic_village_elder.start.choice_encourage",
                "为了村庄！");

        add("dialogue.epic_village_elder.start.prologue_done",
                "§a§l干得漂亮，勇士！\n"
                        + "§f序章已经完成，但真正的挑战才刚刚开始。\n"
                        + "矿洞深处传来诡异的回响……你准备好探索了吗？");
        add("dialogue.epic_village_elder.start.choice_start_chapter1",
                "我准备好了，开始第一章！");

        add("dialogue.epic_village_elder.start.chapter1_done",
                "§b§l难以置信……你竟然击败了矿洞守卫！\n"
                        + "§f现在，你面临一个重要的选择：\n"
                        + "§c• 战斗之路：直捣下界要塞\n"
                        + "§9• 探索之路：追寻海底神殿\n"
                        + "你的选择将决定最终的命运……");
        add("dialogue.epic_village_elder.start.choice_branch",
                "我想了解更多关于这两个选择");

        add("dialogue.epic_village_elder.start.legendary",
                "§5§l✨ 传奇英雄 ✨\n"
                        + "§f你做到了！末影龙已被击败，世界恢复了和平。\n"
                        + "你的名字将被永远铭记在这片土地上！\n"
                        + "§e（获得村庄英雄效果 + 100经验）");
        add("dialogue.epic_village_elder.start.choice_legendary",
                "这是我的荣幸");

        add("dialogue.epic_village_elder.start.default",
                "§f祝你旅途顺利，冒险者。");
        add("dialogue.epic_village_elder.start.choice_default",
                "再见");

        // 介绍故事背景
        add("dialogue.epic_village_elder.intro_story.text",
                "§6§l命运的齿轮开始转动……\n\n"
                        + "§f很久以前，这个世界充满了和平与繁荣。\n"
                        + "但随着末影龙的苏醒，黑暗势力再次崛起。\n"
                        + "不死者的大军已经兵临城下，我们需要一位英雄……\n\n"
                        + "§e你，愿意成为我们的希望吗？");
        add("dialogue.epic_village_elder.intro_story.choice_accept",
                "我愿意接受这个使命！");
        add("dialogue.epic_village_elder.intro_story.choice_decline",
                "我需要时间考虑");

        // 序章已接受
        add("dialogue.epic_village_elder.prologue_accepted.text",
                "§a§l任务已接受！\n\n"
                        + "§f第一步：收集§6橡木原木 x5\n"
                        + "第二步：回来找我获取下一步指示\n\n"
                        + "§e祝你好运，勇士！");
        add("dialogue.epic_village_elder.prologue_accepted.choice_ok",
                "明白了！");

        // 第一章已接受
        add("dialogue.epic_village_elder.chapter1_accepted.text",
                "§b§l第一章：深渊的低语\n\n"
                        + "§f矿洞深处隐藏着古老的秘密……\n"
                        + "准备好火把和武器，小心洞穴蜘蛛！\n\n"
                        + "§e目标：收集煤炭 x16、木棍 x8");
        add("dialogue.epic_village_elder.chapter1_accepted.choice_ok",
                "我会小心的");

        // 分支选择介绍
        add("dialogue.epic_village_elder.branch_introduction.text",
                "§6§l两条道路，两种命运……\n\n"
                        + "§c【战斗之路】\n"
                        + "直捣下界要塞，与烈焰人和凋灵骷髅正面交锋。\n"
                        + "适合喜欢激烈战斗的勇士。\n\n"
                        + "§9【探索之路】\n"
                        + "追寻海底神殿的秘密，解开古老文明的谜题。\n"
                        + "适合喜欢探索和发现的冒险者。\n\n"
                        + "§e你的选择是什么？");
        add("dialogue.epic_village_elder.branch_introduction.choice_combat",
                "我想了解战斗之路");
        add("dialogue.epic_village_elder.branch_introduction.choice_exploration",
                "我想了解探索之路");
        add("dialogue.epic_village_elder.branch_introduction.choice_think",
                "让我再想想");

        // 解释战斗路线
        add("dialogue.epic_village_elder.explain_combat.text",
                "§c§l战斗之路：烈焰的试炼\n\n"
                        + "§f你将面对：\n"
                        + "• §6烈焰人§r - 下界的火焰使者\n"
                        + "• §4凋灵骷髅§r - 要塞的恐怖守卫\n"
                        + "• §5下界要塞§r - 充满危险的迷宫\n\n"
                        + "§e奖励：下界合金碎片、强大的战斗技巧\n\n"
                        + "§c警告：这条路充满危险，但回报丰厚！");
        add("dialogue.epic_village_elder.explain_combat.choice_confirm",
                "我选择战斗！");

        // 解释探索路线
        add("dialogue.epic_village_elder.explain_exploration.text",
                "§9§l探索之路：深海的遗言\n\n"
                        + "§f你将发现：\n"
                        + "• §3守卫者§r - 海底神殿的守护者\n"
                        + "• §b海晶材料§r - 神秘的海洋能量\n"
                        + "• §e远古守卫者§r - 神殿的最终Boss\n\n"
                        + "§e奖励：三叉戟、海洋之心、古代知识\n\n"
                        + "§9提示：准备好水下呼吸药水！");
        add("dialogue.epic_village_elder.explain_exploration.choice_confirm",
                "我选择探索！");

        // 拒绝节点
        add("dialogue.epic_village_elder.decline.text",
                "§f没关系，当你准备好的时候再来找我吧。\n"
                        + "村庄的大门永远为你敞开。");
        add("dialogue.epic_village_elder.decline.choice1",
                "谢谢理解");

        // ═══════════════════════════════════════════
        // 铁匠
        // ═══════════════════════════════════════════
        add("dialogue.epic_blacksmith.npc_name", "§8§l铁匠");

        add("dialogue.epic_blacksmith.start.early",
                "§f嘿，新手！需要武器吗？\n"
                        + "我现在只有石制装备，但足够应付初期的战斗了。\n"
                        + "§e（石剑 x1 = 5金币）");
        add("dialogue.epic_blacksmith.start.choice_buy_stone_sword",
                "给我一把石剑");

        add("dialogue.epic_blacksmith.start.mid",
                "§7哦？你已经开始探索矿洞了？\n"
                        + "那就需要更好的装备了！\n"
                        + "§e（全套铁甲 = 50金币）");
        add("dialogue.epic_blacksmith.start.choice_buy_iron_gear",
                "我要铁制装备");

        add("dialogue.epic_blacksmith.start.late",
                "§b哇，你已经完成了第一章？！\n"
                        + "看来你是认真的冒险者啊。\n"
                        + "来看看这些钻石装备吧！\n"
                        + "§e（全套钻石甲 = 100金币）");
        add("dialogue.epic_blacksmith.start.choice_buy_diamond_gear",
                "钻石装备听起来不错");

        add("dialogue.epic_blacksmith.start.legendary",
                "§5§l传奇英雄驾到！\n"
                        + "§f能为你打造装备是我的荣幸。\n"
                        + "这是我最珍贵的作品——下界合金装备！\n"
                        + "§e（下界合金锭 x1 = 200金币）");
        add("dialogue.epic_blacksmith.start.choice_buy_netherite",
                "我要最强的装备！");

        add("dialogue.epic_blacksmith.start.default",
                "§f需要什么尽管说，我的手艺可是村里最好的！");
        add("dialogue.epic_blacksmith.start.choice_leave",
                "下次再来");
        add("dialogue.epic_blacksmith.start.choice_open_shop",
                "§8查看完整货架 (打开商店)");

        // ═══════════════════════════════════════════
        // 商人
        // ═══════════════════════════════════════════
        add("dialogue.epic_merchant.npc_name", "§a§l旅行商人");

        add("dialogue.epic_merchant.start.phase_gather",
                "§f冒险者，要出发了吗？\n"
                        + "带上些食物吧，路上可能会饿。\n"
                        + "§e（面包 x4 = 2金币）");
        add("dialogue.epic_merchant.start.choice_buy_food",
                "来点面包");

        add("dialogue.epic_merchant.start.phase_defend",
                "§c听说僵尸要来袭击？\n"
                        + "这些治疗药水可能会救你一命！\n"
                        + "§e（治疗药水 x2 = 10金币）");
        add("dialogue.epic_merchant.start.choice_buy_potions",
                "给我一些药水");

        add("dialogue.epic_merchant.start.phase_chapter1",
                "§8要去矿洞？明智的选择是带上足够的火把。\n"
                        + "黑暗中什么都有可能发生……\n"
                        + "§e（火把 x32 = 5金币）");
        add("dialogue.epic_merchant.start.choice_buy_torches",
                "来一组火把");

        add("dialogue.epic_merchant.start.default",
                "§f欢迎来到我的小店！商品琳琅满目，应有尽有~");
        add("dialogue.epic_merchant.start.choice_browse",
                "让我看看有什么商品");
        add("dialogue.epic_merchant.start.choice_leave",
                "下次光临");

        add("dialogue.epic_merchant.shop_menu.text",
                "§a§l=== 商人货架 ===\n\n"
                        + "§f请选择你想购买的商品类别：");
        add("dialogue.epic_merchant.shop_menu.choice_food",
                "🍞 食物包 (5金币)");
        add("dialogue.epic_merchant.shop_menu.choice_tools",
                "⚒️ 工具包 (10金币)");
        add("dialogue.epic_merchant.shop_menu.choice_back",
                "← 返回");
        add("dialogue.epic_merchant.shop_menu.choice_open_shop",
                "§a查看完整行囊 (打开商店)");

        // ═══════════════════════════════════════════
        // 流浪商人 - sayIf() 示例 NPC
        // ═══════════════════════════════════════════
        add("dialogue.epic_wandering_trader.npc_name", "§a§l流浪商人");

        // 起始节点 - 5种状态
        add("dialogue.epic_wandering_trader.start.newbie",
                "§f哟，新面孔！我是周游各地的流浪商人。\n"
                        + "§e如果你刚开始冒险，我可以给你一些建议……");

        add("dialogue.epic_wandering_trader.start.prologue_active",
                "§f哦？你已经接受了长老的任务？\n"
                        + "§e序章进行中……加油啊，冒险者！");

        add("dialogue.epic_wandering_trader.start.chapter1_ready",
                "§b听说你完成了序章？太厉害了！\n"
                        + "§e矿洞深处有宝藏等待着你，准备好探索了吗？");

        add("dialogue.epic_wandering_trader.start.chapter1_active",
                "§8矿洞很危险吧？小心洞穴蜘蛛！\n"
                        + "§e如果装备不够好，我这里有一些不错的商品……");

        add("dialogue.epic_wandering_trader.start.legendary",
                "§5§l✨ 传奇英雄！✨\n"
                        + "§f没想到能在这里见到击败末影龙的大人物！\n"
                        + "§e我的小店蓬荜生辉，请随意挑选！");

        add("dialogue.epic_wandering_trader.start.default",
                "§f欢迎来到我的移动商店！商品每日更新~");

        add("dialogue.epic_wandering_trader.start.choice_shop",
                "我想看看商品");
        add("dialogue.epic_wandering_trader.start.choice_rumors",
                "有什么传闻吗？");
        add("dialogue.epic_wandering_trader.start.choice_bye",
                "再见");

        // 商店菜单
        add("dialogue.epic_wandering_trader.shop_menu.text",
                "§a§l=== 流浪商人货架 ===\n\n"
                        + "§f请选择你想购买的商品类别：");
        add("dialogue.epic_wandering_trader.shop_menu.choice_supplies",
                "📦 补给包 (火把 x32 + 面包 x4)");
        add("dialogue.epic_wandering_trader.shop_menu.choice_equipment",
                "⚔️ 武器装备");
        add("dialogue.epic_wandering_trader.shop_menu.choice_back",
                "← 返回");
        add("dialogue.epic_wandering_trader.shop_menu.choice_open_shop",
                "§b查看每日珍品 (打开商店)");

        // 装备菜单
        add("dialogue.epic_wandering_trader.equipment_menu.text",
                "§6§l=== 武器装备 ===\n\n"
                        + "§f根据你的冒险进度，推荐以下装备：");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_stone",
                "🗡️ 石剑 x1（新手推荐）");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_iron",
                "🛡️ 铁甲全套（进阶装备）");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_diamond",
                "💎 钻石甲全套（精英装备）");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_back",
                "← 返回");

        // 购买完成
        add("dialogue.epic_wandering_trader.purchase_complete.text",
                "§a§l交易完成！\n\n"
                        + "§f感谢惠顾，祝你好运！");
        add("dialogue.epic_wandering_trader.purchase_complete.choice_continue",
                "继续购物");

        // 传闻
        add("dialogue.epic_wandering_trader.rumors.text",
                "§6§l=== 最新传闻 ===\n\n"
                        + "§f• 矿洞深处传来诡异的回响……\n"
                        + "§f• 有人说在下界看到了烈焰要塞\n"
                        + "§f• 海底神殿隐藏着古老的秘密\n"
                        + "§f• 末影龙的力量正在苏醒\n\n"
                        + "§e这些都是我从各地旅人那里听来的~");
        add("dialogue.epic_wandering_trader.rumors.choice_back",
                "← 返回");

        // ═══════════════════════════════════════════
        // 村庄守卫 - 展示游戏时间刻冷却、时间段条件、对话历史状态和权重
        // ═══════════════════════════════════════════
        add("dialogue.epic_village_guard.npc_name", "§b§l村庄守卫");

        // 起始节点 - 根据时间段显示不同问候语
        add("dialogue.epic_village_guard.start.morning",
                "§f早上好，冒险者！§e新的一天开始了，村庄很安全。\n"
                        + "需要我帮忙吗？");

        add("dialogue.epic_village_guard.start.afternoon",
                "§f下午好！§e阳光正好，适合巡逻或训练。\n"
                        + "有什么我能帮你的吗？");

        add("dialogue.epic_village_guard.start.night",
                "§8夜晚好……§7保持警惕，外面有危险。\n"
                        + "如果你需要帮助，我可以提供一些支持。");

        add("dialogue.epic_village_guard.start.default",
                "§f你好，我是村庄的守卫。\n"
                        + "负责保护这里的安全。");

        add("dialogue.epic_village_guard.start.choice_intro",
                "你是谁？");
        add("dialogue.epic_village_guard.start.choice_daily",
                "📋 领取每日巡逻任务（每天6:00重置）");
        add("dialogue.epic_village_guard.start.choice_training",
                "⚔️ 请求训练（1小时冷却）");
        add("dialogue.epic_village_guard.start.choice_chat",
                "闲聊");
        add("dialogue.epic_village_guard.start.choice_quick_trade",
                "§e🍞 快速购买补给 (简易窗口)");
        add("dialogue.epic_village_guard.start.choice_bye",
                "再见");

        // 首次介绍节点
        add("dialogue.epic_village_guard.intro.text",
                "§b§l=== 村庄守卫 ===\n\n"
                        + "§f我叫阿尔文，是这里的守卫队长。\n"
                        + "§e我的职责是保护村庄免受怪物侵扰。\n\n"
                        + "§a每天早上6点，我会发布巡逻任务。\n"
                        + "§6每小时可以提供一次战斗训练。\n\n"
                        + "§d很高兴认识你，冒险者！");
        add("dialogue.epic_village_guard.intro.choice_ok",
                "很高兴认识你");

        // 每日巡逻任务
        add("dialogue.epic_village_guard.daily_patrol.text",
                "§a§l=== 每日巡逻任务 ===\n\n"
                        + "§f今天的任务是巡视村庄周边，确保没有怪物出没。\n\n"
                        + "§a奖励：\n"
                        + "§f• 面包 x3\n"
                        + "§f• 经验值 10\n\n"
                        + "§e此任务每天早上6点重置。");
        add("dialogue.epic_village_guard.daily_patrol.choice_thanks",
                "谢谢！");

        // 训练请求
        add("dialogue.epic_village_guard.training.text",
                "§6§l=== 战斗训练 ===\n\n"
                        + "§f让我教你一些基本的战斗技巧。\n"
                        + "§e这瓶力量药水可以帮助你练习。\n\n"
                        + "§a获得：力量药水 x1\n\n"
                        + "§c下次训练需要等待1小时。");
        add("dialogue.epic_village_guard.training.choice_thanks",
                "非常感谢！");

        // 闲聊节点 - 根据时间段显示不同内容
        add("dialogue.epic_village_guard.chat.morning",
                "§f早晨的空气真清新啊！\n"
                        + "§e我最喜欢这个时候巡逻，能看到日出。\n\n"
                        + "§a今天也会是平静的一天吧……大概。");

        add("dialogue.epic_village_guard.chat.afternoon",
                "§f下午的阳光有点刺眼呢。\n"
                        + "§e不过这样的天气最适合训练新兵了。\n\n"
                        + "§a你要不要也来试试？");

        add("dialogue.epic_village_guard.chat.night",
                "§8夜晚是最危险的时候……\n"
                        + "§7僵尸、骷髅、苦力怕都会出来活动。\n\n"
                        + "§c一定要小心，别离开村庄太远。");

        add("dialogue.epic_village_guard.chat.default",
                "§f最近村庄还算平静。\n"
                        + "§e但我们要时刻保持警惕。");

        add("dialogue.epic_village_guard.chat.choice_back",
                "← 返回");

        // ═══════════════════════════════════════════
        // 神秘商人 - 展示自定义时间区间和复杂条件
        // ═══════════════════════════════════════════
        add("dialogue.epic_mysterious_merchant.npc_name", "§5§l神秘商人");

        // 起始节点 - 只在特定时间开放
        add("dialogue.epic_mysterious_merchant.start.open",
                "§5§l✨ 深夜商店已开放 ✨\n\n"
                        + "§f欢迎来到我的秘密商店……\n"
                        + "§e只有在深夜时分，我才会现身。\n\n"
                        + "§d这里有你在别处找不到的稀有物品。");

        add("dialogue.epic_mysterious_merchant.start.closed_morning",
                "§8……\n\n"
                        + "§7商人似乎在休息。\n"
                        + "§e他只在深夜出现。");

        add("dialogue.epic_mysterious_merchant.start.closed_afternoon",
                "§8……\n\n"
                        + "§7商人不在这里。\n"
                        + "§e也许晚上再来看看吧。");

        add("dialogue.epic_mysterious_merchant.start.default",
                "§8一个神秘的商人，似乎在等待合适的时机。");

        add("dialogue.epic_mysterious_merchant.start.choice_shop",
                "💎 查看商品（仅深夜开放）");
        add("dialogue.epic_mysterious_merchant.start.choice_identity",
                "你是谁？");
        add("dialogue.epic_mysterious_merchant.start.choice_leave",
                "离开");

        // 商店节点
        add("dialogue.epic_mysterious_merchant.shop.text",
                "§5§l=== 神秘商店 ===\n\n"
                        + "§f这些是我从各地收集来的珍品……\n"
                        + "§e价格虽然昂贵，但绝对物超所值。\n\n"
                        + "§d你想买点什么？");

        add("dialogue.epic_mysterious_merchant.shop.choice_rare",
                "⭐ 下界合金锭 x1（稀有物品）");
        add("dialogue.epic_mysterious_merchant.shop.choice_common",
                "💎 钻石甲全套（普通商品）");
        add("dialogue.epic_mysterious_merchant.shop.choice_back",
                "← 返回");

        // 身份揭示节点
        add("dialogue.epic_mysterious_merchant.identity.text",
                "§5§l=== 神秘商人的真实身份 ===\n\n"
                        + "§f呵呵……你想知道我的身份？\n\n"
                        + "§e我是穿梭于各个维度的旅行者，\n"
                        + "§5收集着世间最珍贵的宝物。\n\n"
                        + "§d只有在深夜，当世界沉睡时，\n"
                        + "§5我才会打开我的商店。\n\n"
                        + "§f现在，你知道了这个秘密……\n"
                        + "§e记得保守它哦。");
        add("dialogue.epic_mysterious_merchant.identity.choice_back",
                "← 返回");
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