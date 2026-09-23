package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.api.event.dialogue.DialogueNodeStartedEvent;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;

import java.util.List;

/** 组装对话展示快照；扩展回调返回后只发布仍属于当前会话、当前节点的数据。 */
final class DialoguePresentation {
    private final DialogueSessionManager manager;

    DialoguePresentation(DialogueSessionManager manager) {
        this.manager = manager;
    }

    void sendNodeToClient(DialogueSession session, boolean openMode, boolean triggerNodeEntry) {
        if (!manager.isCurrent(session)) return;
        ServerPlayer player = session.getPlayer();
        DialogueNode node = session.getCurrentNode();
        if (node == null) return;

        if (openMode) {
            ArcQuestNetwork.sendTranscriptSnapshotPacket(
                    session.getPlayer(),
                    new S2CDialogueTranscriptSnapshotPacket(
                            session.getSessionId(),
                            List.of()
                    )
            );
        }

        Component speaker = Component.empty();
        if (node.speaker() != null) {
            speaker = session.processDialogueText(node.speaker());
        }

        if (!manager.isCurrentNode(session, node)) return;
        if (speaker == null || speaker.getString().isBlank()) {
            Entity npcEntity = session.getEntity();
            if (npcEntity instanceof IDialogueNpc dialogueNpc) {
                Component display = dialogueNpc.getDialogueDisplayName();
                if (display != null) {
                    String s = display.getString();
                    if (s != null && !s.isBlank()) {
                        speaker = display;
                    }
                }
            }
        }

        if (!manager.isCurrentNode(session, node)) return;
        if (speaker == null || speaker.getString().isBlank()) {
            DialogueText treeDefaultNpc = session.getTree().defaultNpc();
            if (treeDefaultNpc != null) {
                speaker = session.processDialogueText(treeDefaultNpc);
            }
        }

        if (!manager.isCurrentNode(session, node)) return;
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
        Entity npc = session.getEntity();
        DialogueEvalContext ctx = DialogueEvalContext.of(session.getPlayer(), npc, session.getNamespace(), progress);
        ConditionalTextEvaluator.DialogueTextSelection sayIfResult =
                ConditionalTextEvaluator.evaluateDialogueWithIndex(ctx, node.conditionalTexts(), node.text());
        if (!manager.isCurrentNode(session, node)) return;
        Component text = session.processDialogueComponent(
                session.processDialogueText(sayIfResult.text()));
        SoundEvent matchedSaySound = sayIfResult.sound();
        String selectedSayId = sayIfResult.sayId();
        if (!manager.isCurrentNode(session, node)) return;
        if (data != null) {
            syncDialogueMarkers(session, node, selectedSayId);
            if (!manager.isCurrentNode(session, node)) return;
            if (triggerNodeEntry) {
                DialogueMarkerTriggerService.triggerNodeEntered(
                        player, data, session.getTree(), node.nodeId());
            }
        }

        if (!manager.isCurrentNode(session, node)) return;
        appendTranscriptDelta(session, new S2CDialogueTranscriptDeltaPacket.Entry(
                CoreProcessors.get().time().realTimeMillis(),
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

        if (!manager.isCurrentNode(session, node)) return;
        var visibleChoices = session.getVisibleChoices();
        Component[] choiceTexts = new Component[visibleChoices.size()];
        ResourceLocation[] choiceSounds = new ResourceLocation[visibleChoices.size()];
        String[] choiceIds = new String[visibleChoices.size()];
        for (int i = 0; i < visibleChoices.size(); i++) {
            choiceTexts[i] = session.processDialogueText(visibleChoices.get(i).text());
            if (!manager.isCurrentNode(session, node)) return;
            var sound = visibleChoices.get(i).selectSound();
            if (sound != null) choiceSounds[i] = ForgeRegistries.SOUND_EVENTS.getKey(sound);
            choiceIds[i] = visibleChoices.get(i).choiceId();
        }

        if (!manager.isCurrentNode(session, node)) return;
        var cooldownData = session.getChoiceCooldownRawData();
        long revision = session.advanceRevision();
        S2COpenDialoguePacket packet = new S2COpenDialoguePacket(
                session.getTree().dialogueId(), node.nodeId(), speaker, text, choiceTexts,
                node.isTerminal(), !node.hasChoices() && node.autoNextId() != null, node.delayMs(),
                session.getEntityId(), cooldownData.lastSelectTimes(), cooldownData.purchaseGameTimes(),
                cooldownData.purchaseDayTimes(), cooldownData.cooldownTypes(), cooldownData.cooldownValues(),
                cooldownData.resetTimeTicks(), choiceSounds, saySoundId, selectedSayId, choiceIds,
                session.getSessionId(), revision, session.getPlayerSessionEpoch()
        );
        if (!openMode) {
            packet = S2COpenDialoguePacket.updateFrom(packet);
        }
        ArcQuestNetwork.sendToPlayer(player, packet);
    }

    private void syncDialogueMarkers(DialogueSession session,
                                     DialogueNode node,
                                     String selectedSayId) {
        var data = ArcQuestPlayerManager.get(session.getPlayer());
        if (data == null) return;

        String dialogueId = session.getTree().dialogueId();
        String nodeId = node.nodeId();
        ServerPlayer player = session.getPlayer();

        String cleanupPrefix = "aq:dlg:" + dialogueId + ":";
        for (String markerId : data.getAllMarkers().keySet().stream().toList()) {
            if (markerId.startsWith(cleanupPrefix)) data.removeMarker(markerId);
        }

        for (MarkSpec spec : session.getTree().relatedMarks()) {
            if (!MarkTriggers.isContinuous(spec)) continue;
            String markerId = "aq:dlg:" + dialogueId + ":tree:" + spec.id();
            if (!manager.isCurrentNode(session, node)) return;
            QuestMarkerRuntimeManager.refresh(player, data, markerId, dialogueId, spec, null, -1, true);
        }

        for (DialogueChoice c : session.getVisibleChoices()) {
            for (MarkSpec spec : c.relatedMarks()) {
                if (!MarkTriggers.isContinuous(spec)) continue;
                String markerId = "aq:dlg:" + dialogueId + ":" + nodeId + ":choice:" + c.choiceId() + ":" + spec.id();
                if (!manager.isCurrentNode(session, node)) return;
                QuestMarkerRuntimeManager.refresh(player, data, markerId, dialogueId, spec, null, -1, true);
            }
        }

        if (selectedSayId != null && !selectedSayId.isBlank()) {
            for (var entry : node.conditionalTexts().values()) {
                if (!selectedSayId.equals(entry.sayId())) continue;
                for (MarkSpec spec : entry.relatedMarks()) {
                    if (!MarkTriggers.isContinuous(spec)) continue;
                    String markerId = "aq:dlg:" + dialogueId + ":" + nodeId + ":say:" + entry.sayId() + ":" + spec.id();
                    if (!manager.isCurrentNode(session, node)) return;
                    QuestMarkerRuntimeManager.refresh(player, data, markerId, dialogueId, spec, null, -1, true);
                }
            }
        }
    }

    void appendTranscriptDelta(DialogueSession session, S2CDialogueTranscriptDeltaPacket.Entry entry) {
        if (!manager.isCurrent(session)) return;
        ArcQuestNetwork.sendTranscriptDeltaPacket(
                session.getPlayer(),
                new S2CDialogueTranscriptDeltaPacket(session.getSessionId(), entry)
        );
    }

}
