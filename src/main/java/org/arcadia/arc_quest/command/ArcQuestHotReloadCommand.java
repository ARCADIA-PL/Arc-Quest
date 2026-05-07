package org.arcadia.arc_quest.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.data.ArcQuestDatapackHotReloadService;

public final class ArcQuestHotReloadCommand {

    private static final ArcQuestDatapackHotReloadService HOT_RELOAD_SERVICE = new ArcQuestDatapackHotReloadService();

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
        var result = HOT_RELOAD_SERVICE.reload(ctx.getSource().getServer().getResourceManager());
        ctx.getSource().sendSuccess(() -> Component.literal(formatReloadMessage(result)), true);
        return Command.SINGLE_SUCCESS;
    }

    static String formatReloadMessage(ArcQuestDatapackHotReloadService.ReloadResult result) {
        return "[ArcQuest] ArcQuest datapack 重载完成，仅影响 ArcQuest 任务。scanned="
                + result.scanned() + ", loaded=" + result.loaded() + ", failed=" + result.failed()
                + ", activeDatapack=" + result.activeDatapack() + ", merged=" + result.merged()
                + (result.usedFallback() ? ", source=fallback" : ", source=@datapack");
    }
}
