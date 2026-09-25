package org.arcadia.arc_quest.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.trade.demo.RefreshingTestShopService;

final class TradeTestCommands {
    private TradeTestCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("test").requires(source -> source.hasPermission(2))
                .executes(context -> open(context, false))
                .then(Commands.literal("simple").executes(context -> open(context, true)))
                .then(Commands.literal("stop").executes(context -> {
                    boolean stopped = RefreshingTestShopService.stop();
                    context.getSource().sendSuccess(() -> Component.translatable(stopped
                            ? "arc_quest.trade.test.stopped" : "arc_quest.trade.test.inactive"), false);
                    return stopped ? 1 : 0;
                }));
    }

    private static int open(CommandContext<CommandSourceStack> context, boolean simple) throws CommandSyntaxException {
        if (!RefreshingTestShopService.open(context.getSource().getPlayerOrException(), simple)) {
            context.getSource().sendFailure(Component.translatable("arc_quest.trade.test.conflict"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("arc_quest.trade.test.started"), false);
        return 1;
    }
}
