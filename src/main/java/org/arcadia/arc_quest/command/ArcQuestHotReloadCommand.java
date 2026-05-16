package org.arcadia.arc_quest.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.data.ArcQuestReloadListener;

public final class ArcQuestHotReloadCommand {

    private ArcQuestHotReloadCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("arcquest_reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ArcQuestHotReloadCommand::reloadArcQuest)
        );
    }

    public static int reloadArcQuest(CommandContext<CommandSourceStack> ctx) {
        int loaded = ArcQuestReloadListener.reloadArcQuestDatapacksOnly(ctx.getSource().getServer().getResourceManager());
        ctx.getSource().sendSuccess(() -> Component.literal(formatReloadMessage(loaded)), true);
        return Command.SINGLE_SUCCESS;
    }

    static String formatReloadMessage(int loaded) {
        return "[ArcQuest] datapack 重载完成（Quest / Dialogue / NPC / Trade）。loaded=" + loaded;
    }
}
