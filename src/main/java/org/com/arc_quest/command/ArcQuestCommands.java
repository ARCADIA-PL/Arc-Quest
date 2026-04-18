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
        if (cap == null) {
            error(ctx, Component.translatable("arc_quest.command.error.no_capability", player.getName().getString()).getString());
        }
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

    // ═══════════════════════════════════════════════════════
    //  /arcquest give
    // ═══════════════════════════════════════════════════════

    private static int cmdGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();

        if (resolveQuest(ctx, questId) == null) return 0;

        IQuestCapability cap = getCapOrError(ctx, player);
        if (cap == null) return 0;

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
        if (cap == null) return 0;

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
        if (cap == null) return 0;

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
        if (cap == null) return 0;

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
        if (cap == null) return 0;

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
        if (cap == null) {
            msg.append(Component.translatable("arc_quest.command.list.no_capability"));
            msg.append(Component.literal("\n"));
        } else {
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
        if (cap != null) {
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
        } else {
            msg.append(Component.translatable("arc_quest.command.list.no_capability"));
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
        if (cap == null) return 0;

        if (dialogueId == null) {
            // 重置所有对话进度
            cap.clearAllData();
            success(ctx, Component.literal("已重置玩家 " + player.getName().getString() + " 的所有对话进度").getString());
        } else {
            // 按对话树ID重置进度
            success(ctx, Component.literal("已请求重置玩家 " + player.getName().getString() + " 的对话树 " + dialogueId + "（功能开发中）").getString());
        }
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest dialogue status
    // ═══════════════════════════════════════════════════════

    private static int cmdDialogueStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCapOrError(ctx, player);
        if (cap == null) return 0;

        MutableComponent msg = Component.literal("§e=== 对话状态 ===\n");
        msg.append(Component.literal("§f玩家: " + player.getName().getString() + "\n"));
        msg.append(Component.literal("§7(详细历史记录功能开发中...)\n"));

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    // ═══════════════════════════════════════════════════════
    //  /arcquest resetall
    // ═══════════════════════════════════════════════════════

    private static int cmdResetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCapOrError(ctx, player);
        if (cap == null) return 0;

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
    //  消息工具
    // ═══════════════════════════════════════════════════════

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ArcQuest] §f" + msg), true);
    }

    private static void error(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("§c[ArcQuest] §f" + msg));
    }
}