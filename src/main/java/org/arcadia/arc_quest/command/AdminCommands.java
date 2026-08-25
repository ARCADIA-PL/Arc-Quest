package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.registry.QuestSourceInfo;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

/**
 * Admin command subtree.
 */
public class AdminCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Build admin command subtree.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("admin")
                .then(Commands.literal("registry")
                        .executes(AdminCommands::cmdRegistry))
                .then(Commands.literal("reload")
                        .executes(AdminCommands::cmdReload))
                .then(Commands.literal("quest_sources")
                        .executes(AdminCommands::cmdQuestSourceSummary)
                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> ArcQuestSuggestionUtil.suggest(
                                        QuestRegistry.getAllIds().stream().map(ResourceLocation::toString).toList(), builder,
                                        id -> ArcQuestSuggestionUtil.idTooltip("Quest", id)))
                                .executes(AdminCommands::cmdQuestSourceById)));
    }

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("[ArcQuest] " + msg), true);
    }

    private static int cmdRegistry(CommandContext<CommandSourceStack> ctx) {
        MutableComponent msg = Component.translatable("arc_quest.command.registry.header");
        msg.append(Component.literal("\n"));

        var quests = QuestRegistry.getAll();
        msg.append(Component.translatable("arc_quest.command.registry.quests_header", quests.size()));
        msg.append(Component.literal("\n"));
        for (QuestDefinition def : quests) {
            QuestSourceInfo sourceInfo = QuestRegistry.getSourceInfo(def.getId());
            String sourceLabel = sourceInfo == null ? "unknown" : sourceInfo.sourceType().name().toLowerCase();
            msg.append(Component.literal("    " + def.getId() + " - " + def.getDisplayName().getString() + " [" + sourceLabel + "]\n"));
        }

        var dialogues = DialogueRegistry.INSTANCE.getAll();
        msg.append(Component.translatable("arc_quest.command.registry.dialogues_header", dialogues.size()));
        msg.append(Component.literal("\n"));
        for (DialogueTree tree : dialogues) {
            msg.append(Component.literal("    " + tree.dialogueId() + " - " + tree.defaultNpc() + "\n"));
        }

        var tradeShops = TradeRegistry.getAll();
        msg.append(Component.literal("--- Trade Shops (" + tradeShops.size() + ") ---\n"));
        for (TradeShopDefinition shop : tradeShops) {
            msg.append(Component.literal("    " + shop.getShopId() + " - " + shop.getDisplayName().getString() + " [" + shop.getAllEntries().size() + " entries]\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdReload(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getServer().getCommands().performPrefixedCommand(ctx.getSource(), "reload");
        success(ctx, Component.translatable("arc_quest.command.reload.success").getString());
        return 1;
    }

    private static int cmdQuestSourceSummary(CommandContext<CommandSourceStack> ctx) {
        success(ctx, "Quest sources: code=" + QuestRegistry.codeSize() + ", datapack=" + QuestRegistry.datapackSize() + ", merged=" + QuestRegistry.size());
        return 1;
    }

    private static int cmdQuestSourceById(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "quest_id");
        QuestDefinition quest = QuestRegistry.get(id);
        if (quest == null) {
            ctx.getSource().sendFailure(Component.literal("[ArcQuest] Quest not found: " + id));
            return 0;
        }
        QuestSourceInfo info = QuestRegistry.getSourceInfo(id);
        String detail = info == null
                ? "unknown"
                : ("type=" + info.sourceType() + ", sourceId=" + info.sourceId() + ", loadOrder=" + info.loadOrder()
                + (info.ignoredReason() == null ? "" : ", ignoredReason=" + info.ignoredReason()));
        success(ctx, "Quest '" + id + "' source -> " + detail);
        return 1;
    }
}
