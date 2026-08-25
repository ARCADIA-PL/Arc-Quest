package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestMarkerService;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTracker;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 任务管理命令。
 */
public class QuestCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构建任务命令子树。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("quest")
                // /arcquest quest give <player> <id>
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest_id", StringArgumentType.greedyString())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .executes(QuestCommands::cmdGive))))
                // /arcquest quest complete <player> <id>
                .then(Commands.literal("complete")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest_id", StringArgumentType.greedyString())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .executes(QuestCommands::cmdComplete))))
                // /arcquest quest fail <player> <id>
                .then(Commands.literal("fail")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest_id", StringArgumentType.greedyString())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .executes(QuestCommands::cmdFail))))
                // /arcquest quest reset <player> [id]
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdQuestReset(ctx, null))
                                .then(Commands.argument("quest_id", StringArgumentType.greedyString())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .executes(ctx -> cmdQuestReset(ctx, StringArgumentType.getString(ctx, "quest_id"))))))
                // /arcquest quest phase <player> <id> <phase>
                .then(Commands.literal("phase")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .then(Commands.argument("phase_id", StringArgumentType.greedyString())
                                                .suggests(QuestCommands::suggestPhaseIds)
                                                .executes(QuestCommands::cmdPhase)))))
                // /arcquest quest completephase <player> <id> <phase>
                .then(Commands.literal("completephase")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .then(Commands.argument("phase_id", StringArgumentType.greedyString())
                                                .suggests(QuestCommands::suggestPhaseIds)
                                                .executes(QuestCommands::cmdCompletePhase)))))
                // /arcquest quest progress <player> <id> <index> <amount>
                .then(Commands.literal("progress")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .then(Commands.argument("obj_index", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("amount_expr", StringArgumentType.word())
                                                        .executes(QuestCommands::cmdProgress)
                                                        .then(Commands.argument("phase_id", StringArgumentType.greedyString())
                                                                .suggests(QuestCommands::suggestPhaseIds)
                                                                .executes(QuestCommands::cmdProgress)))))))
                // /arcquest quest list [player]
                .then(Commands.literal("list")
                        .executes(ctx -> cmdList(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdList(ctx, EntityArgument.getPlayer(ctx, "player")))))
                // /arcquest quest debug <player> <id>
                .then(Commands.literal("debug")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("quest_id", StringArgumentType.greedyString())
                                        .suggests(QuestCommands::suggestQuestIds)
                                        .executes(QuestCommands::cmdDebug))));
    }

    // ═══════════════════════════════════════════════════════
    //  Tab 补全
    // ═══════════════════════════════════════════════════════

    private static CompletableFuture<Suggestions> suggestQuestIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return ArcQuestSuggestionUtil.suggest(
                QuestRegistry.getAllIds().stream().map(ResourceLocation::toString).toList(), builder, id -> {
                    QuestDefinition definition = QuestRegistry.get(ResourceLocation.parse(id));
                    return definition == null
                            ? ArcQuestSuggestionUtil.idTooltip("Quest", id)
                            : ArcQuestSuggestionUtil.displayTooltip("Quest", definition.getDisplayName(), id);
                });
    }

    private static CompletableFuture<Suggestions> suggestPhaseIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ResourceLocation questId = ResourceLocationArgument.getId(ctx, "quest_id");
        QuestDefinition definition = QuestRegistry.get(questId);
        if (definition == null) return Suggestions.empty();
        return ArcQuestSuggestionUtil.suggest(definition.getPhaseIds(), builder, phaseId -> {
            PhaseDefinition phase = definition.getPhase(phaseId);
            return phase == null
                    ? ArcQuestSuggestionUtil.idTooltip("Phase", phaseId)
                    : ArcQuestSuggestionUtil.displayTooltip("Phase", phase.getDisplayName(), phaseId);
        });
    }

    // ═══════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════

    private static ArcQuestPlayer getData(ServerPlayer player) {
        return ArcQuestPlayerManager.get(player);
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

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ArcQuest] §f" + msg), true);
    }

    private static void error(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("§c[ArcQuest] §f" + msg));
    }

    // ═══════════════════════════════════════════════════════
    //  命令实现
    // ═══════════════════════════════════════════════════════

    private static int cmdGive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = StringArgumentType.getString(ctx, "quest_id");

        if (resolveQuest(ctx, questId) == null) return 0;

        ArcQuestPlayer data = getData(player);

        if (data.isQuestActive(questId)) {
            error(ctx, Component.translatable("arc_quest.command.give.error.already_active", questId, player.getName().getString()).getString());
            return 0;
        }

        if (data.isQuestCompleted(questId)) {
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

    private static int cmdComplete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = StringArgumentType.getString(ctx, "quest_id");

        QuestDefinition def = resolveQuest(ctx, questId);
        if (def == null) return 0;

        ArcQuestPlayer data = getData(player);

        if (data == null) return 0;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) {
            error(ctx, Component.translatable("arc_quest.command.complete.error.not_active", questId).getString());
            return 0;
        }

        // 并行 phase：把所有 active phase 目标补满
        for (String phaseId : qdata.getActivePhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;

            List<ObjectiveEntry> objs = phase.getObjectives();
            for (int i = 0; i < objs.size(); i++) {
                qdata.setObjectiveProgress(phaseId, i, objs.get(i).getRequiredCount());
            }
        }

        QuestProgressHandler.forceComplete(player, questId);
        success(ctx, Component.translatable("arc_quest.command.complete.success", questId, player.getName().getString()).getString());
        return 1;
    }

    private static int cmdFail(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = StringArgumentType.getString(ctx, "quest_id");

        if (resolveQuest(ctx, questId) == null) return 0;

        ArcQuestPlayer data = getData(player);

        if (data == null) return 0;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null) {
            error(ctx, Component.translatable("arc_quest.command.fail.error.not_active", questId).getString());
            return 0;
        }

        QuestProgressHandler.failQuest(player, questId);
        success(ctx, Component.translatable("arc_quest.command.fail.success", questId, player.getName().getString()).getString());
        return 1;
    }

    private static int cmdQuestReset(CommandContext<CommandSourceStack> ctx, String questId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ArcQuestPlayer data = getData(player);

        if (questId == null) {
            // 重置所有任务进度
            data.clearAllData();
            ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());
            ArcQuestNetwork.syncFullData(player, data);
            success(ctx, Component.translatable("arc_quest.command.resetall.success", player.getName().getString()).getString());
        } else {
            // 重置单个任务
            if (resolveQuest(ctx, questId) == null) return 0;
            boolean wasTracked = questId.equals(data.getTrackedQuestId());
            ObjectiveTracker.INSTANCE.unregisterQuest(player.getUUID(), questId);
            QuestMarkerService.clearQuestMarkers(data, questId);
            data.resetQuest(questId);
            if (wasTracked) TrackedQuestService.onQuestReset(player);
            else ArcQuestNetwork.syncFullData(player, data);
            success(ctx, Component.translatable("arc_quest.command.reset.success", questId, player.getName().getString()).getString());
        }
        return 1;
    }

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

        ArcQuestPlayer data = getData(player);

        if (data == null) return 0;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null) {
            QuestProgressHandler.acceptQuest(player, questId);
            qdata = data.getActiveQuest(questId);
        }
        if (data == null) {
            error(ctx, Component.translatable("arc_quest.command.phase.error.failed", questId).getString());
            return 0;
        }

        if (qdata.getState() != QuestState.ACTIVE) {
            error(ctx, Component.translatable("arc_quest.command.phase.error.wrong_state", questId, qdata.getState()).getString());
            return 0;
        }

        PhaseDefinition phase = def.getPhase(phaseId);
        if (phase != null) {
            if (!qdata.isPhaseActive(phaseId)) {
                qdata.activatePhase(phaseId, phase.getObjectives().size());
                QuestProgressHandler.registerPhaseObjectives(player, def, phase);
            }
            QuestProgressHandler.syncToClient(player, questId);
        }

        success(ctx, Component.translatable("arc_quest.command.phase.success", questId, phaseId, player.getName().getString()).getString());
        return 1;
    }

    private static int cmdCompletePhase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();
        String phaseId = StringArgumentType.getString(ctx, "phase_id");

        QuestDefinition def = resolveQuest(ctx, questId);
        if (def == null) return 0;

        if (!def.getPhaseIds().contains(phaseId)) {
            error(ctx, Component.translatable("arc_quest.command.phase.error.not_found", phaseId, questId).getString());
            return 0;
        }

        ArcQuestPlayer data = getData(player);
        if (data == null) return 0;

        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) {
            error(ctx, Component.translatable("arc_quest.command.complete.error.not_active", questId).getString());
            return 0;
        }

        QuestProgressHandler.forceCompletePhase(player, questId, phaseId);
        success(ctx, Component.translatable("arc_quest.command.completephase.success", questId, phaseId, player.getName().getString()).getString());
        return 1;
    }

    private static int cmdProgress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = ResourceLocationArgument.getId(ctx, "quest_id").toString();
        int objIndex = IntegerArgumentType.getInteger(ctx, "obj_index");
        String amountExpr = StringArgumentType.getString(ctx, "amount_expr");

        String phaseId = null;
        try {
            phaseId = StringArgumentType.getString(ctx, "phase_id");
        } catch (IllegalArgumentException ignored) {
        }

        if (resolveQuest(ctx, questId) == null) return 0;

        ParsedProgress parsed;
        try {
            parsed = parseProgressExpr(amountExpr);
        } catch (IllegalArgumentException e) {
            error(ctx, Component.translatable("arc_quest.command.progress.error.invalid_expr", amountExpr).getString());
            return 0;
        }

        ArcQuestPlayer data = getData(player);
        if (data == null) return 0;
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (qdata == null || qdata.getState() != QuestState.ACTIVE) {
            error(ctx, Component.translatable("arc_quest.command.progress.error.not_active", questId).getString());
            return 0;
        }

        String resolvedPhaseId = (phaseId == null || phaseId.isEmpty()) ? qdata.getCurrentPhaseId() : phaseId;
        if (!qdata.isPhaseActive(resolvedPhaseId)) {
            error(ctx, Component.translatable("arc_quest.command.progress.error.phase_not_active", resolvedPhaseId).getString());
            return 0;
        }

        int[] progress = qdata.getAllProgress(resolvedPhaseId);
        if (objIndex >= progress.length) {
            error(ctx, Component.translatable("arc_quest.command.progress.error.out_of_range", objIndex, progress.length - 1).getString());
            return 0;
        }

        int oldValue = qdata.getObjectiveProgress(resolvedPhaseId, objIndex);
        int newValue = switch (parsed.mode()) {
            case ADD -> oldValue + parsed.value();
            case SET -> parsed.value();
        };

        qdata.setObjectiveProgress(resolvedPhaseId, objIndex, newValue);
        QuestProgressHandler.syncToClient(player, questId);

        String modeText = parsed.mode() == ProgressMode.ADD ? "ADD" : "SET";
        success(ctx, Component.translatable(
                "arc_quest.command.progress.success_mode",
                modeText, questId, resolvedPhaseId, objIndex, oldValue, newValue
        ).getString());
        return 1;
    }

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

        ArcQuestPlayer data = getData(player);
        var allQuests = data.getAllActiveQuests();
        if (allQuests.isEmpty()) {
            msg.append(Component.translatable("arc_quest.command.list.no_quests"));
            msg.append(Component.literal("\n"));
        } else {
            for (var entry : allQuests.entrySet()) {
                QuestRuntimeData qdata = entry.getValue();
                String stateColor = switch (qdata.getState()) {
                    case LOCKED, AVAILABLE -> "§8";
                    case ACTIVE -> "§a";
                    case COMPLETED -> "§2";
                    case FAILED -> "§c";
                };

                String activePhases = qdata.getActivePhaseIds().isEmpty()
                        ? "-"
                        : String.join(",", qdata.getActivePhaseIds());
                String completedPhases = qdata.getCompletedPhaseIds().isEmpty()
                        ? "-"
                        : String.join(",", qdata.getCompletedPhaseIds());

                msg.append(Component.literal(
                        stateColor + "  " + entry.getKey()
                                + " §7[" + qdata.getState().name() + "]"
                                + " §8active=[" + activePhases + "]"
                                + " §8completed=[" + completedPhases + "]\n"
                ));
            }
        }

        var completed = data.getCompletedQuestIds();
        if (completed != null && !completed.isEmpty()) {
            msg.append(Component.translatable("arc_quest.command.list.completed_history", String.join(", ", completed)));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdDebug(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String questId = StringArgumentType.getString(ctx, "quest_id");

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

        // 完成策略定义输出
        msg.append(Component.literal("CompletionPolicy: " + def.getCompletionPolicy().name() + "\n"));
        msg.append(Component.literal("CompletionRequiredCount: " + def.getCompletionRequiredCount() + "\n"));
        msg.append(Component.literal("CompletionTargetPhase: " +
                (def.getCompletionTargetPhaseId() == null ? "-" : def.getCompletionTargetPhaseId()) + "\n"));

        // 静态定义明细
        for (PhaseDefinition phase : def.getAllPhases()) {
            msg.append(Component.literal("§d  Phase: " + phase.getPhaseId() + "\n"));
            for (int i = 0; i < phase.getObjectives().size(); i++) {
                ObjectiveEntry obj = phase.getObjectives().get(i);
                msg.append(Component.literal(
                        "§7    [" + i + "] " + obj.getType().getId()
                                + " target=" + obj.getTargetId()
                                + " req=" + obj.getRequiredCount() + "\n"
                ));
            }
        }

        // 运行时明细（并行phase）
        ArcQuestPlayer data = getData(player);
        QuestRuntimeData qdata = data.getActiveQuest(questId);
        if (data != null) {
            msg.append(Component.translatable("arc_quest.command.debug.runtime_header"));
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable("arc_quest.command.debug.runtime_state", qdata.getState().name()));
            msg.append(Component.literal("\n"));
            msg.append(Component.literal("RuntimeActivePhases: " +
                    (qdata.getActivePhaseIds().isEmpty() ? "-" : String.join(",", qdata.getActivePhaseIds())) + "\n"));
            msg.append(Component.literal("RuntimeCompletedPhases: " +
                    (qdata.getCompletedPhaseIds().isEmpty() ? "-" : String.join(",", qdata.getCompletedPhaseIds())) + "\n"));

            for (String phaseId : qdata.getActivePhaseIds()) {
                msg.append(Component.literal("§b  RuntimePhase: " + phaseId + "\n"));
                int[] progress = qdata.getAllProgress(phaseId);
                PhaseDefinition phase = def.getPhase(phaseId);

                if (phase == null) {
                    for (int i = 0; i < progress.length; i++) {
                        msg.append(Component.literal("§7    [" + i + "] progress=" + progress[i] + " / ?\n"));
                    }
                    continue;
                }

                int objSize = phase.getObjectives().size();
                int len = Math.max(objSize, progress.length);
                for (int i = 0; i < len; i++) {
                    int p = i < progress.length ? progress[i] : 0;
                    int req = i < objSize ? phase.getObjectives().get(i).getRequiredCount() : -1;
                    if (req >= 0) {
                        msg.append(Component.literal("§7    [" + i + "] progress=" + p + " / " + req + "\n"));
                    } else {
                        msg.append(Component.literal("§7    [" + i + "] progress=" + p + " / ?\n"));
                    }
                }
            }
        } else {
            msg.append(Component.translatable("arc_quest.command.debug.no_runtime"));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static ParsedProgress parseProgressExpr(String expr) {
        if (expr == null || expr.isEmpty()) {
            throw new IllegalArgumentException("empty amount expression");
        }

        try {
            if (expr.startsWith("+")) {
                int v = Integer.parseInt(expr.substring(1));
                if (v < 0) throw new IllegalArgumentException("increment must be >= 0");
                return new ParsedProgress(ProgressMode.ADD, v);
            }
            if (expr.startsWith("=")) {
                int v = Integer.parseInt(expr.substring(1));
                if (v < 0) throw new IllegalArgumentException("set value must be >= 0");
                return new ParsedProgress(ProgressMode.SET, v);
            }

            // 兼容旧写法：裸数字视为 SET
            int v = Integer.parseInt(expr);
            if (v < 0) throw new IllegalArgumentException("set value must be >= 0");
            return new ParsedProgress(ProgressMode.SET, v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid amount expression: " + expr);
        }
    }

    private enum ProgressMode {
        ADD, // +amount
        SET  // =amount 或裸数字
    }

    private record ParsedProgress(ProgressMode mode, int value) {
    }
}
