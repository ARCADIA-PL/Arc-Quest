package org.arcadia.arc_quest.data;

import net.minecraft.data.PackOutput;

/**
 * 英文语言文件生成器。
 * <p>
 * 翻译基调：欧美史诗奇幻（《上古卷轴》《巫师》风格），庄重、沉浸感强。
 */
public class ArcQuestENLangProvider extends ArcQuestLangProvider {

    public ArcQuestENLangProvider(PackOutput output) {
        super(output, "en_us");
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

        // ── Quest Chains ──
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
        add("gui.arc_quest.tracking_menu.title", "Quest Tracking");
        add("arc_quest.gui.tracking_menu.hint", "Scroll or drag to browse • Click to track • Hover for phase details");
        add("arc_quest.gui.tracking_menu.empty", "No active quests");
        add("arc_quest.gui.tracking_menu.current_phase", "CURRENT PHASE: %s");
        add("arc_quest.gui.tracking_menu.no_phase", "No active phase");
        add("arc_quest.gui.tracking_menu.no_objectives", "No active objectives");
        add("arc_quest.gui.tracking_menu.phase_progress", "Phase progress: %s/%s");
        add("arc_quest.gui.tracking_menu.optional", " (Optional)");
        add("arc_quest.gui.tracking_menu.more_objectives", "%s more objectives");
        add("key.categories.arc_quest", "Arc Quest");
        add("key.arc_quest.open_journal", "Open Quest Screen");
        add("key.arc_quest.open_tracking_menu", "Hold to Open Quest Tracking Menu");
        add("gui.arc_quest.journal.title", "Quest Journal");
    }

    // ═══════════════════════════════════════════════════════
    //  GUI 界面文本
    // ═══════════════════════════════════════════════════════

    private void addGuiTranslations() {
        add("arc_quest.category.archon", "Archon");
        add("arc_quest.category.companion", "Companion");
        add("arc_quest.category.adventure", "Adventure");
        add("arc_quest.category.daily", "Daily");
        add("arc_quest.category.event", "Event");
        add("arc_quest.quest_group.epic_mainline", "Epic Mainline");

        add("arc_quest.gui.tracker.open_journal_shortcut", "[%s] Quest Panel");
        add("arc_quest.gui.tracker.new_quest", "New quest available. Press [%s] to view");
        add("arc_quest.gui.tracker.new_guide", "New tutorial available. Press [%s] to view");
        add("arc_quest.guide.splash.status", "NEW TUTORIAL UNLOCKED");
        add("arc_quest.guide_group.minerals", "Minerals");
        add("gui.arc_quest.guide_list.title", "Guides");

        // ── Trade Screen Titles ──
        add("arc_quest.gui.trade.full_title", "Trade Matrix");
        add("arc_quest.gui.trade.quick_title", "Quick Trade");

        // ── Trade Status Labels ──
        addTradeGuiText("status", "maxed", "Maxed");
        addTradeGuiText("status", "locked", "Locked");
        addTradeGuiText("status", "on_cooldown", "On Cooldown");

        // ── Trade Buttons ──
        addTradeGuiText("btn", "purchase", "Purchase");
        addTradeGuiText("btn", "wait", "Wait");
        addTradeGuiText("btn", "locked", "Locked");
        addTradeGuiText("btn", "empty", "Empty");

        // ── Trade Tooltip ──
        addTradeGuiText("tooltip", "limit", "Limit: %1$s / %2$s");
        addTradeGuiText("tooltip", "cooldown", "Cooldown: %1$s");
        addTradeGuiText("tooltip", "shortfall_summary", "// FUNDING ERROR");
        addTradeGuiText("tooltip", "shortfall_line", "%1$s x%2$s");
        addTradeGuiText("tooltip", "shortfall_meta", "Need %1$s / Have %2$s");

        // ── Trade Error Messages ──
        addTradeGuiText("error", "unknown_shop", "Unknown Shop");
        addTradeGuiText("error", "shop_closed", "Shop Closed");

        // ── Journal Tabs ──
        addGuiTab("journal", "active", "ACTIVE");
        addGuiTab("journal", "completed", "COMPLETED");
        addGuiTab("journal", "failed", "FAILED");
        addGuiTab("journal", "history", "HISTORY");
        addGuiTab("journal", "guide", "PLAY GUIDE");

        // ── Journal Sections ──
        addGuiSection("journal", "current_phase", "▸ PHASE: %s");
        addGuiSection("journal", "parallel_lanes", "PARALLEL LANES");
        addGuiSection("journal", "focus_phase", "FOCUS: ");
        addGuiSection("journal", "completed_phases", "▸ COMPLETED PHASES");
        addGuiSection("journal", "choose_path", "▸ CHOOSE YOUR PATH");
        addGuiSection("journal", "phase_rewards", "▸ Phase Rewards");
        addGuiSection("journal", "chapter_rewards", "▸ Chapter Rewards");

        // ── Journal Labels ──
        addGuiLabel("journal", "no_phases_completed",
                "§7No phases completed yet.");
        addGuiLabel("journal", "quest_completed",
                "§a✔ This quest has been completed.");
        addGuiLabel("journal", "quest_failed",
                "§c✘ This quest has failed.");
        addGuiLabel("journal", "select_quest", "Select a quest");
        addGuiLabel("journal", "no_active_phase", "No active phase.");
        addGuiLabel("journal", "choices_locked", "Choices locked");
        addGuiLabel("journal", "topology_map", "Topology MAP");
        addGuiLabel("journal", "view_node_graph_history", "View node graph & history");
        add("arc_quest.quest_action_result.template", "[Quest] %1$s failed: %2$s (%3$s)");
        add("arc_quest.quest_action.accept", "Accept quest");
        add("arc_quest.quest_action.abandon", "Abandon quest");
        add("arc_quest.quest_action.choose", "Choose branch");
        add("arc_quest.quest_action.open_chapter_shop", "Open chapter shop");
        add("arc_quest.quest_action.claim_collection_reward", "Claim collection reward");
        add("arc_quest.quest_action.confirm_phase_advance", "Confirm phase completion");
        add("arc_quest.quest_reject.quest_not_found", "Quest not found");
        add("arc_quest.quest_reject.already_active", "Quest is already active");
        add("arc_quest.quest_reject.already_completed_not_repeatable", "Quest is already completed and not repeatable");
        add("arc_quest.quest_reject.unlock_condition_not_met", "Unlock condition not met");
        add("arc_quest.quest_reject.no_initial_phase", "Quest has no initial phase");
        add("arc_quest.quest_reject.not_active", "Quest is not active");
        add("arc_quest.quest_reject.abandon_not_allowed", "This quest cannot be abandoned");
        add("arc_quest.quest_reject.phase_not_found", "Current phase not found");
        add("arc_quest.quest_reject.invalid_choice_index", "Invalid branch index");
        add("arc_quest.quest_reject.choice_condition_not_met", "Branch condition not met");
        add("arc_quest.quest_reject.choice_target_phase_missing", "Branch target phase missing");
        add("arc_quest.quest_reject.chapter_shop_not_configured", "Chapter shop is not configured for this quest");
        add("arc_quest.quest_reject.chapter_shop_not_accessible", "Chapter shop is not accessible in the current state");
        add("arc_quest.quest_reject.chapter_shop_definition_not_found", "Chapter shop definition not found");
        add("arc_quest.quest_reject.collection_reward_id_invalid", "Collection reward ID is invalid");
        add("arc_quest.quest_reject.collection_reward_not_unlocked", "Collection reward is not unlocked yet");
        add("arc_quest.quest_reject.collection_reward_already_claimed", "Collection reward has already been claimed");
        add("arc_quest.quest_reject.collection_reward_not_manual", "This collection reward cannot be claimed manually");
        add("arc_quest.quest_reject.collection_reward_node_not_found", "Collection reward node not found");
        add("arc_quest.quest_reject.collection_data_missing", "Quest runtime collection data is missing");
        add("arc_quest.quest_reject.collection_config_missing", "Quest collection config is missing");
        add("arc_quest.gui.quest_offer.header", "SYS.ARC_QUEST // UPLOAD PROTOCOL");
        add("arc_quest.gui.quest_offer.status", "STATUS: %1$s / %2$s");
        add("arc_quest.gui.quest_intel.header", "SYS.ARC_QUEST // PHASE INTEL");
        add("arc_quest.gui.quest_intel.hint_close", "ESC / Click outside to close");
        add("arc_quest.gui.quest_story.header", "SYS.ARC_QUEST // STORY ARCHIVE");
        add("arc_quest.gui.quest_story.prev", "<< PREV");
        add("arc_quest.gui.quest_story.next", "NEXT >>");
        add("arc_quest.gui.quest_story.page", "PAGE %1$s / %2$s");
        add("arc_quest.message.quest_action_failed", "[Quest] %1$s failed: %2$s (%3$s)");
        add("arc_quest.message.quest_action.accept", "Accept quest");
        add("arc_quest.message.quest_action.abandon", "Abandon quest");
        add("arc_quest.message.quest_action.choose", "Choose branch");
        add("arc_quest.message.quest_action.open_chapter_shop", "Open chapter shop");
        add("arc_quest.message.quest_action.claim_collection_reward", "Claim collection reward");
        add("arc_quest.message.quest_action.confirm_phase_advance", "Confirm phase completion");
        add("arc_quest.message.quest_reason.quest_not_found", "Quest not found");
        add("arc_quest.message.quest_reason.already_active", "Quest is already active");
        add("arc_quest.message.quest_reason.already_completed_not_repeatable", "Quest is already completed and not repeatable");
        add("arc_quest.message.quest_reason.unlock_condition_not_met", "Unlock condition not met");
        add("arc_quest.message.quest_reason.no_initial_phase", "Quest has no initial phase");
        add("arc_quest.message.quest_reason.not_active", "Quest is not active");
        add("arc_quest.message.quest_reason.phase_not_found", "Phase not found");
        add("arc_quest.message.quest_reason.invalid_choice_index", "Invalid choice index");
        add("arc_quest.message.quest_reason.choice_condition_not_met", "Choice condition not met");
        add("arc_quest.message.quest_reason.choice_target_phase_missing", "Choice target phase is missing");
        add("arc_quest.message.quest_reason.chapter_shop_not_configured", "Chapter shop is not configured");
        add("arc_quest.message.quest_reason.chapter_shop_not_accessible", "Chapter shop is not accessible right now");
        add("arc_quest.message.quest_reason.chapter_shop_definition_not_found", "Chapter shop definition not found");
        add("arc_quest.message.quest_reason.collection_reward_id_invalid", "Invalid collection reward ID");
        add("arc_quest.message.quest_reason.collection_reward_not_unlocked", "Collection reward is not unlocked");
        add("arc_quest.message.quest_reason.collection_reward_already_claimed", "Collection reward already claimed");
        add("arc_quest.message.quest_reason.collection_reward_not_manual", "Collection reward cannot be claimed manually");
        add("arc_quest.message.quest_reason.collection_reward_node_not_found", "Collection reward node not found");
        add("arc_quest.message.quest_reason.collection_data_missing", "Collection runtime data is missing");
        add("arc_quest.message.quest_reason.collection_config_missing", "Collection config is missing");
        add("arc_quest.message.quest_reason.unknown", "Unknown reason");
        addGuiLabel("journal", "click_to_submit", "Click to submit items");
        addGuiLabel("journal", "story_archive", "Phase Story");
        addGuiLabel("journal", "read_story", "Read");
        addGuiLabel("journal", "unread_phase_story", "[ Click to view phase story ]");
        addGuiLabel("journal", "objective_complete_prefix", "§a✔ ");
        addGuiLabel("journal", "objective_active_prefix", "§f○ ");
        addGuiLabel("journal", "pending_phase_prefix", "Awaiting confirmation: ");

        // ── Journal Buttons ──
        addGuiButton("journal", "track", "⊕ TRACK");
        addGuiButton("journal", "tracked", "§a⊕ TRACKED");
        addGuiButton("journal", "untrack", "⊖ UNTRACK");
        addGuiButton("journal", "confirm_phase_complete", "✔ CONFIRM PHASE");
        addGuiButton("journal", "chapter_shop", "Chapter Shop");
        addGuiButton("journal", "abandon", "✘ ABANDON");
        addGuiButton("journal", "restart", "↻ RESTART");
    }

    // ═══════════════════════════════════════════════════════
    //  HUD 文本
    // ═══════════════════════════════════════════════════════

    private void addHudTranslations() {
        addHudText("new_phase", "ARC QUEST // NEW PHASE");
        addHudText("phase_prefix", "▸ %s");
        addHudText("objective_complete_prefix", "§a✔ ");
        addHudText("objective_active_prefix", "§7○ ");
        addHudText("phase_switched", "ARC QUEST // PHASE SWITCHED");
        addHudText("phase_completed", "ARC QUEST // PHASE COMPLETED");
        addHudText("parallel_lanes", "Parallel Lanes");
        addHudText("parallel_more", "+%s more lanes...");
        addHudText("toast.phase_added", "[// PARALLEL LANE INITIATED ]");
        addHudText("toast.phase_switched", "[// FOCUS SHIFTED ]");
        addHudText("toast.phase_completed", "[// LANE SECURED ]");
        addHudText("toast.phase_pending_confirm", "[// AWAITING MANUAL CONFIRM ]");
    }

    // ═══════════════════════════════════════════════════════
    //  Toast 提示
    // ═══════════════════════════════════════════════════════

    private void addToastTranslations() {
        addToast("branch_available", "Branch available: %s");
        add("arc_quest.toast.branch.subtitle", "ARES SYSTEM // BRANCH AVAILABLE");
        add("arc_quest.toast.branch.prefix", "New Path Unlocked: ");
        addToast("quest_accepted", "§aQuest Accepted");
        addToast("quest_completed", "§2Quest Completed");
        addToast("quest_failed", "§cQuest Failed");
        addToast("phase_advanced", "Phase Advanced");

        // Toast type prefixes
        add("arc_quest.toast.prefix.quest_accepted", "✦ QUEST ACCEPTED");
        add("arc_quest.toast.prefix.quest_completed", "★ QUEST COMPLETED");
        add("arc_quest.toast.prefix.quest_failed", "✘ QUEST FAILED");
        add("arc_quest.toast.prefix.phase_advanced", "▸ PHASE ADVANCED");
        add("arc_quest.toast.prefix.objective_complete", "✔ OBJECTIVE DONE");
    }

    private void addToastConfigTranslations() {
        add("gui.arc_quest.toast_config.title", "Toast Notifications");
        add("gui.arc_quest.toast_config.subtitle", "Choose which quest notifications may appear on your HUD.");
        add("gui.arc_quest.toast_config.enabled", "ON");
        add("gui.arc_quest.toast_config.disabled", "OFF");
        add("gui.arc_quest.toast_config.reset", "Reset Defaults");
        add("gui.arc_quest.toast_config.pause_button", "Arc Quest Notifications");
        addToastConfigOption("quest_accepted", "Quest Accepted", "Shown when a new quest becomes active.");
        addToastConfigOption("quest_completed", "Quest Completed", "Shown when a quest is completed.");
        addToastConfigOption("quest_failed", "Quest Failed", "Shown when a quest fails.");
        addToastConfigOption("phase_advanced", "Phase Updates (Master)", "Master switch for every phase update toast listed below.");
        addToastConfigOption("objective_complete", "Objective Completed", "Shown when an objective is completed.");
        addToastConfigOption("collection_entry_discovered", "Collection Entry Discovered", "Shown when a hidden collection entry is discovered.");
        addToastConfigOption("collection_entry_completed", "Collection Entry Completed", "Shown when a collection entry is completed.");
        addToastConfigOption("collection_reward_unlocked", "Collection Reward Unlocked", "Shown when a collection reward becomes available.");
        addToastConfigOption("collection_reward_claimed", "Collection Reward Claimed", "Shown after claiming a collection reward.");
        addToastConfigOption("phase_added", "Tracked Phase Added", "Shown when a phase is added to the tracked quest.");
        addToastConfigOption("phase_switched", "Tracked Phase Switched", "Shown when tracked quest focus changes phase.");
        addToastConfigOption("phase_completed", "Tracked Phase Completed", "Shown when the tracked phase is completed.");
        addToastConfigOption("phase_pending_confirm", "Phase Confirmation Required", "Shown when the tracked phase awaits manual confirmation.");
        addToastConfigOption("branch_choice", "Branch Choice Available", "Shown when the tracked quest has an available branch choice.");
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
        // ── give ──
        addCommandFeedback("give", "success",
                "Gave quest §e%1$s§f to %2$s.");
        addCommandFeedback("give", "error.already_active",
                "Quest §e%1$s§f is already ACTIVE for %2$s.");
        addCommandFeedback("give", "error.already_completed",
                "Quest §e%1$s§f is already COMPLETED and not repeatable.");
        addCommandFeedback("give", "error.failed",
                "Failed to start quest '%1$s'.");

        // ── complete ──
        addCommandFeedback("complete", "success",
                "Force-completed quest §e%1$s§f for %2$s.");
        addCommandFeedback("complete", "error.not_active",
                "Quest '%1$s' is not active.");

        // ── fail ──
        addCommandFeedback("fail", "success",
                "Force-failed quest §e%1$s§f for %2$s.");
        addCommandFeedback("fail", "error.not_active",
                "Quest '%1$s' is not active.");

        // ── reset ──
        addCommandFeedback("reset", "success",
                "Reset quest §e%1$s§f for %2$s.");
        addCommandFeedback("reset", "all_success",
                "Cleared all quest progress for %1$s.");

        // ── phase ──
        addCommandFeedback("phase", "success",
                "Set quest §e%1$s§f → phase §d%2$s§f for %3$s.");
        addCommandFeedback("phase", "error.not_found",
                "Phase '%1$s' not found in quest '%2$s'.");
        addCommandFeedback("phase", "error.wrong_state",
                "Quest '%1$s' is in state %2$s, cannot change phase.");
        addCommandFeedback("phase", "error.failed",
                "Failed to activate quest '%1$s'.");

        // ── completephase ──
        addCommandFeedback("completephase", "success",
                "Force-completed phase §d%2$s§f in quest §e%1$s§f for %3$s.");

        // ── progress ──
        addCommandFeedback("progress", "success",
                "Set objective[%1$s] = %2$s for quest §e%3$s§f.");
        addCommandFeedback("progress", "error.not_active",
                "Quest '%1$s' is not active.");
        addCommandFeedback("progress", "error.out_of_range",
                "Objective index %1$s out of range (max %2$s).");
        addCommandFeedback("progress", "error.invalid_expr",
                "Invalid amount expression '%1$s'. Use +N, =N, or N.");
        addCommandFeedback("progress", "error.phase_not_active",
                "Phase '%1$s' is not active.");
        addCommandFeedback("progress", "success_mode",
                "Progress %1$s: quest=%2$s, phase=%3$s, obj=%4$s, %5$s -> %6$s");

        // ── list ──
        addCommandFeedback("list", "header",
                "§6═══ Quests for %1$s ═══");
        addCommandFeedback("list", "no_quests",
                "§7  No quests.");
        addCommandFeedback("list", "specify_player",
                "Specify a player: /arcquest list <player>");
        addCommandFeedback("list", "no_capability",
                "§c  Capability not found.");
        addCommandFeedback("list", "completed_history",
                "§2  Completed history: §f%1$s");

        // ── dialogue ──
        addCommandFeedback("dialogue", "success",
                "Started dialogue §e%1$s§f for %2$s.");
        addCommandFeedback("dialogue", "error.not_found",
                "Dialogue '%1$s' not found.");

        // ── resetall ──
        addCommandFeedback("resetall", "success",
                "Cleared all quest data for %1$s.");

        // ── reload ──
        addCommandFeedback("reload", "success",
                "Triggered datapack reload. Definitions will be refreshed.");

        // ── registry ──
        addCommandFeedback("registry", "header",
                "§6═══ ArcQuest Registry ═══");
        addCommandFeedback("registry", "quests_header",
                "§e  Quests (%1$s):");
        addCommandFeedback("registry", "dialogues_header",
                "§e  Dialogues (%1$s):");

        // ── debug ──
        addCommandFeedback("debug", "header",
                "§6═══ Debug: %1$s ═══");
        addCommandFeedback("debug", "display",
                "§e  Display: §f%1$s");
        addCommandFeedback("debug", "phases",
                "§e  Phases: §f%1$s");
        addCommandFeedback("debug", "repeatable",
                "§e  Repeatable: §f%1$s");
        addCommandFeedback("debug", "runtime_header",
                "§a  --- Player Runtime ---");
        addCommandFeedback("debug", "runtime_state",
                "§a  State: %1$s");
        addCommandFeedback("debug", "runtime_phase",
                "§a  Phase: %1$s");
        addCommandFeedback("debug", "runtime_obj",
                "§a  Obj[%1$s]: %2$s");
        addCommandFeedback("debug", "no_runtime",
                "§7  Player has no runtime data for this quest.");

        // ── 通用错误 ──
        addCommandFeedback("error", "invalid_id",
                "Invalid quest ID format: '%1$s'.");
        addCommandFeedback("error", "not_in_registry",
                "Quest '%1$s' not found in registry.");
        addCommandFeedback("error", "no_capability",
                "Failed to access quest data for %1$s. Capability not attached.");

        // ── Dialogue System Commands ──
        add("arc_quest.command.dialogue.reset.all", "Reset all dialogue progress for player %1$s");
        add("arc_quest.command.dialogue.reset.single", "Requested reset of dialogue tree %1$s for player %2$s (WIP)");
        add("arc_quest.command.dialogue.status.header", "§e=== Dialogue Status ===");
        add("arc_quest.command.dialogue.status.player", "§fPlayer: %1$s");
        add("arc_quest.command.dialogue.status.work_in_progress", "§7(Detailed history feature WIP...)");

        // ── Trade System Commands ──
        add("arc_quest.command.trade.error.not_found", "Trade shop not found: %1$s");
        add("arc_quest.command.trade.open.success", "Opened trade shop %1$s for %2$s");
        add("arc_quest.command.trade.simple.success", "Opened simple trade %1$s for %2$s");
        add("arc_quest.command.trade.list.header", "§e=== Trade Shop Registry (%1$s) ===");
        add("arc_quest.command.trade.list.entry", "§f  %1$s §7- %2$s §8[%3$s entries%4$s]");
        add("arc_quest.command.trade.list.empty", "§7  (No registered trade shops)");
        add("arc_quest.command.trade.debug.header", "§e=== Trade Shop Debug: %1$s ===");
        add("arc_quest.command.trade.debug.name", "§f  Name: %1$s");
        add("arc_quest.command.trade.debug.description", "§7  Description: %1$s");
        add("arc_quest.command.trade.debug.mode.simple", "§f  Mode: §aSimple Popup");
        add("arc_quest.command.trade.debug.mode.full", "§f  Mode: §bFull Window");
        add("arc_quest.command.trade.debug.categories", "§f  Categories: §7%1$s");
        add("arc_quest.command.trade.debug.category_entry", "§d    %1$s §7- %2$s");
        add("arc_quest.command.trade.debug.entries", "§f  Entries: §7%1$s");
        add("arc_quest.command.trade.debug.entry_header", "§a    %1$s §7| %2$s");
        add("arc_quest.command.trade.debug.costs", "§7      Costs: §c%1$s");
        add("arc_quest.command.trade.debug.rewards", "§7      Rewards: §b%1$s");
        add("arc_quest.command.trade.debug.limit", "§7      Limit: §e%1$s times");
        add("arc_quest.command.trade.debug.cooldown", "§7      Cooldown: §e%1$s (%2$s)");

        // ── Trade Reset Commands ──
        add("arc_quest.command.trade.reset.shop_success", "Reset all entries in shop %1$s for player %2$s");
        add("arc_quest.command.trade.reset.entry_success", "Reset entry %1$s in shop %2$s for player %3$s");
        add("arc_quest.command.trade.reset.all_success", "Reset all trade data for player %1$s (%2$s entries total)");
        add("arc_quest.command.trade.reset.error.entry_not_found", "Entry '%1$s' not found in shop '%2$s'");
    }

    // ═══════════════════════════════════════════════════════
    //  对话系统文本
    // ═══════════════════════════════════════════════════════

    private void addDialogueTranslations() {
        // ── Screen Titles ──
        add("screen.phase_choice.title", "Phase Choice");
        add("screen.dialogue.title", "Dialogue");

        // ── Phase Choice Panel ──
        add("screen.phase_choice.choose_path", "Choose Your Path");

        // ── Dialogue Hints ──
        add("dialogue.speaker_prefix", "▸ ");
        add("dialogue.hint.close", "[Click or press Space to close]");
        add("dialogue.hint.loading", "...");
        add("dialogue.hint.continue", "[Click to continue]");

        // ── Test Villager Dialogue ──
        add("dialogue.test_villager.npc_name", "Villager");
        add("dialogue.test_villager.start.text",
                "§fGreetings, adventurer! The village has been troubled of late...");
        add("dialogue.test_villager.start.choice1",
                "What has happened?");
        add("dialogue.test_villager.start.choice2",
                "Farewell");
        add("dialogue.test_villager.ask_problem.text",
                "§fZombies assail the village under cover of night. We require aid!\n"
                        + "Might you gather some provisions for us?");
        add("dialogue.test_villager.ask_problem.choice1",
                "I shall lend my aid!");
        add("dialogue.test_villager.ask_problem.choice2",
                "I have no time for this.");
        add("dialogue.test_villager.decline.text",
                "§fVery well... Should you change your mind, seek me out at any hour.");
        add("dialogue.test_villager.decline.choice1",
                "Farewell");
    }

    /**
     * Trade system translations.
     */
    private void addTradeTranslations() {
        // ── Screen Titles ──
        addTradeScreenTitle("screen", "Trade Matrix");
        addTradeScreenTitle("quick", "Quick Trade");

        // ── Common Labels ──
        addTradeLabel("subtitle", "Select Goods");
        addTradeLabel("closed", "Shop Closed");
        addTradeLabel("unknown_shop", "Unknown Shop");
        addTradeLabel("buy", "Buy");
        addTradeLabel("sold_out", "Sold Out");
        addTradeLabel("purchase", "Purchase");
        addTradeLabel("maxed", "Maxed");
        addTradeLabel("cooldown", "%ds");

        // ── Categories ──
        addTradeCategory("all", "All");

        // ── Trade Offer Templates ──
        addTradeOfferTemplate("item", "%s x%d");
        addTradeOfferTemplate("effect", "%s%s %ds");
        addTradeOfferTemplate("command", "Command: %s");
        addTradeOfferTemplate("flag.require", "Requires: %s");
        addTradeOfferTemplate("flag.reward", "Gain Flag: %s");

        // ── Feedback Messages ──
        addTradeFeedback("success", "Trade Successful!");
        addTradeFeedback("error.cooldown", "On cooldown, wait %d seconds");
        addTradeFeedback("error.limit", "Purchase limit reached");
        addTradeFeedback("error.cannot_afford", "Insufficient resources");
        addTradeFeedback("error.condition", "Conditions not met");

        // ── Cooldown Hint Texts ──
        addTradeCooldownText("game_day", "Resets tomorrow");
        addTradeCooldownText("seconds_remaining", "Resets in %ds");
        addTradeCooldownText("ticks_remaining", "Resets in %dt");

        // ── Trade Shops ──
        addTradeShop("blacksmith_shop", "Blacksmith Shop", "Sells various weapons, armor and tools");
        addTradeShop("potion_shop", "Potion Merchant", "Sells various potion effects");
        addTradeShop("quick_food_trade", "Quick Supplies", null);
        addTradeShop("merchant_shop", "Traveling Merchant's Satchel", "Supplies from across the lands");
        addTradeShop("wandering_trader_shop", "Wandering Trader's Treasures", "Rare goods updated daily");
        addTradeShop("mysterious_merchant_shop", "Mysterious Midnight Shop", "A forbidden vault open only at night");
        addTradeShop("quick_supplies", "Guard's Quick Supplies", "Convenient purchase without closing dialogue");

        // ── Categories ──
        addTradeCategory("weapons", "Weapons");
        addTradeCategory("armor", "Armor");
        addTradeCategory("tools", "Tools");
        addTradeCategory("supplies", "Supplies");
        addTradeCategory("rare", "Treasures");

        // ── Trade Entries (Blacksmith) ──
        addTradeEntry("iron_sword", "Iron Sword", null);
        addTradeEntry("diamond_sword", "Diamond Sword", null);
        addTradeEntry("iron_chestplate", "Iron Chestplate", null);
        addTradeEntry("iron_helmet", "Iron Helmet", null);
        addTradeEntry("iron_pickaxe", "Iron Pickaxe", null);

        // ── Trade Entries (Potion Shop) ──
        addTradeEntry("strength_potion", "Strength Potion", "Grants Strength I for 60 seconds");
        addTradeEntry("speed_potion", "Speed Potion", "Grants Speed I for 120 seconds");
        addTradeEntry("regen_potion", "Regeneration Potion", "Grants Regeneration II for 30 seconds");

        // ── Trade Entries (Quick Trade) ──
        addTradeEntry("buy_bread", "Bread x4", null);
        addTradeEntry("buy_steak", "Steak x2", null);
        addTradeEntry("buy_golden_apple", "Golden Apple", null);

        // ── Trade Entries (Quick Supplies) ──
        addTradeEntry("qs_bread", "Bread x4", null);
        addTradeEntry("qs_potion", "Healing Potion", null);

        // ── Trade Entries (Merchant) ──
        addTradeEntry("merchant_food_pack", "Emergency Rations", "Includes bread and baked potatoes");
        addTradeEntry("merchant_torch_bundle", "Explorer's Torch Bundle", "Essential for deep mining");

        // ── Trade Entries (Wandering Trader) ──
        addTradeEntry("trader_exotic_plant", "Exotic Plant Seeds", "Strange seeds from distant lands");
        addTradeEntry("trader_dye_set", "Dye Collection", "Colorful materials for creation");

        // ── Trade Entries (Mysterious Merchant) ──
        addTradeEntry("mystery_netherite", "Netherite Ingot", "Metal infused with destructive power");
        addTradeEntry("mystery_totem", "Totem of Undying", "The last hope against death");
    }

    /**
     * Gacha system translations.
     */
    private void addGachaTranslations() {
        // ── Screen Titles ──
        add("arc_quest.gui.gacha.title", "Gacha Matrix");

        // ── Button Text ──
        add("arc_quest.gui.gacha.btn.decrypting", "Decrypting...");
        add("arc_quest.gui.gacha.btn.cooldown", "On Cooldown");
        add("arc_quest.gui.gacha.btn.insufficient_funds", "Insufficient Funds");
        add("arc_quest.gui.gacha.tooltip.rarity", "RARITY // %s");
        add("arc_quest.gui.gacha.tooltip.count_fixed", "COUNT // %s");
        add("arc_quest.gui.gacha.tooltip.count_range", "COUNT RANGE // %s - %s");
        add("arc_quest.gui.gacha.tooltip.weight", "WEIGHT // %s");
        add("arc_quest.gui.gacha.tooltip.pity_enabled", "PITY // ENABLED");
        add("arc_quest.gui.gacha.btn.unlock_receptacle", "Unlock Receptacle");

        // ── Result Screen ──
        add("arc_quest.gui.gacha.result.acknowledge", "Click to Acknowledge");

        // ── Common Labels ──
        add("arc_quest.gui.gacha.unknown_item", "Unknown Item");
    }

    /**
     * Epic mainline dialogue translations.
     */
    private void addEpicDialogueTranslations() {
        // ═══════════════════════════════════════════
        // Village Elder
        // ═══════════════════════════════════════════
        add("dialogue.epic_village_elder.npc_name", "§6§lVillage Elder");

        // Start node - multiple states
        add("dialogue.epic_village_elder.start.newcomer",
                "§fWelcome, stranger. I am the guardian of these lands.\n"
                        + "If you are ready, I can tell you the truth about this world...");
        add("dialogue.epic_village_elder.start.choice_intro",
                "Please, tell me what has happened.");

        add("dialogue.epic_village_elder.start.phase_gather_wood",
                "§fAdventurer, gather wood and craft weapons!\n"
                        + "The zombie horde may strike at any moment. We must be prepared.");
        add("dialogue.epic_village_elder.start.choice_wood_tip",
                "I will complete it as soon as possible");

        add("dialogue.epic_village_elder.start.phase_talk_villager",
                "§fExcellent, you have armed yourself.\n"
                        + "Now, let me inform you of the next step...");
        add("dialogue.epic_village_elder.start.choice_accept_mission",
                "I am ready. Give the order!");

        add("dialogue.epic_village_elder.start.phase_scout_forest",
                "§fGood. We need eyes in the wilds.\n"
                        + "Gather enough string so we can set warning snares around the village.");
        add("dialogue.epic_village_elder.start.choice_scout_tip",
                "Understood, I will scout the forest route.");

        add("dialogue.epic_village_elder.start.phase_reinforce_gate",
                "§fThe main gate is our last wall.\n"
                        + "Prepare enough oak planks and reinforce every weak section.");
        add("dialogue.epic_village_elder.start.choice_reinforce_tip",
                "Leave the gate defense to me.");

        add("dialogue.epic_village_elder.start.phase_craft_sword",
                "§fBoth operations are done. Now forge a proper iron blade.\n"
                        + "A true defender must carry real steel.");
        add("dialogue.epic_village_elder.start.choice_craft_tip",
                "I will forge the sword immediately.");

        add("dialogue.epic_village_elder.start.phase_defend",
                "§c§lWARNING! The zombie army approaches!\n"
                        + "§fTake up your arms and defend our home!\n"
                        + "§e(Received Strength Potion x1)");
        add("dialogue.epic_village_elder.start.choice_encourage",
                "For the village!");

        add("dialogue.epic_village_elder.start.phase_gather_food",
                "§fThe battle is won, but our people are starving.\n"
                        + "Bring bread and cooked beef so the village can recover.");
        add("dialogue.epic_village_elder.start.choice_food_tip",
                "I will deliver food right away.");

        add("dialogue.epic_village_elder.start.prologue_done",
                "§a§lWell done, warrior!\n"
                        + "§fThe prologue is complete, but the true challenge has just begun.\n"
                        + "Strange echoes come from the depths of the mine... Are you ready to explore?");
        add("dialogue.epic_village_elder.start.choice_start_chapter1",
                "I am ready. Begin Chapter 1!");

        add("dialogue.epic_village_elder.start.chapter1_done",
                "§b§lIncredible... You defeated the mine guardian!\n"
                        + "§fNow you face an important choice:\n"
                        + "§c• Path of Combat: Assault the Nether Fortress\n"
                        + "§9• Path of Exploration: Seek the Ocean Monument\n"
                        + "Your choice will determine your ultimate fate...");
        add("dialogue.epic_village_elder.start.choice_branch",
                "I wish to learn more about these choices");

        add("dialogue.epic_village_elder.start.legendary",
                "§5§l✨ Legendary Hero ✨\n"
                        + "§fYou did it! The Ender Dragon has been vanquished, and peace restored.\n"
                        + "Your name shall be forever remembered in these lands!\n"
                        + "§e(Received Hero of the Village effect + 100 XP)");
        add("dialogue.epic_village_elder.start.choice_legendary",
                "It is my honor");

        add("dialogue.epic_village_elder.start.default",
                "§fMay your journey be fruitful, adventurer.");
        add("dialogue.epic_village_elder.start.choice_default",
                "Farewell");

        // Intro story
        add("dialogue.epic_village_elder.intro_story.text",
                "§6§lThe gears of fate begin to turn...\n\n"
                        + "§fLong ago, this world was filled with peace and prosperity.\n"
                        + "But with the awakening of the Ender Dragon, dark forces rose once more.\n"
                        + "The undead army stands at our gates. We need a hero...\n\n"
                        + "§eWill you become our hope?");
        add("dialogue.epic_village_elder.intro_story.choice_accept",
                "I accept this mission!");
        add("dialogue.epic_village_elder.intro_story.choice_decline",
                "I need time to consider");

        // Prologue accepted
        add("dialogue.epic_village_elder.prologue_accepted.text",
                "§a§lQuest Accepted!\n\n"
                        + "§fFirst: Collect §6Oak Logs x5\n"
                        + "Second: Return to me for further instructions\n\n"
                        + "§eGood luck, warrior!");
        add("dialogue.epic_village_elder.prologue_accepted.choice_ok",
                "Understood!");

        // Chapter 1 accepted
        add("dialogue.epic_village_elder.chapter1_accepted.text",
                "§b§lChapter I: Whispers of the Abyss\n\n"
                        + "§fAncient secrets lie hidden in the depths of the mine...\n"
                        + "Prepare torches and weapons. Beware of cave spiders!\n\n"
                        + "§eObjectives: Collect Coal x16, Sticks x8");
        add("dialogue.epic_village_elder.chapter1_accepted.choice_ok",
                "I will be careful");

        // Branch introduction
        add("dialogue.epic_village_elder.branch_introduction.text",
                "§6§lTwo paths, two destinies...\n\n"
                        + "§c[Path of Combat]\n"
                        + "Assault the Nether Fortress directly, facing Blazes and Wither Skeletons.\n"
                        + "Suitable for warriors who love intense combat.\n\n"
                        + "§9[Path of Exploration]\n"
                        + "Uncover the secrets of the Ocean Monument, solving ancient puzzles.\n"
                        + "Suitable for adventurers who love discovery.\n\n"
                        + "§eWhat is your choice?");
        add("dialogue.epic_village_elder.branch_introduction.choice_combat",
                "Tell me about the Path of Combat");
        add("dialogue.epic_village_elder.branch_introduction.choice_exploration",
                "Tell me about the Path of Exploration");
        add("dialogue.epic_village_elder.branch_introduction.choice_think",
                "Let me think about it");

        // Explain combat path
        add("dialogue.epic_village_elder.explain_combat.text",
                "§c§lPath of Combat: Trial by Fire\n\n"
                        + "§fYou will face:\n"
                        + "• §6Blazes§r - Flame emissaries of the Nether\n"
                        + "• §4Wither Skeletons§r - Terrifying fortress guards\n"
                        + "• §5Nether Fortress§r - A dangerous labyrinth\n\n"
                        + "§eRewards: Netherite scraps, powerful combat skills\n\n"
                        + "§cWarning: This path is fraught with danger, but the rewards are great!");
        add("dialogue.epic_village_elder.explain_combat.choice_confirm",
                "I choose combat!");

        // Explain exploration path
        add("dialogue.epic_village_elder.explain_exploration.text",
                "§9§lPath of Exploration: Echoes of the Deep\n\n"
                        + "§fYou will discover:\n"
                        + "• §3Guardians§r - Sentinels of the Ocean Monument\n"
                        + "• §bPrismarine Materials§r - Mysterious ocean energy\n"
                        + "• §eElder Guardian§r - The final boss of the temple\n\n"
                        + "§eRewards: Trident, Heart of the Sea, ancient knowledge\n\n"
                        + "§9Tip: Prepare potions of water breathing!");
        add("dialogue.epic_village_elder.explain_exploration.choice_confirm",
                "I choose exploration!");

        // Decline node
        add("dialogue.epic_village_elder.decline.text",
                "§fNo matter. Return when you are ready.\n"
                        + "The village gates are always open to you.");
        add("dialogue.epic_village_elder.decline.choice1",
                "Thank you for understanding");

        // ═══════════════════════════════════════════
        // Blacksmith
        // ═══════════════════════════════════════════
        add("dialogue.epic_blacksmith.npc_name", "§8§lBlacksmith");

        add("dialogue.epic_blacksmith.start.early",
                "§fHey, rookie! Need weapons?\n"
                        + "I only have stone gear for now, but it's enough for early battles.\n"
                        + "§e(Stone Sword x1 = 5 gold)");
        add("dialogue.epic_blacksmith.start.choice_buy_stone_sword",
                "Give me a stone sword");

        add("dialogue.epic_blacksmith.start.mid",
                "§7Oh? You've started exploring the mines?\n"
                        + "Then you'll need better equipment!\n"
                        + "§e(Full Iron Armor Set = 50 gold)");
        add("dialogue.epic_blacksmith.start.choice_buy_iron_gear",
                "I want iron gear");

        add("dialogue.epic_blacksmith.start.late",
                "§bWow, you completed Chapter 1?!\n"
                        + "Looks like you're a serious adventurer.\n"
                        + "Check out these diamond wares!\n"
                        + "§e(Full Diamond Armor Set = 100 gold)");
        add("dialogue.epic_blacksmith.start.choice_buy_diamond_gear",
                "Diamond gear sounds good");

        add("dialogue.epic_blacksmith.start.legendary",
                "§5§lA Legendary Hero arrives!\n"
                        + "§fIt is my honor to forge equipment for you.\n"
                        + "Behold my finest work — Netherite gear!\n"
                        + "§e(Netherite Ingot x1 = 200 gold)");
        add("dialogue.epic_blacksmith.start.choice_buy_netherite",
                "I want the strongest gear!");

        add("dialogue.epic_blacksmith.start.default",
                "§fSpeak your needs. My craftsmanship is the finest in the village!");
        add("dialogue.epic_blacksmith.start.choice_leave",
                "Perhaps next time");
        add("dialogue.epic_blacksmith.start.choice_open_shop",
                "§8View Full Inventory (Open Shop)");

        // ═══════════════════════════════════════════
        // Merchant
        // ═══════════════════════════════════════════
        add("dialogue.epic_merchant.npc_name", "§a§lTraveling Merchant");

        add("dialogue.epic_merchant.start.phase_gather",
                "§fOff on an adventure, adventurer?\n"
                        + "Bring some food. You might get hungry on the road.\n"
                        + "§e(Bread x4 = 2 gold)");
        add("dialogue.epic_merchant.start.choice_buy_food",
                "Some bread, please");

        add("dialogue.epic_merchant.start.phase_defend",
                "§cHeard zombies are coming to attack?\n"
                        + "These healing potions might save your life!\n"
                        + "§e(Healing Potion x2 = 10 gold)");
        add("dialogue.epic_merchant.start.choice_buy_potions",
                "Give me some potions");

        add("dialogue.epic_merchant.start.phase_chapter1",
                "§8Heading to the mines? A wise choice is to bring enough torches.\n"
                        + "Anything could happen in the darkness...\n"
                        + "§e(Torches x32 = 5 gold)");
        add("dialogue.epic_merchant.start.choice_buy_torches",
                "A stack of torches");

        add("dialogue.epic_merchant.start.default",
                "§fWelcome to my shop! A wide variety of goods await~");
        add("dialogue.epic_merchant.start.choice_browse",
                "Let me see what you have");
        add("dialogue.epic_merchant.start.choice_leave",
                "Until next time");

        add("dialogue.epic_merchant.shop_menu.text",
                "§a§l=== Merchant's Shelves ===\n\n"
                        + "§fPlease select the category of goods you wish to purchase:");
        add("dialogue.epic_merchant.shop_menu.choice_food",
                "🍞 Food Pack (5 gold)");
        add("dialogue.epic_merchant.shop_menu.choice_tools",
                "⚒️ Tool Pack (10 gold)");
        add("dialogue.epic_merchant.shop_menu.choice_back",
                "← Back");
        add("dialogue.epic_merchant.shop_menu.choice_open_shop",
                "§aView Full Satchel (Open Shop)");

        // ═══════════════════════════════════════════
        // Wandering Trader - sayIf() Example NPC
        // ═══════════════════════════════════════════
        add("dialogue.epic_wandering_trader.npc_name", "§a§lWandering Trader");

        // Start node - 5 states
        add("dialogue.epic_wandering_trader.start.newbie",
                "§fHey there, new face! I'm a traveling merchant.\n"
                        + "§eIf you're just starting out, I can give you some advice...");

        add("dialogue.epic_wandering_trader.start.prologue_active",
                "§fOh? You've accepted the Elder's quest?\n"
                        + "§ePrologue in progress... Good luck, adventurer!");

        add("dialogue.epic_wandering_trader.start.chapter1_ready",
                "§bHeard you completed the prologue? Amazing!\n"
                        + "§eTreasures await in the mine depths. Ready to explore?");

        add("dialogue.epic_wandering_trader.start.chapter1_active",
                "§8The mine is dangerous, isn't it? Watch out for cave spiders!\n"
                        + "§eIf your gear isn't good enough, I have some nice items...");

        add("dialogue.epic_wandering_trader.start.legendary",
                "§5§l✨ Legendary Hero! ✨\n"
                        + "§fNever thought I'd meet the one who defeated the Ender Dragon!\n"
                        + "§eMy humble shop is honored. Please browse freely!");

        add("dialogue.epic_wandering_trader.start.default",
                "§fWelcome to my mobile shop! Items refresh daily~");

        add("dialogue.epic_wandering_trader.start.choice_shop",
                "Let me see your wares");
        add("dialogue.epic_wandering_trader.start.choice_rumors",
                "Any rumors?");
        add("dialogue.epic_wandering_trader.start.choice_bye",
                "Farewell");

        // Shop menu
        add("dialogue.epic_wandering_trader.shop_menu.text",
                "§a§l=== Wandering Trader's Shelves ===\n\n"
                        + "§fPlease select the category of goods you wish to purchase:");
        add("dialogue.epic_wandering_trader.shop_menu.choice_supplies",
                "📦 Supply Pack (Torches x32 + Bread x4)");
        add("dialogue.epic_wandering_trader.shop_menu.choice_equipment",
                "⚔️ Weapons & Armor");
        add("dialogue.epic_wandering_trader.shop_menu.choice_back",
                "← Back");
        add("dialogue.epic_wandering_trader.shop_menu.choice_open_shop",
                "§bView Daily Treasures (Open Shop)");

        // Equipment menu
        add("dialogue.epic_wandering_trader.equipment_menu.text",
                "§6§l=== Weapons & Armor ===\n\n"
                        + "§fBased on your adventure progress, here are recommendations:");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_stone",
                "🗡️ Stone Sword x1 (Beginner)");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_iron",
                "🛡️ Iron Armor Set (Advanced)");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_diamond",
                "💎 Diamond Armor Set (Elite)");
        add("dialogue.epic_wandering_trader.equipment_menu.choice_back",
                "← Back");

        // Purchase complete
        add("dialogue.epic_wandering_trader.purchase_complete.text",
                "§a§lTransaction Complete!\n\n"
                        + "§fThank you for your business. Good luck!");
        add("dialogue.epic_wandering_trader.purchase_complete.choice_continue",
                "Continue shopping");

        // Rumors
        add("dialogue.epic_wandering_trader.rumors.text",
                "§6§l=== Latest Rumors ===\n\n"
                        + "§f• Strange echoes from the mine depths...\n"
                        + "§f• Some say they saw a Nether Fortress\n"
                        + "§f• Ocean monuments hide ancient secrets\n"
                        + "§f• The Ender Dragon's power is awakening\n\n"
                        + "§eI heard these from travelers across the land~");
        add("dialogue.epic_wandering_trader.rumors.choice_back",
                "← Back");

        // ═══════════════════════════════════════════
        // Village Guard - Game tick cooldown, time period conditions, dialogue history, and weights
        // ═══════════════════════════════════════════
        add("dialogue.epic_village_guard.npc_name", "§b§lVillage Guard");

        // Start node - Different greetings based on time of day
        add("dialogue.epic_village_guard.start.morning",
                "§fGood morning, adventurer! §eA new day begins, and the village is safe.\n"
                        + "How may I assist you?");

        add("dialogue.epic_village_guard.start.afternoon",
                "§fGood afternoon! §eThe sun shines bright, perfect for patrol or training.\n"
                        + "What can I do for you?");

        add("dialogue.epic_village_guard.start.night",
                "§8Good evening... §7Stay vigilant, danger lurks outside.\n"
                        + "If you need assistance, I can offer some support.");

        add("dialogue.epic_village_guard.start.default",
                "§fGreetings, I am the village guard.\n"
                        + "My duty is to protect this place.");

        add("dialogue.epic_village_guard.start.choice_intro",
                "Who are you?");
        add("dialogue.epic_village_guard.start.choice_daily",
                "📋 Daily Patrol Mission (resets at 6:00)");
        add("dialogue.epic_village_guard.start.choice_training",
                "⚔️ Request Training (1h cooldown)");
        add("dialogue.epic_village_guard.start.choice_chat",
                "Casual Chat");
        add("dialogue.epic_village_guard.start.choice_quick_trade",
                "§e🍞 Quick Buy Supplies (Simple Window)");
        add("dialogue.epic_village_guard.start.choice_bye",
                "Farewell");

        // Introduction node
        add("dialogue.epic_village_guard.intro.text",
                "§b§l=== Village Guard ===\n\n"
                        + "§fI am Alvin, captain of the guard here.\n"
                        + "§eMy duty is to protect the village from monster attacks.\n\n"
                        + "§aEvery morning at 6:00, I post patrol missions.\n"
                        + "§6Combat training is available once per hour.\n\n"
                        + "§dPleased to meet you, adventurer!");
        add("dialogue.epic_village_guard.intro.choice_ok",
                "Pleased to meet you too");

        // Daily patrol mission
        add("dialogue.epic_village_guard.daily_patrol.text",
                "§a§l=== Daily Patrol Mission ===\n\n"
                        + "§fToday's mission is to patrol around the village and ensure no monsters appear.\n\n"
                        + "§aRewards:\n"
                        + "§f• Bread x3\n"
                        + "§f• 10 XP\n\n"
                        + "§eThis mission resets every morning at 6:00.");
        add("dialogue.epic_village_guard.daily_patrol.choice_thanks",
                "Thank you!");

        // Training request
        add("dialogue.epic_village_guard.training.text",
                "§6§l=== Combat Training ===\n\n"
                        + "§fLet me teach you some basic combat techniques.\n"
                        + "§eThis strength potion will aid your practice.\n\n"
                        + "§aObtained: Strength Potion x1\n\n"
                        + "§cNext training session available in 1 hour.");
        add("dialogue.epic_village_guard.training.choice_thanks",
                "Many thanks!");

        // Chat node - Different content based on time
        add("dialogue.epic_village_guard.chat.morning",
                "§fThe morning air is so refreshing!\n"
                        + "§eI love patrolling at this time, watching the sunrise.\n\n"
                        + "§aToday will be a peaceful day... probably.");

        add("dialogue.epic_village_guard.chat.afternoon",
                "§fThe afternoon sun is quite bright.\n"
                        + "§eBut this weather is perfect for training recruits.\n\n"
                        + "§aWould you like to try as well?");

        add("dialogue.epic_village_guard.chat.night",
                "§8Night is the most dangerous time...\n"
                        + "§7Zombies, skeletons, and creepers all come out.\n\n"
                        + "§cBe careful, don't venture too far from the village.");

        add("dialogue.epic_village_guard.chat.default",
                "§fThe village has been relatively calm lately.\n"
                        + "§eBut we must remain vigilant at all times.");

        add("dialogue.epic_village_guard.chat.choice_back",
                "← Back");

        // ═══════════════════════════════════════════
        // Mysterious Merchant - Custom time ranges and complex conditions
        // ═══════════════════════════════════════════
        add("dialogue.epic_mysterious_merchant.npc_name", "§5§lMysterious Merchant");

        // Start node - Only open during specific hours
        add("dialogue.epic_mysterious_merchant.start.open",
                "§5§l✨ Midnight Shop Open ✨\n\n"
                        + "§fWelcome to my secret shop...\n"
                        + "§eI only appear during the late night hours.\n\n"
                        + "§dHere you'll find rare items unavailable elsewhere.");

        add("dialogue.epic_mysterious_merchant.start.closed_morning",
                "§8...\n\n"
                        + "§7The merchant seems to be resting.\n"
                        + "§eHe only appears late at night.");

        add("dialogue.epic_mysterious_merchant.start.closed_afternoon",
                "§8...\n\n"
                        + "§7The merchant is not here.\n"
                        + "§ePerhaps try again tonight.");

        add("dialogue.epic_mysterious_merchant.start.default",
                "§8A mysterious merchant, seemingly waiting for the right moment.");

        add("dialogue.epic_mysterious_merchant.start.choice_shop",
                "💎 Browse Wares (late night only)");
        add("dialogue.epic_mysterious_merchant.start.choice_identity",
                "Who are you?");
        add("dialogue.epic_mysterious_merchant.start.choice_leave",
                "Leave");

        // Shop node
        add("dialogue.epic_mysterious_merchant.shop.text",
                "§5§l=== Mysterious Shop ===\n\n"
                        + "§fThese are treasures I've collected from across the realms...\n"
                        + "§eThough pricey, they're absolutely worth it.\n\n"
                        + "§dWhat would you like to purchase?");

        add("dialogue.epic_mysterious_merchant.shop.choice_rare",
                "⭐ Netherite Ingot x1 (Rare Item)");
        add("dialogue.epic_mysterious_merchant.shop.choice_common",
                "💎 Diamond Armor Set (Common Item)");
        add("dialogue.epic_mysterious_merchant.shop.choice_back",
                "← Back");

        // Identity reveal node
        add("dialogue.epic_mysterious_merchant.identity.text",
                "§5§l=== The Merchant's True Identity ===\n\n"
                        + "§fHeh... you wish to know who I am?\n\n"
                        + "§eI am a traveler who journeys between dimensions,\n"
                        + "§5collecting the world's most precious treasures.\n\n"
                        + "§dOnly when the world sleeps, in the dead of night,\n"
                        + "§5do I open my shop.\n\n"
                        + "§fNow that you know this secret...\n"
                        + "§eKeep it well.");
        add("dialogue.epic_mysterious_merchant.identity.choice_back",
                "← Back");
    }

    // ═══════════════════════════════════════════════════════
    //  序章：村庄危机
    // ═══════════════════════════════════════════════════════

    private void addPrologueQuest() {
        addQuest("epic_prologue",
                "§6§lPrologue: The Village Under Siege",
                "The village trembles under the relentless assault of the undead.\n\n"
                        + "Take up arms and drive back the darkness before all is lost.");

        // ── Phase: gather_wood ──
        addPhase("epic_prologue", "gather_wood", "Arm Yourself");
        addObjective("epic_prologue", "gather_wood", 0,
                "Gather Oak Logs (0/5)");

        // ── Phase: talk_villager ──
        addPhase("epic_prologue", "talk_villager", "Seek Counsel");
        addObjective("epic_prologue", "talk_villager", 0,
                "Speak with the Village Elder");

        // ── Phase: scout_forest ──
        addPhase("epic_prologue", "scout_forest", "Scout the Forest");
        addObjective("epic_prologue", "scout_forest", 0,
                "Gather Clues: String (0/6)");

        // ── Phase: reinforce_gate ──
        addPhase("epic_prologue", "reinforce_gate", "Reinforce the Village Gate");
        addObjective("epic_prologue", "reinforce_gate", 0,
                "Gather Building Materials: Oak Planks (0/16)");

        // ── Phase: craft_sword ──
        addPhase("epic_prologue", "craft_sword", "Forge a Weapon");
        addObjective("epic_prologue", "craft_sword", 0,
                "Collect Iron Ingots (0/3)");
        addObjective("epic_prologue", "craft_sword", 1,
                "Collect Sticks (0/2)");

        // ── Phase: defend_village ──
        addPhase("epic_prologue", "defend_village", "Defend the Homeland");
        addObjective("epic_prologue", "defend_village", 0,
                "Slay the Besieging Zombies (0/10)");
        addObjective("epic_prologue", "defend_village", 1,
                "Vanquish Desert Husks (0/3)");

        // ── Phase: gather_food ──
        addPhase("epic_prologue", "gather_food", "Stockpile Provisions");
        addObjective("epic_prologue", "gather_food", 0,
                "Gather Bread (0/10)");
        addObjective("epic_prologue", "gather_food", 1,
                "Gather Cooked Beef (0/5)");
    }

    // ═══════════════════════════════════════════════════════
    //  第一章：暗影矿洞
    // ═══════════════════════════════════════════════════════

    private void addChapter1Quest() {
        addQuest("epic_chapter1",
                "§6§lChapter I: Shadows Beneath the Stone",
                "Unearthly sounds echo from the depths of the mine.\n\n"
                        + "Steel your nerves and descend into the abyss to uncover the truth.");

        // ── Phase: prepare_tools ──
        addPhase("epic_chapter1", "prepare_tools", "Prepare Your Gear");
        addObjective("epic_chapter1", "prepare_tools", 0,
                "Collect Coal (0/16)");
        addObjective("epic_chapter1", "prepare_tools", 1,
                "Collect Sticks (0/8)");

        // ── Phase: explore_depths ──
        addPhase("epic_chapter1", "explore_depths", "Explore the Depths");
        addObjective("epic_chapter1", "explore_depths", 0,
                "Eradicate Cave Spiders (0/8)");
        addObjective("epic_chapter1", "explore_depths", 1,
                "Collect Spider Silk (0/10)");

        // ── Phase: find_resources ──
        addPhase("epic_chapter1", "find_resources", "Mine the Veins");
        addObjective("epic_chapter1", "find_resources", 0,
                "Mine Iron Ore (0/8)");
        addObjective("epic_chapter1", "find_resources", 1,
                "Mine Gold Ore (0/4)");

        // ── Phase: boss_fight ──
        addPhase("epic_chapter1", "boss_fight", "The Mine Guardian");
        addObjective("epic_chapter1", "boss_fight", 0,
                "Defeat the Wither Skeleton");
    }

    // ═══════════════════════════════════════════════════════
    //  第二章A：烈焰堡垒
    // ═══════════════════════════════════════════════════════

    private void addChapter2CombatQuest() {
        addQuest("epic_chapter2_combat",
                "§c§lChapter II-A: The Blazing Fortress",
                "You have chosen the path of war — a direct assault upon the Nether Fortress.\n\n"
                        + "May the flames forge your legend, or consume you whole.");

        // ── Phase: build_portal ──
        addPhase("epic_chapter2_combat", "build_portal", "Open the Gate");
        addObjective("epic_chapter2_combat", "build_portal", 0,
                "Collect Obsidian (0/10)");
        addObjective("epic_chapter2_combat", "build_portal", 1,
                "Obtain Flint and Steel");

        // ── Phase: enter_nether ──
        addPhase("epic_chapter2_combat", "enter_nether", "Enter the Nether");
        addObjective("epic_chapter2_combat", "enter_nether", 0,
                "Slay Blazes (0/5)");
        addObjective("epic_chapter2_combat", "enter_nether", 1,
                "Collect Blaze Rods (0/7)");

        // ── Phase: assault_fortress ──
        addPhase("epic_chapter2_combat", "assault_fortress", "Storm the Fortress");
        addObjective("epic_chapter2_combat", "assault_fortress", 0,
                "Slay Wither Skeletons (0/5)");
        addObjective("epic_chapter2_combat", "assault_fortress", 1,
                "Collect Nether Bricks (0/16)");
    }

    // ═══════════════════════════════════════════════════════
    //  第二章B：深海遗迹
    // ═══════════════════════════════════════════════════════

    private void addChapter2ExplorationQuest() {
        addQuest("epic_chapter2_exploration",
                "§b§lChapter II-B: Ruins of the Deep",
                "You have chosen the path of discovery — the ocean monument beckons.\n\n"
                        + "Beneath the waves lie ancient secrets and forgotten power.");

        // ── Phase: prepare_underwater ──
        addPhase("epic_chapter2_exploration", "prepare_underwater",
                "Underwater Preparations");
        addObjective("epic_chapter2_exploration", "prepare_underwater", 0,
                "Collect Glass (0/8)");
        addObjective("epic_chapter2_exploration", "prepare_underwater", 1,
                "Collect Sand (0/16)");

        // ── Phase: dive_ocean ──
        addPhase("epic_chapter2_exploration", "dive_ocean", "Plunge into the Abyss");
        addObjective("epic_chapter2_exploration", "dive_ocean", 0,
                "Defeat Guardians (0/5)");
        addObjective("epic_chapter2_exploration", "dive_ocean", 1,
                "Collect Prismarine Crystals (0/10)");

        // ── Phase: solve_puzzle ──
        addPhase("epic_chapter2_exploration", "solve_puzzle", "The Ancient Enigma");
        addObjective("epic_chapter2_exploration", "solve_puzzle", 0,
                "Collect Prismarine Shards (0/16)");
        addObjective("epic_chapter2_exploration", "solve_puzzle", 1,
                "Collect Sponges (0/2)");

        // ── Phase: defeat_guardian ──
        addPhase("epic_chapter2_exploration", "defeat_guardian",
                "The Temple Sentinel");
        addObjective("epic_chapter2_exploration", "defeat_guardian", 0,
                "Defeat the Elder Guardian");
    }

    // ═══════════════════════════════════════════════════════
    //  终章：末地决战
    // ═══════════════════════════════════════════════════════

    private void addFinaleQuest() {
        addQuest("epic_finale",
                "§5§lFinale: The End of All Things",
                "All roads converge here — the hour of reckoning is at hand.\n\n"
                        + "Face the Ender Dragon and etch your name into eternity.");

        // ── Phase: prepare_ritual ──
        addPhase("epic_finale", "prepare_ritual", "Preparations for Doomsday");
        addObjective("epic_finale", "prepare_ritual", 0,
                "Collect Ender Pearls (0/16)");
        addObjective("epic_finale", "prepare_ritual", 1,
                "Collect Blaze Powder (0/8)");

        // ── Phase: craft_eyes ──
        addPhase("epic_finale", "craft_eyes", "Forge the Eyes of Ender");
        addObjective("epic_finale", "craft_eyes", 0,
                "Craft Eyes of Ender (0/12)");

        // ── Phase: find_stronghold ──
        addPhase("epic_finale", "find_stronghold", "Locate the Stronghold");
        addObjective("epic_finale", "find_stronghold", 0,
                "Slay Silverfish (0/10)");
        addObjective("epic_finale", "find_stronghold", 1,
                "Collect End Stone (0/32)");

        // ── Phase: activate_portal ──
        addPhase("epic_finale", "activate_portal", "Open the Final Gate");
        addObjective("epic_finale", "activate_portal", 0,
                "Place Eyes of Ender (0/12)");

        // ── Phase: enter_end ──
        addPhase("epic_finale", "enter_end", "Set Foot in the End");
        addObjective("epic_finale", "enter_end", 0,
                "Defeat Endermen (0/20)");

        // ── Phase: destroy_crystals ──
        addPhase("epic_finale", "destroy_crystals", "Shatter the Wards");
        addObjective("epic_finale", "destroy_crystals", 0,
                "Clear Endermen from the Area (0/5)");

        // ── Phase: dragon_fight ──
        addPhase("epic_finale", "dragon_fight", "The Final Hour");
        addObjective("epic_finale", "dragon_fight", 0,
                "Slay the Ender Dragon");

        // ── Phase: return_home ──
        addPhase("epic_finale", "return_home", "The Hero's Return");
        addObjective("epic_finale", "return_home", 0,
                "Claim the Dragon Head");
    }

    // ═══════════════════════════════════════════════════════
    //  抉择时刻
    // ═══════════════════════════════════════════════════════

    private void addBranchChoiceQuest() {
        addQuest("epic_branch_choice",
                "§e§lThe Crossroads of Fate",
                "A fork in the road lies before you — choose wisely, for there is no turning back.\n\n"
                        + "Each path demands a different sacrifice.");

        // ── Phase: gather_info ──
        addPhase("epic_branch_choice", "gather_info", "Gather Intelligence");
        addObjective("epic_branch_choice", "gather_info", 0,
                "Collect Emeralds (0/10)");
        addObjective("epic_branch_choice", "gather_info", 1,
                "Collect Books (0/5)");

        // ── Choices ──
        addQuestChoice("epic_branch_choice", "gather_info", 0,
                "§cI shall storm the Blazing Fortress!");
        addQuestChoice("epic_branch_choice", "gather_info", 1,
                "§bI wish to explore the Ocean Ruins.");
        addQuestChoice("epic_branch_choice", "gather_info", 2,
                "§6§l[Hidden] I will conquer both!");

        // ── Phase: path_combat ──
        addPhase("epic_branch_choice", "path_combat", "Embark on the Crusade");
        addObjective("epic_branch_choice", "path_combat", 0,
                "Collect Netherrack (0/32)");
        addObjective("epic_branch_choice", "path_combat", 1,
                "Collect Lava Buckets (0/2)");

        // ── Phase: path_exploration ──
        addPhase("epic_branch_choice", "path_exploration", "Voyage into the Deep");
        addObjective("epic_branch_choice", "path_exploration", 0,
                "Collect Kelp (0/32)");
        addObjective("epic_branch_choice", "path_exploration", 1,
                "Collect Cod (0/10)");

        // ── Phase: path_both ──
        addPhase("epic_branch_choice", "path_both", "The Path of Dual Mastery");
        addObjective("epic_branch_choice", "path_both", 0,
                "Defeat 3 Blazes to Prove Your Strength");
        addObjective("epic_branch_choice", "path_both", 1,
                "Collect Prismarine Shards (0/5)");
    }

    // ═══════════════════════════════════════════════════════
    private void addCollectionCodexDemoQuest() {
        addQuest("collection_codex_demo",
                "Collection Codex Demo",
                "Objective Framework 2.0 collection quest demo.");

        add("arc_quest.phase.collection_codex_demo.codex_zombie", "Zombie Record");
        add("arc_quest.phase.collection_codex_demo.codex_zombie.desc", "Defeat one zombie to record it in the codex.");
        add("arc_quest.phase.collection_codex_demo.codex_skeleton", "Skeleton Record");
        add("arc_quest.phase.collection_codex_demo.codex_skeleton.desc", "Defeat skeletons to complete this entry.");
        add("arc_quest.phase.collection_codex_demo.codex_spider_hidden", "Spider Record");
        add("arc_quest.phase.collection_codex_demo.codex_spider_hidden.desc", "A hidden entry used to verify placeholder presentation.");
        add("arc_quest.phase.collection_codex_demo.codex_bone", "Bone Sample");
        add("arc_quest.phase.collection_codex_demo.codex_bone.desc", "Collect bones to test accumulated collection progress.");
        add("arc_quest.phase.collection_codex_demo.codex_rotten_flesh_unique", "Rotten Flesh Sample");
        add("arc_quest.phase.collection_codex_demo.codex_rotten_flesh_unique.desc", "A unique-set entry for collection dispatcher coverage.");

        add("arc_quest.objective.collection_codex_demo.codex_zombie.0", "Defeat 1 zombie");
        add("arc_quest.objective.collection_codex_demo.codex_skeleton.0", "Defeat 3 skeletons");
        add("arc_quest.objective.collection_codex_demo.codex_spider_hidden.0", "Defeat 1 spider");
        add("arc_quest.objective.collection_codex_demo.codex_bone.0", "Collect 5 bones");
        add("arc_quest.objective.collection_codex_demo.codex_rotten_flesh_unique.0", "Collect 1 rotten flesh");

        add("arc_quest.collection.collection_codex_demo.category.hostile_mobs", "Hostile Mobs");
        add("arc_quest.collection.collection_codex_demo.category.field_resources", "Field Resources");
    }

    //  Item Tag Translations
    // ═══════════════════════════════════════════════════════

    private void addGuideTranslations() {
        addGuiGuideListText("no_guides", "No unlocked guides");
        addGuiGuideListText("no_guides_hint", "Complete tutorials, quests, or story to archive them here");

        add("key.arc_quest.open_guide_list", "Open Guide List");

        addGuideTitle("movement_basics", "Movement Basics");
        addGuidePageDesc("movement_basics", 1, "Master the basics of movement to explore the world freely.");
        addGuidePageDesc("movement_basics", 2, "Watch the demonstration to learn advanced movement techniques.");

        addGuideCategory("basics", "Basics");
        addGuideCategory("quest", "Quest");
        addGuideCategory("dialogue", "Dialogue");
        addGuideCategory("trade", "Trade");
        addGuideCategory("ponder", "Ponder");
        addGuideCategory("advanced", "Advanced");

        addGuideCommandFeedback("list", "header", "§e=== Guide Registry (%1$s) ===");
        addGuideCommandFeedback("list", "empty", "§7  (no guides registered)");
        addGuideCommandFeedback("debug", "header", "§e=== Guide: %1$s ===");
        addGuideCommandFeedback("debug", "title", "§f  Title: %1$s");
        addGuideCommandFeedback("debug", "category", "§f  Category: %1$s");
        addGuideCommandFeedback("debug", "sort", "§7  SortOrder: %1$s");
        addGuideCommandFeedback("debug", "hidden", "§7  Hidden: %1$s");
        addGuideCommandFeedback("debug", "repeatable", "§7  Repeatable: %1$s");
        addGuideCommandFeedback("debug", "conditions", "§7  UnlockConditions: %1$s");
        addGuideCommandFeedback("debug", "pages", "§7  Pages: %1$s");
        addGuideCommandFeedback("status", "header", "§e=== Guide Status: %1$s ===");
        addGuideCommandFeedback("status", "unlocked", "§f  Unlocked: %1$s");
    }

    private void addItemTagTranslations() {
        // Basic Materials
        addItemTag("wool", "Wool");
        addItemTag("planks", "Planks");
        addItemTag("stone_bricks", "Stone Bricks");
        addItemTag("sand", "Sand");
        addItemTag("dirt", "Dirt");
        addItemTag("terracotta", "Terracotta");
        addItemTag("leaves", "Leaves");
        addItemTag("saplings", "Saplings");

        // Wooden Items
        addItemTag("wooden_buttons", "Wooden Buttons");
        addItemTag("wooden_doors", "Wooden Doors");
        addItemTag("wooden_stairs", "Wooden Stairs");
        addItemTag("wooden_slabs", "Wooden Slabs");
        addItemTag("wooden_fences", "Wooden Fences");
        addItemTag("wooden_pressure_plates", "Wooden Pressure Plates");
        addItemTag("wooden_trapdoors", "Wooden Trapdoors");
        addItemTag("logs", "Logs");
        addItemTag("oak_logs", "Oak Logs");
        addItemTag("birch_logs", "Birch Logs");
        addItemTag("spruce_logs", "Spruce Logs");
        addItemTag("jungle_logs", "Jungle Logs");
        addItemTag("acacia_logs", "Acacia Logs");
        addItemTag("dark_oak_logs", "Dark Oak Logs");
        addItemTag("mangrove_logs", "Mangrove Logs");
        addItemTag("cherry_logs", "Cherry Logs");
        addItemTag("crimson_stems", "Crimson Stems");
        addItemTag("warped_stems", "Warped Stems");
        addItemTag("bamboo_blocks", "Bamboo Blocks");

        // Building Blocks
        addItemTag("stairs", "Stairs");
        addItemTag("slabs", "Slabs");
        addItemTag("walls", "Walls");
        addItemTag("fences", "Fences");
        addItemTag("fence_gates", "Fence Gates");
        addItemTag("buttons", "Buttons");
        addItemTag("stone_buttons", "Stone Buttons");
        addItemTag("doors", "Doors");
        addItemTag("trapdoors", "Trapdoors");
        addItemTag("anvil", "Anvil");
        addItemTag("rails", "Rails");
        addItemTag("wool_carpets", "Wool Carpets");

        // Flowers & Plants
        addItemTag("flowers", "Flowers");
        addItemTag("small_flowers", "Small Flowers");
        addItemTag("tall_flowers", "Tall Flowers");
        addItemTag("wart_blocks", "Wart Blocks");

        // Ores
        addItemTag("gold_ores", "Gold Ores");
        addItemTag("iron_ores", "Iron Ores");
        addItemTag("diamond_ores", "Diamond Ores");
        addItemTag("redstone_ores", "Redstone Ores");
        addItemTag("lapis_ores", "Lapis Lazuli Ores");
        addItemTag("coal_ores", "Coal Ores");
        addItemTag("emerald_ores", "Emerald Ores");
        addItemTag("copper_ores", "Copper Ores");

        // Tools & Weapons
        addItemTag("swords", "Swords");
        addItemTag("axes", "Axes");
        addItemTag("hoes", "Hoes");
        addItemTag("pickaxes", "Pickaxes");
        addItemTag("shovels", "Shovels");
        addItemTag("tools", "Tools");
        addItemTag("arrows", "Arrows");

        // Special Items
        addItemTag("banners", "Banners");
        addItemTag("beds", "Beds");
        addItemTag("boats", "Boats");
        addItemTag("chest_boats", "Boats with Chest");
        addItemTag("signs", "Signs");
        addItemTag("hanging_signs", "Hanging Signs");
        addItemTag("music_discs", "Music Discs");
        addItemTag("candles", "Candles");
        addItemTag("fishes", "Fishes");
        addItemTag("coals", "Coals");
        addItemTag("books", "Books");
        addItemTag("lectern_books", "Lectern Books");
        addItemTag("bookshelf_books", "Bookshelf Books");

        // Mob Related
        addItemTag("piglin_loved", "Piglin Loved Items");
        addItemTag("piglin_repellents", "Piglin Repellents");
        addItemTag("fox_food", "Fox Food");
        addItemTag("axolotl_tempt_items", "Axolotl Tempt Items");
        addItemTag("sniffer_food", "Sniffer Food");
        addItemTag("villager_plantable_seeds", "Villager Plantable Seeds");

        // Decoration & Crafting
        addItemTag("decorated_pot_sherds", "Decorated Pot Sherds");
        addItemTag("decorated_pot_ingredients", "Decorated Pot Ingredients");
        addItemTag("trim_materials", "Armor Trim Materials");
        addItemTag("trim_templates", "Armor Trim Templates");
        addItemTag("trimmable_armor", "Trimmable Armor");
        addItemTag("beacon_payment_items", "Beacon Payment Items");

        // Others
        addItemTag("compasses", "Compasses");
        addItemTag("creeper_drop_music_discs", "Creeper Drop Music Discs");
        addItemTag("creeper_igniters", "Creeper Igniters");
        addItemTag("noteblock_top_instruments", "Noteblock Top Instruments");
        addItemTag("breaks_decorated_pots", "Breaks Decorated Pots");
        addItemTag("dampens_vibrations", "Dampens Vibrations");
        addItemTag("cluster_max_harvestables", "Cluster Max Harvestables");
        addItemTag("freeze_immune_wearables", "Freeze Immune Wearables");
        addItemTag("stone_tool_materials", "Stone Tool Materials");
        addItemTag("stone_crafting_materials", "Stone Crafting Materials");
        addItemTag("smelts_to_glass", "Smelts to Glass");
        addItemTag("non_flammable_wood", "Non-Flammable Wood");
        addItemTag("soul_fire_base_blocks", "Soul Fire Base Blocks");
        addItemTag("completes_find_tree_tutorial", "Completes Find Tree Tutorial");
    }
}
