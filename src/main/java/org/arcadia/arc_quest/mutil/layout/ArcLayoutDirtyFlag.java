package org.arcadia.arc_quest.mutil.layout;

public enum ArcLayoutDirtyFlag {
    CLEAN,
    CONTENT,
    BOUNDS,
    FULL;

    public boolean isDirty() {
        return this != CLEAN;
    }

    public ArcLayoutDirtyFlag merge(ArcLayoutDirtyFlag other) {
        if (other == null || other == CLEAN) return this;
        if (this == CLEAN) return other;
        if (this == FULL || other == FULL) return FULL;
        if (this != other) return FULL;
        return this;
    }
}
