package org.arcadia.arc_quest.npc.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Comparator;

public final class NpcBehaviorFocusResolver {

    private NpcBehaviorFocusResolver() {
    }

    @Nullable
    public static ServerPlayer resolve(Entity npc, Collection<NpcLease> leases) {
        MinecraftServer server = npc.getServer();
        if (server == null) return null;

        return leases.stream()
                .map(lease -> new Candidate(lease, server.getPlayerList().getPlayer(lease.owner().playerUuid())))
                .filter(candidate -> candidate.player() != null)
                .filter(candidate -> candidate.player().isAlive())
                .filter(candidate -> PlayerSessionEpochManager.matches(
                        candidate.player(), candidate.lease().owner().loginEpoch()))
                .filter(candidate -> candidate.player().level().dimension().equals(npc.level().dimension()))
                .max(Comparator.comparingLong((Candidate candidate) -> candidate.lease().lastActivityTick())
                        .thenComparingDouble(candidate -> -candidate.player().distanceToSqr(npc))
                        .thenComparing(candidate -> candidate.player().getUUID().toString()))
                .map(Candidate::player)
                .orElse(null);
    }

    private record Candidate(NpcLease lease, @Nullable ServerPlayer player) {
    }
}
