package org.arcadia.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.api.event.dialogue.*;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.data.DialogueNpcStateManager;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.util.TimeSanitizer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.quest.network.SyncObservability.Reason;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
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
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) {
            LOGGER.warn("[Dialogue] Missing quest data for player {}, cannot start dialogue '{}'",
                    player.getName().getString(), tree.dialogueId());
            return null;
        }
        String dialogueId = tree.dialogueId();
        var progress = data.getDialogueProgress();
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
            DialogueNpcStateManager.setConversing(npcEntity, player);
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
                Component choiceText = session.processDialogueText(choice.text());

                MinecraftForge.EVENT_BUS.post(new DialogueChoiceSelectedEvent(
                        player, npc, session.getTree().dialogueId(),
                        currentNode.nodeId(), choiceIndex, choice.choiceId(), choiceText.getString()
                ));

                appendTranscriptDelta(session, new S2CDialogueTranscriptDeltaPacket.Entry(
                        System.currentTimeMillis(),
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

        var data = ArcQuestPlayerManager.get(player);
        if (data != null) {
            String cleanupPrefix = "aq:dlg:" + session.getTree().dialogueId() + ":";
            for (String markerId : data.getAllMarkers().keySet().stream().toList()) {
                if (markerId.startsWith(cleanupPrefix)) data.removeMarker(markerId);
            }
        }

        transcriptMap.remove(player.getUUID());
        String dialogueId = session.getTree().dialogueId();
        Entity npcEntity = null;
        if (session.getEntityId() != -1) {
            npcEntity = player.level().getEntity(session.getEntityId());
            if (npcEntity instanceof IDialogueNpc) {
                DialogueNpcStateManager.clear(npcEntity);
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

        Component speaker = Component.empty();
        if (node.speaker() != null) {
            speaker = session.processDialogueText(node.speaker());
        }

        if (speaker == null || speaker.getString().isBlank()) {
            Entity npcEntity = session.getEntityId() != -1 ? player.level().getEntity(session.getEntityId()) : null;
            if (npcEntity instanceof IDialogueNpc dialogueNpc) {
                Component display = dialogueNpc.getDialogueDisplayName();
                if (display != null) {
                    String s = display.getString();
                    if (s != null && !s.isBlank()) {
                        speaker = Component.literal(s);
                    }
                }
            }
        }

        if (speaker == null || speaker.getString().isBlank()) {
            DialogueText treeDefaultNpc = session.getTree().defaultNpc();
            if (treeDefaultNpc != null) {
                speaker = session.processDialogueText(treeDefaultNpc);
            }
        }

        if (speaker == null || speaker.getString().isBlank()) {
            Object fallback = session.getContext().get("defaultNpc");
            if (fallback instanceof String s && !s.isBlank()) {
                speaker = Component.literal(s);
            }
        }

        if (speaker == null) {
            speaker = Component.empty();
        }
        var data = ArcQuestPlayerManager.get(player);
        DialogueProgressStore progress = data != null ? data.getDialogueProgress() : null;
        Entity npc = session.getEntityId() != -1 ? player.level().getEntity(session.getEntityId()) : null;
        DialogueEvalContext ctx = DialogueEvalContext.of(session.getPlayer(), npc, session.getNamespace(), progress);
        ConditionalTextEvaluator.SayIfResult sayIfResult = ConditionalTextEvaluator.evaluateWithIndex(ctx, node.conditionalTexts(), session.processDialogueText(node.text()).getString());
        Component text = Component.literal(session.processText(sayIfResult.text));
        SoundEvent matchedSaySound = sayIfResult.sound;
        String selectedSayId = sayIfResult.sayId;
        if (data != null) syncDialogueMarkers(session, node, sayIfResult);

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
        MinecraftForge.EVENT_BUS.post(new DialogueNodeStartedEvent(player, npc, session.getTree().dialogueId(), node.nodeId(), selectedSayId, text.getString(), matchedSaySound, saySoundId));

        var visibleChoices = session.getVisibleChoices();
        Component[] choiceTexts = new Component[visibleChoices.size()];
        ResourceLocation[] choiceSounds = new ResourceLocation[visibleChoices.size()];
        String[] choiceIds = new String[visibleChoices.size()];
        for (int i = 0; i < visibleChoices.size(); i++) {
            choiceTexts[i] = session.processDialogueText(visibleChoices.get(i).text());
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

    private void syncDialogueMarkers(DialogueSession session,
                                     DialogueNode node,
                                     ConditionalTextEvaluator.SayIfResult sayIfResult) {
        var data = ArcQuestPlayerManager.get(session.getPlayer());
        if (data == null) return;

        String dialogueId = session.getTree().dialogueId();
        String nodeId = node.nodeId();
        ServerPlayer player = session.getPlayer();
        ServerLevel level = player.serverLevel();

        String cleanupPrefix = "aq:dlg:" + dialogueId + ":";
        for (String markerId : data.getAllMarkers().keySet().stream().toList()) {
            if (markerId.startsWith(cleanupPrefix)) data.removeMarker(markerId);
        }

        for (DialogueChoice c : session.getVisibleChoices()) {
            for (MarkSpec spec : c.relatedMarks()) {
                boolean active = spec.activateWhen().test(player, data) && !spec.deactivateWhen().test(player, data);
                if (!active) continue;
                String markerId = "aq:dlg:" + dialogueId + ":" + nodeId + ":choice:" + c.choiceId() + ":" + spec.id();
                QuestMarkerData marker = resolveDialogueMarker(markerId, spec, player, level, dialogueId);
                if (marker != null) data.upsertMarker(marker);
            }
        }

        if (sayIfResult.sayId != null && !sayIfResult.sayId.isBlank()) {
            for (var entry : node.conditionalTexts().values()) {
                if (!sayIfResult.sayId.equals(entry.sayId())) continue;
                for (MarkSpec spec : entry.relatedMarks()) {
                    boolean active = spec.activateWhen().test(player, data) && !spec.deactivateWhen().test(player, data);
                    if (!active) continue;
                    String markerId = "aq:dlg:" + dialogueId + ":" + nodeId + ":say:" + entry.sayId() + ":" + spec.id();
                    QuestMarkerData marker = resolveDialogueMarker(markerId, spec, player, level, dialogueId);
                    if (marker != null) data.upsertMarker(marker);
                }
            }
        }
    }

    private QuestMarkerData resolveDialogueMarker(String markerId,
                                                  MarkSpec spec,
                                                  ServerPlayer player,
                                                  ServerLevel level,
                                                  String dialogueId) {
        MarkableObject target = spec.target();
        if (target instanceof MarkableObject.Pos p) {
            return new QuestMarkerData.Builder(markerId, p.x(), p.y(), p.z(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(dialogueId)
                    .type(spec.markerType())
                    .build();
        }
        if (target instanceof MarkableObject.DimensionPos dp) {
            if (!level.dimension().equals(dp.dimension())) return null;
            return new QuestMarkerData.Builder(markerId, dp.x(), dp.y(), dp.z(), spec.id())
                    .dimension(dp.dimension().location().toString())
                    .bindQuest(dialogueId)
                    .type(spec.markerType())
                    .build();
        }
        if (target instanceof MarkableObject.EntityByUuid byUuid) {
            Entity ent = level.getEntity(byUuid.uuid());
            if (ent == null) return null;
            return new QuestMarkerData.Builder(markerId, ent.getX(), ent.getY(), ent.getZ(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(dialogueId)
                    .followEntity(ent.getId(), byUuid.uuid().toString(), "", QuestMarkerData.EntityAttachPoint.HEAD)
                    .type(spec.markerType())
                    .build();
        }
        if (target instanceof MarkableObject.EntityByNpcId byNpc) {
            Entity nearestNpc = level.getEntities(player,
                            player.getBoundingBox().inflate(byNpc.searchRadius()),
                            e -> e.getPersistentData().contains("ArcQuestNpcId")
                                    && byNpc.npcId().equals(e.getPersistentData().getString("ArcQuestNpcId")))
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player))).orElse(null);
            if (nearestNpc == null) return null;
            return new QuestMarkerData.Builder(markerId, nearestNpc.getX(), nearestNpc.getY(), nearestNpc.getZ(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(dialogueId)
                    .followEntity(nearestNpc.getId(), nearestNpc.getUUID().toString(), byNpc.npcId(), QuestMarkerData.EntityAttachPoint.HEAD)
                    .type(spec.markerType())
                    .build();
        }
        if (target instanceof MarkableObject.EntityByTypeNearest byType) {
            Entity nearest = level.getEntities(player,
                            player.getBoundingBox().inflate(byType.searchRadius()),
                            e -> e.getType() == byType.type())
                    .stream().min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player))).orElse(null);
            if (nearest == null) return null;
            return new QuestMarkerData.Builder(markerId, nearest.getX(), nearest.getY(), nearest.getZ(), spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(dialogueId)
                    .followEntity(nearest.getId(), nearest.getUUID().toString(), "", QuestMarkerData.EntityAttachPoint.HEAD)
                    .type(spec.markerType())
                    .build();
        }
        if (target instanceof MarkableObject.StructureNearest byStructure) {
            BlockPos pos = level.findNearestMapStructure(byStructure.structureTag(), player.blockPosition(), byStructure.searchRadius(), false);
            if (pos == null) return null;
            return new QuestMarkerData.Builder(markerId, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, spec.id())
                    .dimension(level.dimension().location().toString())
                    .bindQuest(dialogueId)
                    .type(spec.markerType())
                    .build();
        }
        return null;
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
