package org.arcadia.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.api.event.dialogue.*;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.capability.DialogueNpcPatch;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.util.TimeSanitizer;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.quest.network.SyncObservability.Reason;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogueSessionManager {

    public static final DialogueSessionManager INSTANCE = new DialogueSessionManager();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<String>> restoreNodeMap = new ConcurrentHashMap<>();

    private final Map<UUID, List<S2CDialogueTranscriptDeltaPacket.Entry>> transcriptMap = new ConcurrentHashMap<>();

    private DialogueSessionManager() {
    }

    public DialogueSession startDialogue(ServerPlayer player, DialogueTree tree) {
        return startDialogue(player, null, tree, new DialogueContext());
    }

    @Nullable
    public DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                         String dialogueId, DialogueContext context) {
        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            LOGGER.error("[Dialogue] Dialogue tree '{}' not found.", dialogueId);
            return null;
        }
        return startDialogue(player, npcEntity, tree, context);
    }

    private DialogueSession startDialogue(ServerPlayer player, @Nullable Entity npcEntity,
                                          DialogueTree tree, DialogueContext context) {
        var cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) {
            LOGGER.warn("[Dialogue] Missing quest capability for player {}, cannot start dialogue '{}'",
                    player.getName().getString(), tree.dialogueId());
            return null;
        }
        String dialogueId = tree.dialogueId();
        var progress = cap.getDialogueProgress();
        long nowReal = TimeSanitizer.getCurrentRealTime();
        long nowGame = TimeSanitizer.getCurrentGameTime(player);
        long nowDayTime = TimeSanitizer.getCurrentDayTime(player);

        int entityId = npcEntity != null ? npcEntity.getId() : -1;
        DialogueSession session = new DialogueSession(player, tree, context, entityId);
        String namespace = session.getNamespace();

        LOGGER.debug("[Dialogue] Resolved namespace='{}' for dialogue='{}'", namespace, dialogueId);

        if (!tree.repeatable() && progress.hasCompletedDialogue(namespace, dialogueId)) {
            LOGGER.debug("[Dialogue] One-time dialogue '{}' already completed for player {}.", dialogueId, player.getName().getString());
            return null;
        }

        if (tree.cooldownSeconds() != 0 || tree.cooldownType() != CooldownType.NONE) {
            boolean onCooldown = progress.isDialogueOnCooldown(namespace, dialogueId, tree.cooldownType(), (int) tree.cooldownSeconds(), tree.resetTimeTicks(), nowReal, nowGame, nowDayTime);
            if (onCooldown) {
                LOGGER.debug("[Dialogue] Dialogue '{}' on cooldown for player {}.", dialogueId, player.getName().getString());
                return null;
            }
        }

        endDialogue(player);
        sessions.put(player.getUUID(), session);
        transcriptMap.put(player.getUUID(), new ArrayList<>());

        if (npcEntity instanceof IDialogueNpc) {
            DialogueNpcPatch.get(npcEntity).setConversing(player);
        }

        LOGGER.info("[Dialogue] Started dialogue '{}' for player '{}' (entityId={}, namespace={}).", tree.dialogueId(), player.getName().getString(), entityId, namespace);
        progress.recordDialogueVisit(namespace, dialogueId, nowReal, nowGame, nowDayTime);
        sendNodeToClient(session, true);
        SyncObservability.trace("dialogue", dialogueId, player.getName().getString(), SyncObservability.Stage.OPEN, Reason.DIALOGUE_OPEN);
        MinecraftForge.EVENT_BUS.post(new DialogueStartedEvent(player, npcEntity, dialogueId));
        return session;
    }

    public void handleChoice(ServerPlayer player, int choiceIndex) {
        DialogueSession session = sessions.get(player.getUUID());
        if (session == null || session.isEnded()) {
            LOGGER.debug("[Dialogue] No active session for player {}", player.getName().getString());
            sendClose(player);
            return;
        }

        SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                SyncObservability.Stage.ACTION, Reason.DIALOGUE_CHOICE);

        DialogueNode currentNode = session.getCurrentNode();
        if (currentNode != null) {
            var visibleChoices = session.getVisibleChoices();
            if (choiceIndex >= 0 && choiceIndex < visibleChoices.size()) {
                Entity npc = session.getEntityId() != -1 ? player.level().getEntity(session.getEntityId()) : null;
                var choice = visibleChoices.get(choiceIndex);
                String choiceText = session.processDialogueText(choice.text()).getString();

                MinecraftForge.EVENT_BUS.post(new DialogueChoiceSelectedEvent(
                        player, npc, session.getTree().dialogueId(),
                        currentNode.nodeId(), choiceIndex, choice.choiceId(), choiceText
                ));

                appendTranscriptDelta(session, new S2CDialogueTranscriptDeltaPacket.Entry(
                        System.currentTimeMillis(),
                        "player",
                        player.getName().getString(),
                        choiceText,
                        currentNode.nodeId(),
                        null,
                        choice.choiceId(),
                        choiceIndex
                ));
            }
        }

        DialogueNode next = session.choose(choiceIndex);
        if (session.isEnded() || next == null) {
            endDialogue(player);
            sendClose(player);
            SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                    SyncObservability.Stage.RESULT, Reason.DIALOGUE_CHOICE_END);
            return;
        }
        sendNodeToClient(session, false);
        SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                SyncObservability.Stage.RESULT, Reason.DIALOGUE_CHOICE_NEXT_NODE);
    }

    public void handleAutoAdvance(ServerPlayer player) {
        DialogueSession session = sessions.get(player.getUUID());
        if (session == null || session.isEnded()) {
            sendClose(player);
            return;
        }
        SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                SyncObservability.Stage.ACTION, Reason.DIALOGUE_AUTO_ADVANCE);
        String fromNodeId = session.getCurrentNode() != null ? session.getCurrentNode().nodeId() : "";
        DialogueNode next = session.autoAdvance();
        if (session.isEnded() || next == null) {
            endDialogue(player);
            sendClose(player);
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
        sendNodeToClient(session, false);
        SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                SyncObservability.Stage.RESULT, Reason.DIALOGUE_AUTO_ADVANCE_NEXT_NODE);
    }

    public void handleRestoreDialogue(ServerPlayer player) {
        DialogueSession session = getSession(player);
        if (session != null && !session.isEnded()) {
            SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                    SyncObservability.Stage.ACTION, Reason.DIALOGUE_RESTORE);
            String restoreNodeId = pollRestoreNodeId(player);
            MinecraftForge.EVENT_BUS.post(new DialogueRestoreAttemptEvent(
                    player,
                    session.getTree().dialogueId(),
                    restoreNodeId == null ? "" : restoreNodeId
            ));
            if (restoreNodeId != null && !restoreNodeId.isEmpty() && "__CURRENT__".equals(restoreNodeId)) {
                sendNodeToClient(session, false);
                SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                        SyncObservability.Stage.RESULT, Reason.DIALOGUE_RESTORE_NEXT_NODE);
                return;
            }
            if (restoreNodeId != null && !restoreNodeId.isEmpty()) {
                DialogueNode targetNode = session.getTree().getNode(restoreNodeId);
                if (targetNode != null) {
                    session.setCurrentNode(targetNode);
                } else {
                    LOGGER.warn("[Dialogue] Restore node '{}' not found for player {}", restoreNodeId, player.getName().getString());
                    MinecraftForge.EVENT_BUS.post(new DialogueRestoreFailedEvent(
                            player,
                            session.getTree().dialogueId(),
                            restoreNodeId,
                            DialogueRestoreFailedEvent.FailureReason.NODE_NOT_FOUND
                    ));
                }
            }
            sendNodeToClient(session, false);
            SyncObservability.trace("dialogue", session.getTree().dialogueId(), player.getName().getString(),
                    SyncObservability.Stage.RESULT, Reason.DIALOGUE_RESTORE_NEXT_NODE);
        } else {
            clearRestoreNodeState(player);
            LOGGER.warn("[Dialogue] No active session for player {}", player.getName().getString());
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

    public void endDialogue(ServerPlayer player) {
        DialogueSession session = sessions.remove(player.getUUID());
        if (session == null) return;

        transcriptMap.remove(player.getUUID());
        String dialogueId = session.getTree().dialogueId();
        Entity npcEntity = null;
        if (session.getEntityId() != -1) {
            npcEntity = player.level().getEntity(session.getEntityId());
            if (npcEntity instanceof IDialogueNpc) {
                DialogueNpcPatch.get(npcEntity).clearConversing();
            }
        }
        if (!session.isEnded()) {
            session.end();
        }
        if (npcEntity != null) {
            MinecraftForge.EVENT_BUS.post(new DialogueEndedEvent(player, npcEntity, dialogueId));
        }
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
        clearRestoreNodeState(player);
        endDialogue(player);
    }

    private void sendNodeToClient(DialogueSession session, boolean openMode) {
        ServerPlayer player = session.getPlayer();
        DialogueNode node = session.getCurrentNode();
        if (node == null) return;

        if (openMode) {
            ArcQuestNetwork.sendTranscriptSnapshotPacket(
                    session.getPlayer(),
                    new S2CDialogueTranscriptSnapshotPacket(
                            session.getSessionId(),
                            transcriptMap.getOrDefault(session.getPlayer().getUUID(), List.of())
                    )
            );
        }

        String speaker = "";
        if (node.speaker() != null) {
            speaker = session.processDialogueText(node.speaker()).getString();
        }

        if (speaker == null || speaker.isBlank()) {
            Entity npcEntity = session.getEntityId() != -1 ? player.level().getEntity(session.getEntityId()) : null;
            if (npcEntity instanceof IDialogueNpc dialogueNpc) {
                Component display = dialogueNpc.getDialogueDisplayName();
                if (display != null) {
                    String s = display.getString();
                    if (s != null && !s.isBlank()) {
                        speaker = s;
                    }
                }
            }
        }

        if (speaker == null || speaker.isBlank()) {
            Object fallback = session.getContext().get("defaultNpc");
            if (fallback instanceof String s && !s.isBlank()) {
                speaker = s;
            }
        }

        if (speaker == null) {
            speaker = "";
        }
        var cap = QuestCapabilityProvider.getOrNull(session.getPlayer());
        DialogueProgressStore progress = cap != null ? cap.getDialogueProgress() : null;
        Entity npc = session.getEntityId() != -1 ? player.level().getEntity(session.getEntityId()) : null;
        DialogueEvalContext ctx = DialogueEvalContext.of(session.getPlayer(), npc, session.getNamespace(), progress);
        ConditionalTextEvaluator.SayIfResult sayIfResult = ConditionalTextEvaluator.evaluateWithIndex(ctx, node.conditionalTexts(), session.processDialogueText(node.text()).getString());
        String text = session.processText(sayIfResult.text);
        SoundEvent matchedSaySound = sayIfResult.sound;
        String selectedSayId = sayIfResult.sayId;

        appendTranscriptDelta(session, new S2CDialogueTranscriptDeltaPacket.Entry(
                System.currentTimeMillis(),
                "npc",
                speaker,
                text,
                node.nodeId(),
                selectedSayId,
                null,
                -1
        ));

        ResourceLocation saySoundId = matchedSaySound != null ? ForgeRegistries.SOUND_EVENTS.getKey(matchedSaySound) : null;
        MinecraftForge.EVENT_BUS.post(new DialogueNodeStartedEvent(player, npc, session.getTree().dialogueId(), node.nodeId(), selectedSayId, text, matchedSaySound, saySoundId));

        var visibleChoices = session.getVisibleChoices();
        String[] choiceTexts = new String[visibleChoices.size()];
        ResourceLocation[] choiceSounds = new ResourceLocation[visibleChoices.size()];
        String[] choiceIds = new String[visibleChoices.size()];
        for (int i = 0; i < visibleChoices.size(); i++) {
            choiceTexts[i] = session.processDialogueText(visibleChoices.get(i).text()).getString();
            var sound = visibleChoices.get(i).selectSound();
            if (sound != null) choiceSounds[i] = ForgeRegistries.SOUND_EVENTS.getKey(sound);
            choiceIds[i] = visibleChoices.get(i).choiceId();
        }

        var cooldownData = session.getChoiceCooldownRawData();
        S2COpenDialoguePacket packet = new S2COpenDialoguePacket(
                session.getTree().dialogueId(), node.nodeId(), speaker, text, choiceTexts,
                node.isTerminal(), !node.hasChoices() && node.autoNextId() != null, node.delayMs(),
                session.getEntityId(), cooldownData.lastSelectTimes(), cooldownData.purchaseGameTimes(),
                cooldownData.purchaseDayTimes(), cooldownData.cooldownTypes(), cooldownData.cooldownValues(),
                cooldownData.resetTimeTicks(), choiceSounds, saySoundId, selectedSayId, choiceIds
        );
        if (!openMode) {
            packet = S2COpenDialoguePacket.updateFrom(packet);
        }
        ArcQuestNetwork.sendToPlayer(player, packet);
    }

    private void sendClose(ServerPlayer player) {
        ArcQuestNetwork.sendToPlayer(player, S2COpenDialoguePacket.close());
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

    private void appendTranscriptDelta(DialogueSession session, S2CDialogueTranscriptDeltaPacket.Entry entry) {
        UUID playerId = session.getPlayer().getUUID();
        transcriptMap.computeIfAbsent(playerId, k -> new ArrayList<>()).add(entry);
        ArcQuestNetwork.sendTranscriptDeltaPacket(
                session.getPlayer(),
                new S2CDialogueTranscriptDeltaPacket(session.getSessionId(), entry)
        );
    }

    private void clearRestoreNodeState(ServerPlayer player) {
        restoreNodeMap.remove(player.getUUID());
    }
}
