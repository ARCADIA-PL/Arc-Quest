package org.com.arc_quest.data;

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
        add("gui.arc_quest.journal.title", "Quest Journal");
    }

    // ═══════════════════════════════════════════════════════
    //  GUI 界面文本
    // ═══════════════════════════════════════════════════════

    private void addGuiTranslations() {
        // ── Journal Tabs ──
        addGuiTab("journal", "active", "ACTIVE");
        addGuiTab("journal", "completed", "COMPLETED");
        addGuiTab("journal", "failed", "FAILED");

        // ── Journal Sections ──
        addGuiSection("journal", "current_phase", "▸ PHASE: %s");
        addGuiSection("journal", "completed_phases", "▸ COMPLETED PHASES");
        addGuiSection("journal", "choose_path", "▸ CHOOSE YOUR PATH");

        // ── Journal Labels ──
        addGuiLabel("journal", "no_phases_completed",
                "§7No phases completed yet.");
        addGuiLabel("journal", "quest_completed",
                "§a✔ This quest has been completed.");
        addGuiLabel("journal", "quest_failed",
                "§c✘ This quest has failed.");
        addGuiLabel("journal", "select_quest", "Select a quest");
        addGuiLabel("journal", "objective_complete_prefix", "§a✔ ");
        addGuiLabel("journal", "objective_active_prefix", "§f○ ");

        // ── Journal Buttons ──
        addGuiButton("journal", "track", "⊕ TRACK");
        addGuiButton("journal", "tracked", "§a⊕ TRACKED");
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
    }

    // ═══════════════════════════════════════════════════════
    //  Toast 提示
    // ═══════════════════════════════════════════════════════

    private void addToastTranslations() {
        addToast("branch_available", "Branch available: %s");
        addToast("quest_accepted", "§aQuest Accepted");
        addToast("quest_completed", "§2Quest Completed");
        addToast("quest_failed", "§cQuest Failed");
        addToast("phase_advanced", "Phase Advanced");
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

        // ── reset ──
        addCommandFeedback("reset", "success",
                "Reset quest §e%1$s§f for %2$s.");

        // ── phase ──
        addCommandFeedback("phase", "success",
                "Set quest §e%1$s§f → phase §d%2$s§f for %3$s.");
        addCommandFeedback("phase", "error.not_found",
                "Phase '%1$s' not found in quest '%2$s'.");
        addCommandFeedback("phase", "error.wrong_state",
                "Quest '%1$s' is in state %2$s, cannot change phase.");
        addCommandFeedback("phase", "error.failed",
                "Failed to activate quest '%1$s'.");

        // ── progress ──
        addCommandFeedback("progress", "success",
                "Set objective[%1$s] = %2$s for quest §e%3$s§f.");
        addCommandFeedback("progress", "error.out_of_range",
                "Objective index %1$s out of range (max %2$s).");

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
}