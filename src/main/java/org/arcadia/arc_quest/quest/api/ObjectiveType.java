package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 运行时 objective 类型句柄。
 *
 * 第一阶段先保留与旧 enum 常量同名的内置静态常量，
 * 以便渐进替换下游 ObjectiveType.X 的调用点。
 */
public final class ObjectiveType {

    private final ResourceLocation id;
    private final boolean counting;
    private final boolean builtin;
    private final String displayKey;
    private final boolean requireTargetId;
    private final String defaultTargetKind;

    public static final ObjectiveType NULL = ObjectiveTypes.NULL;
    public static final ObjectiveType KILL = ObjectiveTypes.KILL;
    public static final ObjectiveType COLLECT = ObjectiveTypes.COLLECT;
    public static final ObjectiveType TALK = ObjectiveTypes.TALK;
    public static final ObjectiveType INTERACT = ObjectiveTypes.INTERACT;
    public static final ObjectiveType REACH_LOCATION = ObjectiveTypes.REACH_LOCATION;
    public static final ObjectiveType DELIVER = ObjectiveTypes.DELIVER;
    public static final ObjectiveType CRAFT = ObjectiveTypes.CRAFT;
    public static final ObjectiveType OFFER = ObjectiveTypes.OFFER;
    public static final ObjectiveType CUSTOM = ObjectiveTypes.CUSTOM;

    public ObjectiveType(ResourceLocation id,
                         boolean counting,
                         boolean builtin,
                         String displayKey,
                         boolean requireTargetId,
                         String defaultTargetKind) {
        this.id = Objects.requireNonNull(id, "id");
        this.counting = counting;
        this.builtin = builtin;
        this.displayKey = displayKey == null ? "" : displayKey;
        this.requireTargetId = requireTargetId;
        this.defaultTargetKind = defaultTargetKind == null ? "" : defaultTargetKind;
    }

    public ResourceLocation getId() {
        return id;
    }

    public boolean isCounting() {
        return counting;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public String getDisplayKey() {
        return displayKey;
    }

    public boolean requiresTargetId() {
        return requireTargetId;
    }

    public String getDefaultTargetKind() {
        return defaultTargetKind;
    }

    public String getPathToken() {
        return id.getPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectiveType that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
