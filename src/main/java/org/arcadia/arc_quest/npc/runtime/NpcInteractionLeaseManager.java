package org.arcadia.arc_quest.npc.runtime;

import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NpcInteractionLeaseManager {

    public static final NpcInteractionLeaseManager INSTANCE = new NpcInteractionLeaseManager();
    public static final long DEFAULT_TTL_TICKS = 20L * 30L;

    private final Map<EntityRef, LinkedHashMap<UUID, NpcLease>> leasesByEntity = new LinkedHashMap<>();
    private final Map<UUID, NpcLease> leasesById = new LinkedHashMap<>();

    private NpcInteractionLeaseManager() {
    }

    public synchronized AcquireResult acquire(EntityRef entityRef, PlayerSessionRef owner,
                                               NpcInteractionPolicy policy, long nowTick) {
        return acquire(entityRef, owner, policy, nowTick, DEFAULT_TTL_TICKS);
    }

    synchronized AcquireResult acquire(EntityRef entityRef, PlayerSessionRef owner,
                                       NpcInteractionPolicy policy, long nowTick, long ttlTicks) {
        if (policy == null) policy = NpcInteractionPolicy.PARALLEL_PRIVATE;
        if (!policy.isImplemented()) {
            return new AcquireResult(AcquireStatus.UNSUPPORTED_POLICY, null, null);
        }

        LinkedHashMap<UUID, NpcLease> entityLeases = leasesByEntity.computeIfAbsent(
                entityRef, ignored -> new LinkedHashMap<>());
        NpcLease existing = entityLeases.values().stream()
                .filter(lease -> lease.owner().equals(owner))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            NpcLease renewed = existing.heartbeat(nowTick, ttlTicks);
            replace(renewed);
            return new AcquireResult(AcquireStatus.RENEWED, renewed, null);
        }

        boolean hasExclusiveLease = entityLeases.values().stream()
                .anyMatch(lease -> lease.policy() == NpcInteractionPolicy.EXCLUSIVE);
        if (!entityLeases.isEmpty() && (policy == NpcInteractionPolicy.EXCLUSIVE || hasExclusiveLease)) {
            return new AcquireResult(AcquireStatus.BUSY, null, entityLeases.values().iterator().next());
        }

        NpcLease lease = new NpcLease(UUID.randomUUID(), entityRef, owner, policy,
                nowTick, nowTick, nowTick + ttlTicks, 0L);
        entityLeases.put(lease.leaseId(), lease);
        leasesById.put(lease.leaseId(), lease);
        return new AcquireResult(AcquireStatus.ACQUIRED, lease, null);
    }

    public synchronized boolean heartbeat(UUID leaseId, long nowTick) {
        NpcLease lease = leasesById.get(leaseId);
        if (lease == null) return false;
        replace(lease.heartbeat(nowTick, DEFAULT_TTL_TICKS));
        return true;
    }

    public synchronized boolean release(UUID leaseId) {
        NpcLease lease = leasesById.remove(leaseId);
        if (lease == null) return false;
        LinkedHashMap<UUID, NpcLease> entityLeases = leasesByEntity.get(lease.entityRef());
        if (entityLeases != null) {
            entityLeases.remove(leaseId);
            if (entityLeases.isEmpty()) leasesByEntity.remove(lease.entityRef());
        }
        return true;
    }

    public synchronized void releasePlayer(UUID playerUuid) {
        List<UUID> leaseIds = leasesById.values().stream()
                .filter(lease -> lease.owner().playerUuid().equals(playerUuid))
                .map(NpcLease::leaseId)
                .toList();
        leaseIds.forEach(this::release);
    }

    public synchronized void releaseEntity(EntityRef entityRef) {
        LinkedHashMap<UUID, NpcLease> removed = leasesByEntity.remove(entityRef);
        if (removed != null) removed.keySet().forEach(leasesById::remove);
    }

    public synchronized List<NpcLease> getActiveLeases(EntityRef entityRef, long nowTick) {
        Collection<NpcLease> leases = leasesByEntity.containsKey(entityRef)
                ? leasesByEntity.get(entityRef).values() : List.of();
        return List.copyOf(leases);
    }

    @Nullable
    public synchronized NpcLease get(UUID leaseId) {
        return leasesById.get(leaseId);
    }

    public synchronized int size() {
        return leasesById.size();
    }

    public synchronized void clear() {
        leasesByEntity.clear();
        leasesById.clear();
    }

    public synchronized List<NpcLease> tick(long nowTick) {
        return cleanupExpired(nowTick);
    }

    private List<NpcLease> cleanupExpired(long nowTick) {
        List<NpcLease> expired = new ArrayList<>();
        for (NpcLease lease : leasesById.values()) {
            if (lease.expiresAtTick() < nowTick) expired.add(lease);
        }
        expired.stream().map(NpcLease::leaseId).toList().forEach(this::release);
        return List.copyOf(expired);
    }

    private void replace(NpcLease lease) {
        leasesById.put(lease.leaseId(), lease);
        LinkedHashMap<UUID, NpcLease> entityLeases = leasesByEntity.get(lease.entityRef());
        if (entityLeases != null) entityLeases.put(lease.leaseId(), lease);
    }

    public enum AcquireStatus {
        ACQUIRED,
        RENEWED,
        BUSY,
        UNSUPPORTED_POLICY
    }

    public record AcquireResult(AcquireStatus status, @Nullable NpcLease lease, @Nullable NpcLease conflictingLease) {
        public boolean acquired() {
            return status == AcquireStatus.ACQUIRED || status == AcquireStatus.RENEWED;
        }
    }
}
