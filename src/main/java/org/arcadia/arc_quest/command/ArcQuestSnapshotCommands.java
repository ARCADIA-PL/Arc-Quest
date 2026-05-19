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
import org.arcadia.arc_quest.questplayer.restore.*;
import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestPlayerSnapshotRef;
import org.arcadia.arc_quest.questplayer.snapshot.FileArcQuestPlayerSnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ArcQuestSnapshotCommands {
    private static final ArcQuestPlayerMigrationParser PARSER = new ArcQuestPlayerMigrationParser();
    private static final ArcQuestPlayerMigrationValidator VALIDATOR = new ArcQuestPlayerMigrationValidator();
    private static final ArcQuestPlayerRestoreService RESTORE_SERVICE = new ArcQuestPlayerRestoreService();
    private static final Set<String> VALID_SECTIONS = Set.of("flagsVars","questState","dialogue","trade","gacha","markers");
    private ArcQuestSnapshotCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> d) {
        return Commands.literal("snapshot")
            .then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player()).executes(ArcQuestSnapshotCommands::cmdList)))
            .then(Commands.literal("inspect").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("snapshot", StringArgumentType.word()).suggests(ArcQuestSnapshotCommands::suggestSnapshotNames).executes(ArcQuestSnapshotCommands::cmdInspect))))
            .then(Commands.literal("inspect-file").then(Commands.argument("path", StringArgumentType.greedyString()).executes(ArcQuestSnapshotCommands::cmdInspectFile)))
            .then(Commands.literal("dryrun").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("snapshot", StringArgumentType.word()).suggests(ArcQuestSnapshotCommands::suggestSnapshotNames).executes(c -> cmdDryRun(c, null)).then(Commands.literal("sections").then(Commands.argument("section_list", StringArgumentType.word()).executes(c -> cmdDryRun(c, StringArgumentType.getString(c, "section_list"))))))))
            .then(Commands.literal("dryrun-file").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("path", StringArgumentType.greedyString()).executes(c -> cmdDryRunFile(c, null)))).then(Commands.argument("player", EntityArgument.player()).then(Commands.literal("sections").then(Commands.argument("section_list", StringArgumentType.word()).then(Commands.argument("path", StringArgumentType.greedyString()).executes(c -> cmdDryRunFile(c, StringArgumentType.getString(c, "section_list"))))))))
            .then(Commands.literal("restore").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("snapshot", StringArgumentType.word()).suggests(ArcQuestSnapshotCommands::suggestSnapshotNames).executes(c -> cmdRestore(c, null)).then(Commands.literal("sections").then(Commands.argument("section_list", StringArgumentType.word()).executes(c -> cmdRestore(c, StringArgumentType.getString(c, "section_list"))))))))
            .then(Commands.literal("restore-file").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("path", StringArgumentType.greedyString()).executes(c -> cmdRestoreFile(c, null)))).then(Commands.argument("player", EntityArgument.player()).then(Commands.literal("sections").then(Commands.argument("section_list", StringArgumentType.word()).then(Commands.argument("path", StringArgumentType.greedyString()).executes(c -> cmdRestoreFile(c, StringArgumentType.getString(c, "section_list"))))))));
    }

    private static CompletableFuture<Suggestions> suggestSnapshotNames(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        try {
            ServerPlayer p = EntityArgument.getPlayer(c, "player");
            List<ArcQuestPlayerSnapshotRef> refs = FileArcQuestPlayerSnapshotStore.INSTANCE.listSnapshots(p.getUUID());
            return SharedSuggestionProvider.suggest(refs.stream().map(r -> r.path().getFileName().toString()), b);
        } catch (Exception ignored) { return Suggestions.empty(); }
    }

    private static int cmdList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player"); List<ArcQuestPlayerSnapshotRef> refs = FileArcQuestPlayerSnapshotStore.INSTANCE.listSnapshots(p.getUUID());
        if (refs.isEmpty()) { c.getSource().sendFailure(Component.literal("[ArcQuest] 未找到该玩家的恢复快照。")); return 0; }
        c.getSource().sendSuccess(() -> Component.literal("[ArcQuest] 玩家 " + p.getGameProfile().getName() + " 的快照数量: " + refs.size()), false);
        for (int i = 0; i < refs.size(); i++) { ArcQuestPlayerSnapshotRef r = refs.get(i); int idx = i + 1; c.getSource().sendSuccess(() -> Component.literal("  [" + idx + "] " + r.path().getFileName() + " | reason=" + r.reason() + " | at=" + r.createdAt() + " | world=" + r.worldHint()), false); }
        return refs.size();
    }

    private static int cmdInspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player"); String name = StringArgumentType.getString(c, "snapshot"); ArcQuestPlayerSnapshotRef r = resolveSnapshot(p, name);
        if (r == null) { c.getSource().sendFailure(Component.literal("[ArcQuest] 未找到指定快照: " + name)); return 0; }
        sendInspectOutput(c, PARSER.parse(FileArcQuestPlayerSnapshotStore.INSTANCE.loadSnapshot(r)), r.path().getFileName().toString()); return 1;
    }

    private static int cmdInspectFile(CommandContext<CommandSourceStack> c) { Path p = resolveExternalSnapshotPath(c, "path"); if (p == null) return 0; sendInspectOutput(c, PARSER.parse(FileArcQuestPlayerSnapshotStore.INSTANCE.loadSnapshot(p)), p.toString()); return 1; }

    private static int cmdDryRun(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player"); String name = StringArgumentType.getString(c, "snapshot"); ArcQuestPlayerSnapshotRef r = resolveSnapshot(p, name);
        if (r == null) { c.getSource().sendFailure(Component.literal("[ArcQuest] 未找到指定快照: " + name)); return 0; }
        ArcQuestPlayerMigrationBundle b = PARSER.parse(FileArcQuestPlayerSnapshotStore.INSTANCE.loadSnapshot(r)); Set<String> s = resolveSections(c, b, raw); if (s == null) return 0;
        ArcQuestPlayerMigrationReport report = VALIDATOR.validate(p, b, s); sendDryRunOutput(c, report, r.path().getFileName().toString(), s); return report.isBlocking() ? 0 : 1;
    }

    private static int cmdDryRunFile(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player"); Path path = resolveExternalSnapshotPath(c, "path"); if (path == null) return 0;
        ArcQuestPlayerMigrationBundle b = PARSER.parse(FileArcQuestPlayerSnapshotStore.INSTANCE.loadSnapshot(path)); Set<String> s = resolveSections(c, b, raw); if (s == null) return 0;
        ArcQuestPlayerMigrationReport report = VALIDATOR.validate(p, b, s); sendDryRunOutput(c, report, path.toString(), s); return report.isBlocking() ? 0 : 1;
    }

    private static int cmdRestore(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player"); String name = StringArgumentType.getString(c, "snapshot"); ArcQuestPlayerSnapshotRef r = resolveSnapshot(p, name);
        if (r == null) { c.getSource().sendFailure(Component.literal("[ArcQuest] 未找到指定快照: " + name)); return 0; }
        ArcQuestPlayerMigrationBundle b = PARSER.parse(FileArcQuestPlayerSnapshotStore.INSTANCE.loadSnapshot(r)); Set<String> s = resolveSections(c, b, raw); if (s == null) return 0;
        return handleRestoreResult(c, RESTORE_SERVICE.restore(p, b, s), r.path().getFileName().toString(), s);
    }

    private static int cmdRestoreFile(CommandContext<CommandSourceStack> c, String raw) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player"); Path path = resolveExternalSnapshotPath(c, "path"); if (path == null) return 0;
        ArcQuestPlayerMigrationBundle b = PARSER.parse(FileArcQuestPlayerSnapshotStore.INSTANCE.loadSnapshot(path)); Set<String> s = resolveSections(c, b, raw); if (s == null) return 0;
        return handleRestoreResult(c, RESTORE_SERVICE.restore(p, b, s), path.toString(), s);
    }

    private static void sendInspectOutput(CommandContext<CommandSourceStack> c, ArcQuestPlayerMigrationBundle b, String src) {
        c.getSource().sendSuccess(() -> Component.literal("[ArcQuest] Snapshot inspect -> " + src), false);
        c.getSource().sendSuccess(() -> Component.literal("  format=" + b.getFormat() + ", version=" + b.getVersion()), false);
        c.getSource().sendSuccess(() -> Component.literal("  player=" + b.getMeta().getSourcePlayerName() + " (" + b.getMeta().getSourcePlayerUuid() + ")"), false);
        c.getSource().sendSuccess(() -> Component.literal("  world=" + b.getMeta().getSourceWorldHint() + ", exportedAt=" + b.getMeta().getExportedAt()), false);
        c.getSource().sendSuccess(() -> Component.literal("  sections=" + String.join(", ", b.getMeta().getExportedSections())), false);
    }

    private static void sendDryRunOutput(CommandContext<CommandSourceStack> c, ArcQuestPlayerMigrationReport r, String src, Set<String> s) {
        c.getSource().sendSuccess(() -> Component.literal("[ArcQuest] Snapshot dry-run -> " + src), false);
        c.getSource().sendSuccess(() -> Component.literal("  selectedSections=" + String.join(", ", s)), false);
        c.getSource().sendSuccess(() -> Component.literal("  blocking=" + r.isBlocking() + ", errors=" + r.count(ArcQuestPlayerMigrationSeverity.ERROR) + ", warnings=" + r.count(ArcQuestPlayerMigrationSeverity.WARNING) + ", infos=" + r.count(ArcQuestPlayerMigrationSeverity.INFO)), false);
        for (ArcQuestPlayerMigrationIssue i : r.getIssues()) c.getSource().sendSuccess(() -> Component.literal("  [" + i.severity() + "] " + i.section() + " / " + i.code() + " / " + i.referenceId() + " -> " + i.message()), false);
    }

    private static int handleRestoreResult(CommandContext<CommandSourceStack> c, ArcQuestPlayerMigrationApplyResult r, String src, Set<String> s) {
        if (!r.isSuccess()) {
            ArcQuestPlayerMigrationReport report = r.getReport();
            c.getSource().sendFailure(Component.literal("[ArcQuest] 快照恢复被阻断。errors=" + report.count(ArcQuestPlayerMigrationSeverity.ERROR) + ", warnings=" + report.count(ArcQuestPlayerMigrationSeverity.WARNING)));
            for (ArcQuestPlayerMigrationIssue i : report.getIssues()) c.getSource().sendFailure(Component.literal("  [" + i.severity() + "] " + i.section() + " / " + i.code() + " / " + i.referenceId() + " -> " + i.message()));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("[ArcQuest] 快照恢复成功: " + src), true);
        c.getSource().sendSuccess(() -> Component.literal("  selectedSections=" + String.join(", ", s)), false);
        if (r.getPreRestoreSnapshot() != null) c.getSource().sendSuccess(() -> Component.literal("  已生成 PRE_RESTORE 快照: " + r.getPreRestoreSnapshot().path().getFileName()), false);
        return 1;
    }

    private static Set<String> resolveSections(CommandContext<CommandSourceStack> c, ArcQuestPlayerMigrationBundle b, String raw) {
        if (raw == null || raw.isBlank()) return b.getMeta().getExportedSections();
        Set<String> out = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String s = token.trim(); if (s.isEmpty()) continue;
            if (!VALID_SECTIONS.contains(s)) { c.getSource().sendFailure(Component.literal("[ArcQuest] 未知 section: " + s + "。可用值: " + String.join(", ", VALID_SECTIONS))); return null; }
            if (!b.getMeta().getExportedSections().contains(s)) { c.getSource().sendFailure(Component.literal("[ArcQuest] 快照未导出 section: " + s)); return null; }
            out.add(s);
        }
        if (out.isEmpty()) { c.getSource().sendFailure(Component.literal("[ArcQuest] section_list 不能为空。")); return null; }
        return out;
    }

    private static Path resolveExternalSnapshotPath(CommandContext<CommandSourceStack> c, String arg) {
        String raw = StringArgumentType.getString(c, arg).trim(); Path p = Path.of(raw).toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) { c.getSource().sendFailure(Component.literal("[ArcQuest] 外部快照文件不存在: " + p)); return null; }
        return p;
    }

    private static ArcQuestPlayerSnapshotRef resolveSnapshot(ServerPlayer p, String name) {
        return FileArcQuestPlayerSnapshotStore.INSTANCE.listSnapshots(p.getUUID()).stream().filter(r -> r.path().getFileName().toString().equals(name)).findFirst().orElse(null);
    }
}