package org.arcadia.arc_quest.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.data.reload.ArcQuestReloadCoordinator;

import java.util.concurrent.CompletableFuture;

public final class ArcQuestHotReloadCommand {
    private ArcQuestHotReloadCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arcquest_reload")
                .requires(source -> source.hasPermission(2))
                .executes(ArcQuestHotReloadCommand::reloadArcQuest));
    }

    public static int reloadArcQuest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("ArcQuest reload started."), false);
        CompletableFuture.supplyAsync(ArcQuestReloadCoordinator.INSTANCE::prepare)
                .thenAccept(plan -> source.getServer().execute(() -> {
                    var summary = ArcQuestReloadCoordinator.INSTANCE.apply(plan);
                    if (summary.applied()) source.sendSuccess(() -> Component.literal(summary.formatForCommand()), true);
                    else source.sendFailure(Component.literal(summary.formatForCommand()));
                }))
                .exceptionally(exception -> {
                    source.getServer().execute(() -> source.sendFailure(Component.literal(
                            "ArcQuest reload preparation failed: " + exception.getMessage())));
                    return null;
                });
        return Command.SINGLE_SUCCESS;
    }
}
