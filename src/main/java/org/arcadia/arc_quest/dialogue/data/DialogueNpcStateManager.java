package org.arcadia.arc_quest.dialogue.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSession;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.npc.runtime.NpcBehaviorFocusResolver;
import org.arcadia.arc_quest.npc.runtime.NpcInteractionLeaseManager;
import org.arcadia.arc_quest.npc.runtime.NpcLease;
import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public final class DialogueNpcStateManager {

    private DialogueNpcStateManager() {
    }

    public record State(@Nullable Player conversingPlayer, @Nullable UUID playerUuid) {}

    @Nullable
    public static State get(Entity npc) {
        List<NpcLease> leases = getDialogueLeases(npc);
        ServerPlayer focusedPlayer = NpcBehaviorFocusResolver.resolve(npc, leases);
        return focusedPlayer != null ? new State(focusedPlayer, focusedPlayer.getUUID()) : null;
    }

    public static void setConversing(Entity npc, @Nullable Player player) {
        if (player == null) {
            clear(npc);
        } else if (player instanceof ServerPlayer serverPlayer) {
            NpcInteractionLeaseManager.INSTANCE.acquire(
                    EntityRef.of(npc),
                    new PlayerSessionRef(serverPlayer.getUUID(), PlayerSessionEpochManager.getOrCreate(serverPlayer)),
                    NpcInteractionPolicy.PARALLEL_PRIVATE,
                    currentServerTick(npc)
            );
        }
    }

    public static List<ServerPlayer> getParticipants(Entity npc) {
        if (npc.getServer() == null) return List.of();
        List<NpcLease> leases = getDialogueLeases(npc);
        return leases.stream()
                .map(lease -> new Participant(
                        lease, npc.getServer().getPlayerList().getPlayer(lease.owner().playerUuid())))
                .filter(participant -> participant.player() != null && participant.player().isAlive())
                .filter(participant -> PlayerSessionEpochManager.matches(
                        participant.player(), participant.lease().owner().loginEpoch()))
                .map(Participant::player)
                .toList();
    }

    private static List<NpcLease> getDialogueLeases(Entity npc) {
        if (npc.getServer() == null) return List.of();
        EntityRef entityRef = EntityRef.of(npc);
        return NpcInteractionLeaseManager.INSTANCE.getActiveLeases(entityRef, currentServerTick(npc)).stream()
                .filter(lease -> isActiveDialogueLease(npc, entityRef, lease))
                .toList();
    }

    private static boolean isActiveDialogueLease(Entity npc, EntityRef entityRef, NpcLease lease) {
        ServerPlayer player = npc.getServer().getPlayerList().getPlayer(lease.owner().playerUuid());
        if (player == null || !PlayerSessionEpochManager.matches(player, lease.owner().loginEpoch())) return false;

        DialogueSession session = DialogueSessionManager.INSTANCE.getSession(player);
        return session != null
                && lease.leaseId().equals(session.getNpcLeaseId())
                && entityRef.equals(session.getEntityRef());
    }

    public static void clear(Entity npc, Player player) {
        NpcInteractionLeaseManager.INSTANCE.getActiveLeases(EntityRef.of(npc), currentServerTick(npc)).stream()
                .filter(lease -> lease.owner().playerUuid().equals(player.getUUID()))
                .map(NpcLease::leaseId)
                .toList()
                .forEach(NpcInteractionLeaseManager.INSTANCE::release);
    }

    public static void clear(Entity npc) {
        NpcInteractionLeaseManager.INSTANCE.releaseEntity(EntityRef.of(npc));
    }

    public static void clearPlayer(UUID playerUuid) {
        NpcInteractionLeaseManager.INSTANCE.releasePlayer(playerUuid);
    }

    public static void clearAll() {
        NpcInteractionLeaseManager.INSTANCE.clear();
    }

    private static long currentServerTick(Entity npc) {
        return npc.getServer() != null ? npc.getServer().getTickCount() : 0L;
    }

    private record Participant(NpcLease lease, @Nullable ServerPlayer player) {
    }
}
