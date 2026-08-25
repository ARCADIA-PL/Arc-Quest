package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class DebugCommands {
    private DebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree() {
        return Commands.literal("debug")
                .then(Commands.literal("complete_phase")
                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                .suggests(DebugCommands::suggestActiveQuests)
                                .then(Commands.argument("phase_id", ResourceLocationArgument.id())
                                        .suggests(DebugCommands::suggestActivePhases)
                                        .executes(DebugCommands::completePhase))))
                .then(Commands.literal("complete_quest")
                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                .suggests(DebugCommands::suggestActiveQuests)
                                .executes(DebugCommands::completeQuest)));
    }

    private static CompletableFuture<Suggestions> suggestActiveQuests(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
        if (player == null) return Suggestions.empty();
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return Suggestions.empty();

        List<String> ids = new ArrayList<>();
        for (String id : data.getAllActiveQuests().keySet()) {
            QuestDefinition definition = QuestRegistry.get(ResourceLocation.parse(id));
            if (definition != null) ids.add(id);
        }
        return ArcQuestSuggestionUtil.suggest(ids, builder, id -> {
            QuestDefinition definition = QuestRegistry.get(ResourceLocation.parse(id));
            return definition == null
                    ? ArcQuestSuggestionUtil.idTooltip("Quest", id)
                    : ArcQuestSuggestionUtil.displayTooltip("Quest", definition.getDisplayName(), id);
        });
    }

    private static CompletableFuture<Suggestions> suggestActivePhases(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
        if (player == null) return Suggestions.empty();
        ResourceLocation questId = ResourceLocationArgument.getId(context, "quest_id");
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        QuestDefinition definition = QuestRegistry.get(questId);
        QuestRuntimeData runtime = data == null ? null : data.getActiveQuest(questId.toString());
        if (definition == null || runtime == null) return Suggestions.empty();

        return ArcQuestSuggestionUtil.suggest(runtime.getActivePhaseIds(), builder, phaseId -> {
            PhaseDefinition phase = definition.getPhase(phaseId);
            return phase == null
                    ? ArcQuestSuggestionUtil.idTooltip("Phase", phaseId)
                    : ArcQuestSuggestionUtil.displayTooltip("Phase", phase.getDisplayName(), phaseId);
        });
    }

    private static int completePhase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation questId = ResourceLocationArgument.getId(context, "quest_id");
        ResourceLocation phaseId = ResourceLocationArgument.getId(context, "phase_id");
        QuestRejectCodeDictionary.Code result = QuestProgressHandler.forceCompletePhase(
                player, questId.toString(), phaseId.toString());
        if (result != QuestRejectCodeDictionary.Code.OK) {
            context.getSource().sendFailure(Component.literal("[ArcQuest] Unable to complete phase: " + result));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("[ArcQuest] Completed phase "
                + phaseId + " of quest " + questId + " for " + player.getName().getString()), true);
        return 1;
    }

    private static int completeQuest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation questId = ResourceLocationArgument.getId(context, "quest_id");
        QuestRejectCodeDictionary.Code result = QuestProgressHandler.forceComplete(
                player, questId.toString());
        if (result != QuestRejectCodeDictionary.Code.OK) {
            context.getSource().sendFailure(Component.literal("[ArcQuest] Unable to complete quest: " + result));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("[ArcQuest] Completed quest "
                + questId + " for " + player.getName().getString()), true);
        return 1;
    }
}
