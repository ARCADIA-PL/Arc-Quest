package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveType;

import java.util.Objects;

/**
 * 目标查找键——由 (ObjectiveType, targetId) 组成。
 * 用作 HashMap 的 key，保证 O(1) 查找。
 */
public final class ObjectiveKey {

    private final ObjectiveType type;
    private final ResourceLocation targetId;
    private final int hashCode;

    public ObjectiveKey(ObjectiveType type, ResourceLocation targetId) {
        this.type = Objects.requireNonNull(type);
        this.targetId = Objects.requireNonNull(targetId);
        // 预计算 hashCode（不可变对象）
        hashCode = Objects.hash(type, targetId);
    }

    public ObjectiveType getType() {
        return type;
    }

    public ResourceLocation getTargetId() {
        return targetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectiveKey other)) return false;
        return type.equals(other.type) && targetId.equals(other.targetId);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "ObjKey[" + type + ":" + targetId + "]";
    }
}