package org.arcadia.arc_quest.quest.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.reload.ArcQuestReloadCoordinator;
import org.arcadia.arc_quest.data.reload.ReloadSummary;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;
import org.arcadia.arc_quest.quest.spec.io.QuestDatapackWriter;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.quest.editor.network.S2COpenQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestEditorSessionService {
    public static final QuestEditorSessionService INSTANCE = new QuestEditorSessionService();
    public static final int MAX_DOCUMENT_CHARS = 1_048_576;
    private final Map<UUID, QuestEditorSession> sessions = new HashMap<>();
    private final Map<ResourceLocation, UUID> locks = new HashMap<>();

    private QuestEditorSessionService() {
    }

    public void open(ServerPlayer player, ResourceLocation questId) {
        QuestAuthoringEntry entry = QuestAuthoringSnapshotRegistry.get(questId);
        if (entry != null) {
            openResolved(player, questId, entry);
            return;
        }

        player.sendSystemMessage(Component.literal("正在读取任务数据包: " + questId));
        CompletableFuture.supplyAsync(() -> QuestAuthoringEntryLoader.load(questId))
                .thenAccept(result -> player.getServer().execute(() -> {
                    if (!result.successful()) {
                        player.sendSystemMessage(Component.literal(result.errorMessage()));
                        return;
                    }
                    String validationError = validateForEditing(result.entry());
                    if (validationError != null) {
                        player.sendSystemMessage(Component.literal(validationError));
                        return;
                    }
                    openResolved(player, questId, result.entry());
                }))
                .exceptionally(exception -> {
                    player.getServer().execute(() -> player.sendSystemMessage(
                            Component.literal("读取任务数据包失败: " + exception.getMessage())));
                    return null;
                });
    }

    private String validateForEditing(QuestAuthoringEntry entry) {
        var report = new QuestSpecValidator().validate(entry.spec());
        if (report.hasErrors()) {
            String message = report.getIssues().stream()
                    .filter(issue -> issue.severity.name().equals("ERROR"))
                    .findFirst()
                    .map(issue -> issue.path + ": " + issue.message)
                    .orElse("任务校验失败");
            return "任务数据校验失败（" + entry.sourcePath() + "）: " + message;
        }
        try {
            new QuestSpecCompiler().compile(entry.spec());
            return null;
        } catch (Exception exception) {
            return "任务编译失败（" + entry.sourcePath() + "）: " + exception.getMessage();
        }
    }

    private void openResolved(ServerPlayer player, ResourceLocation questId, QuestAuthoringEntry entry) {
        UUID owner = locks.get(questId);
        if (owner != null && !owner.equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("该任务正被其他玩家编辑: " + questId));
            return;
        }
        close(player.getUUID());
        QuestSpec copy = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(entry.spec()));
        QuestEditorSession session = new QuestEditorSession(player.getUUID(), questId, entry.sourcePath(), copy);
        sessions.put(player.getUUID(), session);
        locks.put(questId, player.getUUID());
        ArcQuestNetwork.sendQuestEditorOpen(player, new S2COpenQuestEditorPacket(session.sessionId(), questId,
                DatapackPathResolver.resolveQuestsDir().relativize(entry.sourcePath()).toString().replace('\\', '/'),
                session.revision(), ArcQuestReloadCoordinator.INSTANCE.getCommittedEpoch(),
                QuestSpecJsonWriter.write(copy)));
    }

    public void save(ServerPlayer player, UUID sessionId, long baseRevision, String json) {
        QuestEditorSession session = sessions.get(player.getUUID());
        if (session == null || !session.sessionId().equals(sessionId)) {
            sendResult(player, false, baseRevision, "编辑会话已失效", 0L);
            return;
        }
        if (baseRevision != session.revision()) {
            sendResult(player, false, session.revision(), "文档版本已变化，请重新打开编辑器", 0L);
            return;
        }
        if (json.length() > MAX_DOCUMENT_CHARS) {
            sendResult(player, false, session.revision(), "任务文档超过大小限制", 0L);
            return;
        }
        Path target = session.sourcePath();
        Path backup = target.resolveSibling(target.getFileName() + ".editor-backup");
        boolean hadOriginal = Files.exists(target);
        try {
            QuestSpec candidate = QuestSpecJsonReader.read(json);
            if (candidate == null || !session.questId().toString().equals(candidate.id)) {
                sendResult(player, false, session.revision(), "Quest ID 与编辑会话不一致", 0L);
                return;
            }
            var report = new QuestSpecValidator().validate(candidate);
            if (report.hasErrors()) {
                String message = report.getIssues().stream().filter(issue -> issue.severity.name().equals("ERROR"))
                        .findFirst().map(issue -> issue.path + ": " + issue.message).orElse("任务校验失败");
                sendResult(player, false, session.revision(), message, 0L);
                return;
            }
            new QuestSpecCompiler().compile(candidate);
            if (hadOriginal) Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            new QuestDatapackWriter().write(target, candidate);
            ReloadSummary summary = ArcQuestReloadCoordinator.INSTANCE.apply(ArcQuestReloadCoordinator.INSTANCE.prepare());
            if (!summary.applied()) {
                if (Files.exists(backup)) Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                else if (!hadOriginal) Files.deleteIfExists(target);
                sendResult(player, false, session.revision(), summary.formatForCommand(), summary.epoch());
                return;
            }
            Files.deleteIfExists(backup);
            session.replaceDraft(candidate);
            sendResult(player, true, session.revision(), "任务已保存并完成热重载", summary.epoch());
        } catch (Exception exception) {
            try {
                if (Files.exists(backup)) Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                else if (!hadOriginal) Files.deleteIfExists(target);
            }
            catch (Exception ignored) { }
            sendResult(player, false, session.revision(), "保存失败: " + exception.getMessage(), 0L);
        }
    }

    public void close(UUID playerId) {
        QuestEditorSession removed = sessions.remove(playerId);
        if (removed != null) locks.remove(removed.questId(), playerId);
    }

    private void sendResult(ServerPlayer player, boolean success, long revision, String message, long epoch) {
        ArcQuestNetwork.sendQuestEditorResult(player, new S2CQuestEditorResultPacket(success, revision, epoch, message));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INSTANCE.close(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        INSTANCE.sessions.clear();
        INSTANCE.locks.clear();
    }
}
