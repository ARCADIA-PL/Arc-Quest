package org.arcadia.arc_quest.dialogue.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.dialogue.api.DialogueSubmissionDefinition;
import org.arcadia.arc_quest.dialogue.network.*;
import org.arcadia.arc_quest.dialogue.registry.DialogueSubmissionRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.arcadia.arc_quest.questplayer.interaction.PlayerInteractionGuard;
import java.util.UUID;

/** Panel authorization lives in the dialogue session, never in a global player map. */
public final class DialogueSubmissionService {
    record Pending(DialogueSubmissionDefinition definition, UUID token, String nodeId) {}
    private DialogueSubmissionService() {}

    public static void open(ServerPlayer player, DialogueSession session, String id) {
        var definition = DialogueSubmissionRegistry.get(id);
        if (definition == null || session == null || session.isEnded()
                || DialogueSessionManager.INSTANCE.getSession(player) != session)
            throw new IllegalStateException("Submission requires an owned active dialogue: " + id);
        session.submission = new Pending(definition, UUID.randomUUID(), session.getCurrentNode().nodeId());
        send(player, session, false, false);
    }

    static boolean hasSample(ServerPlayer player, String id) {
        var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
        if (item == null) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        return false;
    }

    public static void handle(ServerPlayer player, C2SDialogueSubmissionPacket request) {
        var manager = DialogueSessionManager.INSTANCE;
        var session = manager.getSession(player);
        if (session == null || session.isEnded() || !session.getSessionId().equals(request.sessionId())
                || !PlayerSessionEpochManager.matches(player, request.epoch()) || !player.isAlive()) return;
        var pending = session.submission;
        if (pending == null || !pending.token().equals(request.token())
                || !pending.nodeId().equals(session.getCurrentNode().nodeId())) return;
        var npc = session.getEntity();
        if (session.getEntityId() >= 0 && (npc == null || !npc.isAlive() || npc.level() != player.level()
                || player.distanceToSqr(npc) > 64)) {
            send(player, session, true, true); session.submission = null; manager.endDialogue(player); return;
        }
        try (var guard = PlayerInteractionGuard.INSTANCE.enter(player.getUUID()); var operation = session.beginOperation()) {
            if (guard == null || operation == null) return;
            if (request.cancel()) {
                send(player, session, true, false); session.submission = null; return;
            }
            var entry = pending.definition().entries().stream().filter(row -> row.id().equals(request.entryId())).findFirst().orElse(null);
            if (request.amount() != 1 || entry == null || !entry.available().test(player) || !hasSample(player, entry.itemId())) {
                send(player, session, false, true); return;
            }
            // Invalidate before callbacks, so duplicate/reentrant requests cannot pay twice.
            session.submission = null;
            try {
                for (var action : entry.actions()) action.execute(player, session);
            } catch (RuntimeException failure) {
                session.submission = new Pending(pending.definition(), UUID.randomUUID(), pending.nodeId());
                send(player, session, false, true);
                org.arcadia.arc_quest.util.log.ArcQuestLog.error(org.arcadia.arc_quest.util.log.ArcQuestLog.Category.DIALOGUE,
                        "Submission failed: {} / {}", pending.definition().id(), entry.id(), failure);
                return;
            }
            // Close the old panel before publishing the next dialogue node.
            session.submission = pending; send(player, session, true, false); session.submission = null;
            if (entry.successNodeId() != null && !entry.successNodeId().isEmpty()) {
                var node = session.getTree().getNode(entry.successNodeId());
                if (node != null) session.setCurrentNode(node);
            }
        }
        if (manager.getSession(player) == session && !session.isEnded()) {
            manager.handleRestoreDialogue(player);
            ArcQuestNetwork.syncFullData(player, org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager.get(player));
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** The authorized view, also useful to server-side integrations without inspecting inventory twice. */
    public static S2CDialogueSubmissionPacket currentView(ServerPlayer player) {
        var session = DialogueSessionManager.INSTANCE.getSession(player);
        return session == null || session.submission == null ? null : view(player, session, false, false);
    }

    private static void send(ServerPlayer player, DialogueSession session, boolean closed, boolean rejected) {
        ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), view(player, session, closed, rejected));
    }

    private static S2CDialogueSubmissionPacket view(ServerPlayer player, DialogueSession session, boolean closed, boolean rejected) {
        var pending = session.submission;
        var rows = closed ? java.util.List.<S2CDialogueSubmissionPacket.Row>of() : pending.definition().entries().stream()
                .filter(row -> row.available().test(player) && hasSample(player, row.itemId()))
                .map(row -> new S2CDialogueSubmissionPacket.Row(row.id(), new ItemStack(java.util.Objects.requireNonNull(
                        ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(row.itemId())))), 1, 1)).toList();
        return new S2CDialogueSubmissionPacket(session.getSessionId(), session.getPlayerSessionEpoch(), pending.token(),
                pending.definition().title(), rows, closed, rejected);
    }
}
