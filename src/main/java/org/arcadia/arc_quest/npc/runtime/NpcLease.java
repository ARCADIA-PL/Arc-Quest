package org.arcadia.arc_quest.npc.runtime;

import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;

import java.util.UUID;

public record NpcLease(
        UUID leaseId,
        EntityRef entityRef,
        PlayerSessionRef owner,
        NpcInteractionPolicy policy,
        long acquiredAtTick,
        long lastActivityTick,
        long expiresAtTick,
        long heartbeatRevision
) {

    NpcLease heartbeat(long nowTick, long ttlTicks) {
        return new NpcLease(leaseId, entityRef, owner, policy, acquiredAtTick,
                nowTick, nowTick + ttlTicks, heartbeatRevision + 1L);
    }
}
