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
import org.arcadia.arc_quest.quest.player.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.player.ArcQuestPlayerManager;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * 交易管理命令。
 */
public class TradeCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构建交易命令子树。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("trade")
                // /arcquest trade open <player> <shop>
                .then(Commands.literal("open")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                        .suggests(TradeCommands::suggestTradeShopIds)
                                        .executes(TradeCommands::cmdTradeOpen))))
                // /arcquest trade simple <player> <shop>
                .then(Commands.literal("simple")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                        .suggests(TradeCommands::suggestTradeShopIds)
                                        .executes(TradeCommands::cmdTradeSimple))))
                // /arcquest trade list
                .then(Commands.literal("list")
                        .executes(TradeCommands::cmdTradeList))
                // /arcquest trade debug <shop>
                .then(Commands.literal("debug")
                        .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                .suggests(TradeCommands::suggestTradeShopIds)
                                .executes(TradeCommands::cmdTradeDebug)))
                // /arcquest trade reset ...
                .then(Commands.literal("reset")
                        // /arcquest trade reset shop <player> <shop>
                        .then(Commands.literal("shop")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                                .suggests(TradeCommands::suggestTradeShopIds)
                                                .executes(TradeCommands::cmdTradeResetShop))))
                        // /arcquest trade reset entry <player> <shop> <entry>
                        .then(Commands.literal("entry")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                                .suggests(TradeCommands::suggestTradeShopIds)
                                                .then(Commands.argument("entry_id", StringArgumentType.string())
                                                        .suggests(TradeCommands::suggestTradeEntryIds)
                                                        .executes(TradeCommands::cmdTradeResetEntry)))))
                        // /arcquest trade reset all <player>
                        .then(Commands.literal("all")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(TradeCommands::cmdTradeResetAll))));
    }

    // ═══════════════════════════════════════════════════════
    //  Tab 补全
    // ═══════════════════════════════════════════════════════

    private static CompletableFuture<Suggestions> suggestTradeShopIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(TradeRegistry.getAllIds(), builder);
    }

    private static CompletableFuture<Suggestions> suggestTradeEntryIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String shopId = StringArgumentType.getString(ctx, "shop_id");
            TradeShopDefinition shop = TradeRegistry.get(shopId);
            if (shop != null) {
                return SharedSuggestionProvider.suggest(
                        shop.getAllEntries().stream().map(TradeEntry::getEntryId), builder);
            }
        } catch (IllegalArgumentException ignored) {
            // 参数尚未输入完毕，安全忽略
        }
        return Suggestions.empty();
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

    private static int cmdTradeOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();

        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        C2SRequestTradePacket.handleServerOpen(player, shop, false);
        success(ctx, Component.translatable("arc_quest.command.trade.open.success", shopId, player.getName().getString()).getString());
        return 1;
    }

    private static int cmdTradeSimple(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();

        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        C2SRequestTradePacket.handleServerOpen(player, shop, true);
        success(ctx, Component.translatable("arc_quest.command.trade.simple.success", shopId, player.getName().getString()).getString());
        return 1;
    }

    private static int cmdTradeList(CommandContext<CommandSourceStack> ctx) {
        var shops = TradeRegistry.getAll();
        MutableComponent msg = Component.translatable("arc_quest.command.trade.list.header", shops.size());
        msg.append(Component.literal("\n"));

        for (TradeShopDefinition shop : shops) {
            String modeText = shop.isSimpleMode() ? ", simple" : "";
            msg.append(Component.translatable("arc_quest.command.trade.list.entry",
                    shop.getShopId(),
                    shop.getDisplayName().getString(),
                    shop.getAllEntries().size(),
                    modeText));
            msg.append(Component.literal("\n"));
        }

        if (shops.isEmpty()) {
            msg.append(Component.translatable("arc_quest.command.trade.list.empty"));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdTradeDebug(CommandContext<CommandSourceStack> ctx) {
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();
        TradeShopDefinition shop = TradeRegistry.get(shopId);

        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        MutableComponent msg = Component.translatable("arc_quest.command.trade.debug.header", shopId);
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.trade.debug.name", shop.getDisplayName().getString()));
        msg.append(Component.literal("\n"));
        if (shop.getDescription() != null) {
            msg.append(Component.translatable("arc_quest.command.trade.debug.description", shop.getDescription().getString()));
            msg.append(Component.literal("\n"));
        }
        String modeKey = shop.isSimpleMode() ? "arc_quest.command.trade.debug.mode.simple" : "arc_quest.command.trade.debug.mode.full";
        msg.append(Component.translatable(modeKey));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.trade.debug.categories", shop.getCategories().size()));
        msg.append(Component.literal("\n"));

        for (TradeCategory cat : shop.getCategories()) {
            msg.append(Component.translatable("arc_quest.command.trade.debug.category_entry",
                    cat.getId(), cat.getDisplayName().getString()));
            msg.append(Component.literal("\n"));
        }

        msg.append(Component.translatable("arc_quest.command.trade.debug.entries", shop.getAllEntries().size()));
        msg.append(Component.literal("\n"));

        for (TradeEntry entry : shop.getAllEntries()) {
            StringBuilder costStr = new StringBuilder();
            for (ITradeOffer cost : entry.getCosts()) {
                if (!costStr.isEmpty()) costStr.append(" + ");
                costStr.append(cost.describe().getString());
            }
            StringBuilder rewardStr = new StringBuilder();
            for (ITradeOffer reward : entry.getRewards()) {
                if (!rewardStr.isEmpty()) rewardStr.append(" + ");
                rewardStr.append(reward.describe().getString());
            }

            msg.append(Component.translatable("arc_quest.command.trade.debug.entry_header",
                    entry.getEntryId(), entry.getDisplayName().getString()));
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable("arc_quest.command.trade.debug.costs", costStr.toString()));
            msg.append(Component.literal("\n"));
            msg.append(Component.translatable("arc_quest.command.trade.debug.rewards", rewardStr.toString()));
            msg.append(Component.literal("\n"));

            if (entry.hasLimit()) {
                msg.append(Component.translatable("arc_quest.command.trade.debug.limit", entry.getMaxPurchases()));
                msg.append(Component.literal("\n"));
            }
            if (entry.hasCooldown()) {
                msg.append(Component.translatable("arc_quest.command.trade.debug.cooldown",
                        entry.getCooldownType().name(), entry.getCooldownValue()));
                msg.append(Component.literal("\n"));
            }
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdTradeResetShop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();
        ArcQuestPlayer data = getData(player);

        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        // 重置整个商店的所有交易项
        for (TradeEntry entry : shop.getAllEntries()) {
            data.getTradeDataStore().resetEntry(shopId, entry.getEntryId());
        }
        success(ctx, Component.translatable("arc_quest.command.trade.reset.shop_success",
                shopId, player.getName().getString()).getString());

        return 1;
    }

    private static int cmdTradeResetEntry(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();
        String entryId = StringArgumentType.getString(ctx, "entry_id");
        IQuestCapability cap = getCap(player);

        TradeShopDefinition shop = TradeRegistry.get(shopId);
        if (shop == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.error.not_found", shopId).getString());
            return 0;
        }

        // 查找并重置单个交易项
        TradeEntry targetEntry = null;
        for (TradeEntry entry : shop.getAllEntries()) {
            if (entry.getEntryId().equals(entryId)) {
                targetEntry = entry;
                break;
            }
        }

        if (targetEntry == null) {
            error(ctx, Component.translatable("arc_quest.command.trade.reset.error.entry_not_found",
                    entryId, shopId).getString());
            return 0;
        }

        cap.getTradeDataStore().resetEntry(shopId, entryId);
        success(ctx, Component.translatable("arc_quest.command.trade.reset.entry_success",
                entryId, shopId, player.getName().getString()).getString());

        return 1;
    }

    private static int cmdTradeResetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCap(player);

        // 遍历所有商店，重置所有交易项
        int resetCount = 0;
        for (TradeShopDefinition shop : TradeRegistry.getAll()) {
            for (TradeEntry entry : shop.getAllEntries()) {
                cap.getTradeDataStore().resetEntry(shop.getShopId(), entry.getEntryId());
                resetCount++;
            }
        }

        success(ctx, Component.translatable("arc_quest.command.trade.reset.all_success",
                player.getName().getString(), resetCount).getString());
        return 1;
    }
}
