package org.arcadia.arc_quest.data;

import net.minecraft.data.PackOutput;

/**
 * 中文语言文件生成器。
 * <p>
 * 翻译基调：简明、自然的中文界面文案。
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
        addToastConfigTranslations();
        addCommandTranslations();
        addDialogueTranslations();
        addEpicDialogueTranslations();
        addTradeTranslations();
        addGachaTranslations();
        addGuideTranslations();
        addItemTagTranslations();

        // ── 任务链 ──
        addPrologueQuest();
        addChapter1Quest();
        addChapter2CombatQuest();
        addChapter2ExplorationQuest();
        addFinaleQuest();
        addBranchChoiceQuest();
        addCollectionCodexDemoQuest();
    }

    // ═══════════════════════════════════════════════════════
    //  屏幕标题
    // ═══════════════════════════════════════════════════════

    private void addScreenTitles() {
        add("gui.arc_quest.tracking_menu.title", "任务追踪");
        add("arc_quest.gui.tracking_menu.hint", "滚轮或拖拽切换任务 · 在阶段栏滚动或点击切换阶段");
        add("arc_quest.gui.tracking_menu.empty", "当前没有进行中的任务");
        add("arc_quest.gui.tracking_menu.current_phase", "当前阶段：%s");
        add("arc_quest.gui.tracking_menu.no_phase", "暂无活动阶段");
        add("arc_quest.gui.tracking_menu.no_objectives", "暂无活动目标");
        add("arc_quest.gui.tracking_menu.phase_progress", "阶段进度：%s/%s");
        add("arc_quest.gui.tracking_menu.parallel_phases", "并行阶段");
        add("arc_quest.gui.tracking_menu.optional", "（可选）");
        add("arc_quest.gui.tracking_menu.more_objectives", "另有 %s 个目标");
        add("arc_quest.marker.epic_prologue.nearest_tree", "\u6700\u8fd1\u7684\u6811\u6728");
        add("key.categories.arc_quest", "Arc Quest");
        add("key.arc_quest.open_tracking_menu", "打开任务追踪菜单");
        add("gui.arc_quest.journal.title", "任务日志");
        add("arc_quest.gui.journal.title_with_key", "[ %s ] 任务日志");
        add("arc_quest.guide_group.minerals", "矿物");
    }

    // ═══════════════════════════════════════════════════════
    //  GUI 界面文本
    // ═══════════════════════════════════════════════════════

    private void addGuiTranslations() {
        add("arc_quest.category.archon", "主线");
        add("arc_quest.category.companion", "同伴");
        add("arc_quest.category.adventure", "冒险");
        add("arc_quest.category.daily", "日常");
        add("arc_quest.category.event", "活动");
        add("arc_quest.quest_group.epic_mainline", "史诗主线");

        add("arc_quest.gui.tracker.open_journal_shortcut", "[%s] 任务面板");
        add("arc_quest.gui.tracker.new_quest", "有新任务，按[%s]查看");
        add("arc_quest.gui.tracker.new_guide", "有新教程，按[%s]查看");
        add("arc_quest.guide.splash.status", "新教程已解锁");

        add("arc_quest.gui.trade.full_title", "交易商店");
        add("arc_quest.gui.trade.quick_title", "快速交易");

        addTradeGuiText("status", "maxed", "已达上限");
        addTradeGuiText("status", "locked", "未解锁");
        addTradeGuiText("status", "on_cooldown", "冷却中");

        addTradeGuiText("btn", "purchase", "购买");
        addTradeGuiText("btn", "wait", "等待");
        addTradeGuiText("btn", "locked", "锁定");
        addTradeGuiText("btn", "empty", "暂无");

        addTradeGuiText("tooltip", "limit", "限购：%1$s / %2$s");
        addTradeGuiText("tooltip", "cooldown", "冷却：%1$s");
        addTradeGuiText("tooltip", "shortfall_summary", "物品不足");
        addTradeGuiText("tooltip", "shortfall_line", "%1$s ×%2$s");
        addTradeGuiText("tooltip", "shortfall_meta", "需 %1$s / 持有 %2$s");

        addTradeGuiText("error", "unknown_shop", "未知商店");
        addTradeGuiText("error", "shop_closed", "商店已关闭");

        addGuiTab("journal", "active", "进行中");
        addGuiTab("journal", "completed", "已完成");
        addGuiTab("journal", "failed", "已失败");
        addGuiTab("journal", "history", "历史记录");
        addGuiTab("journal", "guide", "游玩指南");
        addGuiSection("journal", "current_phase", "▸ 当前阶段: %s");
        addGuiSection("journal", "parallel_lanes", "并行阶段");
        addGuiSection("journal", "focus_phase", "焦点阶段：");
        addGuiSection("journal", "completed_phases", "▸ 已通过阶段");
        addGuiSection("journal", "choose_path", "▸ 选择你的道路");
        addGuiSection("journal", "phase_rewards", "▸ 阶段奖励");
        addGuiSection("journal", "chapter_rewards", "▸ 章节奖励");

        addGuiLabel("journal", "no_phases_completed",
                "§7尚无已完成的阶段。");
        addGuiLabel("journal", "quest_completed",
                "§a✔ 该任务已完成。");
        addGuiLabel("journal", "quest_failed",
                "§c✘ 该任务已失败。");
        addGuiLabel("journal", "select_quest", "选择一个任务");
        addGuiLabel("journal", "no_active_phase", "当前没有激活阶段。 ");
        addGuiLabel("journal", "choices_locked", "分支尚未解锁");
        addGuiLabel("journal", "topology_map", "拓扑视图");
        addGuiLabel("journal", "view_node_graph_history", "查看节点图与历史");
        add("arc_quest.quest_action_result.template", "[任务] %1$s失败：%2$s（%3$s）");
        add("arc_quest.quest_action.accept", "接受任务");
        add("arc_quest.quest_action.abandon", "放弃任务");
        add("arc_quest.quest_action.choose", "选择分支");
        add("arc_quest.quest_action.open_chapter_shop", "打开章节商店");
        add("arc_quest.quest_action.claim_collection_reward", "领取收集奖励");
        add("arc_quest.quest_action.confirm_phase_advance", "确认阶段完成");
        add("arc_quest.quest_reject.quest_not_found", "任务不存在");
        add("arc_quest.quest_reject.already_active", "任务已在进行中");
        add("arc_quest.quest_reject.already_completed_not_repeatable", "任务已完成且不可重复");
        add("arc_quest.quest_reject.unlock_condition_not_met", "未满足前置条件");
        add("arc_quest.quest_reject.no_initial_phase", "任务缺少初始阶段");
        add("arc_quest.quest_reject.not_active", "任务未激活");
        add("arc_quest.quest_reject.abandon_not_allowed", "该任务不允许放弃");
        add("arc_quest.quest_reject.phase_not_found", "当前阶段不存在");
        add("arc_quest.quest_reject.invalid_choice_index", "分支索引无效");
        add("arc_quest.quest_reject.choice_condition_not_met", "分支条件不满足");
        add("arc_quest.quest_reject.choice_target_phase_missing", "分支目标阶段缺失");
        add("arc_quest.quest_reject.chapter_shop_not_configured", "该任务未配置章节商店");
        add("arc_quest.quest_reject.chapter_shop_not_accessible", "当前状态不可访问章节商店");
        add("arc_quest.quest_reject.chapter_shop_definition_not_found", "章节商店定义不存在");
        add("arc_quest.quest_reject.collection_reward_id_invalid", "收集奖励编号无效");
        add("arc_quest.quest_reject.collection_reward_not_unlocked", "收集奖励尚未解锁");
        add("arc_quest.quest_reject.collection_reward_already_claimed", "收集奖励已领取");
        add("arc_quest.quest_reject.collection_reward_not_manual", "该收集奖励不可手动领取");
        add("arc_quest.quest_reject.collection_reward_node_not_found", "收集奖励节点不存在");
        add("arc_quest.quest_reject.collection_data_missing", "任务缺少收集运行时数据");
        add("arc_quest.quest_reject.collection_config_missing", "任务缺少收集配置");
        add("arc_quest.gui.quest_offer.header", "提交物品");
        add("arc_quest.gui.quest_offer.status", "状态：%1$s / %2$s");
        add("arc_quest.gui.quest_intel.header", "阶段详情");
        add("arc_quest.gui.quest_intel.hint_close", "按 ESC 或点击外侧关闭");
        add("arc_quest.gui.quest_story.header", "阶段故事");
        add("arc_quest.gui.quest_story.prev", "<< 上一页");
        add("arc_quest.gui.quest_story.next", "下一页 >>");
        add("arc_quest.gui.quest_story.page", "第 %1$s / %2$s 页");
        add("arc_quest.message.quest_action_failed", "[任务] %1$s失败：%2$s（%3$s）");
        add("arc_quest.message.quest_action.accept", "接受任务");
        add("arc_quest.message.quest_action.abandon", "放弃任务");
        add("arc_quest.message.quest_action.choose", "选择分支");
        add("arc_quest.message.quest_action.open_chapter_shop", "打开章节商店");
        add("arc_quest.message.quest_action.claim_collection_reward", "领取收集奖励");
        add("arc_quest.message.quest_action.confirm_phase_advance", "确认阶段完成");
        add("arc_quest.message.quest_reason.quest_not_found", "任务不存在");
        add("arc_quest.message.quest_reason.already_active", "任务已在进行中");
        add("arc_quest.message.quest_reason.already_completed_not_repeatable", "任务已完成且不可重复");
        add("arc_quest.message.quest_reason.unlock_condition_not_met", "未满足前置条件");
        add("arc_quest.message.quest_reason.no_initial_phase", "任务缺少初始阶段");
        add("arc_quest.message.quest_reason.not_active", "任务未激活");
        add("arc_quest.message.quest_reason.phase_not_found", "当前阶段不存在");
        add("arc_quest.message.quest_reason.invalid_choice_index", "分支索引无效");
        add("arc_quest.message.quest_reason.choice_condition_not_met", "分支条件不满足");
        add("arc_quest.message.quest_reason.choice_target_phase_missing", "分支目标阶段缺失");
        add("arc_quest.message.quest_reason.chapter_shop_not_configured", "该任务未配置章节商店");
        add("arc_quest.message.quest_reason.chapter_shop_not_accessible", "当前状态不可访问章节商店");
        add("arc_quest.message.quest_reason.chapter_shop_definition_not_found", "章节商店定义不存在");
        add("arc_quest.message.quest_reason.collection_reward_id_invalid", "收集奖励编号无效");
        add("arc_quest.message.quest_reason.collection_reward_not_unlocked", "收集奖励尚未解锁");
        add("arc_quest.message.quest_reason.collection_reward_already_claimed", "收集奖励已领取");
        add("arc_quest.message.quest_reason.collection_reward_not_manual", "该收集奖励不可手动领取");
        add("arc_quest.message.quest_reason.collection_reward_node_not_found", "收集奖励节点不存在");
        add("arc_quest.message.quest_reason.collection_data_missing", "任务缺少收集运行时数据");
        add("arc_quest.message.quest_reason.collection_config_missing", "任务缺少收集配置");
        add("arc_quest.message.quest_reason.unknown", "未知原因");
        addGuiLabel("journal", "story_archive", "阶段叙事");
        addGuiLabel("journal", "read_story", "阅读");
        addGuiLabel("journal", "unread_phase_story", "【点击查看阶段叙事】");
        addGuiLabel("journal", "click_to_submit", "点击提交物品");
        addGuiLabel("journal", "objective_complete_prefix", "§a✔ ");
        addGuiLabel("journal", "objective_active_prefix", "§f○ ");
        addGuiLabel("journal", "pending_phase_prefix", "等待确认阶段：");

        addGuiButton("journal", "track", "⊕ 追踪");
        addGuiButton("journal", "tracked", "§a⊕ 追踪中");
        addGuiButton("journal", "untrack", "⊖ 取消追踪");
        addGuiButton("journal", "confirm_phase_complete", "✔ 确认阶段完成");
        addGuiButton("journal", "abandon", "✘ 放弃");
        addGuiButton("journal", "chapter_shop", "章节商店");
        addGuiButton("journal", "restart", "↻ 重新开始");
    }

    // ═══════════════════════════════════════════════════════
    //  HUD 文本
    // ═══════════════════════════════════════════════════════

    private void addHudTranslations() {
        addHudText("new_phase", "新阶段");
        addHudText("phase_prefix", "▸ %s");
        addHudText("objective_complete_prefix", "§a✔ ");
        addHudText("objective_active_prefix", "§7○ ");
        addHudText("phase_switched", "阶段已切换");
        addHudText("phase_completed", "阶段完成");
        addHudText("parallel_lanes", "并行阶段");
        addHudText("parallel_more", "还有 %s 个阶段");
        addHudText("toast.phase_added", "阶段已加入");
        addHudText("toast.phase_switched", "焦点已切换");
        addHudText("toast.phase_completed", "阶段完成");
        addHudText("toast.phase_pending_confirm", "等待确认");
        addHudText("dialogue.transcript", "对话记录");
        addHudText("dialogue.you", "你");
        addHudText("dialogue.unknown", "未知");
        addHudText("dialogue.journal_button", "[ J ] 对话记录");
        addHudText("gacha.rarity", "稀有度");
        addHudText("gacha.yield", "数量");
        addHudText("gacha.weight", "权重");
        addHudText("gacha.pity", "保底");
        addHudText("gacha.select_target", "选择奖励");
        addHudText("gacha.decrypted", "奖励已揭示");
        addHudText("gacha.guaranteed", "保底奖励");
        addHudText("gacha.no_records", "暂无记录");
        addHudText("gacha.uplink", "奖励");
        addHudText("guide.no_media", "暂无内容");
        addHudText("guide.page", "第 %s / %s 页");
        addHudText("guide.datablock", "指引");
        addHudText("ponder.scene_missing", "场景不可用");
        addHudText("trade.no_item_data", "暂无物品信息");
        addHudText("trade.item_data", "物品信息 [D]");
        addHudText("trade.trade_data", "交易信息 [A]");
        addHudText("collection.archive", "收集档案");
        addHudText("collection.claimable_rewards", "可领取奖励：%s");
        addHudText("collection.all_assets", "全部条目");
        addHudText("collection.in_progress", "进行中");
        addHudText("collection.phase_rewards", "阶段奖励");
        addHudText("collection.no_entries", "暂无条目");
        addHudText("collection.current_rewards", "当前奖励");
        addHudText("collection.progress", "进度：%s / %s");
        addHudText("collection.seen", "已发现：%s");
        addHudText("collection.rewards", "奖励：%s");
        addHudText("collection.mode", "计数方式：%s");
        addHudText("collection.entry_reward_claimable", "条目奖励可领取");
        addHudText("collection.completed", "已完成");
        addHudText("collection.tracked_entry", "正在追踪");
        addHudText("collection.click_track", "点击追踪此条目");
        addHudText("collection.unknown_entry", "未知条目");
        addHudText("collection.claim", "领取");
        addHudText("collection.claimed", "已领取");
        addHudText("collection.locked", "未解锁");
        addHudText("history.archive", "任务记录：%s");
        addHudText("history.all_quests", "全部任务");
        addHudText("history.all_logs", "全部记录");
        addHudText("history.filter.global", "全部");
        addHudText("history.filter.phases", "阶段");
        addHudText("history.filter.objectives", "目标");
        addHudText("history.filter.rewards", "奖励");
        addHudText("history.filter.items", "物品");
        addHudText("history.no_records", "暂无记录");
        addHudText("history.exact_time", "具体时间：%s");
        addHudText("history.type", "类型：%s");
        addHudText("history.time", "时间：%s");
        addHudText("history.unknown_quest", "未知任务");
        addHudText("history.objective_fallback", "目标 %s");
        addHudText("history.phase_header", "阶段记录——右键聚焦当前阶段");
        addHudText("journal.change_log", "任务变化");
        addHudText("journal.phase_details", "阶段详情");
        addHudText("offer.submit", "提交");
        addHudText("offer.submit_count", "提交 [%s]");
        addHudText("offer.quantity", "数量：%s");
        addHudText("offer.insufficient_details", "物品不足，需要 %s，当前有 %s");
        addHudText("offer.insufficient", "物品不足，需要 %s");
        addHudText("offer.cleared", "已清除");
        addHudText("splash.dismiss", "点击关闭");
        addHudText("tracker.entry", "条目：%s");
        addHudText("tracker.collection_progress", "收集进度");
        addHudText("tracker.discovered_entries", "已发现条目");
        addHudText("tracker.claimable_rewards", "有奖励可领取");
        addHudText("tracker.global_log", "收集记录");
        addHudText("tracker.seen", "已发现：%s");
        addHudText("tracker.last", "最近更新：%s");
        addHudText("tracker.entry_focus", "当前条目");
        addHudText("parallel.completed", "已完成");
        addHudText("parallel.tracking", "正在追踪");
        addHudText("parallel.standby", "等待中");
        addHudText("history.type.quest_accepted", "接受任务");
        addHudText("history.type.quest_completed", "任务完成");
        addHudText("history.type.quest_failed", "任务失败");
        addHudText("history.type.quest_abandoned", "放弃任务");
        addHudText("history.type.phase_added", "阶段加入");
        addHudText("history.type.phase_switched", "阶段切换");
        addHudText("history.type.phase_advanced", "阶段推进");
        addHudText("history.type.phase_completed", "阶段完成");
        addHudText("history.type.objective_progress", "目标进度");
        addHudText("history.type.objective_completed", "目标完成");
        addHudText("history.type.collection_entry_discovered", "发现条目");
        addHudText("history.type.collection_entry_completed", "条目完成");
        addHudText("history.type.collection_category_completed", "分类完成");
        addHudText("history.type.collection_quest_completed", "收集完成");
        addHudText("history.type.collection_reward_unlocked", "奖励解锁");
        addHudText("history.type.collection_reward_claimed", "领取奖励");
        addHudText("history.type.system_sync", "数据更新");
        addHudText("splash.category", "任务 · %s");
        addHudText("splash.type.quest_detail", "任务详情");
        addHudText("splash.type.quest_acquired", "新任务");
        addHudText("splash.type.phase_start", "阶段开始");
        addHudText("splash.type.phase_complete", "阶段完成");
        addHudText("splash.type.quest_completed", "任务完成");
        addHudText("splash.type.dialogue_start", "对话开始");
        addHudText("splash.type.dialogue_end", "对话结束");
        addHudText("splash.type.quest_failed", "任务失败");
    }
    // ═══════════════════════════════════════════════════════
    //  Toast 提示
    // ═══════════════════════════════════════════════════════

    private void addToastTranslations() {
        addToast("branch_available", "分支可用：%s");
        addToastText("branch", "subtitle", "分支可用");
        addToastText("branch", "prefix", "新路径已解锁：");
        addToast("quest_accepted", "任务已接受");
        addToast("quest_completed", "任务完成");
        addToast("quest_failed", "任务失败");
        addToast("phase_advanced", "阶段已推进");
        add("arc_quest.toast.prefix.quest_accepted", "任务已接受");
        add("arc_quest.toast.prefix.quest_completed", "任务完成");
        add("arc_quest.toast.prefix.quest_failed", "任务失败");
        add("arc_quest.toast.prefix.phase_advanced", "阶段已推进");
        add("arc_quest.toast.prefix.objective_complete", "目标完成");
    }
    private void addToastConfigTranslations() {
        add("gui.arc_quest.toast_config.title", "任务通知");
        add("gui.arc_quest.toast_config.subtitle", "选择要显示的任务通知。");
        add("gui.arc_quest.toast_config.enabled", "开启");
        add("gui.arc_quest.toast_config.disabled", "关闭");
        add("gui.arc_quest.toast_config.reset", "恢复默认");
        add("gui.arc_quest.text_config.button", "字号调整");
        add("gui.arc_quest.text_config.title", "字号调整");
        add("gui.arc_quest.text_config.dialogue", "对话");
        add("gui.arc_quest.text_config.journal", "任务日志");
        add("gui.arc_quest.text_config.guide", "指南");
        add("gui.arc_quest.text_config.shop", "商店");
        add("gui.arc_quest.text_config.restore_defaults", "恢复默认值");
        add("gui.arc_quest.mod_config.title", "Arc Quest 设置");
        add("gui.arc_quest.mod_config.pause_button", "Arc Quest 设置");
        add("gui.arc_quest.mod_config.reset", "恢复本栏默认");
        add("gui.arc_quest.mod_config.on", "开启");
        add("gui.arc_quest.mod_config.off", "关闭");
        add("gui.arc_quest.mod_config.scale_value", "%s%%");
        add("gui.arc_quest.mod_config.tab.general", "常规");
        add("gui.arc_quest.mod_config.tab.general.description", "任务日志与标记设置。");
        add("gui.arc_quest.mod_config.tab.toast", "通知");
        add("gui.arc_quest.mod_config.tab.toast.description", "选择要显示的任务通知。");
        add("gui.arc_quest.mod_config.tab.text", "字号");
        add("gui.arc_quest.mod_config.tab.text.description", "分别调整各个界面的文字大小。");
        add("gui.arc_quest.mod_config.tab.log", "日志");
        add("gui.arc_quest.mod_config.tab.log.description", "开启需要记录的诊断日志分类。");
        addModConfigTranslations();
        addToastConfigOption("quest_accepted", "接受任务", "新任务进入进行中状态时显示。");
        addToastConfigOption("quest_completed", "完成任务", "任务完成时显示。");
        addToastConfigOption("quest_failed", "任务失败", "任务失败时显示。");
        addToastConfigOption("phase_advanced", "阶段更新（总开关）", "控制下方所有阶段新增、切换、完成与确认提示。");
        addToastConfigOption("objective_complete", "目标完成", "任务目标完成时显示。");
        addToastConfigOption("collection_entry_discovered", "发现收集条目", "发现隐藏的收集条目时显示。");
        addToastConfigOption("collection_entry_completed", "完成收集条目", "收集条目完成时显示。");
        addToastConfigOption("collection_reward_unlocked", "解锁收集奖励", "收集奖励变为可领取时显示。");
        addToastConfigOption("collection_reward_claimed", "领取收集奖励", "领取收集奖励后显示。");
        addToastConfigOption("phase_added", "追踪阶段新增", "追踪任务新增活动阶段时显示。");
        addToastConfigOption("phase_switched", "追踪阶段切换", "追踪任务的焦点阶段切换时显示。");
        addToastConfigOption("phase_completed", "追踪阶段完成", "当前追踪阶段完成时显示。");
        addToastConfigOption("phase_pending_confirm", "等待阶段确认", "追踪阶段等待手动确认时显示。");
        addToastConfigOption("branch_choice", "可选择任务分支", "追踪任务出现可选分支时显示。");
    }

    private void addModConfigTranslations() {
        add("gui.arc_quest.mod_config.general.history_tab", "任务历史页");
        add("gui.arc_quest.mod_config.general.history_tab.description", "在任务日志中显示历史页签。");
        add("gui.arc_quest.mod_config.general.history_unread_dots", "历史未读提示");
        add("gui.arc_quest.mod_config.general.history_unread_dots.description", "显示任务历史中的未读提示。");
        add("gui.arc_quest.mod_config.general.journal_mark_all_read", "任务日志全部已读");
        add("gui.arc_quest.mod_config.general.journal_mark_all_read.description", "显示任务日志中的全部已读按钮。");
        add("gui.arc_quest.mod_config.general.guide_mark_all_read", "指南全部已读");
        add("gui.arc_quest.mod_config.general.guide_mark_all_read.description", "显示指南列表中的清理未读按钮。");
        add("gui.arc_quest.mod_config.general.xaero_markers", "同步 Xaero 标记");
        add("gui.arc_quest.mod_config.general.xaero_markers.description", "安装 Xaero 小地图时同步 Arc Quest 标记。");
        add("gui.arc_quest.text_config.dialogue.description", "调整对话文字大小。");
        add("gui.arc_quest.text_config.journal.description", "调整任务日志文字大小。");
        add("gui.arc_quest.text_config.guide.description", "调整指南文字大小。");
        add("gui.arc_quest.text_config.shop.description", "调整商店文字大小。");
        String[] keys = {"core", "quest", "quest_progress", "quest_network", "quest_reload", "dialogue", "dialogue_network", "guide", "trade", "gacha", "npc", "marker", "hud", "render", "command", "compat", "data", "api", "persistence", "websocket"};
        String[] names = {"核心", "任务", "任务进度", "任务网络", "任务重载", "对话", "对话网络", "指南", "商店", "抽卡", "非玩家角色", "标记", "界面显示", "渲染", "命令", "兼容", "数据", "接口", "持久化", "网络连接"};
        for (int i = 0; i < keys.length; i++) {
            add("gui.arc_quest.mod_config.log." + keys[i], names[i]);
            add("gui.arc_quest.mod_config.log." + keys[i] + ".description", "开启" + names[i] + "诊断日志。");
        }
    }
    private void addToastConfigOption(String key, String title, String description) {
        String prefix = "gui.arc_quest.toast_config.option." + key;
        add(prefix, title);
        add(prefix + ".description", description);
    }

    // ═══════════════════════════════════════════════════════
    //  命令反馈
    // ═══════════════════════════════════════════════════════

    private void addCommandTranslations() {
        addCommandFeedback("give", "success",
                "已将任务 §e%1$s§f 授予 %2$s。");
        addCommandFeedback("give", "error.already_active",
                "任务 §e%1$s§f 在 %2$s 处已为激活状态。");
        addCommandFeedback("give", "error.already_completed",
                "任务 §e%1$s§f 已完成且不可重复。");
        addCommandFeedback("give", "error.failed",
                "启动任务 '%1$s' 失败。");

        addCommandFeedback("complete", "success",
                "已强制完成 %2$s 的任务 §e%1$s§f。");
        addCommandFeedback("complete", "error.not_active",
                "任务 '%1$s' 未处于激活状态。");

        addCommandFeedback("fail", "success",
                "已强制失败 %2$s 的任务 §e%1$s§f。");
        addCommandFeedback("fail", "error.not_active",
                "任务 '%1$s' 未处于激活状态。");

        addCommandFeedback("reset", "success",
                "已重置 %2$s 的任务 §e%1$s§f。");
        addCommandFeedback("reset", "all_success",
                "已清除 %1$s 的所有任务进度。");

        addCommandFeedback("phase", "success",
                "已设置任务 §e%1$s§f → 阶段 §d%2$s§f，目标: %3$s。");
        addCommandFeedback("phase", "error.not_found",
                "在任务 '%2$s' 中未找到阶段 '%1$s'。");
        addCommandFeedback("phase", "error.wrong_state",
                "任务 '%1$s' 当前状态为 %2$s，无法切换阶段。");
        addCommandFeedback("phase", "error.failed",
                "激活任务 '%1$s' 失败。");

        addCommandFeedback("completephase", "success",
                "已强制完成 %3$s 在任务 §e%1$s§f 中的阶段 §d%2$s§f。");

        addCommandFeedback("progress", "success",
                "已设置任务 §e%3$s§f 的目标[%1$s] = %2$s。");
        addCommandFeedback("progress", "error.not_active",
                "任务 '%1$s' 未处于激活状态。");
        addCommandFeedback("progress", "error.out_of_range",
                "目标索引 %1$s 超出范围（最大 %2$s）。");
        addCommandFeedback("progress", "error.invalid_expr",
                "非法数量表达式 '%1$s'，请使用 +N、=N 或 N。");
        addCommandFeedback("progress", "error.phase_not_active",
                "阶段 '%1$s' 当前未激活。");
        addCommandFeedback("progress", "success_mode",
                "进度%1$s：任务=%2$s，阶段=%3$s，目标=%4$s，%5$s -> %6$s");

        addCommandFeedback("list", "header",
                "§6═══ %1$s 的任务列表 ═══");
        addCommandFeedback("list", "no_quests",
                "§7  暂无任务。");
        addCommandFeedback("list", "specify_player",
                "请指定玩家: /arcquest list <player>");
        addCommandFeedback("list", "no_capability",
                "§c  未找到玩家任务数据。");
        addCommandFeedback("list", "completed_history",
                "§2  完成历史: §f%1$s");

        addCommandFeedback("dialogue", "success",
                "已为 %2$s 启动对话 §e%1$s§f。");
        addCommandFeedback("dialogue", "error.not_found",
                "对话 '%1$s' 未找到。");

        addCommandFeedback("resetall", "success",
                "已清除 %1$s 的所有任务数据。");

        addCommandFeedback("reload", "success",
                "已触发数据包重载，定义将刷新。");

        addCommandFeedback("registry", "header",
                "§6═══ ArcQuest 注册表 ═══");
        addCommandFeedback("registry", "quests_header",
                "§e  任务 (%1$s):");
        addCommandFeedback("registry", "dialogues_header",
                "§e  对话 (%1$s):");

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
                "无效的任务编号格式：'%1$s'。");
        addCommandFeedback("error", "not_in_registry",
                "任务 '%1$s' 未在注册表中找到。");
        addCommandFeedback("error", "no_capability",
                "无法访问 %1$s 的任务数据。");

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
        add("arc_quest.command.trade.list.entry", "§f  %1$s §7- %2$s §8[共 %3$s 项%4$s]");
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

        // ── 交易重置命令 ──
        add("arc_quest.command.trade.reset.shop_success", "已重置玩家 %2$s 的商店 %1$s 的所有交易项");
        add("arc_quest.command.trade.reset.entry_success", "已重置玩家 %3$s 的商店 %2$s 中的交易项 %1$s");
        add("arc_quest.command.trade.reset.all_success", "已重置玩家 %1$s 的所有交易数据（共 %2$s 个交易项）");
        add("arc_quest.command.trade.reset.error.entry_not_found", "在商店 '%2$s' 中未找到交易项 '%1$s'");
    }

    // ═══════════════════════════════════════════════════════
    //  对话系统文本
    // ═══════════════════════════════════════════════════════

    private void addDialogueTranslations() {
        add("screen.phase_choice.title", "阶段选择");
        add("screen.dialogue.title", "对话");

        add("screen.phase_choice.choose_path", "选择你的道路");

        add("dialogue.speaker_prefix", "▸ ");
        add("dialogue.hint.close", "[点击或按空格键关闭]");
        add("dialogue.hint.loading", "...");
        add("dialogue.hint.continue", "[点击继续]");

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
     * 抽奖系统翻译。
     */
    private void addGachaTranslations() {
        // ── 屏幕标题 ──
        add("arc_quest.gui.gacha.title", "抽奖");

        // ── 按钮文本 ──
        add("arc_quest.gui.gacha.btn.decrypting", "处理中...");
        add("arc_quest.gui.gacha.btn.cooldown", "冷却中");
        add("arc_quest.gui.gacha.btn.insufficient_funds", "资金不足");
        add("arc_quest.gui.gacha.tooltip.rarity", "稀有度：%s");
        add("arc_quest.gui.gacha.tooltip.count_fixed", "数量：%s");
        add("arc_quest.gui.gacha.tooltip.count_range", "数量范围：%s - %s");
        add("arc_quest.gui.gacha.tooltip.weight", "权重：%s");
        add("arc_quest.gui.gacha.tooltip.pity_enabled", "保底：已开启");
        add("arc_quest.gui.gacha.btn.unlock_receptacle", "解锁容器");

        // ── 结果界面 ──
        add("arc_quest.gui.gacha.result.acknowledge", "点击确认");

        // ── 通用标签 ──
        add("arc_quest.gui.gacha.unknown_item", "未知物品");
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

        add("dialogue.epic_village_elder.start.phase_scout_forest",
                "§f很好，我们需要前线侦查。\n"
                        + "收集足够的线，布置预警陷阱，提前发现敌人动向。");
        add("dialogue.epic_village_elder.start.choice_scout_tip",
                "明白，我去侦查森林线。");

        add("dialogue.epic_village_elder.start.phase_reinforce_gate",
                "§f村门是最后一道防线。\n"
                        + "准备好橡木木板，把每一处薄弱点加固。");
        add("dialogue.epic_village_elder.start.choice_reinforce_tip",
                "村门防线交给我。");

        add("dialogue.epic_village_elder.start.phase_craft_sword",
                "§f两条支线都完成了，现在锻造一把真正的铁剑。\n"
                        + "真正的守护者，必须握有真正的钢铁。");
        add("dialogue.epic_village_elder.start.choice_craft_tip",
                "我这就去锻造铁剑。");

        add("dialogue.epic_village_elder.start.phase_defend",
                "§c§l警告！僵尸大军正在逼近！\n"
                        + "§f拿起你的武器，保卫我们的家园！\n"
                        + "§e（获得力量药水 x1）");
        add("dialogue.epic_village_elder.start.choice_encourage",
                "为了村庄！");

        add("dialogue.epic_village_elder.start.phase_gather_food",
                "§f战斗结束了，但村民仍然饥饿。\n"
                        + "带回面包和熟牛肉，让村庄恢复元气。");
        add("dialogue.epic_village_elder.start.choice_food_tip",
                "我马上去准备食物。");

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
                        + "§f第一步：收集§6橡木原木\n"
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

        addPhase("epic_prologue", "gather_wood", "武装自己");
        addObjective("epic_prologue", "gather_wood", 0,
                "收集§6橡木原木§r");

        addPhase("epic_prologue", "talk_villager", "寻求建议");
        addObjective("epic_prologue", "talk_villager", 0,
                "与§b村庄长老§r交谈");

        addPhase("epic_prologue", "scout_forest", "侦查森林");
        addObjective("epic_prologue", "scout_forest", 0,
                "收集线索：线");

        addPhase("epic_prologue", "reinforce_gate", "加固村门");
        addObjective("epic_prologue", "reinforce_gate", 0, "收集加固材料：木板");
        addObjective("epic_prologue", "reinforce_gate", 1, "收集加固材料：栅栏");
        addObjective("epic_prologue", "reinforce_gate", 2, "收集加固材料：墙");
        addObjective("epic_prologue", "reinforce_gate2", 0, "收集加固材料：门");
        addObjective("epic_prologue", "reinforce_gate2", 1, "收集加固材料：砖块");
        addObjective("epic_prologue", "reinforce_gate2", 2, "收集加固材料：白桦木原木");

        addPhase("epic_prologue", "craft_sword", "打造武器");
        addObjective("epic_prologue", "craft_sword", 0,
                "收集§f铁锭§r");
        addObjective("epic_prologue", "craft_sword", 1,
                "收集§6木棍§r)");

        addPhase("epic_prologue", "defend_village", "——保卫家园！");
        addObjective("epic_prologue", "defend_village", 0,
                "击退来袭的§c僵尸§r");
        addObjective("epic_prologue", "defend_village", 1,
                "驱逐§e沙漠尸鬼§r");

        addPhase("epic_prologue", "gather_food", "筹备物资");
        addObjective("epic_prologue", "gather_food", 0,
                "收集§6面包§r");
        addObjective("epic_prologue", "gather_food", 1,
                "收集§c熟牛肉§r");
    }

    // ═══════════════════════════════════════════════════════
    //  第壹章・深渊的低语
    // ═══════════════════════════════════════════════════════

    private void addChapter1Quest() {
        addQuest("epic_chapter1",
                "§6§l第壹章・深渊的低语",
                "矿洞深处传来不属于这个世界的诡异回响……\n\n"
                        + "是谁在黑暗中窃窃私语？鼓起勇气，向深渊进发吧。");

        addPhase("epic_chapter1", "prepare_tools", "准备装备");
        addObjective("epic_chapter1", "prepare_tools", 0,
                "收集§8煤炭§r (0/16)");
        addObjective("epic_chapter1", "prepare_tools", 1,
                "收集§6木棍§r (0/8)");

        addPhase("epic_chapter1", "explore_depths", "探索深处");
        addObjective("epic_chapter1", "explore_depths", 0,
                "清除§c洞穴蜘蛛§r (0/8)");
        addObjective("epic_chapter1", "explore_depths", 1,
                "收集§f蛛丝§r (0/10)");

        addPhase("epic_chapter1", "find_resources", "开采矿脉");
        addObjective("epic_chapter1", "find_resources", 0,
                "采集§f铁矿石§r (0/8)");
        addObjective("epic_chapter1", "find_resources", 1,
                "采集§6金矿石§r (0/4)");

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

        addPhase("epic_chapter2_combat", "build_portal", "打开通往炼狱的大门");
        addObjective("epic_chapter2_combat", "build_portal", 0,
                "收集§5黑曜石§r (0/10)");
        addObjective("epic_chapter2_combat", "build_portal", 1,
                "获得§f打火石§r");

        addPhase("epic_chapter2_combat", "enter_nether", "踏入下界");
        addObjective("epic_chapter2_combat", "enter_nether", 0,
                "击败§6烈焰人§r (0/5)");
        addObjective("epic_chapter2_combat", "enter_nether", 1,
                "收集§6烈焰棒§r (0/7)");

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

        addPhase("epic_chapter2_exploration", "prepare_underwater", "潜水准备");
        addObjective("epic_chapter2_exploration", "prepare_underwater", 0,
                "收集§f玻璃§r (0/8)");
        addObjective("epic_chapter2_exploration", "prepare_underwater", 1,
                "收集§e沙子§r (0/16)");

        addPhase("epic_chapter2_exploration", "dive_ocean", "深渊潜行");
        addObjective("epic_chapter2_exploration", "dive_ocean", 0,
                "击败§3守卫者§r (0/5)");
        addObjective("epic_chapter2_exploration", "dive_ocean", 1,
                "收集§b海晶砂粒§r (0/10)");

        addPhase("epic_chapter2_exploration", "solve_puzzle", "亘古的谜题");
        addObjective("epic_chapter2_exploration", "solve_puzzle", 0,
                "收集§b海晶碎片§r (0/16)");
        addObjective("epic_chapter2_exploration", "solve_puzzle", 1,
                "收集§e海绵§r (0/2)");

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

        addPhase("epic_finale", "prepare_ritual", "末日准备");
        addObjective("epic_finale", "prepare_ritual", 0,
                "收集§5末影珍珠§r (0/16)");
        addObjective("epic_finale", "prepare_ritual", 1,
                "收集§6烈焰粉§r (0/8)");

        addPhase("epic_finale", "craft_eyes", "锻造「虚空之眼」");
        addObjective("epic_finale", "craft_eyes", 0,
                "合成§5末影之眼§r (0/12)");

        addPhase("epic_finale", "find_stronghold", "寻找遗迹要塞");
        addObjective("epic_finale", "find_stronghold", 0,
                "击败§7蠹虫§r (0/10)");
        addObjective("epic_finale", "find_stronghold", 1,
                "收集§f末地石§r (0/32)");

        addPhase("epic_finale", "activate_portal", "开启「末路之门」");
        addObjective("epic_finale", "activate_portal", 0,
                "放置§5末影之眼§r (0/12)");

        addPhase("epic_finale", "enter_end", "踏足终末之地");
        addObjective("epic_finale", "enter_end", 0,
                "击败§5末影人§r (0/20)");

        addPhase("epic_finale", "destroy_crystals", "破除结界守护");
        addObjective("epic_finale", "destroy_crystals", 0,
                "清场——击败§5末影人§r (0/5)");

        addPhase("epic_finale", "dragon_fight", "——终结之刻！");
        addObjective("epic_finale", "dragon_fight", 0,
                "击败§5末影龙§r");

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

        addPhase("epic_branch_choice", "gather_info", "收集情报");
        addObjective("epic_branch_choice", "gather_info", 0,
                "收集§a绿宝石§r (0/10)");
        addObjective("epic_branch_choice", "gather_info", 1,
                "收集§f书籍§r (0/5)");

        addQuestChoice("epic_branch_choice", "gather_info", 0,
                "§c我要挑战烈焰堡垒！燃烧吧，我的战魂！");
        addQuestChoice("epic_branch_choice", "gather_info", 1,
                "§b我想探索深海遗迹……那里一定藏着古老的秘密。");
        addQuestChoice("epic_branch_choice", "gather_info", 2,
                "§6§l【隐藏】全、都、要！这就是我的觉悟！");

        addPhase("epic_branch_choice", "path_combat", "踏上征途");
        addObjective("epic_branch_choice", "path_combat", 0,
                "收集§4下界岩§r (0/32)");
        addObjective("epic_branch_choice", "path_combat", 1,
                "收集§6熔岩桶§r (0/2)");

        addPhase("epic_branch_choice", "path_exploration", "深海之旅");
        addObjective("epic_branch_choice", "path_exploration", 0,
                "收集§a海带§r (0/32)");
        addObjective("epic_branch_choice", "path_exploration", 1,
                "收集§f鳕鱼§r (0/10)");

        addPhase("epic_branch_choice", "path_both", "双修之路・觉醒");
        addObjective("epic_branch_choice", "path_both", 0,
                "击败§6烈焰人§r以证明实力 (0/3)");
        addObjective("epic_branch_choice", "path_both", 1,
                "收集§b海晶碎片§r (0/5)");
    }

    // ═══════════════════════════════════════════════════════
    private void addCollectionCodexDemoQuest() {
        addQuest("collection_codex_demo",
                "图鉴收录演示",
                "Objective Framework 2.0 收集任务演示。");

        add("arc_quest.phase.collection_codex_demo.codex_zombie", "僵尸图鉴");
        add("arc_quest.phase.collection_codex_demo.codex_zombie.desc", "击败 1 只僵尸并完成收录。");
        add("arc_quest.phase.collection_codex_demo.codex_skeleton", "骷髅图鉴");
        add("arc_quest.phase.collection_codex_demo.codex_skeleton.desc", "击败骷髅以完成该条目。");
        add("arc_quest.phase.collection_codex_demo.codex_spider_hidden", "蜘蛛图鉴");
        add("arc_quest.phase.collection_codex_demo.codex_spider_hidden.desc", "用于验证隐藏占位显示的条目。");
        add("arc_quest.phase.collection_codex_demo.codex_bone", "骨头样本");
        add("arc_quest.phase.collection_codex_demo.codex_bone.desc", "收集骨头以验证累计进度。");
        add("arc_quest.phase.collection_codex_demo.codex_rotten_flesh_unique", "腐肉样本");
        add("arc_quest.phase.collection_codex_demo.codex_rotten_flesh_unique.desc", "用于验证唯一集合收集逻辑的条目。");

        add("arc_quest.objective.collection_codex_demo.codex_zombie.0", "击败 1 只僵尸");
        add("arc_quest.objective.collection_codex_demo.codex_skeleton.0", "击败 3 只骷髅");
        add("arc_quest.objective.collection_codex_demo.codex_spider_hidden.0", "击败 1 只蜘蛛");
        add("arc_quest.objective.collection_codex_demo.codex_bone.0", "收集 5 个骨头");
        add("arc_quest.objective.collection_codex_demo.codex_rotten_flesh_unique.0", "收集 1 个腐肉");

        add("arc_quest.collection.collection_codex_demo.category.hostile_mobs", "敌对生物");
        add("arc_quest.collection.collection_codex_demo.category.field_resources", "野外资源");
    }

    //  Item Tag 翻译
    // ═══════════════════════════════════════════════════════

    private void addGuideTranslations() {
        add("gui.arc_quest.mark_all_read", "全部已读");
        addGuiGuideListText("title", "指南");
        addGuiGuideListText("no_guides", "暂无已解锁指南");
        addGuiGuideListText("no_guides_hint", "完成教学、任务或剧情后将在这里收录");

        add("key.arc_quest.open_guide_list", "打开指南列表");

        addGuideTitle("journal_basics", "任务日志");
        add("guide.arc_quest.journal_basics.summary", "学习如何打开任务日志。");
        addGuidePageDesc("journal_basics", 1,
                "按下 [%s] 打开任务日志，查看进行中、已完成和已失败的任务。");
        addGuideTitle("tracking_menu_basics", "快速切换任务");
        add("guide.arc_quest.tracking_menu_basics.summary", "学习如何快速切换进行中的任务。");
        addGuidePageDesc("tracking_menu_basics", 1,
                "长按 [%s] 打开任务追踪菜单，然后拖拽或滚动卡片来切换当前追踪的任务。");

        addGuideTitle("parallel_phases_basics", "并行阶段");
        add("guide.arc_quest.parallel_phases_basics.summary", "了解并行阶段，以及如何管理阶段焦点。");
        addGuidePageDesc("parallel_phases_basics", 1,
                "有些任务可以同时进行多个阶段。切换焦点可以选择任务追踪器跟随的阶段；在任务日志中，还可以拖动阶段卡片来调整显示顺序。");

        addGuideTitle("movement_basics", "移动基础");
        addGuidePageDesc("movement_basics", 1, "掌握基础移动技巧，自由探索世界。");
        addGuidePageDesc("movement_basics", 2, "观看演示学习高级移动技巧。");

        addGuideCategory("basics", "基础");
        addGuideCategory("quest", "任务");
        addGuideCategory("dialogue", "对话");
        addGuideCategory("trade", "交易");
        addGuideCategory("ponder", "沉思");
        addGuideCategory("advanced", "高级");

        addGuideCommandFeedback("list", "header", "§e=== 指南注册表 (%1$s) ===");
        addGuideCommandFeedback("list", "empty", "§7  （无已注册指南）");
        addGuideCommandFeedback("debug", "header", "§e=== 指南: %1$s ===");
        addGuideCommandFeedback("debug", "title", "§f  标题: %1$s");
        addGuideCommandFeedback("debug", "category", "§f  分类: %1$s");
        addGuideCommandFeedback("debug", "sort", "§7  排序: %1$s");
        addGuideCommandFeedback("debug", "hidden", "§7  隐藏: %1$s");
        addGuideCommandFeedback("debug", "repeatable", "§7  可重复弹出: %1$s");
        addGuideCommandFeedback("debug", "conditions", "§7  解锁条件数: %1$s");
        addGuideCommandFeedback("debug", "pages", "§7  页数: %1$s");
        addGuideCommandFeedback("status", "header", "§e=== 指南状态: %1$s ===");
        addGuideCommandFeedback("status", "unlocked", "§f  已解锁: %1$s");
    }

    private void addItemTagTranslations() {
        // 基础材料
        addItemTag("wool", "羊毛");
        addItemTag("planks", "木板");
        addItemTag("stone_bricks", "石砖");
        addItemTag("sand", "沙子");
        addItemTag("dirt", "泥土");
        addItemTag("terracotta", "陶瓦");
        addItemTag("leaves", "树叶");
        addItemTag("saplings", "树苗");

        // 木制物品
        addItemTag("wooden_buttons", "木质按钮");
        addItemTag("wooden_doors", "木门");
        addItemTag("wooden_stairs", "木楼梯");
        addItemTag("wooden_slabs", "木台阶");
        addItemTag("wooden_fences", "木栅栏");
        addItemTag("wooden_pressure_plates", "木质压力板");
        addItemTag("wooden_trapdoors", "木活板门");
        addItemTag("logs", "原木");
        addItemTag("oak_logs", "橡木原木");
        addItemTag("birch_logs", "白桦木原木");
        addItemTag("spruce_logs", "云杉木原木");
        addItemTag("jungle_logs", "丛林木原木");
        addItemTag("acacia_logs", "金合欢木原木");
        addItemTag("dark_oak_logs", "深色橡木原木");
        addItemTag("mangrove_logs", "红树木原木");
        addItemTag("cherry_logs", "樱花木原木");
        addItemTag("crimson_stems", "绯红菌柄");
        addItemTag("warped_stems", "诡异菌柄");
        addItemTag("bamboo_blocks", "竹方块");

        // 建筑方块
        addItemTag("stairs", "楼梯");
        addItemTag("slabs", "台阶");
        addItemTag("walls", "墙");
        addItemTag("fences", "栅栏");
        addItemTag("fence_gates", "栅栏门");
        addItemTag("buttons", "按钮");
        addItemTag("stone_buttons", "石质按钮");
        addItemTag("doors", "门");
        addItemTag("trapdoors", "活板门");
        addItemTag("anvil", "铁砧");
        addItemTag("rails", "铁轨");
        addItemTag("wool_carpets", "羊毛地毯");

        // 花卉与植物
        addItemTag("flowers", "花");
        addItemTag("small_flowers", "小型花");
        addItemTag("tall_flowers", "高花");
        addItemTag("wart_blocks", "疣块");

        // 矿石
        addItemTag("gold_ores", "金矿石");
        addItemTag("iron_ores", "铁矿石");
        addItemTag("diamond_ores", "钻石矿石");
        addItemTag("redstone_ores", "红石矿石");
        addItemTag("lapis_ores", "青金石矿石");
        addItemTag("coal_ores", "煤矿石");
        addItemTag("emerald_ores", "绿宝石矿石");
        addItemTag("copper_ores", "铜矿石");

        // 工具与武器
        addItemTag("swords", "剑");
        addItemTag("axes", "斧");
        addItemTag("hoes", "锄");
        addItemTag("pickaxes", "镐");
        addItemTag("shovels", "锹");
        addItemTag("tools", "工具");
        addItemTag("arrows", "箭");

        // 特殊物品
        addItemTag("banners", "旗帜");
        addItemTag("beds", "床");
        addItemTag("boats", "船");
        addItemTag("chest_boats", "运输船");
        addItemTag("signs", "告示牌");
        addItemTag("hanging_signs", "悬挂告示牌");
        addItemTag("music_discs", "音乐唱片");
        addItemTag("candles", "蜡烛");
        addItemTag("fishes", "鱼");
        addItemTag("coals", "煤炭");
        addItemTag("books", "书");
        addItemTag("lectern_books", "讲台用书");
        addItemTag("bookshelf_books", "书架用书");

        // 生物相关
        addItemTag("piglin_loved", "猪灵喜爱物品");
        addItemTag("piglin_repellents", "猪灵驱避物品");
        addItemTag("fox_food", "狐狸食物");
        addItemTag("axolotl_tempt_items", "美西螈诱捕物品");
        addItemTag("sniffer_food", "嗅探兽食物");
        addItemTag("villager_plantable_seeds", "村民可种植种子");

        // 装饰与合成
        addItemTag("decorated_pot_sherds", "饰纹陶罐碎片");
        addItemTag("decorated_pot_ingredients", "饰纹陶罐材料");
        addItemTag("trim_materials", "盔甲纹饰材料");
        addItemTag("trim_templates", "盔甲纹饰模板");
        addItemTag("trimmable_armor", "可纹饰盔甲");
        addItemTag("beacon_payment_items", "信标支付物品");

        // 其他
        addItemTag("compasses", "指南针");
        addItemTag("creeper_drop_music_discs", "苦力怕掉落音乐唱片");
        addItemTag("creeper_igniters", "苦力怕点燃器");
        addItemTag("noteblock_top_instruments", "音符盒顶部乐器");
        addItemTag("breaks_decorated_pots", "可破坏饰纹陶罐");
        addItemTag("dampens_vibrations", "阻尼振动");
        addItemTag("cluster_max_harvestables", "簇最大可收获物");
        addItemTag("freeze_immune_wearables", "防冻可穿戴物品");
        addItemTag("stone_tool_materials", "石制工具材料");
        addItemTag("stone_crafting_materials", "石制合成材料");
        addItemTag("smelts_to_glass", "可熔炼成玻璃");
        addItemTag("non_flammable_wood", "不可燃木材");
        addItemTag("soul_fire_base_blocks", "灵魂火基座方块");
        addItemTag("completes_find_tree_tutorial", "完成找树教程");
    }
}
