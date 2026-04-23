package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
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
import org.com.arc_quest.dialogue.runtime.ProgressKey;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.api.GachaPool;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.api.PityConfig;
import org.com.arc_quest.trade.gacha.network.S2COpenGachaPacket;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * 抽奖管理命令。
 */
public class GachaCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 构建抽奖命令子树。
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("gacha")
                // /arcquest gacha open <player> <shop>
                .then(Commands.literal("open")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                        .suggests(GachaCommands::suggestGachaShopIds)
                                        .executes(GachaCommands::cmdGachaOpen))))
                // /arcquest gacha list
                .then(Commands.literal("list")
                        .executes(GachaCommands::cmdGachaList))
                // /arcquest gacha debug <shop>
                .then(Commands.literal("debug")
                        .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                .suggests(GachaCommands::suggestGachaShopIds)
                                .executes(GachaCommands::cmdGachaDebug)))
                // /arcquest gacha reset draws <player> <shop>
                .then(Commands.literal("reset")
                        // 重置指定商店的所有数据
                        .then(Commands.literal("shop")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                                .suggests(GachaCommands::suggestGachaShopIds)
                                                .executes(GachaCommands::cmdGachaResetShop))))
                        // 重置玩家所有抽奖数据
                        .then(Commands.literal("all")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(GachaCommands::cmdGachaResetAll)))
                        // 保留旧的 draws 命令（向后兼容）
                        .then(Commands.literal("draws")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("shop_id", ResourceLocationArgument.id())
                                                .suggests(GachaCommands::suggestGachaShopIds)
                                                .executes(GachaCommands::cmdGachaResetDraws)))));
    }

    // ═══════════════════════════════════════════════════════
    //  Tab 补全
    // ═══════════════════════════════════════════════════════

    private static CompletableFuture<Suggestions> suggestGachaShopIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            GachaRegistry.getAllShops().stream()
                .map(GachaShopDefinition::getShopId), 
            builder
        );
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

    /**
     * /arcquest gacha open <player> <shop>
     * 为玩家打开抽奖界面
     */
    private static int cmdGachaOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();

        GachaShopDefinition shop = GachaRegistry.get(shopId);
        if (shop == null) {
            error(ctx, "抽奖商店不存在: " + shopId);
            return 0;
        }

        // 获取玩家能力数据
        IQuestCapability cap = getCap(player);
        if (cap == null) {
            error(ctx, "无法获取玩家数据");
            return 0;
        }

        // 发送打开抽奖界面的网络包
        S2COpenGachaPacket.handleServerOpen(player, shop, cap);
        
        success(ctx, String.format("已为 %s 打开抽奖界面: %s", player.getName().getString(), shopId));
        LOGGER.info("[GachaCommand] Opened gacha '{}' for player {}", shopId, player.getName().getString());
        
        return 1;
    }

    /**
     * /arcquest gacha list
     * 列出所有注册的抽奖商店
     */
    private static int cmdGachaList(CommandContext<CommandSourceStack> ctx) {
        Collection<GachaShopDefinition> shops = GachaRegistry.getAllShops();
        MutableComponent msg = Component.literal(String.format("§e=== 抽奖商店列表 (共 %d 个) ===\n", shops.size()));

        for (GachaShopDefinition shop : shops) {
            msg.append(Component.literal(String.format("§b- %s\n", shop.getShopId())));
            
            // 显示物品数量
            int itemCount = shop.getGachaPool().getItems().size();
            msg.append(Component.literal(String.format("  §7物品数: %d\n", itemCount)));
            
            // 显示保底配置
            PityConfig pityConfig = shop.getPityConfig();
            if (pityConfig != null && pityConfig.getPityThreshold() > 0) {
                msg.append(Component.literal(String.format("  §7保底: %d次必出%s\n", 
                    pityConfig.getPityThreshold(),
                    pityConfig.getGuaranteedRarity() != null ? pityConfig.getGuaranteedRarity().name() : "LEGENDARY")));
            } else {
                msg.append(Component.literal("  §7保底: 无\n"));
            }
            
            // 显示限购
            int maxDraws = shop.getMaxDraws();
            msg.append(Component.literal(String.format("  §7限购: %s\n", maxDraws < 0 ? "无限" : maxDraws + "次")));
            
            msg.append(Component.literal("\n"));
        }

        if (shops.isEmpty()) {
            msg.append(Component.literal("§c暂无注册的抽奖商店\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    /**
     * /arcquest gacha debug <shop>
     * 显示抽奖商店的详细信息
     */
    private static int cmdGachaDebug(CommandContext<CommandSourceStack> ctx) {
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();
        GachaShopDefinition shop = GachaRegistry.get(shopId);

        if (shop == null) {
            error(ctx, "抽奖商店不存在: " + shopId);
            return 0;
        }

        MutableComponent msg = Component.literal(String.format("§e=== 抽奖商店详情: %s ===\n", shopId));
        
        // 基本信息
        msg.append(Component.literal(String.format("§b标题: §f%s\n", shop.getDisplayName().getString())));
        
        // 成本信息
        msg.append(Component.literal(String.format("§b抽奖成本: §f%s\n", shop.getDrawCost().describe().getString())));
        
        // 冷却和限购
        msg.append(Component.literal(String.format("§b冷却类型: §f%s\n", shop.getCooldownType().name())));
        msg.append(Component.literal(String.format("§b冷却值: §f%d\n", shop.getCooldownValue())));
        msg.append(Component.literal(String.format("§b限购次数: §f%s\n", shop.getMaxDraws() < 0 ? "无限" : shop.getMaxDraws())));
        
        // 主题色
        msg.append(Component.literal(String.format("§b主题色: §f#%06X\n", shop.getThemeColor() & 0xFFFFFF)));
        
        // 保底配置
        PityConfig pityConfig = shop.getPityConfig();
        if (pityConfig != null && pityConfig.getPityThreshold() > 0) {
            msg.append(Component.literal("§b--- 保底配置 ---\n"));
            msg.append(Component.literal(String.format("  §7阈值: %d次\n", pityConfig.getPityThreshold())));
            if (pityConfig.getGuaranteedRarity() != null) {
                msg.append(Component.literal(String.format("  §7保证稀有度: %s\n", pityConfig.getGuaranteedRarity().name())));
            }
        }
        
        // 奖池物品列表
        GachaPool pool = shop.getGachaPool();
        var items = pool.getItems();
        msg.append(Component.literal(String.format("§b--- 奖池物品 (共 %d 个) ---\n", items.size())));
        
        for (GachaItem item : items) {
            msg.append(Component.literal(String.format("  §e[%s] %s\n", item.getRarity().name(), item.getItemId())));
            msg.append(Component.literal(String.format("    §7权重: %d | 排序: %d\n", item.getBaseWeight(), item.getSortOrder())));
            msg.append(Component.literal(String.format("    §7数量范围: %d-%d\n", item.getMinCount(), item.getMaxCount())));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    /**
     * /arcquest gacha reset draws <player> <shop>
     * 重置玩家的抽奖次数（向后兼容）
     */
    private static int cmdGachaResetDraws(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();
        IQuestCapability cap = getCap(player);

        GachaShopDefinition shop = GachaRegistry.get(shopId);
        if (shop == null) {
            error(ctx, "抽奖商店不存在: " + shopId);
            return 0;
        }

        // 重置抽奖次数
        cap.resetGachaDrawCount(shopId);
        
        success(ctx, String.format("已重置 %s 在 %s 的抽奖次数", player.getName().getString(), shopId));
        LOGGER.info("[GachaCommand] Reset draw count for shop '{}' and player {}", shopId, player.getName().getString());
        
        return 1;
    }
    
    /**
     * /arcquest gacha reset shop <player> <shop>
     * 重置指定商店的所有数据（次数、冷却、保底、历史）
     */
    private static int cmdGachaResetShop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String shopId = ResourceLocationArgument.getId(ctx, "shop_id").toString();
        IQuestCapability cap = getCap(player);

        GachaShopDefinition shop = GachaRegistry.get(shopId);
        if (shop == null) {
            error(ctx, "抽奖商店不存在: " + shopId);
            return 0;
        }

        // 重置所有相关数据
        cap.resetGachaDrawCount(shopId);           // 重置抽奖次数
        cap.setGachaPityCounter(shopId, 0);        // 重置保底计数
        cap.clearGachaDrawHistory(shopId);         // 【新增】清除抽奖历史
        cap.getDialogueProgress().clearCooldownRecord(
            ProgressKey.ofTrade(shopId, "draw")
        );                                         // 清除冷却记录
        
        success(ctx, String.format("已重置 %s 在 %s 的所有抽奖数据（次数、冷却、保底、历史）", 
            player.getName().getString(), shopId));
        LOGGER.info("[GachaCommand] Reset all gacha data for shop '{}' and player {}", 
            shopId, player.getName().getString());
        
        return 1;
    }
    
    /**
     * /arcquest gacha reset all <player>
     * 重置玩家所有抽奖商店的数据（次数、冷却、保底、历史）
     */
    private static int cmdGachaResetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        IQuestCapability cap = getCap(player);

        // 获取所有注册的抽奖商店
        Collection<GachaShopDefinition> allShops = GachaRegistry.getAllShops();
        int resetCount = 0;
        
        for (GachaShopDefinition shop : allShops) {
            String shopId = shop.getShopId();
            
            // 重置所有相关数据
            cap.resetGachaDrawCount(shopId);
            cap.setGachaPityCounter(shopId, 0);
            cap.clearGachaDrawHistory(shopId);     // 【新增】清除抽奖历史
            cap.getDialogueProgress().clearCooldownRecord(
                ProgressKey.ofTrade(shopId, "draw")
            );
            
            resetCount++;
        }
        
        success(ctx, String.format("已重置 %s 的所有 %d 个抽奖商店数据（次数、冷却、保底、历史）", 
            player.getName().getString(), resetCount));
        LOGGER.info("[GachaCommand] Reset all gacha data for {} shops and player {}", 
            resetCount, player.getName().getString());
        
        return 1;
    }
}
