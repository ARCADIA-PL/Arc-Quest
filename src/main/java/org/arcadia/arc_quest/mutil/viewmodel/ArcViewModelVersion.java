package org.arcadia.arc_quest.mutil.viewmodel;

public class ArcViewModelVersion {
    private long version;
    private boolean dirty = true;

    public long getVersion() {
        return version;
    }

    public boolean isDirty() {
        return dirty;
    }

    public long markDirty() {
        dirty = true;
        return ++version;
    }

    public void markClean() {
        dirty = false;
    }
}
