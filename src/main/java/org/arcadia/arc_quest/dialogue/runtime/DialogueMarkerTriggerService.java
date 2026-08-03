package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.DialogueChoice;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.List;

public final class DialogueMarkerTriggerService {
    private DialogueMarkerTriggerService() {
    }

    public static void triggerNodeEntered(ServerPlayer player, ArcQuestPlayer data,
                                          DialogueTree tree, String nodeId) {
        trigger(player, data, tree.dialogueId(), "node:" + nodeId,
                tree.relatedMarks(), MarkTrigger.DIALOGUE_NODE_ENTERED, nodeId);
    }

    public static void triggerChoiceSelected(ServerPlayer player, ArcQuestPlayer data,
                                             DialogueTree tree, String nodeId, DialogueChoice choice) {
        trigger(player, data, tree.dialogueId(), "node:" + nodeId + ":choice:" + choice.choiceId(),
                choice.relatedMarks(), MarkTrigger.DIALOGUE_CHOICE_SELECTED, "");
    }

    private static void trigger(ServerPlayer player, ArcQuestPlayer data, String dialogueId,
                                String scope, List<MarkSpec> specs, MarkTrigger trigger,
                                String requiredNodeId) {
        for (MarkSpec spec : specs) {
            if (MarkTriggers.trigger(spec) != trigger) continue;
            if (!requiredNodeId.isEmpty() && !requiredNodeId.equals(MarkTriggers.dialogueNodeId(spec))) continue;
            String markerId = "aq:trigger:dialogue:" + dialogueId + ":" + scope + ":"
                    + trigger.name().toLowerCase() + ":" + spec.id();
            if (!QuestMarkerRuntimeManager.trigger(
                    player, data, markerId, dialogueId, spec, "", -1)) continue;
            QuestMarkerData marker = data.getAllMarkers().get(markerId);
            if (marker != null) ArcQuestNetwork.syncMarkerDeltaUpsert(player, marker);
            else ArcQuestNetwork.syncMarkerDeltaRemove(player, markerId);
        }
    }
}
