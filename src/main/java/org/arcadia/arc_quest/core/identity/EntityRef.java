package org.arcadia.arc_quest.core.identity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public record EntityRef(ResourceKey<Level> dimensionKey, UUID entityUuid, int lastKnownRuntimeId) {

    public EntityRef {
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(entityUuid, "entityUuid");
    }

    public static EntityRef of(Entity entity) {
        return new EntityRef(entity.level().dimension(), entity.getUUID(), entity.getId());
    }

    @Nullable
    public Entity resolve(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) return null;

        Entity runtimeEntity = lastKnownRuntimeId >= 0 ? level.getEntity(lastKnownRuntimeId) : null;
        if (runtimeEntity != null && entityUuid.equals(runtimeEntity.getUUID())) {
            return runtimeEntity;
        }
        return level.getEntity(entityUuid);
    }

    @Override
    public String toString() {
        return dimensionKey.location() + "/" + entityUuid + "#" + lastKnownRuntimeId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EntityRef entityRef)) return false;
        return dimensionKey.equals(entityRef.dimensionKey) && entityUuid.equals(entityRef.entityUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensionKey, entityUuid);
    }
}
