package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.S2CSyncMarkersPacket;

import java.util.List;

public class MarkerTestCommand {

    private static final String TYPE_MAIN = "QUEST_MAIN";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("marker")

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

                // /marker test — 只在玩家当前位置生成一个测试标记
                .then(Commands.literal("test")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var pos = player.position();

                            ArcQuestNetwork.CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    new S2CSyncMarkersPacket());
                            ArcQuestNetwork.CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> player),
                                    new S2CSyncMarkersPacket(List.of(
                                            new S2CSyncMarkersPacket.MarkerEntry(
                                                    "test_marker",
                                                    TYPE_MAIN,
                                                    pos.x,
                                                    pos.y + 2.0,
                                                    pos.z,
                                                    "测试标记",
                                                    0xFFFFD700
                                            )
                                    )));

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("已在玩家位置生成测试标记"), false);
                            return 1;
                        }))
        );
    }
}
