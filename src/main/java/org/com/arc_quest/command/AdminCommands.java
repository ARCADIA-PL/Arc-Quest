package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

/**
 * 管理员功能命令。
 */
public class AdminCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构建管理员命令子树。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("admin")
                        // /arcquest admin registry
                        .then(Commands.literal("registry")
                                .executes(AdminCommands::cmdRegistry))
                        // /arcquest admin reload
                        .then(Commands.literal("reload")
                                .executes(AdminCommands::cmdReload));
    }

    // ═══════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ArcQuest] §f" + msg), true);
    }

    // ═══════════════════════════════════════════════════════
    //  命令实现
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

    private static int cmdReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource(), "reload");
        success(ctx, Component.translatable("arc_quest.command.reload.success").getString());
        return 1;
    }
}
