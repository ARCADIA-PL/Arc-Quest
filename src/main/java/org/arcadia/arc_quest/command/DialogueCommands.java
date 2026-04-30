package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * 对话管理命令。
 */
public class DialogueCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构建对话命令子树。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("dialogue")
                // /arcquest dialogue start <player> <id>
                .then(Commands.literal("start")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dialogue_id", ResourceLocationArgument.id())
                                        .suggests(DialogueCommands::suggestDialogueIds)
                                        .executes(DialogueCommands::cmdDialogue))))
                // /arcquest dialogue reset <player> [id]
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdDialogueReset(ctx, null))
                                .then(Commands.argument("dialogue_id", StringArgumentType.string())
                                        .suggests(DialogueCommands::suggestDialogueIds)
                                        .executes(ctx -> cmdDialogueReset(ctx, StringArgumentType.getString(ctx, "dialogue_id")))))
                        // /arcquest dialogue status <player>
                        .then(Commands.literal("status")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(DialogueCommands::cmdDialogueStatus))));
    }

    // ═══════════════════════════════════════════════════════
    //  Tab 补全
    // ═══════════════════════════════════════════════════════

    private static CompletableFuture<Suggestions> suggestDialogueIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(DialogueRegistry.INSTANCE.getAllIds(), builder);
    }

    // ═══════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════

    private static IQuestCapability getCap(ServerPlayer player) {
        return QuestCapabilityProvider.getOrNull(player);
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

    private static int cmdDialogue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String dialogueId = ResourceLocationArgument.getId(ctx, "dialogue_id").toString();

        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            error(ctx, Component.translatable("arc_quest.command.dialogue.error.not_found", dialogueId).getString());
            return 0;
        }

        DialogueSessionManager.INSTANCE.startDialogue(player, tree);
        success(ctx, Component.translatable("arc_quest.command.dialogue.success", dialogueId, player.getName().getString()).getString());
        return 1;
    }

    private static int cmdDialogueReset(CommandContext<CommandSourceStack> ctx, String dialogueId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCap(player);

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

    private static int cmdDialogueStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCap(player);

        MutableComponent msg = Component.translatable("arc_quest.command.dialogue.status.header");
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.dialogue.status.player", player.getName().getString()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.dialogue.status.work_in_progress"));

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }
}
