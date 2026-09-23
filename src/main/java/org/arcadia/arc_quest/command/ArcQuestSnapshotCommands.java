package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationBundle;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationParser;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerMigrationApplyResult;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerMigrationIssue;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerMigrationReport;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerMigrationSeverity;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerMigrationValidator;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerRestoreService;
import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestPlayerSnapshotRef;
import org.arcadia.arc_quest.questplayer.snapshot.FileArcQuestPlayerSnapshotStore;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ArcQuestSnapshotCommands {
    private static final ArcQuestPlayerMigrationParser PARSER = new ArcQuestPlayerMigrationParser();
    private static final ArcQuestPlayerMigrationValidator VALIDATOR = new ArcQuestPlayerMigrationValidator();
    private static final ArcQuestPlayerRestoreService RESTORE_SERVICE = new ArcQuestPlayerRestoreService();

    private ArcQuestSnapshotCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("snapshot")
                .then(Commands.literal("list")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ArcQuestSnapshotCommands::cmdList)))
                .then(Commands.literal("dryrun")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("snapshot", StringArgumentType.word())
                                        .suggests(ArcQuestSnapshotCommands::suggestSnapshotNames)
                                        .executes(ArcQuestSnapshotCommands::cmdDryRun))))
                .then(Commands.literal("restore")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("snapshot", StringArgumentType.word())
                                        .suggests(ArcQuestSnapshotCommands::suggestSnapshotNames)
                                        .executes(ArcQuestSnapshotCommands::cmdRestore))))
                .then(Commands.literal("restore-file")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(ArcQuestSnapshotCommands::cmdRestoreFile))));
    }

    private static CompletableFuture<Suggestions> suggestSnapshotNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
            List<ArcQuestPlayerSnapshotRef> refs = FileArcQuestPlayerSnapshotStore.INSTANCE.listSnapshots(player.getUUID());
            return ArcQuestSuggestionUtil.suggest(refs.stream().map(ref -> ref.path().getFileName().toString()).toList(), builder, id -> ArcQuestSuggestionUtil.idTooltip("Snapshot", id));
        } catch (Exception ignored) {
            return Suggestions.empty();
        }
    }

    private static int cmdList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        List<ArcQuestPlayerSnapshotRef> refs = FileArcQuestPlayerSnapshotStore.INSTANCE.listSnapshots(player.getUUID());
        if (refs.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[ArcQuest] 未找到该玩家的恢复快照。"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[ArcQuest] 玩家 " + player.getGameProfile().getName() + " 的快照数量: " + refs.size()), false);
        for (int i = 0; i < refs.size(); i++) {
            ArcQuestPlayerSnapshotRef ref = refs.get(i);
            int index = i + 1;
            ctx.getSource().sendSuccess(() -> Component.literal("  [" + index + "] " + ref.path().getFileName() + " | reason=" + ref.reason() + " | at=" + ref.createdAt() + " | world=" + ref.worldHint()), false);
        }
        return refs.size();
    }

    private static int cmdDryRun(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String snapshotName = StringArgumentType.getString(ctx, "snapshot");
        ArcQuestPlayerSnapshotRef ref = resolveSnapshot(player, snapshotName);
        if (ref == null) {
            ctx.getSource().sendFailure(Component.literal("[ArcQuest] 未找到指定快照: " + snapshotName));
            return 0;
        }

        ArcQuestPlayerMigrationBundle bundle = loadBundle(ctx, ref.path());
        if (bundle == null) return 0;
        Set<String> sections = bundle.getMeta().getExportedSections();
        ArcQuestPlayerMigrationReport report = VALIDATOR.validate(player, bundle, sections);
        sendDryRunOutput(ctx, report, ref.path().getFileName().toString());
        return report.isBlocking() ? 0 : 1;
    }

    private static int cmdRestore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String snapshotName = StringArgumentType.getString(ctx, "snapshot");
        ArcQuestPlayerSnapshotRef ref = resolveSnapshot(player, snapshotName);
        if (ref == null) {
            ctx.getSource().sendFailure(Component.literal("[ArcQuest] 未找到指定快照: " + snapshotName));
            return 0;
        }

        ArcQuestPlayerMigrationBundle bundle = loadBundle(ctx, ref.path());
        if (bundle == null) return 0;
        return handleRestoreResult(ctx, bundle, RESTORE_SERVICE.restore(player, bundle, bundle.getMeta().getExportedSections()), ref.path().getFileName().toString());
    }

    private static int cmdRestoreFile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Path path = resolveExternalSnapshotPath(ctx, "path");
        if (path == null) {
            return 0;
        }

        ArcQuestPlayerMigrationBundle bundle = loadBundle(ctx, path);
        if (bundle == null) return 0;
        return handleRestoreResult(ctx, bundle, RESTORE_SERVICE.restore(player, bundle, bundle.getMeta().getExportedSections()), path.toString());
    }

    private static void sendDryRunOutput(CommandContext<CommandSourceStack> ctx, ArcQuestPlayerMigrationReport report, String sourceLabel) {
        ctx.getSource().sendSuccess(() -> Component.literal("[ArcQuest] Snapshot dry-run -> " + sourceLabel), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  blocking=" + report.isBlocking() + ", errors=" + report.count(ArcQuestPlayerMigrationSeverity.ERROR) + ", warnings=" + report.count(ArcQuestPlayerMigrationSeverity.WARNING) + ", infos=" + report.count(ArcQuestPlayerMigrationSeverity.INFO)), false);
        for (ArcQuestPlayerMigrationIssue issue : report.getIssues()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  [" + issue.severity() + "] " + issue.section() + " / " + issue.code() + " / " + issue.referenceId() + " -> " + issue.message()), false);
        }
    }

    private static ArcQuestPlayerMigrationBundle loadBundle(CommandContext<CommandSourceStack> ctx, Path path) {
        try {
            return PARSER.parse(FileArcQuestPlayerSnapshotStore.INSTANCE.loadSnapshot(path));
        } catch (RuntimeException failure) {
            ArcQuestLog.warn(ArcQuestLog.Category.PERSISTENCE, "Rejected player snapshot at {}", path, failure);
            ctx.getSource().sendFailure(Component.literal("[ArcQuest] 快照无法读取或格式无效，玩家数据未修改: " + path));
            return null;
        }
    }

    private static int handleRestoreResult(CommandContext<CommandSourceStack> ctx,
                                           ArcQuestPlayerMigrationBundle bundle,
                                           ArcQuestPlayerMigrationApplyResult result,
                                           String sourceLabel) {
        if (!result.isSuccess()) {
            ArcQuestPlayerMigrationReport report = result.getReport();
            ctx.getSource().sendFailure(Component.literal("[ArcQuest] 快照恢复被阻断。errors=" + report.count(ArcQuestPlayerMigrationSeverity.ERROR) + ", warnings=" + report.count(ArcQuestPlayerMigrationSeverity.WARNING)));
            for (ArcQuestPlayerMigrationIssue issue : report.getIssues()) {
                ctx.getSource().sendFailure(Component.literal("  [" + issue.severity() + "] " + issue.section() + " / " + issue.code() + " / " + issue.referenceId() + " -> " + issue.message()));
            }
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("[ArcQuest] 快照恢复成功: " + sourceLabel), true);
        for (ArcQuestPlayerMigrationIssue warning : result.getReport().getIssues(ArcQuestPlayerMigrationSeverity.WARNING)) {
            ctx.getSource().sendSuccess(() -> Component.literal("  [WARNING] " + warning.code() + " -> " + warning.message()), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("  sections=" + String.join(", ", bundle.getMeta().getExportedSections())), false);
        if (result.getPreRestoreSnapshot() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("  已生成 PRE_RESTORE 快照: " + result.getPreRestoreSnapshot().path().getFileName()), false);
        }
        return 1;
    }

    private static Path resolveExternalSnapshotPath(CommandContext<CommandSourceStack> ctx, String argumentName) {
        String rawPath = StringArgumentType.getString(ctx, argumentName).trim();
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            ctx.getSource().sendFailure(Component.literal("[ArcQuest] 外部快照文件不存在: " + path));
            return null;
        }
        return path;
    }

    private static ArcQuestPlayerSnapshotRef resolveSnapshot(ServerPlayer player, String snapshotName) {
        return FileArcQuestPlayerSnapshotStore.INSTANCE.listSnapshots(player.getUUID()).stream()
                .filter(ref -> ref.path().getFileName().toString().equals(snapshotName))
                .findFirst()
                .orElse(null);
    }
}
