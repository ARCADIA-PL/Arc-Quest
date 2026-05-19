package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MarkerTestCommand {

    private static final String ENTITY_MARKER_GUID_KEY = "arc_quest.marker_guid";

    private static final List<String> PRESETS = List.of("gold", "blue", "green", "red", "purple", "white");
    private static final List<String> STATES = Arrays.stream(QuestMarkerState.values())
            .map(e -> e.name().toLowerCase(Locale.ROOT))
            .toList();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("marker")

                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            var data = ArcQuestPlayerManager.get(player);

                            data.clearMarkers();
                            ArcQuestNetwork.syncMarkerDeltaClear(player);

                            ctx.getSource().sendSuccess(() -> Component.literal("已清除所有标记"), false);
                            return 1;
                        }))

                // /marker test [preset] [active]
                .then(Commands.literal("test")
                        .executes(ctx -> createTestMarker(ctx.getSource(), "gold", true, QuestMarkerState.ACTIVE))
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests(MarkerTestCommand::suggestPresets)
                                .executes(ctx -> createTestMarker(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "preset"),
                                        true,
                                        QuestMarkerState.ACTIVE
                                ))
                                .then(Commands.argument("active", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean active = BoolArgumentType.getBool(ctx, "active");
                                            return createTestMarker(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "preset"),
                                                    active,
                                                    active ? QuestMarkerState.ACTIVE : QuestMarkerState.STANDBY
                                            );
                                        })))
                        .then(Commands.argument("active", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean active = BoolArgumentType.getBool(ctx, "active");
                                    return createTestMarker(
                                            ctx.getSource(),
                                            "gold",
                                            active,
                                            active ? QuestMarkerState.ACTIVE : QuestMarkerState.STANDBY
                                    );
                                })))

                // /marker teststate <state> [preset]
                .then(Commands.literal("teststate")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests(MarkerTestCommand::suggestStates)
                                .executes(ctx -> createTestMarker(
                                        ctx.getSource(),
                                        "gold",
                                        true,
                                        parseState(StringArgumentType.getString(ctx, "state"))
                                ))
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests(MarkerTestCommand::suggestPresets)
                                        .executes(ctx -> createTestMarker(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "preset"),
                                                true,
                                                parseState(StringArgumentType.getString(ctx, "state"))
                                        )))))

                // /marker follow <entity> <head|center> [active]
                .then(Commands.literal("follow")
                        .then(Commands.argument("entity_id", EntityArgument.entity())
                                .then(Commands.literal("head")
                                        .executes(ctx -> createFollowMarker(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "entity_id"),
                                                QuestMarkerData.EntityAttachPoint.HEAD,
                                                QuestMarkerState.ACTIVE
                                        ))
                                        .then(Commands.argument("active", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    boolean active = BoolArgumentType.getBool(ctx, "active");
                                                    return createFollowMarker(
                                                            ctx.getSource(),
                                                            EntityArgument.getEntity(ctx, "entity_id"),
                                                            QuestMarkerData.EntityAttachPoint.HEAD,
                                                            active ? QuestMarkerState.ACTIVE : QuestMarkerState.STANDBY
                                                    );
                                                })))
                                .then(Commands.literal("center")
                                        .executes(ctx -> createFollowMarker(
                                                ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "entity_id"),
                                                QuestMarkerData.EntityAttachPoint.CENTER,
                                                QuestMarkerState.ACTIVE
                                        ))
                                        .then(Commands.argument("active", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    boolean active = BoolArgumentType.getBool(ctx, "active");
                                                    return createFollowMarker(
                                                            ctx.getSource(),
                                                            EntityArgument.getEntity(ctx, "entity_id"),
                                                            QuestMarkerData.EntityAttachPoint.CENTER,
                                                            active ? QuestMarkerState.ACTIVE : QuestMarkerState.STANDBY
                                                    );
                                                })))))

                // /marker setstate <markerId> <state>
                .then(Commands.literal("setstate")
                        .then(Commands.argument("marker_id", StringArgumentType.word())
                                .executes(ctx -> {
                                    ctx.getSource().sendFailure(Component.literal("用法: /marker setstate <marker_id> <state>"));
                                    return 0;
                                })
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests(MarkerTestCommand::suggestStates)
                                        .executes(ctx -> setMarkerState(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "marker_id"),
                                                parseState(StringArgumentType.getString(ctx, "state"))
                                        )))))
        );
    }

    private static int createTestMarker(
            CommandSourceStack source,
            String preset,
            boolean activeFlag,
            QuestMarkerState state
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var data = ArcQuestPlayerManager.get(player);

        int color = colorFromPreset(preset);
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
                .state(state)
                .color(color)
                .showDistance(true)
                .allowOffscreenArrow(true)
                .build();

        data.upsertMarker(marker);
        ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);

        int total = data.getAllMarkers().size();
        source.sendSuccess(() -> Component.literal(
                "已追加测试标记: " + markerId
                        + "，preset=" + preset.toLowerCase(Locale.ROOT)
                        + "，active=" + activeFlag
                        + "，state=" + state.name().toLowerCase(Locale.ROOT)
                        + "（当前共 " + total + " 个）"
        ), false);
        return 1;
    }

    private static int createFollowMarker(
            CommandSourceStack source,
            Entity target,
            QuestMarkerData.EntityAttachPoint attachPoint,
            QuestMarkerState state
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var data = ArcQuestPlayerManager.get(player);

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
                .state(state)
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

        data.upsertMarker(marker);
        ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);

        int total = data.getAllMarkers().size();
        source.sendSuccess(() -> Component.literal(
                "已创建跟随标记: " + markerId
                        + " -> 实体#" + target.getId()
                        + "（附着点: " + attachPoint.name().toLowerCase(Locale.ROOT)
                        + "，state=" + state.name().toLowerCase(Locale.ROOT)
                        + "，当前共 " + total + " 个）"
        ), false);
        return 1;
    }

    private static int setMarkerState(CommandSourceStack source, String markerId, QuestMarkerState newState) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var data = ArcQuestPlayerManager.get(player);

        Map<String, QuestMarkerData> all = data.getAllMarkers();
        QuestMarkerData old = all.get(markerId);
        if (old == null) {
            source.sendFailure(Component.literal("未找到 marker: " + markerId));
            return 0;
        }

        QuestMarkerData.Builder builder = new QuestMarkerData.Builder(
                old.getId(),
                old.getWorldX(),
                old.getWorldY(),
                old.getWorldZ(),
                old.getLabel()
        )
                .dimension(old.getDimension())
                .bindQuest(old.getQuestId())
                .bindPhase(old.getPhaseId())
                .bindObjective(old.getObjectiveIndex())
                .type(old.getType())
                .state(newState)
                .color(old.getColorARGB())
                .showDistance(old.isShowDistance())
                .allowOffscreenArrow(old.isAllowOffscreenArrow());

        if (old.hasEntityBinding()) {
            builder.followEntity(
                    old.getFollowEntityId(),
                    old.getFollowEntityUuid(),
                    old.getFollowEntityGuid(),
                    old.getAttachPoint()
            );
        }

        QuestMarkerData updated = builder.build();
        data.upsertMarker(updated);
        ArcQuestNetwork.syncMarkerDeltaUpsert(player, updated);

        source.sendSuccess(() -> Component.literal(
                "已更新 marker 状态: " + markerId + " -> " + newState.name().toLowerCase(Locale.ROOT)
        ), false);
        return 1;
    }

    private static int colorFromPreset(String preset) {
        return switch (preset.toLowerCase(Locale.ROOT)) {
            case "gold" -> 0xFFFFD700;
            case "blue" -> 0xFF00BFFF;
            case "green" -> 0xFF44FF88;
            case "red" -> 0xFFFF4444;
            case "purple" -> 0xFFB388FF;
            case "white" -> 0xFFFFFFFF;
            default -> 0xFFFFD700;
        };
    }

    private static QuestMarkerState parseState(String text) {
        try {
            return QuestMarkerState.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return QuestMarkerState.ACTIVE;
        }
    }

    private static CompletableFuture<Suggestions> suggestPresets(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(PRESETS, builder);
    }

    private static CompletableFuture<Suggestions> suggestStates(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(STATES, builder);
    }

    private static String getOrCreateEntityMarkerGuid(Entity entity) {
        String guid = entity.getPersistentData().getString(ENTITY_MARKER_GUID_KEY);
        if (guid == null || guid.isEmpty()) {
            guid = UUID.randomUUID().toString();
            entity.getPersistentData().putString(ENTITY_MARKER_GUID_KEY, guid);
        }
        return guid;
    }
}
