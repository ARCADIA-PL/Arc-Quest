package org.arcadia.arc_quest.dialogue.runtime;

import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.core.execution.CoreRule;
import org.arcadia.arc_quest.core.time.TimeSnapshot;
import org.arcadia.arc_quest.api.event.dialogue.*;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.npc.runtime.NpcInteractionLeaseManager;
import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.quest.network.SyncObservability.Reason;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueSessionManager {

    public static final DialogueSessionManager INSTANCE = new DialogueSessionManager();
    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<String>> restoreNodeMap = new ConcurrentHashMap<>();

    private final DialoguePresentation presentation = new DialoguePresentation(this);
    private final Set<UUID> closingPlayers = new HashSet<>();
    private boolean shuttingDown;
    private final Set<UUID> startingPlayers = new HashSet<>();

    private DialogueSessionManager() {
    }

    public DialogueSession startDialogue(ServerPlayer player, DialogueTree tree) {
        return startDialogue(player, null, tree, new DialogueContext(), NpcInteractionPolicy.PARALLEL_PRIVATE);
    }

    @Nullable
    public DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                         String dialogueId, DialogueContext context) {
        return startDialogue(player, npcEntity, dialogueId, context, NpcInteractionPolicy.PARALLEL_PRIVATE);
    }

    @Nullable
    public DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                         String dialogueId, DialogueContext context,
                                         NpcInteractionPolicy interactionPolicy) {
        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            ArcQuestLog.error(ArcQuestLog.Category.DIALOGUE, "Dialogue tree '{}' not found.", dialogueId);
            return null;
        }
        return startDialogue(player, npcEntity, tree, context, interactionPolicy);
    }

    private DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                          DialogueTree tree, DialogueContext context,
                                          NpcInteractionPolicy interactionPolicy) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return null;
            if (shuttingDown || closingPlayers.contains(player.getUUID())) return null;
            if (!startingPlayers.add(player.getUUID())) return null;
            DialogueSession session;
            DialogueProgressStore progress;
            TimeSnapshot now;
            String namespace;
            String dialogueId = tree.dialogueId();
            int entityId = npcEntity != null ? npcEntity.getId() : -1;
            try {
                var data = ArcQuestPlayerManager.get(player);
                if (data == null) {
                    ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Missing quest data for player {}, cannot start dialogue '{}'",
                            player.getName().getString(), tree.dialogueId());
                    return null;
                }
                DialogueStartingEvent startingEvent = new DialogueStartingEvent(player, npcEntity, tree, context);
                MinecraftForge.EVENT_BUS.post(startingEvent);
                if (startingEvent.isCancelled()) {
                    ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE,
                            "Dialogue start cancelled by event. player={}, dialogue={}, reason={}",
                            player.getUUID(), tree.dialogueId(), startingEvent.getCancellationReason());
                    return null;
                }
                progress = data.getDialogueProgress();
                now = CoreProcessors.get().time().capture(player);

                session = new DialogueSession(player, tree, context, npcEntity);
                namespace = session.getNamespace();

                ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Resolved namespace='{}' for dialogue='{}'", namespace, dialogueId);

                DialogueStartContext startContext = new DialogueStartContext(
                        tree, progress, namespace, dialogueId, now);
                var startDecision = CoreProcessors.get().executions().decide(startContext, List.of(
                        CoreRule.require(contextValue -> contextValue.tree().repeatable()
                                        || !contextValue.progress().hasCompletedDialogue(
                                        contextValue.namespace(), contextValue.dialogueId()),
                                DialogueStartFailure.ALREADY_COMPLETED),
                        CoreRule.require(DialogueSessionManager::isDialogueCooldownReady,
                                DialogueStartFailure.ON_COOLDOWN)
                ));
                if (!startDecision.allowed()) {
                    if (startDecision.failure() == DialogueStartFailure.ALREADY_COMPLETED) {
                        ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "One-time dialogue '{}' already completed for player {}.",
                                dialogueId, player.getName().getString());
                    } else {
                        ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Dialogue '{}' on cooldown for player {}.",
                                dialogueId, player.getName().getString());
                    }
                    return null;
                }
            } finally {
                startingPlayers.remove(player.getUUID());
            }

            endDialogue(player);
            // 结束事件可以开启后继会话；外层启动不得覆盖它。
            if (sessions.containsKey(player.getUUID()) || !session.canContinue()) {
                session.end();
                return null;
            }
            if (npcEntity != null) {
                var leaseResult = NpcInteractionLeaseManager.INSTANCE.acquire(
                        EntityRef.of(npcEntity),
                        new PlayerSessionRef(player.getUUID(), PlayerSessionEpochManager.getOrCreate(player)),
                        interactionPolicy != null ? interactionPolicy : NpcInteractionPolicy.PARALLEL_PRIVATE,
                        player.server.getTickCount()
                );
                if (!leaseResult.acquired() || leaseResult.lease() == null) {
                    ArcQuestLog.info(ArcQuestLog.Category.DIALOGUE, "Dialogue start rejected. player={}, entityRef={}, policy={}, status={}",
                            player.getUUID(), EntityRef.of(npcEntity), interactionPolicy, leaseResult.status());
                    return null;
                }
                session.bindNpcLease(leaseResult.lease().leaseId());
            }
            try {
                sessions.put(player.getUUID(), session);

                ArcQuestLog.info(ArcQuestLog.Category.DIALOGUE, "Started dialogue '{}' for player '{}' (entityId={}, namespace={}).", tree.dialogueId(), player.getName().getString(), entityId, namespace);
                progress.recordDialogueVisit(
                        namespace, dialogueId, now.realTime(), now.gameTime(), now.dayTime());
                try (var operation = session.beginOperation()) {
                    sendNodeToClient(session, true, true);
                    if (!isCurrent(session)) return null;
                    SyncObservability.trace("dialogue", dialogueId, player.getName().getString(), SyncObservability.Stage.OPEN, Reason.DIALOGUE_OPEN);
                    MinecraftForge.EVENT_BUS.post(new DialogueStartedEvent(player, npcEntity, dialogueId));
                }
                return isCurrent(session) ? session : null;
            } catch (RuntimeException failure) {
                abortSession(session, failure);
                throw failure;
            } finally {
                finishEndedSession(session);
            }
        }
    }

    public void handleChoice(ServerPlayer player, int choiceIndex) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            DialogueSession session = sessions.get(player.getUUID());
            if (session == null || session.isEnded()) {
                ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "No active session for player {}", player.getName().getString());
                sendClose(player);
                return;
            }

            try (var operation = session.beginOperation()) {
                if (operation == null) return;
                SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                        SyncObservability.Stage.ACTION, Reason.DIALOGUE_CHOICE);

                DialogueNode currentNode = session.getCurrentNode();
                DialogueChoice selectedChoice = null;
                if (currentNode != null) {
                    var visibleChoices = session.getVisibleChoices();
                    if (choiceIndex >= 0 && choiceIndex < visibleChoices.size()) {
                        Entity npc = session.getEntity();
                        var choice = visibleChoices.get(choiceIndex);
                        selectedChoice = choice;
                        Component choiceText = session.processDialogueText(choice.text());

                        if (!isCurrentNode(session, currentNode)) return;
                        MinecraftForge.EVENT_BUS.post(new DialogueChoiceSelectedEvent(
                                player, npc, session.getTree().dialogueId(),
                                currentNode.nodeId(), choiceIndex, choice.choiceId(), choiceText.getString()
                        ));
                        if (!isCurrentNode(session, currentNode)) return;
                        presentation.appendTranscriptDelta(session, new S2CDialogueTranscriptDeltaPacket.Entry(
                                CoreProcessors.get().time().realTimeMillis(),
                                "player",
                                Component.literal(player.getName().getString()),
                                choiceText,
                                currentNode.nodeId(),
                                null,
                                choice.choiceId(),
                                choiceIndex
                        ));
                    }
                }

                DialogueNode next = session.chooseWithinCommand(choiceIndex);
                if (!ownsSession(session)) return;
                if (session.didExecuteChoice() && currentNode != null && selectedChoice != null) {
                    var data = ArcQuestPlayerManager.get(player);
                    if (data != null) {
                        DialogueMarkerTriggerService.triggerChoiceSelected(
                                player, data, session.getTree(), currentNode.nodeId(), selectedChoice);
                    }
                }
                if (!ownsSession(session)) return;
                if (session.isEnded() || next == null) {
                    finishSession(session);
                    SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                            SyncObservability.Stage.RESULT, Reason.DIALOGUE_CHOICE_END);
                    return;
                }
                sendNodeToClient(session, false, true);
                SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                        SyncObservability.Stage.RESULT, Reason.DIALOGUE_CHOICE_NEXT_NODE);
            } catch (RuntimeException failure) {
                abortSession(session, failure);
                throw failure;
            } finally {
                finishEndedSession(session);
            }
        }
    }

    public void handleChoice(ServerPlayer player, int choiceIndex, UUID sessionId, long expectedRevision,
                             String expectedNodeId, String expectedChoiceId, long playerSessionEpoch) {
        DialogueSession session = validateCommand(player, sessionId, expectedRevision, expectedNodeId, playerSessionEpoch);
        if (session == null) return;
        List<DialogueChoice> visibleChoices = session.getVisibleChoices();
        if (choiceIndex < 0 || choiceIndex >= visibleChoices.size()) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Invalid choice index. player={}, sessionId={}, revision={}, index={}",
                    player.getName().getString(), sessionId, expectedRevision, choiceIndex);
            return;
        }
        String actualChoiceId = visibleChoices.get(choiceIndex).choiceId();
        if (expectedChoiceId != null && !expectedChoiceId.isEmpty() && !Objects.equals(expectedChoiceId, actualChoiceId)) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Choice id mismatch. player={}, sessionId={}, revision={}, expected={}, actual={}",
                    player.getName().getString(), sessionId, expectedRevision, expectedChoiceId, actualChoiceId);
            return;
        }
        handleChoice(player, choiceIndex);
    }

    public void handleAutoAdvance(ServerPlayer player) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            DialogueSession session = sessions.get(player.getUUID());
            if (session == null || session.isEnded()) {
                sendClose(player);
                return;
            }
            try (var operation = session.beginOperation()) {
                if (operation == null) return;
                SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                        SyncObservability.Stage.ACTION, Reason.DIALOGUE_AUTO_ADVANCE);
                String fromNodeId = session.getCurrentNode() != null ? session.getCurrentNode().nodeId() : "";
                DialogueNode next = session.autoAdvanceWithinCommand();
                if (!ownsSession(session)) return;
                if (session.isEnded() || next == null) {
                    finishSession(session);
                    SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                            SyncObservability.Stage.RESULT, Reason.DIALOGUE_AUTO_ADVANCE_END);
                    return;
                }
                String toNodeId = next.nodeId();
                MinecraftForge.EVENT_BUS.post(new DialogueNodeAutoAdvancedEvent(
                        player,
                        session.getTree().dialogueId(),
                        fromNodeId,
                        toNodeId
                ));
                sendNodeToClient(session, false, true);
                SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                        SyncObservability.Stage.RESULT, Reason.DIALOGUE_AUTO_ADVANCE_NEXT_NODE);
            } catch (RuntimeException failure) {
                abortSession(session, failure);
                throw failure;
            } finally {
                finishEndedSession(session);
            }
        }
    }

    public void handleAutoAdvance(ServerPlayer player, UUID sessionId, long expectedRevision,
                                  String expectedNodeId, long playerSessionEpoch) {
        if (validateCommand(player, sessionId, expectedRevision, expectedNodeId, playerSessionEpoch) == null) return;
        handleAutoAdvance(player);
    }

    public void handleRestoreDialogue(ServerPlayer player) {
        try (var interaction = PlayerInteractionGuard.INSTANCE.enter(player.getUUID())) {
            if (interaction == null) return;
            DialogueSession session = getSession(player);
            if (session != null && !session.isEnded()) {
                try (var operation = session.beginOperation()) {
                    if (operation == null) return;
                    SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                            SyncObservability.Stage.ACTION, Reason.DIALOGUE_RESTORE);
                    String restoreNodeId = pollRestoreNodeId(player);
                    MinecraftForge.EVENT_BUS.post(new DialogueRestoreAttemptEvent(
                            player,
                            session.getTree().dialogueId(),
                            restoreNodeId == null ? "" : restoreNodeId
                    ));
                    if (!isCurrent(session)) return;
                    if (restoreNodeId != null && !restoreNodeId.isEmpty() && "__CURRENT__".equals(restoreNodeId)) {
                        sendNodeToClient(session, false, false);
                        SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                                SyncObservability.Stage.RESULT, Reason.DIALOGUE_RESTORE_NEXT_NODE);
                        return;
                    }
                    if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
                        DialogueNode targetNode = session.getTree().getNode(restoreNodeId);
                        if (targetNode != null) {
                            session.setCurrentNode(targetNode);
                        } else {
                            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Restore node '{}' not found for player {}", restoreNodeId, player.getName().getString());
                            MinecraftForge.EVENT_BUS.post(new DialogueRestoreFailedEvent(
                                    player,
                                    session.getTree().dialogueId(),
                                    restoreNodeId,
                                    DialogueRestoreFailedEvent.FailureReason.NODE_NOT_FOUND
                            ));
                        }
                    }
                    sendNodeToClient(session, false, false);
                    SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                            SyncObservability.Stage.RESULT, Reason.DIALOGUE_RESTORE_NEXT_NODE);
                } catch (RuntimeException failure) {
                    abortSession(session, failure);
                    throw failure;
                } finally {
                    finishEndedSession(session);
                }
            } else {
                clearRestoreNodeState(player);
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "No active session for player {}", player.getName().getString());
                MinecraftForge.EVENT_BUS.post(new DialogueRestoreFailedEvent(
                        player,
                        "",
                        "",
                        DialogueRestoreFailedEvent.FailureReason.NO_SESSION
                ));
                SyncObservability.trace("dialogue", "restore", player.getName().getString(),
                        SyncObservability.Stage.RESULT, Reason.DIALOGUE_RESTORE_NO_SESSION);
            }
        }
    }

    public void handleRestoreDialogue(ServerPlayer player, UUID sessionId, long expectedRevision,
                                      String expectedNodeId, long playerSessionEpoch) {
        if (validateCommand(player, sessionId, expectedRevision, expectedNodeId, playerSessionEpoch) == null) return;
        handleRestoreDialogue(player);
    }

    public void handleClose(ServerPlayer player, UUID sessionId, long expectedRevision, long playerSessionEpoch) {
        DialogueSession session = validateCommand(player, sessionId, expectedRevision, null, playerSessionEpoch);
        if (session != null) endDialogue(player);
    }

    @Nullable
    private DialogueSession validateCommand(ServerPlayer player, UUID sessionId, long expectedRevision,
                                            @Nullable String expectedNodeId, long playerSessionEpoch) {
        if (!PlayerSessionEpochManager.matches(player, playerSessionEpoch)) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Player session epoch mismatch. player={}, packetEpoch={}, serverEpoch={}",
                    player.getName().getString(), playerSessionEpoch, PlayerSessionEpochManager.getOrCreate(player));
            return null;
        }

        DialogueSession session = sessions.get(player.getUUID());
        if (session == null || session.isEnded()) {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "No active session. player={}, packetSessionId={}",
                    player.getName().getString(), sessionId);
            sendClose(player, sessionId, expectedRevision, playerSessionEpoch);
            return null;
        }
        if (!session.getSessionId().equals(sessionId)) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Session mismatch. player={}, packetSessionId={}, activeSessionId={}",
                    player.getName().getString(), sessionId, session.getSessionId());
            sendClose(player, sessionId, expectedRevision, playerSessionEpoch);
            return null;
        }
        if (session.getRevision() != expectedRevision) {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Revision mismatch. player={}, sessionId={}, packetRevision={}, serverRevision={}",
                    player.getName().getString(), sessionId, expectedRevision, session.getRevision());
            return null;
        }
        DialogueNode currentNode = session.getCurrentNode();
        if (expectedNodeId != null && !expectedNodeId.isEmpty()
                && (currentNode == null || !expectedNodeId.equals(currentNode.nodeId()))) {
            ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Node mismatch. player={}, sessionId={}, expectedNode={}, actualNode={}",
                    player.getName().getString(), sessionId, expectedNodeId,
                    currentNode != null ? currentNode.nodeId() : "<none>");
            return null;
        }
        touchLease(session);
        return session;
    }

    private static boolean isDialogueCooldownReady(DialogueStartContext context) {
        DialogueTree tree = context.tree();
        if (tree.cooldownSeconds() == 0 && tree.cooldownType() == CooldownType.NONE) return true;
        TimeSnapshot now = context.now();
        return !context.progress().isDialogueOnCooldown(
                context.namespace(), context.dialogueId(), tree.cooldownType(),
                (int) tree.cooldownSeconds(), tree.resetTimeTicks(),
                now.realTime(), now.gameTime(), now.dayTime());
    }

    public void endDialogue(ServerPlayer player) {
        DialogueSession session = sessions.get(player.getUUID());
        if (session != null) endDialogue(player, session);
    }

    boolean ownsSession(DialogueSession session) {
        return sessions.get(session.getPlayer().getUUID()) == session;
    }

    boolean isCurrent(DialogueSession session) {
        return ownsSession(session) && session.canContinue();
    }

    boolean isCurrentNode(DialogueSession session, DialogueNode node) {
        return isCurrent(session) && session.getCurrentNode() == node;
    }

    private void abortSession(DialogueSession session, RuntimeException failure) {
        session.end();
        DialogueSessionCleanup.run(failure, () -> finishEndedSession(session));
        ArcQuestLog.error(ArcQuestLog.Category.DIALOGUE,
                "Dialogue operation failed: player={}, dialogue={}, session={}",
                session.getPlayer().getUUID(), session.getTree().dialogueId(), session.getSessionId(), failure);
    }

    private void finishEndedSession(DialogueSession session) {
        if (session.isEnded()) finishSession(session);
    }

    private void finishSession(DialogueSession session) {
        if (!ownsSession(session)) return;
        RuntimeException failure = DialogueSessionCleanup.run(null, () -> endDialogue(session.getPlayer(), session));
        failure = DialogueSessionCleanup.run(failure, () -> {
            sendClose(session.getPlayer(), session.getSessionId(), session.getRevision(), session.getPlayerSessionEpoch());
        });
        if (failure != null) throw failure;
    }

    private void endDialogue(ServerPlayer player, DialogueSession session) {
        if (!ownsSession(session)) return;
        sessions.remove(player.getUUID());
        session.end();
        clearRestoreNodeState(player);
        DialogueSessionCleanup.close(session);
    }

    public boolean isInDialogue(ServerPlayer player) {
        DialogueSession session = sessions.get(player.getUUID());
        return session != null && !session.isEnded();
    }

    @Nullable
    public DialogueSession getSession(ServerPlayer player) {
        DialogueSession session = sessions.get(player.getUUID());
        return session != null && !session.isEnded() ? session : null;
    }

    public void onPlayerLogout(ServerPlayer player) {
        boolean ownsGuard = closingPlayers.add(player.getUUID());
        try {
            clearRestoreNodeState(player);
            endDialogue(player);
        } finally {
            try {
                NpcInteractionLeaseManager.INSTANCE.releasePlayer(player.getUUID());
            } finally {
                if (ownsGuard) closingPlayers.remove(player.getUUID());
            }
        }
    }

    public void heartbeat(ServerPlayer player) {
        DialogueSession session = getSession(player);
        if (session != null) touchLease(session);
    }

    /** 结束旧会话并通知客户端；恢复保护由调用者持有，结束事件无法重新打开交互。 */
    public void closeForPlayerRestore(ServerPlayer player) {
        if (!PlayerInteractionGuard.INSTANCE.isRestoring(player.getUUID())) {
            throw new IllegalStateException("Dialogue restore cleanup requires an active restore scope");
        }
        DialogueSession session = sessions.get(player.getUUID());
        RuntimeException cleanupFailure = null;
        try {
            onPlayerLogout(player);
        } catch (RuntimeException failure) {
            cleanupFailure = failure;
        }
        if (session != null) {
            session.end();
            try {
                sendClose(player, session.getSessionId(), session.getRevision(), session.getPlayerSessionEpoch());
            } catch (RuntimeException failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure);
            }
        }
        if (cleanupFailure != null) throw cleanupFailure;
    }

    public void shutdown() {
        shuttingDown = true;
        try {
            for (DialogueSession session : List.copyOf(sessions.values())) {
                try {
                    onPlayerLogout(session.getPlayer());
                } catch (RuntimeException failure) {
                    ArcQuestLog.error(ArcQuestLog.Category.DIALOGUE,
                            "Failed to close dialogue during shutdown: player={}, dialogue={}",
                            session.getPlayer().getUUID(), session.getTree().dialogueId(), failure);
                }
            }
        } finally {
            sessions.clear();
            closingPlayers.clear();
            restoreNodeMap.clear();
            NpcInteractionLeaseManager.INSTANCE.clear();
            shuttingDown = false;
        }
    }

    private void sendNodeToClient(DialogueSession session, boolean openMode, boolean triggerNodeEntry) {
        if (!isCurrent(session)) return;
        touchLease(session);
        presentation.sendNodeToClient(session, openMode, triggerNodeEntry);
    }

    private void touchLease(DialogueSession session) {
        if (session.getNpcLeaseId() != null) {
            NpcInteractionLeaseManager.INSTANCE.heartbeat(
                    session.getNpcLeaseId(), session.getPlayer().server.getTickCount());
        }
    }

    private void sendClose(ServerPlayer player) {
        ArcQuestNetwork.sendToPlayer(player, S2COpenDialoguePacket.close());
    }

    private void sendClose(ServerPlayer player, UUID sessionId, long revision, long playerSessionEpoch) {
        ArcQuestNetwork.sendToPlayer(player,
                S2COpenDialoguePacket.close(sessionId, revision, playerSessionEpoch));
    }

    public void setRestoreNodeId(ServerPlayer player, String restoreNodeId) {
        UUID playerId = player.getUUID();
        if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
            restoreNodeMap.compute(playerId, (id, stack) -> {
                Deque<String> restoreStack = stack != null ? stack : new ArrayDeque<>();
                if (!restoreStack.isEmpty() && restoreNodeId.equals(restoreStack.peek())) {
                    return restoreStack;
                }
                restoreStack.push(restoreNodeId);
                return restoreStack;
            });
        } else {
            restoreNodeMap.remove(playerId);
        }
    }

    @Nullable
    public String pollRestoreNodeId(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Deque<String> stack = restoreNodeMap.get(playerId);
        if (stack == null || stack.isEmpty()) {
            restoreNodeMap.remove(playerId);
            return null;
        }
        String restoreNodeId = stack.pop();
        if (stack.isEmpty()) {
            restoreNodeMap.remove(playerId);
        }
        return restoreNodeId;
    }

    private void clearRestoreNodeState(ServerPlayer player) {
        restoreNodeMap.remove(player.getUUID());
    }
    private enum DialogueStartFailure {
        ALREADY_COMPLETED,
        ON_COOLDOWN
    }

    private record DialogueStartContext(DialogueTree tree, DialogueProgressStore progress, String namespace,
                                        String dialogueId, TimeSnapshot now) {
    }
}
