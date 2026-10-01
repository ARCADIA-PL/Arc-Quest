package org.arcadia.arc_quest.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.trade.gacha.network.PendingDrawManager;

import java.util.List;
import java.util.UUID;

/** 最高管理员权限下核查崩溃遗留结果；只接受已经人工结算的事务，不重放未知副作用。 */
final class GachaRecoveryCommands {
    private GachaRecoveryCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> registerSubtree() {
        return Commands.literal("recovery").requires(source -> source.hasPermission(4))
                .then(Commands.literal("list").executes(context -> list(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context, IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("transaction", UuidArgument.uuid()).executes(GachaRecoveryCommands::inspect)))
                .then(Commands.literal("resolve")
                        .then(Commands.argument("transaction", UuidArgument.uuid())
                                .then(Commands.literal("settled")
                                        .then(Commands.argument("note", StringArgumentType.greedyString())
                                                .executes(GachaRecoveryCommands::resolve)))))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "先用 list / inspect 核对玩家扣费、背包与附属奖励；人工退款或补偿完成后，使用 resolve <事务 UUID> settled <处理说明> 归档。此命令不会发奖或退款。"), false);
                    return 1;
                });
    }

    private static int list(CommandContext<CommandSourceStack> context, int page) {
        try {
            List<CompoundTag> entries = PendingDrawManager.inspectPendingDraws(context.getSource().getServer());
            int pages = Math.max(1, (entries.size() + 19) / 20);
            if (page > pages) throw new IllegalArgumentException("页码超出范围，共 " + pages + " 页");
            context.getSource().sendSuccess(() -> Component.literal(
                    "抽卡待核查记录 " + entries.size() + " 笔，第 " + page + "/" + pages + " 页"), false);
            for (CompoundTag entry : entries.subList((page - 1) * 20, Math.min(page * 20, entries.size()))) {
                context.getSource().sendSuccess(() -> Component.literal(describe(entry)), false);
            }
            return entries.size();
        } catch (IllegalArgumentException | IllegalStateException failure) { return failed(context, failure); }
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        UUID token = UuidArgument.getUuid(context, "transaction");
        try {
            CompoundTag entry = PendingDrawManager.inspectPendingDraws(context.getSource().getServer()).stream()
                    .filter(record -> record.getUUID("Transaction").equals(token)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无此待核查记录；已完成的人工结算可查存档 draw_journal/reconciled"));
            context.getSource().sendSuccess(() -> Component.literal(describe(entry)), false);
            String payload = entry.getCompound("Payload").toString();
            String preview = payload.length() > 4096 ? payload.substring(0, 4096) + "…（完整记录见服务器事务文件）" : payload;
            context.getSource().sendSuccess(() -> Component.literal(preview), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException failure) { return failed(context, failure); }
    }

    private static int resolve(CommandContext<CommandSourceStack> context) {
        UUID token = UuidArgument.getUuid(context, "transaction");
        try {
            var source = context.getSource();
            String operator = source.getEntity() == null ? source.getTextName() : source.getEntity().getUUID().toString();
            PendingDrawManager.resolvePendingDraw(source.getServer(), token, operator, StringArgumentType.getString(context, "note"));
            source.sendSuccess(() -> Component.literal("已提交人工结算记录 " + token + "；落盘成功后解除阻塞，使用 list / inspect 复核。"), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException failure) { return failed(context, failure); }
    }

    private static String describe(CompoundTag entry) {
        CompoundTag payload = entry.getCompound("Payload");
        return entry.getUUID("Transaction") + " | 玩家=" + entry.getUUID("Player")
                + " | " + entry.getString("Stage") + " | " + entry.getString("Shop")
                + " | " + payload.getString("ItemId") + " ×" + payload.getInt("Count");
    }

    private static int failed(CommandContext<CommandSourceStack> context, RuntimeException failure) {
        context.getSource().sendFailure(Component.literal(failure.getMessage()));
        return 0;
    }
}
