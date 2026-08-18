package org.arcadia.arc_quest.quest.editor;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class QuestEditorSessionService {
    public static final QuestEditorSessionService INSTANCE = new QuestEditorSessionService();
    public static final int MAX_DOCUMENT_CHARS = 1_048_576;
    private final Map<UUID, QuestEditorSession> sessions = new HashMap<>();
    private final Map<ResourceLocation, UUID> locks = new HashMap<>();
    private ExecutorService ioExecutor = createExecutor();

    private static ExecutorService createExecutor() {
        return new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> {
        Thread thread = new Thread(runnable, "ArcQuest-Editor-IO");
        thread.setDaemon(true);
        return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    private QuestEditorSessionService() {
    }

    public void open(ServerPlayer player, ResourceLocation questId) {
        QuestAuthoringEntry entry = QuestAuthoringSnapshotRegistry.get(questId);
        if (entry != null) {
            openResolved(player, questId, entry);
            return;
        }

        player.sendSystemMessage(Component.literal("正在读取任务数据包: " + questId));
        CompletableFuture.supplyAsync(() -> QuestAuthoringEntryLoader.load(questId), ioExecutor)
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
            sendResult(player, false, baseRevision, "Editor session expired", 0L);
            return;
        }
        if (!player.hasPermissions(2) || baseRevision != session.revision() || json.length() > MAX_DOCUMENT_CHARS) {
            sendResult(player, false, session.revision(), "Save request rejected", 0L);
            return;
        }
        UUID requestId = UUID.randomUUID();
        if (!session.beginSave(requestId)) {
            sendResult(player, false, session.revision(), "A save is already running", 0L);
            return;
        }
        CompletableFuture.supplyAsync(() -> prepareSave(session, json), ioExecutor)
                .thenAccept(prepared -> player.getServer().execute(() -> applySave(player, session,
                        requestId, baseRevision, prepared)))
                .exceptionally(exception -> {
                    player.getServer().execute(() -> {
                        session.finishSave(requestId);
                        sendResult(player, false, session.revision(), "Save failed: " + rootMessage(exception), 0L);
                    });
                    return null;
                });
    }

    private PreparedSave prepareSave(QuestEditorSession session, String json) {
        QuestSpec candidate = QuestSpecJsonReader.read(json);
        if (candidate == null || !session.questId().toString().equals(candidate.id)) {
            throw new IllegalArgumentException("Quest ID does not match the editor session");
        }
        var report = new QuestSpecValidator().validate(candidate);
        if (report.hasErrors()) {
            String message = report.getIssues().stream().filter(issue -> issue.severity.name().equals("ERROR"))
                    .findFirst().map(issue -> issue.path + ": " + issue.message).orElse("Quest validation failed");
            throw new IllegalArgumentException(message);
        }
        new QuestSpecCompiler().compile(candidate);
        try {
            QuestDatapackWriter.Transaction transaction = new QuestDatapackWriter().replace(session.sourcePath(), candidate);
            return new PreparedSave(candidate, transaction, ArcQuestReloadCoordinator.INSTANCE.prepare());
        } catch (Exception exception) {
            throw new java.util.concurrent.CompletionException(exception);
        }
    }

    private void applySave(ServerPlayer player, QuestEditorSession session, UUID requestId,
                           long baseRevision, PreparedSave prepared) {
        if (!session.ownsSave(requestId) || sessions.get(player.getUUID()) != session
                || session.revision() != baseRevision) {
            rollback(prepared.transaction());
            session.finishSave(requestId);
            return;
        }
        ReloadSummary summary = ArcQuestReloadCoordinator.INSTANCE.apply(prepared.plan());
        if (!summary.applied()) {
            rollback(prepared.transaction());
            session.finishSave(requestId);
            sendResult(player, false, session.revision(), summary.formatForCommand(), summary.epoch());
            return;
        }
        try {
            prepared.transaction().commit();
            session.replaceDraft(prepared.candidate());
            session.finishSave(requestId);
            sendResult(player, true, session.revision(), "Quest saved and reloaded", summary.epoch());
        } catch (Exception exception) {
            session.finishSave(requestId);
            sendResult(player, false, session.revision(), "Commit failed: " + exception.getMessage(), summary.epoch());
        }
    }

    private void rollback(QuestDatapackWriter.Transaction transaction) {
        CompletableFuture.runAsync(() -> {
            try {
                transaction.rollback();
            } catch (Exception exception) {
                Arc_Quest.LOGGER.error("[QuestEditor] Rollback failed: {}", transaction.target(), exception);
            }
        }, ioExecutor);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    public void create(ServerPlayer player, ResourceLocation questId) {
        if (!player.hasPermissions(2)) return;
        QuestSpec spec = new QuestSpec();
        spec.id = questId.toString();
        spec.displayName.value = questId.getPath();
        spec.initialPhaseId = "start";
        var phase = new org.arcadia.arc_quest.quest.spec.PhaseSpec();
        phase.phaseId = "start";
        phase.displayName.value = "Start";
        var objective = new org.arcadia.arc_quest.quest.spec.ObjectiveSpec();
        objective.id = "objective_1";
        phase.objectives.add(objective);
        spec.phases.add(phase);
        createFromSpec(player, questId, spec);
    }

    public void duplicate(ServerPlayer player, ResourceLocation sourceId, ResourceLocation targetId) {
        if (!player.hasPermissions(2)) return;
        QuestAuthoringEntry source = QuestAuthoringSnapshotRegistry.get(sourceId);
        if (source == null) {
            player.sendSystemMessage(Component.literal("Source quest not found: " + sourceId));
            return;
        }
        QuestSpec copy = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(source.spec()));
        copy.id = targetId.toString();
        createFromSpec(player, targetId, copy);
    }

    private void createFromSpec(ServerPlayer player, ResourceLocation questId, QuestSpec spec) {
        Path target = DatapackPathResolver.resolveQuestFile(questId);
        CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(target)) throw new IllegalStateException("Quest file already exists");
                QuestDatapackWriter.Transaction transaction = new QuestDatapackWriter().replace(target, spec);
                var plan = ArcQuestReloadCoordinator.INSTANCE.prepare();
                player.getServer().execute(() -> finishFileMutation(player, transaction, plan,
                        () -> open(player, questId)));
            } catch (Exception exception) {
                player.getServer().execute(() -> player.sendSystemMessage(Component.literal("Create failed: " + rootMessage(exception))));
            }
        }, ioExecutor);
    }

    public void delete(ServerPlayer player, ResourceLocation questId) {
        if (!player.hasPermissions(2)) return;
        if (locks.containsKey(questId)) {
            player.sendSystemMessage(Component.literal("Quest is currently being edited: " + questId));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                QuestDatapackWriter.Transaction transaction = new QuestDatapackWriter()
                        .delete(DatapackPathResolver.resolveQuestFile(questId));
                var plan = ArcQuestReloadCoordinator.INSTANCE.prepare();
                player.getServer().execute(() -> finishFileMutation(player, transaction, plan,
                        () -> player.sendSystemMessage(Component.literal("Deleted quest: " + questId))));
            } catch (Exception exception) {
                player.getServer().execute(() -> player.sendSystemMessage(Component.literal("Delete failed: " + rootMessage(exception))));
            }
        }, ioExecutor);
    }

    private void finishFileMutation(ServerPlayer player, QuestDatapackWriter.Transaction transaction,
                                    ArcQuestReloadCoordinator.ReloadPlan plan, Runnable success) {
        ReloadSummary summary = ArcQuestReloadCoordinator.INSTANCE.apply(plan);
        if (!summary.applied()) {
            rollback(transaction);
            player.sendSystemMessage(Component.literal(summary.formatForCommand()));
            return;
        }
        try {
            transaction.commit();
            success.run();
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal("Transaction commit failed: " + exception.getMessage()));
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
        INSTANCE.ioExecutor.shutdownNow();
        INSTANCE.ioExecutor = createExecutor();
    }

    private record PreparedSave(QuestSpec candidate, QuestDatapackWriter.Transaction transaction,
                                ArcQuestReloadCoordinator.ReloadPlan plan) { }
}
