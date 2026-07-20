package org.arcadia.arc_quest.npc.runtime;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.arcadia.arc_quest.core.identity.EntityRef;
import org.arcadia.arc_quest.core.identity.PlayerSessionRef;
import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcInteractionLeaseManagerTest {

    private static final ResourceKey<Level> DIMENSION = createTestDimensionKey();
    private final NpcInteractionLeaseManager manager = NpcInteractionLeaseManager.INSTANCE;

    @AfterEach
    void cleanup() {
        manager.clear();
    }

    @Test
    void parallelPolicyAllowsIndependentPlayers() {
        EntityRef entityRef = entityRef(10);

        var first = manager.acquire(entityRef, player(1L), NpcInteractionPolicy.PARALLEL_PRIVATE, 100L, 20L);
        var second = manager.acquire(entityRef, player(2L), NpcInteractionPolicy.PARALLEL_PRIVATE, 100L, 20L);

        assertTrue(first.acquired());
        assertTrue(second.acquired());
        assertEquals(2, manager.getActiveLeases(entityRef, 100L).size());
    }

    @Test
    void exclusivePolicyRejectsSecondPlayer() {
        EntityRef entityRef = entityRef(10);

        var first = manager.acquire(entityRef, player(1L), NpcInteractionPolicy.EXCLUSIVE, 100L, 20L);
        var second = manager.acquire(entityRef, player(2L), NpcInteractionPolicy.EXCLUSIVE, 100L, 20L);

        assertTrue(first.acquired());
        assertEquals(NpcInteractionLeaseManager.AcquireStatus.BUSY, second.status());
        assertEquals(first.lease(), second.conflictingLease());
    }

    @Test
    void existingExclusiveLeaseCannotBeBypassedByParallelRequest() {
        EntityRef entityRef = entityRef(10);

        manager.acquire(entityRef, player(1L), NpcInteractionPolicy.EXCLUSIVE, 100L, 20L);
        var second = manager.acquire(
                entityRef, player(2L), NpcInteractionPolicy.PARALLEL_PRIVATE, 100L, 20L);

        assertEquals(NpcInteractionLeaseManager.AcquireStatus.BUSY, second.status());
        assertEquals(1, manager.size());
    }

    @Test
    void runtimeIdDoesNotChangeEntityIdentity() {
        UUID entityUuid = UUID.randomUUID();
        EntityRef firstRuntimeId = new EntityRef(DIMENSION, entityUuid, 10);
        EntityRef secondRuntimeId = new EntityRef(DIMENSION, entityUuid, 99);

        manager.acquire(firstRuntimeId, player(1L), NpcInteractionPolicy.EXCLUSIVE, 100L, 20L);

        assertEquals(1, manager.getActiveLeases(secondRuntimeId, 100L).size());
    }

    @Test
    void expiredAndReleasedLeasesAreRemovedIdempotently() {
        EntityRef entityRef = entityRef(10);
        var acquired = manager.acquire(entityRef, player(1L), NpcInteractionPolicy.EXCLUSIVE, 100L, 5L);

        manager.tick(106L);
        assertEquals(0, manager.size());
        assertFalse(manager.release(acquired.lease().leaseId()));
    }

    @Test
    void reservedPoliciesFailClosed() {
        var result = manager.acquire(entityRef(10), player(1L),
                NpcInteractionPolicy.GROUP_SHARED, 100L, 20L);

        assertEquals(NpcInteractionLeaseManager.AcquireStatus.UNSUPPORTED_POLICY, result.status());
        assertEquals(0, manager.size());
    }

    private static EntityRef entityRef(int runtimeId) {
        return new EntityRef(DIMENSION, UUID.randomUUID(), runtimeId);
    }

    private static PlayerSessionRef player(long epoch) {
        return new PlayerSessionRef(UUID.randomUUID(), epoch);
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<Level> createTestDimensionKey() {
        try {
            Constructor<ResourceKey> constructor = ResourceKey.class.getDeclaredConstructor(
                    ResourceLocation.class, ResourceLocation.class);
            constructor.setAccessible(true);
            return (ResourceKey<Level>) constructor.newInstance(
                    ResourceLocation.fromNamespaceAndPath("arc_quest", "test_dimension_registry"),
                    ResourceLocation.fromNamespaceAndPath("arc_quest", "lease_test"));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create isolated test dimension key", exception);
        }
    }
}
