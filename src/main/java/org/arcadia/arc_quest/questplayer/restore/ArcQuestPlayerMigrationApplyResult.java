package org.arcadia.arc_quest.questplayer.restore;

import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestPlayerSnapshotRef;

import javax.annotation.Nullable;

public final class ArcQuestPlayerMigrationApplyResult {

    private final boolean success;
    private final ArcQuestPlayerMigrationReport report;
    @Nullable
    private final ArcQuestPlayerSnapshotRef preRestoreSnapshot;

    public ArcQuestPlayerMigrationApplyResult(boolean success,
                                              ArcQuestPlayerMigrationReport report,
                                              @Nullable ArcQuestPlayerSnapshotRef preRestoreSnapshot) {
        this.success = success;
        this.report = report;
        this.preRestoreSnapshot = preRestoreSnapshot;
    }

    public boolean isSuccess() {
        return success;
    }

    public ArcQuestPlayerMigrationReport getReport() {
        return report;
    }

    @Nullable
    public ArcQuestPlayerSnapshotRef getPreRestoreSnapshot() {
        return preRestoreSnapshot;
    }
}
