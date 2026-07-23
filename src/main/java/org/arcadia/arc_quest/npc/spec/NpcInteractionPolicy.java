package org.arcadia.arc_quest.npc.spec;

public enum NpcInteractionPolicy {
    PARALLEL_PRIVATE,
    EXCLUSIVE,
    QUEUED,
    GROUP_SHARED,
    SPECTATE_SHARED;

    public boolean isImplemented() {
        return this == PARALLEL_PRIVATE || this == EXCLUSIVE;
    }
}
