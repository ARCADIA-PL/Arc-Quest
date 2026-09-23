package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.api.event.dialogue.DialogueEndedEvent;
import org.arcadia.arc_quest.dialogue.api.IDialogueNpc;
import org.arcadia.arc_quest.dialogue.data.DialogueNpcStateManager;
import org.arcadia.arc_quest.npc.runtime.NpcInteractionLeaseManager;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

/** 会话已从管理器移除并标为结束后调用，各清理阶段独立完成。 */
final class DialogueSessionCleanup {
    private DialogueSessionCleanup() {
    }

    static void close(DialogueSession session) {
        var player = session.getPlayer();
        RuntimeException failure = run(null, () -> {
            if (session.getNpcLeaseId() != null) {
                NpcInteractionLeaseManager.INSTANCE.release(session.getNpcLeaseId());
            }
        });
        failure = run(failure, () -> {
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return;
            String prefix = "aq:dlg:" + session.getTree().dialogueId() + ":";
            for (String markerId : data.getAllMarkers().keySet().stream().toList()) {
                if (markerId.startsWith(prefix)) data.removeMarker(markerId);
            }
        });
        Entity entity = null;
        try {
            entity = session.getEntity();
        } catch (RuntimeException problem) {
            failure = merge(failure, problem);
        }
        Entity npc = entity;
        failure = run(failure, () -> {
            if (npc instanceof IDialogueNpc) DialogueNpcStateManager.clear(npc, player);
        });
        failure = run(failure, () -> {
            if (npc != null) {
                MinecraftForge.EVENT_BUS.post(new DialogueEndedEvent(player, npc, session.getTree().dialogueId()));
            }
        });
        if (failure != null) throw failure;
    }

    static RuntimeException run(RuntimeException previous, Runnable cleanup) {
        try {
            cleanup.run();
            return previous;
        } catch (RuntimeException failure) {
            return merge(previous, failure);
        }
    }

    private static RuntimeException merge(RuntimeException previous, RuntimeException failure) {
        if (previous == null) return failure;
        if (previous != failure) previous.addSuppressed(failure);
        return previous;
    }
}
