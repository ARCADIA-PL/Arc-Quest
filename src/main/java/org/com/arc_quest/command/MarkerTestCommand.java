package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.com.arc_quest.questmarker.api.QuestMarkerType;

public class MarkerTestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("marker")

                // /marker clear
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var cap = QuestCapabilityProvider.getOrNull(player);

                            cap.clearMarkers();
                            ArcQuestNetwork.syncMarkers(player, cap);

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("已清除所有标记"), false);
                            return 1;
                        }))

                // /marker test — 在玩家当前位置生成一个服务端持久化测试标记
                .then(Commands.literal("test")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var cap = QuestCapabilityProvider.getOrNull(player);

                            var pos = player.position();
                            cap.clearMarkers();
                            cap.upsertMarker(new QuestMarkerData.Builder(
                                    "test_marker",
                                    pos.x,
                                    pos.y + 2.0,
                                    pos.z,
                                    "测试标记")
                                    .dimension(player.level().dimension().location().toString())
                                    .type(QuestMarkerType.QUEST_MAIN)
                                    .color(0xFFFFD700)
                                    .showDistance(true)
                                    .allowOffscreenArrow(true)
                                    .build());

                            ArcQuestNetwork.syncMarkers(player, cap);

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("已生成服务端持久化测试标记"), false);
                            return 1;
                        }))
        );
    }
}
