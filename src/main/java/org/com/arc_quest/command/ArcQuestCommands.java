package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.com.arc_quest.quest.api.ObjectiveEntry;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.com.arc_quest.quest.tracking.ObjectiveTracker;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.api.TradeCategory;
import org.com.arc_quest.trade.api.TradeEntry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 管理员命令集。
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArcQuestCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("arcquest")
                        .requires(src -> src.hasPermission(2))

                        // ═══ give ═══
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                                .suggests(ArcQuestCommands::suggestQuestIds)
                                                .executes(ArcQuestCommands::cmdGive))))

                        // ═══ complete ═══
                        .then(Commands.literal("complete")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                                .suggests(ArcQuestCommands::suggestQuestIds)
                                                .executes(ArcQuestCommands::cmdComplete))))

                        // ═══ fail ═══
                        .then(Commands.literal("fail")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                                .suggests(ArcQuestCommands::suggestQuestIds)
                                                .executes(ArcQuestCommands::cmdFail))))

                        // ═══ reset ═══
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                                .suggests(ArcQuestCommands::suggestQuestIds)
                                                .executes(ArcQuestCommands::cmdReset))))

                        // ═══ phase ═══
                        .then(Commands.literal("phase")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                                .suggests(ArcQuestCommands::suggestQuestIds)
                                                .then(Commands.argument("phase_id", StringArgumentType.string())
                                                        .suggests(ArcQuestCommands::suggestPhaseIds)
                                                        .executes(ArcQuestCommands::cmdPhase)))))

                        // ═══ progress ═══
                        .then(Commands.literal("progress")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                                .suggests(ArcQuestCommands::suggestQuestIds)
                                                .then(Commands.argument("obj_index", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                .executes(ArcQuestCommands::cmdProgress))))))

                        // ═══ list ═══
                        .then(Commands.literal("list")
                                .executes(ctx -> cmdList(ctx, null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> cmdList(ctx, EntityArgument.getPlayer(ctx, "player")))))

                        // ═══ debug ═══
                        .then(Commands.literal("debug")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                                .suggests(ArcQuestCommands::suggestQuestIds)
                                                .executes(ArcQuestCommands::cmdDebug))))

                        // ═══ registry ═══
                        .then(Commands.literal("registry")
                                .executes(ArcQuestCommands::cmdRegistry))

                        // ═══ reload ═══
                        .then(Commands.literal("reload")
                                .executes(ArcQuestCommands::cmdReload))

                        // ═══ dialogue ═══
                        .then(Commands.literal("dialogue")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("dialogue_id", StringArgumentType.string())
                                                .suggests(ArcQuestCommands::suggestDialogueIds)
                                                .executes(ArcQuestCommands::cmdDialogue)))
                                // 对话调试子命令
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> cmdDialogueReset(ctx, null))
                                                .then(Commands.argument("dialogue_id", StringArgumentType.string())
                                                        .suggests(ArcQuestCommands::suggestDialogueIds)
                                                        .executes(ctx -> cmdDialogueReset(ctx, StringArgumentType.getString(ctx, "dialogue_id"))))))
                                .then(Commands.literal("status")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ArcQuestCommands::cmdDialogueStatus))))

                        // ═══ resetall ═══
                        .then(Commands.literal("resetall")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ArcQuestCommands::cmdResetAll)))

                        // trade
                        .then(Commands.literal("trade")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("shop_id", StringArgumentType.string())
                                                .suggests(ArcQuestCommands::suggestTradeShopIds)
                                                .executes(ArcQuestCommands::cmdTradeOpen)))
                                .then(Commands.literal("simple")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("shop_id", StringArgumentType.string())
                                                        .suggests(ArcQuestCommands::suggestTradeShopIds)
                                                        .executes(ArcQuestCommands::cmdTradeSimple))))
                                .then(Commands.literal("list")
                                        .executes(ArcQuestCommands::cmdTradeList))
                                .then(Commands.literal("debug")
                                        .then(Commands.argument("shop_id", StringArgumentType.string())
                                                .suggests(ArcQuestCommands::suggestTradeShopIds)
                                                .executes(ArcQuestCommands::cmdTradeDebug)))
                                // 重置交易状态
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("shop_id", StringArgumentType.string())
                                                        .suggests(ArcQuestCommands::suggestTradeShopIds)
                                                        .executes(ctx -> cmdTradeReset(ctx, null)))
                                                .then(Commands.literal("all")
                                                        .executes(ArcQuestCommands::cmdTradeResetAll)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("shop_id", StringArgumentType.string())
                                                        .suggests(ArcQuestCommands::suggestTradeShopIds)
                                                        .then(Commands.argument("entry_id", StringArgumentType.string())
                                                                .suggests(ArcQuestCommands::suggestTradeEntryIds)
                                                                .executes(ctx -> cmdTradeReset(ctx, StringArgumentType.getString(ctx, "entry_id"))))))))
        );

        LOGGER.info("[ArcQuest] Commands registered.");
    }

    // ═══════════════════════════════════════════════════════
    //  工具：安全获取 Capability
    // ═══════════════════════════════════════════════════════

    private static IQuestCapability getCap(ServerPlayer player) {
        return player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
    }

    private static IQuestCapability getCapOrError(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        IQuestCapability cap = getCap(player);
        return cap;
    }

    private static QuestDefinition resolveQuest(CommandContext<CommandSourceStack> ctx, String questId) {
        ResourceLocation rl = ResourceLocation.tryParse(questId);
        if (rl == null) {
            error(ctx, Component.translatable("arc_quest.command.error.invalid_id", questId).getString());
            return null;
        }
        QuestDefinition def = QuestRegistry.get(rl);
        if (def == null) {
            error(ctx, Component.translatable("arc_quest.command.error.not_in_registry", questId).getString());
        }
        return def;
    }

    // ═══════════════════════════════════════════════════════
    //  Tab 补全
    // ═══════════════════════════════════════════════════════

    private static CompletableFuture<Suggestions> suggestQuestIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                QuestRegistry.getAllIds().stream().map(ResourceLocation::toString), builder);
    }

    private static CompletableFuture<Suggestions> suggestPhaseIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            // 安全提取 ResourceLocation
            ResourceLocation rl = ResourceLocationArgument.getId(ctx, "quest_id");
            QuestDefinition def = QuestRegistry.get(rl);
            if (def != null) {
                return SharedSuggestionProvider.suggest(def.getPhaseIds().stream(), builder);
            }
        } catch (IllegalArgumentException ignored) {
            // 参数尚未输入完毕，安全忽略
        }
        return Suggestions.empty();
    }

    private static CompletableFuture<Suggestions> suggestDialogueIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(DialogueRegistry.INSTANCE.getAllIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestTradeShopIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(TradeRegistry.getAllIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestTradeEntryIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String shopId = StringArgumentType.getString(ctx, "shop_id");
            TradeShopDefinition shop = TradeRegistry.get(shopId);
            if (shop != null) {
                return SharedSuggestionProvider.suggest(
                        shop.getAllEntries().stream().map(TradeEntry::getEntryId), builder);
            }
        } catch (IllegalArgumentException ignored) {
            // 参数尚未输入完毕，安全忽略
        }
        return Suggestions.empty();
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest give
    // ═══════════════════════════════════════════════════════

    private static int cmdGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();

        if (resolveQuest(ctx, questId) == null) return 0;

        IQuestCapability cap = getCapOrError(ctx, player);

        if (cap.isQuestActive(questId)) {
            error(ctx, Component.translatable("arc_quest.command.give.error.already_active", questId, player.getName().getString()).getString());
            return 0;
        }

        if (cap.isQuestCompleted(questId)) {
            QuestDefinition def = resolveQuest(ctx, questId);
            if (def != null && !def.isRepeatable()) {
                error(ctx, Component.translatable("arc_quest.command.give.error.already_completed", questId).getString());
                return 0;
            }
        }

        boolean ok = QuestProgressHandler.acceptQuest(player, questId);
        if (ok) {
            success(ctx, Component.translatable("arc_quest.command.give.success", questId, player.getName().getString()).getString());
        } else {
            error(ctx, Component.translatable("arc_quest.command.give.error.failed", questId).getString());
        }
        return ok ? 1 : 0;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest complete
    // ═══════════════════════════════════════════════════════

    private static int cmdComplete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();

        QuestDefinition def = resolveQuest(ctx, questId);
        if (def == null) return 0;

        IQuestCapability cap = getCapOrError(ctx, player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE) {
            error(ctx, Component.translatable("arc_quest.command.complete.error.not_active", questId).getString());
            return 0;
        }

        PhaseDefinition phase = def.getPhase(data.getCurrentPhaseId());
        if (phase != null) {
            List<ObjectiveEntry> objs = phase.getObjectives();
            for (int i = 0; i < objs.size(); i++) {
                data.setObjectiveProgress(i, objs.get(i).getRequiredCount());
            }
        }

        QuestProgressHandler.forceComplete(player, questId);
        success(ctx, Component.translatable("arc_quest.command.complete.success", questId, player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest fail
    // ═══════════════════════════════════════════════════════

    private static int cmdFail(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();

        if (resolveQuest(ctx, questId) == null) return 0;

        IQuestCapability cap = getCapOrError(ctx, player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) {
            error(ctx, Component.translatable("arc_quest.command.fail.error.not_active", questId).getString());
            return 0;
        }

        QuestProgressHandler.failQuest(player, questId);
        success(ctx, Component.translatable("arc_quest.command.fail.success", questId, player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest reset
    // ═══════════════════════════════════════════════════════

    private static int cmdReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();

        if (resolveQuest(ctx, questId) == null) return 0;

        QuestProgressHandler.abandonQuest(player, questId);
        success(ctx, Component.translatable("arc_quest.command.reset.success", questId, player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest phase
    // ═══════════════════════════════════════════════════════

    private static int cmdPhase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();
        String phaseId = StringArgumentType.getString(ctx, "phase_id");

        QuestDefinition def = resolveQuest(ctx, questId);
        if (def == null) return 0;

        if (!def.getPhaseIds().contains(phaseId)) {
            error(ctx, Component.translatable("arc_quest.command.phase.error.not_found", phaseId, questId).getString());
            return 0;
        }

        IQuestCapability cap = getCapOrError(ctx, player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null) {
            QuestProgressHandler.acceptQuest(player, questId);
            data = cap.getActiveQuest(questId);
        }
        if (data == null) {
            error(ctx, Component.translatable("arc_quest.command.phase.error.failed", questId).getString());
            return 0;
        }

        if (data.getState() != QuestState.ACTIVE) {
            error(ctx, Component.translatable("arc_quest.command.phase.error.wrong_state", questId, data.getState()).getString());
            return 0;
        }

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase != null) {
            data.setCurrentPhaseId(phaseId);
            data.resetObjectives(phase.getObjectives().size());

            // 注册追踪器
            ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
            QuestProgressHandler.registerPhaseObjectives(player, def, phase);

            QuestProgressHandler.syncToClient(player, questId);
        }

        success(ctx, Component.translatable("arc_quest.command.phase.success", questId, phaseId, player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest progress
    // ═══════════════════════════════════════════════════════

    private static int cmdProgress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();
        int objIndex = IntegerArgumentType.getInteger(ctx, "obj_index");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        if (resolveQuest(ctx, questId) == null) return 0;

        IQuestCapability cap = getCapOrError(ctx, player);

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || data.getState() != QuestState.ACTIVE) {
            error(ctx, Component.translatable("arc_quest.command.progress.error.not_active", questId).getString());
            return 0;
        }

        int[] progress = data.getAllProgress();
        if (objIndex >= progress.length) {
            error(ctx, Component.translatable("arc_quest.command.progress.error.out_of_range", objIndex, progress.length - 1).getString());
            return 0;
        }

        data.setObjectiveProgress(objIndex, amount);
        QuestProgressHandler.syncToClient(player, questId);

        success(ctx, Component.translatable("arc_quest.command.progress.success", objIndex, amount, questId).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest list
    // ═══════════════════════════════════════════════════════

    private static int cmdList(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer) throws CommandSyntaxException {
        ServerPlayer player;
        if (targetPlayer != null) {
            player = targetPlayer;
        } else {
            if (!ctx.getSource().isPlayer()) {
                error(ctx, Component.translatable("arc_quest.command.list.specify_player").getString());
                return 0;
            }
            player = ctx.getSource().getPlayerOrException();
        }

        MutableComponent msg = Component.translatable("arc_quest.command.list.header", player.getName().getString());
        msg.append(Component.literal("\n"));

        IQuestCapability cap = getCap(player);
        var allQuests = cap.getAllActiveQuests();
        if (allQuests.isEmpty()) {
            msg.append(Component.translatable("arc_quest.command.list.no_quests"));
            msg.append(Component.literal("\n"));
        } else {
            for (var entry : allQuests.entrySet()) {
                QuestRuntimeData data = entry.getValue();
                String stateColor = switch (data.getState()) {
                    case LOCKED, AVAILABLE -> "§8";
                    case ACTIVE -> "§a";
                    case COMPLETED -> "§2";
                    case FAILED -> "§c";
                };
                msg.append(Component.literal(stateColor + "  " + entry.getKey() + " §7[" + data.getState().name() + "]" + " §8phase=" + data.getCurrentPhaseId() + "\n"));
            }
        }

        var completed = cap.getCompletedQuestIds();
        if (completed != null && !completed.isEmpty()) {
            msg.append(Component.translatable("arc_quest.command.list.completed_history", String.join(", ", completed)));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest debug
    // ═══════════════════════════════════════════════════════

    private static int cmdDebug(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();

        QuestDefinition def = resolveQuest(ctx, questId);
        if (def == null) return 0;

        MutableComponent msg = Component.translatable("arc_quest.command.debug.header", questId);
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.debug.display", def.getDisplayName().getString()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.debug.phases", def.getAllPhases().size()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.debug.repeatable", def.isRepeatable()));
        msg.append(Component.literal("\n"));

        for (PhaseDefinition phase : def.getAllPhases()) {
            msg.append("§d  Phase: " + phase.getPhaseId() + "\n");
            for (int i = 0; i < phase.getObjectives().size(); i++) {
                ObjectiveEntry obj = phase.getObjectives().get(i);
                msg.append("§7    [" + i + "] " + obj.getType().name() + " target=" + obj.getTargetId() + " req=" + obj.getRequiredCount() + "\n");
            }
        }

        IQuestCapability cap = getCap(player);
        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data != null) {
            msg.append(Component.translatable("arc_quest.command.debug.runtime_header"));
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable("arc_quest.command.debug.runtime_state", data.getState().name()));
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable("arc_quest.command.debug.runtime_phase", data.getCurrentPhaseId()));
            msg.append(Component.literal("\n"));
            int[] progress = data.getAllProgress();
            for (int i = 0; i < progress.length; i++) {
                msg.append(Component.translatable("arc_quest.command.debug.runtime_obj", i, progress[i]));
                msg.append(Component.literal("\n"));
            }
        } else {
            msg.append(Component.translatable("arc_quest.command.debug.no_runtime"));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest registry
    // ═══════════════════════════════════════════════════════

    private static int cmdRegistry(CommandContext<CommandSourceStack> ctx) {
        MutableComponent msg = Component.translatable("arc_quest.command.registry.header");
        msg.append(Component.literal("\n"));

        var quests = QuestRegistry.getAll();
        msg.append(Component.translatable("arc_quest.command.registry.quests_header", quests.size()));
        msg.append(Component.literal("\n"));
        for (QuestDefinition def : quests) {
            msg.append(Component.literal("§f    " + def.getId() + " §7- " + def.getDisplayName().getString() + "\n"));
        }

        var dialogues = DialogueRegistry.INSTANCE.getAll();
        msg.append(Component.translatable("arc_quest.command.registry.dialogues_header", dialogues.size()));
        msg.append(Component.literal("\n"));
        for (DialogueTree tree : dialogues) {
            msg.append(Component.literal("§f    " + tree.dialogueId() + " §7- " + tree.defaultNpc() + "\n"));
        }
        var tradeShops = TradeRegistry.getAll();
        msg.append(Component.literal("§e--- Trade Shops (" + tradeShops.size() + ") ---\n"));
        for (TradeShopDefinition shop : tradeShops) {
            msg.append(Component.literal("§f    " + shop.getShopId() + " §7- " + shop.getDisplayName().getString() + " §8[" + shop.getAllEntries().size() + " entries]\n"));
        }


        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest reload
    // ═══════════════════════════════════════════════════════

    private static int cmdReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource(), "reload");
        success(ctx, Component.translatable("arc_quest.command.reload.success").getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest dialogue
    // ═══════════════════════════════════════════════════════

    private static int cmdDialogue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String dialogueId = StringArgumentType.getString(ctx, "dialogue_id");

        // 尝试直接查找，如果找不到则自动添加命名空间前缀
        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null && !dialogueId.contains(":")) {
            // 自动添加 arc_quest 命名空间
            String fullId = Arc_quest.MOD_ID + ":" + dialogueId;
            tree = DialogueRegistry.INSTANCE.get(fullId);
        }

        if (tree == null) {
            error(ctx, Component.translatable("arc_quest.command.dialogue.error.not_found", dialogueId).getString());
            return 0;
        }

        DialogueSessionManager.INSTANCE.startDialogue(player, tree);
        success(ctx, Component.translatable("arc_quest.command.dialogue.success", dialogueId, player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest dialogue reset
    // ═══════════════════════════════════════════════════════

    private static int cmdDialogueReset(CommandContext<CommandSourceStack> ctx, String dialogueId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCapOrError(ctx, player);

        if (dialogueId == null) {
            // 重置所有对话进度
            cap.clearAllData();
            success(ctx, Component.translatable("arc_quest.command.dialogue.reset.all", player.getName().getString()).getString());
        } else {
            // 按对话树ID重置进度
            success(ctx, Component.translatable("arc_quest.command.dialogue.reset.single", dialogueId, player.getName().getString()).getString());
        }
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest dialogue status
    // ═══════════════════════════════════════════════════════

    private static int cmdDialogueStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCapOrError(ctx, player);

        MutableComponent msg = Component.translatable("arc_quest.command.dialogue.status.header");
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.dialogue.status.player", player.getName().getString()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.dialogue.status.work_in_progress"));

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest resetall
    // ═══════════════════════════════════════════════════════

    private static int cmdResetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCapOrError(ctx, player);

        // 清空所有数据
        cap.clearAllData();

        // 清理追踪器
        ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());

        // 全量同步
        ArcQuestNetwork.syncFullData(player, cap);

        success(ctx, Component.translatable("arc_quest.command.resetall.success", player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest trade <player> <shop_id>
    // ═══════════════════════════════════════════════════════

    private static int cmdTradeOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = StringArgumentType.getString(ctx, "shop_id");

        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        C2SRequestTradePacket.handleServerOpen(player, shop, false);
        success(ctx, Component.translatable("arc_quest.command.trade.open.success", shopId, player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest trade simple <player> <shop_id>
    // ═══════════════════════════════════════════════════════

    private static int cmdTradeSimple(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = StringArgumentType.getString(ctx, "shop_id");

        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        C2SRequestTradePacket.handleServerOpen(player, shop, true);
        success(ctx, Component.translatable("arc_quest.command.trade.simple.success", shopId, player.getName().getString()).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest trade list
    // ═══════════════════════════════════════════════════════

    private static int cmdTradeList(CommandContext<CommandSourceStack> ctx) {
        var shops = TradeRegistry.getAll();
        MutableComponent msg = Component.translatable("arc_quest.command.trade.list.header", shops.size());
        msg.append(Component.literal("\n"));

        for (TradeShopDefinition shop : shops) {
            String modeText = shop.isSimpleMode() ? ", simple" : "";
            msg.append(Component.translatable("arc_quest.command.trade.list.entry",
                    shop.getShopId(),
                    shop.getDisplayName().getString(),
                    shop.getAllEntries().size(),
                    modeText));
            msg.append(Component.literal("\n"));
        }

        if (shops.isEmpty()) {
            msg.append(Component.translatable("arc_quest.command.trade.list.empty"));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest trade debug <shop_id>
    // ═══════════════════════════════════════════════════════

    private static int cmdTradeDebug(CommandContext<CommandSourceStack> ctx) {
        String shopId = StringArgumentType.getString(ctx, "shop_id");
        TradeShopDefinition shop = TradeRegistry.get(shopId);

        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        MutableComponent msg = Component.translatable("arc_quest.command.trade.debug.header", shopId);
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.trade.debug.name", shop.getDisplayName().getString()));
        msg.append(Component.literal("\n"));
        if (shop.getDescription() != null) {
            msg.append(Component.translatable("arc_quest.command.trade.debug.description", shop.getDescription().getString()));
            msg.append(Component.literal("\n"));
        }
        String modeKey = shop.isSimpleMode() ? "arc_quest.command.trade.debug.mode.simple" : "arc_quest.command.trade.debug.mode.full";
        msg.append(Component.translatable(modeKey));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.trade.debug.categories", shop.getCategories().size()));
        msg.append(Component.literal("\n"));

        for (TradeCategory cat : shop.getCategories()) {
            msg.append(Component.translatable("arc_quest.command.trade.debug.category_entry",
                    cat.getId(), cat.getDisplayName().getString()));
            msg.append(Component.literal("\n"));
        }

        msg.append(Component.translatable("arc_quest.command.trade.debug.entries", shop.getAllEntries().size()));
        msg.append(Component.literal("\n"));

        for (TradeEntry entry : shop.getAllEntries()) {
            StringBuilder costStr = new StringBuilder();
            for (ITradeOffer cost : entry.getCosts()) {
                if (!costStr.isEmpty()) costStr.append(" + ");
                costStr.append(cost.describe().getString());
            }
            StringBuilder rewardStr = new StringBuilder();
            for (ITradeOffer reward : entry.getRewards()) {
                if (!rewardStr.isEmpty()) rewardStr.append(" + ");
                rewardStr.append(reward.describe().getString());
            }

            msg.append(Component.translatable("arc_quest.command.trade.debug.entry_header",
                    entry.getEntryId(), entry.getDisplayName().getString()));
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable("arc_quest.command.trade.debug.costs", costStr.toString()));
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable("arc_quest.command.trade.debug.rewards", rewardStr.toString()));
            msg.append(Component.literal("\n"));

            if (entry.hasLimit()) {
                msg.append(Component.translatable("arc_quest.command.trade.debug.limit", entry.getMaxPurchases()));
                msg.append(Component.literal("\n"));
            }
            if (entry.hasCooldown()) {
                msg.append(Component.translatable("arc_quest.command.trade.debug.cooldown",
                        entry.getCooldownType().name(), entry.getCooldownValue()));
                msg.append(Component.literal("\n"));
            }
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest trade reset <player> <shop_id> [entry_id]
    // ═══════════════════════════════════════════════════════

    private static int cmdTradeReset(CommandContext<CommandSourceStack> ctx, String entryId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = StringArgumentType.getString(ctx, "shop_id");
        IQuestCapability cap = getCapOrError(ctx, player);

        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        if (entryId == null) {
            // 重置整个商店的所有交易项
            for (TradeEntry entry : shop.getAllEntries()) {
                cap.resetTradePurchaseCount(shopId, entry.getEntryId());
            }
            success(ctx, Component.translatable("arc_quest.command.trade.reset.shop_success",
                    shopId, player.getName().getString()).getString());
        } else {
            // 重置单个交易项
            TradeEntry targetEntry = null;
            for (TradeEntry entry : shop.getAllEntries()) {
                if (entry.getEntryId().equals(entryId)) {
                    targetEntry = entry;
                    break;
                }
            }

            if (targetEntry == null) {
                error(ctx, Component.translatable("arc_quest.command.trade.reset.error.entry_not_found",
                        entryId, shopId).getString());
                return 0;
            }

            cap.resetTradePurchaseCount(shopId, entryId);
            success(ctx, Component.translatable("arc_quest.command.trade.reset.entry_success",
                    entryId, shopId, player.getName().getString()).getString());
        }

        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest trade reset all <player>
    // ═══════════════════════════════════════════════════════

    private static int cmdTradeResetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCapOrError(ctx, player);

        // 遍历所有商店，重置所有交易项
        int resetCount = 0;
        for (TradeShopDefinition shop : TradeRegistry.getAll()) {
            for (TradeEntry entry : shop.getAllEntries()) {
                cap.resetTradePurchaseCount(shop.getShopId(), entry.getEntryId());
                resetCount++;
            }
        }

        success(ctx, Component.translatable("arc_quest.command.trade.reset.all_success",
                player.getName().getString(), resetCount).getString());
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  消息工具
    // ═══════════════════════════════════════════════════════

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ArcQuest] §f" + msg), true);
    }

    private static void error(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("§c[ArcQuest] §f" + msg));
    }
}