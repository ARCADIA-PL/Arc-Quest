package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.runtime.GuidePlayerStateSyncService;
import org.arcadia.arc_quest.guide.runtime.GuideTriggerService;
import org.arcadia.arc_quest.guide.runtime.GuideUnlockService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class GuideCommands {
    private static final GuideTriggerService triggerService = new GuideTriggerService();
    private static final GuideUnlockService unlockService = new GuideUnlockService();

    private GuideCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("guide")
                .then(Commands.literal("open")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("guide_id", ResourceLocationArgument.id())
                                        .suggests(GuideCommands::suggestGuideIds)
                                        .executes(GuideCommands::cmdOpen))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("guide_id", ResourceLocationArgument.id())
                                        .suggests(GuideCommands::suggestGuideIds)
                                        .executes(GuideCommands::cmdUnlock))))
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("guide_id", ResourceLocationArgument.id())
                                        .suggests(GuideCommands::suggestGuideIds)
                                        .executes(GuideCommands::cmdUnlock))))
                .then(Commands.literal("unlock_all")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(GuideCommands::cmdUnlockAll)))
                .then(Commands.literal("list")
                        .executes(GuideCommands::cmdList))
                .then(Commands.literal("debug")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(GuideCommands::cmdDebugPlayer))
                        .then(Commands.argument("guide_id", ResourceLocationArgument.id())
                                .suggests(GuideCommands::suggestGuideIds)
                                .executes(GuideCommands::cmdDebugGuide)))
                .then(Commands.literal("status")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(GuideCommands::cmdStatus)))
                .then(Commands.literal("reset")
                        .then(Commands.literal("seen")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> cmdResetSeen(ctx, null))
                                        .then(Commands.argument("guide_id", ResourceLocationArgument.id())
                                                .suggests(GuideCommands::suggestGuideIds)
                                                .executes(ctx -> cmdResetSeen(ctx, ResourceLocationArgument.getId(ctx, "guide_id"))))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> cmdResetUnlock(ctx, null))
                                        .then(Commands.argument("guide_id", ResourceLocationArgument.id())
                                                .suggests(GuideCommands::suggestGuideIds)
                                                .executes(ctx -> cmdResetUnlock(ctx, ResourceLocationArgument.getId(ctx, "guide_id"))))))
                        .then(Commands.literal("all")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(GuideCommands::cmdResetAll))));
    }

    // ═══════════════════════════════════════════════════════
    //  Tab 补全
    // ═══════════════════════════════════════════════════════

    private static CompletableFuture<Suggestions> suggestGuideIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return ArcQuestSuggestionUtil.suggest(
                GuideRegistry.getAllIds().stream().map(ResourceLocation::toString).toList(),
                builder, id -> {
                    GuideDefinition definition = GuideRegistry.get(ResourceLocation.parse(id));
                    return definition == null
                            ? ArcQuestSuggestionUtil.idTooltip("Guide", id)
                            : ArcQuestSuggestionUtil.displayTooltip(
                                    "Guide", definition.getTitle(), id);
                });
    }

    // ═══════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════

    private static ArcQuestPlayer getData(ServerPlayer player) {
        return ArcQuestPlayerManager.getOrCreate(player);
    }

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ArcQuest] §f" + msg), true);
    }

    private static void error(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("§c[ArcQuest] §f" + msg));
    }

    private static void persistAndSyncGuideState(ServerPlayer player, ArcQuestPlayer data) {
        QuestSyncCoordinator.persistSnapshot(player, data);
        GuidePlayerStateSyncService.sync(player, data);
        data.clearDirty(ArcQuestPlayer.DirtyKind.GUIDE_STATE);
    }

    // ═══════════════════════════════════════════════════════
    //  命令实现
    // ═══════════════════════════════════════════════════════

    private static int cmdOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation guideId = ResourceLocationArgument.getId(ctx, "guide_id");

        GuideDefinition guide = GuideRegistry.get(guideId);
        if (guide == null) {
            error(ctx, "Unknown guide: " + guideId);
            return 0;
        }

        if (!triggerService.open(player, guideId)) {
            error(ctx, "Failed to open guide: " + guideId);
            return 0;
        }

        success(ctx, "Opened guide " + guideId + " for " + player.getName().getString());
        return 1;
    }

    private static int cmdUnlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation guideId = ResourceLocationArgument.getId(ctx, "guide_id");

        GuideDefinition guide = GuideRegistry.get(guideId);
        if (guide == null) {
            error(ctx, "Unknown guide: " + guideId);
            return 0;
        }

        if (!unlockService.unlock(player, guideId)) {
            success(ctx, "Guide " + guideId + " is already unlocked for " + player.getName().getString());
            return 1;
        }

        success(ctx, "Unlocked guide " + guideId + " for " + player.getName().getString());
        return 1;
    }

    private static int cmdUnlockAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int count = 0;
        for (GuideDefinition guide : GuideRegistry.getAll()) {
            if (unlockService.unlock(player, guide.getId())) {
                count++;
            }
        }

        success(ctx, "Unlocked " + count + " guide(s) for " + player.getName().getString());
        return 1;
    }

    private static int cmdList(CommandContext<CommandSourceStack> ctx) {
        var guides = GuideRegistry.getAll();
        MutableComponent msg = Component.translatable("arc_quest.command.guide.list.header", guides.size());
        msg.append(Component.literal("\n"));

        ResourceLocation lastCategory = null;
        for (GuideDefinition guide : GuideRegistry.getAll()) {
            GuideCategory cat = guide.getCategory();
            ResourceLocation catId = cat.getId();
            if (!catId.equals(lastCategory)) {
                msg.append(Component.literal(" §e" + cat.getDisplayName().getString() + "§r"));
                msg.append(Component.literal("\n"));
                lastCategory = catId;
            }

            StringBuilder mediaList = new StringBuilder();
            for (int i = 0; i < guide.getPageCount(); i++) {
                if (i > 0) mediaList.append(", ");
                mediaList.append(guide.getPage(i).getMedia().getType().name().toLowerCase());
            }

            String hidden = guide.isHidden() ? " §7[HIDDEN]§f" : "";
            msg.append(Component.literal("  " + guide.getId().toString() + " (" + guide.getPageCount() + " pages) [" + mediaList + "]" + hidden));
            msg.append(Component.literal("\n"));
        }

        if (guides.isEmpty()) {
            msg.append(Component.translatable("arc_quest.command.guide.list.empty"));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdDebugGuide(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation guideId = ResourceLocationArgument.getId(ctx, "guide_id");
        GuideDefinition guide = GuideRegistry.get(guideId);

        if (guide == null) {
            error(ctx, "Unknown guide: " + guideId);
            return 0;
        }

        MutableComponent msg = Component.translatable("arc_quest.command.guide.debug.header", guideId.toString());
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.debug.title", guide.getTitle().getString()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.debug.category", guide.getCategory().getId().toString()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.debug.sort", guide.getSortOrder()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.debug.hidden", guide.isHidden()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.debug.repeatable", guide.isRepeatablePopup()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.debug.conditions", guide.getUnlockConditions().size()));
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.debug.pages", guide.getPageCount()));
        msg.append(Component.literal("\n"));

        for (int i = 0; i < guide.getPageCount(); i++) {
            GuideMediaType mediaType = guide.getPage(i).getMedia().getType();
            msg.append(Component.literal("  Page " + (i + 1) + ": " + mediaType.name().toLowerCase()));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdDebugPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ArcQuestPlayer data = getData(player);

        Set<ResourceLocation> unlocked = data.getUnlockedGuides();
        Set<ResourceLocation> seen = data.getSeenGuides();

        MutableComponent msg = Component.translatable("arc_quest.command.guide.status.header", player.getName().getString());
        msg.append(Component.literal("\n"));
        msg.append(Component.translatable("arc_quest.command.guide.status.unlocked", unlocked.size()));
        msg.append(Component.literal("\n"));

        int idx = 0;
        for (ResourceLocation id : unlocked) {
            GuideDefinition guide = GuideRegistry.get(id);
            String title = guide != null ? guide.getTitle().getString() : "?";
            boolean isSeen = seen.contains(id);
            String mark = isSeen ? "  §7[seen]§r" : "  §e[NEW]§r";
            msg.append(Component.literal("  " + id + " §7(" + title + ")§r" + mark));
            msg.append(Component.literal("\n"));
            if (++idx >= 50) {
                msg.append(Component.literal("  §7... and " + (unlocked.size() - idx) + " more§r"));
                msg.append(Component.literal("\n"));
                break;
            }
        }

        if (unlocked.isEmpty()) {
            msg.append(Component.literal("  §7(none)§r"));
            msg.append(Component.literal("\n"));
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return cmdDebugPlayer(ctx);
    }

    private static int cmdResetSeen(CommandContext<CommandSourceStack> ctx,
                                    ResourceLocation guideId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ArcQuestPlayer data = getData(player);

        if (guideId == null) {
            int count = 0;
            for (ResourceLocation id : data.getSeenGuides()) {
                data.clearGuideSeen(id);
                count++;
            }
            persistAndSyncGuideState(player, data);
            success(ctx, "Reset seen status for " + count + " guide(s) for " + player.getName().getString());
            return 1;
        }

        GuideDefinition guide = GuideRegistry.get(guideId);
        if (guide == null) {
            error(ctx, "Unknown guide: " + guideId);
            return 0;
        }

        data.clearGuideSeen(guideId);
        persistAndSyncGuideState(player, data);
        success(ctx, "Reset seen status for " + guideId + " (" + player.getName().getString() + ")");
        return 1;
    }

    private static int cmdResetUnlock(CommandContext<CommandSourceStack> ctx,
                                      ResourceLocation guideId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ArcQuestPlayer data = getData(player);

        if (guideId == null) {
            int count = 0;
            for (ResourceLocation id : data.getUnlockedGuides()) {
                data.revokeGuideUnlock(id);
                count++;
            }
            persistAndSyncGuideState(player, data);
            success(ctx, "Revoked unlock for " + count + " guide(s) from " + player.getName().getString());
            return 1;
        }

        data.revokeGuideUnlock(guideId);
        persistAndSyncGuideState(player, data);
        success(ctx, "Revoked unlock for " + guideId + " (" + player.getName().getString() + ")");
        return 1;
    }

    private static int cmdResetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ArcQuestPlayer data = getData(player);

        int seenCount = 0;
        for (ResourceLocation id : data.getSeenGuides()) {
            data.clearGuideSeen(id);
            seenCount++;
        }
        int unlockCount = 0;
        for (ResourceLocation id : data.getUnlockedGuides()) {
            data.revokeGuideUnlock(id);
            unlockCount++;
        }
        persistAndSyncGuideState(player, data);
        success(ctx, "Reset " + seenCount + " seen + " + unlockCount + " unlock guide(s) for " + player.getName().getString());
        return 1;
    }
}
