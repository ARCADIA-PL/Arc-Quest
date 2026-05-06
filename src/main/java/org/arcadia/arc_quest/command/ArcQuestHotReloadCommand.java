package org.arcadia.arc_quest.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.data.ArcQuestReloadListener;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

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
        ctx.getSource().sendSuccess(() -> Component.literal("[ArcQuest] ArcQuest datapack 重载完成，仅影响 ArcQuest 任务。loaded=" + loaded + ", activeDatapack=" + QuestRegistry.datapackSize() + ", merged=" + QuestRegistry.size()), true);
        return Command.SINGLE_SUCCESS;
    }
}
