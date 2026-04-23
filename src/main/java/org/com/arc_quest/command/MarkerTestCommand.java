package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.S2CSyncMarkersPacket;

import java.util.ArrayList;
import java.util.List;

public class MarkerTestCommand {

    private static final String TYPE_MAIN  = "QUEST_MAIN";
    private static final String TYPE_SIDE  = "QUEST_SIDE";
    private static final String TYPE_NPC   = "NPC_INTERACT";
    private static final String TYPE_ENEMY = "ENEMY_TARGET";
    private static final String TYPE_LOC   = "LOCATION";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("marker")

                // /marker add <id> <x> <y> <z>
                .then(Commands.literal("add")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> {
                                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                            String id = StringArgumentType.getString(ctx, "id");
                                                            double x = DoubleArgumentType.getDouble(ctx, "x");
                                                            double y = DoubleArgumentType.getDouble(ctx, "y");
                                                            double z = DoubleArgumentType.getDouble(ctx, "z");
                                                            ArcQuestNetwork.CHANNEL.send(
                                                                    PacketDistributor.PLAYER.with(() -> player),
                                                                    new S2CSyncMarkersPacket(List.of(
                                                                            new S2CSyncMarkersPacket.MarkerEntry(
                                                                                    id, TYPE_MAIN, x, y, z, "任务: " + id, 0xFFFFD700)
                                                                    )));
                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.literal("已添加标记: " + id), false);
                                                            return 1;
                                                        }))))
                ))

                // /marker remove <id>
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    ArcQuestNetwork.CHANNEL.send(
                                            PacketDistributor.PLAYER.with(() -> player),
                                            new S2CSyncMarkersPacket(id));
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("已移除标记: " + id), false);
                                    return 1;
                                })))

                // /marker clear
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ArcQuestNetwork.CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    new S2CSyncMarkersPacket());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("已清除所有标记"), false);
                            return 1;
                        }))

                // /marker test — 在玩家位置周围生成6个测试标记
                .then(Commands.literal("test")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var pos = player.position();

                            List<S2CSyncMarkersPacket.MarkerEntry> list = new ArrayList<>();
                            list.add(new S2CSyncMarkersPacket.MarkerEntry(
                                    "main1", TYPE_MAIN,
                                    pos.x + 50, pos.y + 5, pos.z, "讨伐魔物", 0xFFFFD700));
                            list.add(new S2CSyncMarkersPacket.MarkerEntry(
                                    "side1", TYPE_SIDE,
                                    pos.x - 30, pos.y, pos.z + 40, "收集素材", 0xFF00BFFF));
                            list.add(new S2CSyncMarkersPacket.MarkerEntry(
                                    "npc1", TYPE_NPC,
                                    pos.x, pos.y, pos.z - 60, "长老·沧澜", 0xFF44FF88));
                            list.add(new S2CSyncMarkersPacket.MarkerEntry(
                                    "enemy1", TYPE_ENEMY,
                                    pos.x + 80, pos.y + 10, pos.z + 80, "无冠者", 0xFFFF4444));
                            list.add(new S2CSyncMarkersPacket.MarkerEntry(
                                    "loc1", TYPE_LOC,
                                    pos.x - 100, pos.y, pos.z - 100, "央城遗迹", 0xFFAAAAAA));
                            list.add(new S2CSyncMarkersPacket.MarkerEntry(
                                    "behind1", TYPE_MAIN,
                                    pos.x, pos.y, pos.z + 200, "后方目标", 0xFFFFD700));

                            ArcQuestNetwork.CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    new S2CSyncMarkersPacket());
                            ArcQuestNetwork.CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    new S2CSyncMarkersPacket(list));

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("已生成6个测试标记（基于玩家当前坐标）"), false);
                            return 1;
                        }))
        );
    }
}
