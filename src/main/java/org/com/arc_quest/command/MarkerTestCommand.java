package org.com.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.questmarker.api.QuestMarkerData;
import org.com.arc_quest.questmarker.api.QuestMarkerType;

public class MarkerTestCommand {

    private static final String ENTITY_MARKER_GUID_KEY = "arc_quest.marker_guid";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("marker")

                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var cap = QuestCapabilityProvider.getOrNull(player);

                            cap.clearMarkers();
                            ArcQuestNetwork.syncMarkerDeltaClear(player);

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("已清除所有标记"), false);
                            return 1;
                        }))

                .then(Commands.literal("test")
                        .executes(ctx -> createTestMarker(ctx.getSource(), 0xFFFFD700, "gold"))
                        .then(Commands.literal("gold")
                                .executes(ctx -> createTestMarker(ctx.getSource(), 0xFFFFD700, "gold")))
                        .then(Commands.literal("blue")
                                .executes(ctx -> createTestMarker(ctx.getSource(), 0xFF00BFFF, "blue")))
                        .then(Commands.literal("green")
                                .executes(ctx -> createTestMarker(ctx.getSource(), 0xFF44FF88, "green")))
                        .then(Commands.literal("red")
                                .executes(ctx -> createTestMarker(ctx.getSource(), 0xFFFF4444, "red")))
                        .then(Commands.literal("purple")
                                .executes(ctx -> createTestMarker(ctx.getSource(), 0xFFB388FF, "purple")))
                        .then(Commands.literal("white")
                                .executes(ctx -> createTestMarker(ctx.getSource(), 0xFFFFFFFF, "white"))))

                .then(Commands.literal("follow")
                        .then(Commands.argument("entity_id", EntityArgument.entity())
                                .then(Commands.literal("head")
                                        .executes(ctx -> createFollowMarker(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "entity_id"),
                                                QuestMarkerData.EntityAttachPoint.HEAD)))
                                .then(Commands.literal("center")
                                        .executes(ctx -> createFollowMarker(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "entity_id"),
                                                QuestMarkerData.EntityAttachPoint.CENTER)))))
        );
    }

    private static int createTestMarker(CommandSourceStack source, int color, String presetName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var cap = QuestCapabilityProvider.getOrNull(player);

        var pos = player.position();
        String markerId = "test_marker_" + System.currentTimeMillis();

        QuestMarkerData marker = new QuestMarkerData.Builder(
                markerId,
                pos.x,
                pos.y + 2.0,
                pos.z,
                "测试标记")
                .dimension(player.level().dimension().location().toString())
                .type(QuestMarkerType.QUEST_MAIN)
                .color(color)
                .showDistance(true)
                .allowOffscreenArrow(true)
                .build();

        cap.upsertMarker(marker);
        ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);

        int total = cap.getAllMarkers().size();
        source.sendSuccess(
                () -> Component.literal("已追加测试标记: " + markerId + "，颜色预设: " + presetName + "（当前共 " + total + " 个）"), false);
        return 1;
    }

    private static int createFollowMarker(CommandSourceStack source,
                                          Entity target,
                                          QuestMarkerData.EntityAttachPoint attachPoint) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var cap = QuestCapabilityProvider.getOrNull(player);

        if (target == null || !target.isAlive()) {
            source.sendFailure(Component.literal("实体不存在或已死亡"));
            return 0;
        }

        String markerId = "follow_marker_" + target.getId() + "_" + System.currentTimeMillis();

        String guid = getOrCreateEntityMarkerGuid(target);

        QuestMarkerData marker = new QuestMarkerData.Builder(
                markerId,
                target.getX(),
                target.getY(),
                target.getZ(),
                "跟随: " + target.getName().getString())
                .dimension(player.level().dimension().location().toString())
                .type(QuestMarkerType.QUEST_MAIN)
                .color(0xFFFFD700)
                .followEntity(
                        target.getId(),
                        target.getUUID().toString(),
                        guid,
                        attachPoint
                )
                .showDistance(true)
                .allowOffscreenArrow(true)
                .build();

        cap.upsertMarker(marker);
        ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);

        int total = cap.getAllMarkers().size();
        source.sendSuccess(() -> Component.literal("已创建跟随标记: " + markerId
                + " -> 实体#" + target.getId()
                + "（附着点: " + attachPoint.name().toLowerCase() + "，当前共 " + total + " 个）"), false);
        return 1;
    }

    private static String getOrCreateEntityMarkerGuid(Entity entity) {
        String guid = entity.getPersistentData().getString(ENTITY_MARKER_GUID_KEY);
        if (guid == null || guid.isEmpty()) {
            guid = java.util.UUID.randomUUID().toString();
            entity.getPersistentData().putString(ENTITY_MARKER_GUID_KEY, guid);
        }
        return guid;
    }
}
