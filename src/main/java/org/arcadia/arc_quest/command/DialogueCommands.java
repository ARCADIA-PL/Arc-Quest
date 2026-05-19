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
import org.arcadia.arc_quest.dialogue.api.ConditionalSay;
import org.arcadia.arc_quest.dialogue.api.DialogueChoice;
import org.arcadia.arc_quest.dialogue.api.DialogueNode;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.io.DialogueDatapackHotReloadService;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 对话管理命令。
 */
public class DialogueCommands {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DialogueDatapackHotReloadService DIALOGUE_HOT_RELOAD_SERVICE = new DialogueDatapackHotReloadService();

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("dialogue")
                .then(Commands.literal("start")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dialogue_id", ResourceLocationArgument.id())
                                        .suggests(DialogueCommands::suggestDialogueIds)
                                        .executes(DialogueCommands::cmdDialogue))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> cmdDialogueReset(ctx, null))
                                .then(Commands.argument("dialogue_id", StringArgumentType.string())
                                        .suggests(DialogueCommands::suggestDialogueIds)
                                        .executes(ctx -> cmdDialogueReset(ctx, StringArgumentType.getString(ctx, "dialogue_id"))))))
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DialogueCommands::cmdDialogueStatus)))
                .then(Commands.literal("list")
                        .executes(DialogueCommands::cmdDialogueList))
                .then(Commands.literal("debug")
                        .then(Commands.argument("dialogue_id", ResourceLocationArgument.id())
                                .suggests(DialogueCommands::suggestDialogueIds)
                                .executes(DialogueCommands::cmdDialogueDebug)))
                .then(Commands.literal("reload")
                        .executes(DialogueCommands::cmdDialogueReload))
                .then(Commands.literal("sources")
                        .executes(DialogueCommands::cmdDialogueSources));
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

    private static ArcQuestPlayer getData(ServerPlayer player) {
        return ArcQuestPlayerManager.get(player);
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
        ArcQuestPlayer data = getData(player);

        if (dialogueId == null) {
            // 重置所有对话进度
            data.clearAllData();
            success(ctx, Component.translatable("arc_quest.command.dialogue.reset.all", player.getName().getString()).getString());
        } else {
            // 按对话树ID重置进度
            success(ctx, Component.translatable("arc_quest.command.dialogue.reset.single", dialogueId, player.getName().getString()).getString());
        }
        return 1;
    }

    private static int cmdDialogueStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ArcQuestPlayer data = getData(player);

        MutableComponent msg = Component.translatable("arc_quest.command.dialogue.status.header");
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.dialogue.status.player", player.getName().getString()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.dialogue.status.work_in_progress"));

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdDialogueList(CommandContext<CommandSourceStack> ctx) {
        Collection<DialogueTree> all = DialogueRegistry.INSTANCE.getAll();

        MutableComponent msg = Component.literal("§6═══ Dialogue Registry ═══\n");
        msg.append(Component.literal("§e  Total: §f" + all.size()
                + " §7(code=" + DialogueRegistry.INSTANCE.codeSize()
                + ", datapack=" + DialogueRegistry.INSTANCE.datapackSize() + ")\n"));

        if (all.isEmpty()) {
            msg.append(Component.literal("§7  (no dialogues registered)\n"));
        } else {
            for (DialogueTree tree : all) {
                String source = DialogueRegistry.INSTANCE.codeSize() > 0
                        && DialogueRegistry.INSTANCE.getAllIds().contains(tree.dialogueId())
                        ? "code" : "datapack";
                int nodeCount = tree.nodes() != null ? tree.nodes().size() : 0;
                msg.append(Component.literal("§a  " + tree.dialogueId()
                        + " §7[nodes=" + nodeCount
                        + ", start=" + tree.startNodeId()
                        + ", repeatable=" + tree.repeatable()
                        + "]\n"));
            }
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdDialogueDebug(CommandContext<CommandSourceStack> ctx) {
        String dialogueId = ResourceLocationArgument.getId(ctx, "dialogue_id").toString();
        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);

        if (tree == null) {
            error(ctx, "Dialogue not found: " + dialogueId);
            return 0;
        }

        MutableComponent msg = Component.literal("§6═══ Dialogue Debug: " + dialogueId + " ═══\n");
        msg.append(Component.literal("§e  Default NPC: §f" + tree.defaultNpc() + "\n"));
        msg.append(Component.literal("§e  Start Node: §f" + tree.startNodeId() + "\n"));
        msg.append(Component.literal("§e  Repeatable: §f" + tree.repeatable() + "\n"));
        msg.append(Component.literal("§e  Cooldown: §f" + tree.cooldownSeconds() + "s, type=" + tree.cooldownType() + "\n"));
        msg.append(Component.literal("§e  Nodes: §f" + (tree.nodes() != null ? tree.nodes().size() : 0) + "\n"));

        if (tree.nodes() != null) {
            for (Map.Entry<String, DialogueNode> entry : tree.nodes().entrySet()) {
                DialogueNode node = entry.getValue();
                boolean isStart = entry.getKey().equals(tree.startNodeId());
                String marker = isStart ? " §6[START]" : "";

                msg.append(Component.literal("§d  Node: " + entry.getKey() + marker + "\n"));
                msg.append(Component.literal("§7    speaker: §f" + node.speaker() + "\n"));
                msg.append(Component.literal("§7    text: §f" + node.text() + "\n"));

                if (node.conditionalTexts() != null && !node.conditionalTexts().isEmpty()) {
                    msg.append(Component.literal("§b    ConditionalTexts (" + node.conditionalTexts().size() + "):\n"));
                    for (Map.Entry<String, ConditionalSay> ct : node.conditionalTexts().entrySet()) {
                        msg.append(Component.literal("§7      [" + ct.getKey() + "] sayId="
                                + ct.getValue().sayId() + " text=" + ct.getValue().text() + "\n"));
                    }
                }

                msg.append(Component.literal("§7    autoNextId: §f" + (node.autoNextId() != null ? node.autoNextId() : "-") + "\n"));
                msg.append(Component.literal("§7    delayMs: §f" + node.delayMs() + "\n"));
                msg.append(Component.literal("§7    repeatable: §f" + node.repeatable() + "\n"));
                msg.append(Component.literal("§7    cooldown: §f" + node.cooldownSeconds() + "s, type=" + node.cooldownType() + "\n"));

                if (node.choices() != null && !node.choices().isEmpty()) {
                    msg.append(Component.literal("§b    Choices (" + node.choices().size() + "):\n"));
                    for (int i = 0; i < node.choices().size(); i++) {
                        DialogueChoice choice = node.choices().get(i);
                        int condCount = choice.conditions() != null ? choice.conditions().size() : 0;
                        int actionCount = choice.actions() != null ? choice.actions().size() : 0;
                        msg.append(Component.literal("§7      [" + i + "] " + choice.choiceId()
                                + " -> " + (choice.nextNodeId() != null ? choice.nextNodeId() : "(terminal)")
                                + " §8[cond=" + condCount + ", actions=" + actionCount
                                + ", priority=" + choice.priority()
                                + ", cooldown=" + choice.cooldownSeconds() + "s/" + choice.cooldownType() + "]\n"));
                    }
                }
            }
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdDialogueReload(CommandContext<CommandSourceStack> ctx) {
        var result = DIALOGUE_HOT_RELOAD_SERVICE.reload();
        success(ctx, "Dialogue datapack reloaded. scanned=" + result.scanned()
                + ", loaded=" + result.discovered()
                + ", failed=" + result.failed()
                + ", activeDatapack=" + result.activeDatapack());
        return 1;
    }

    private static int cmdDialogueSources(CommandContext<CommandSourceStack> ctx) {
        MutableComponent msg = Component.literal("§6═══ Dialogue Sources ═══\n");
        msg.append(Component.literal("§e  Code: §f" + DialogueRegistry.INSTANCE.codeSize() + "\n"));
        msg.append(Component.literal("§e  Datapack: §f" + DialogueRegistry.INSTANCE.datapackSize() + "\n"));
        msg.append(Component.literal("§e  Total (merged): §f" + DialogueRegistry.INSTANCE.size() + "\n"));

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }
}
