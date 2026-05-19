package org.arcadia.arc_quest.questplayer.restore;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class ArcQuestPlayerMigrationReport {

    private final boolean blocking;
    private final List<ArcQuestPlayerMigrationIssue> issues;

    public ArcQuestPlayerMigrationReport(boolean blocking, List<ArcQuestPlayerMigrationIssue> issues) {
        this.blocking = blocking;
        this.issues = List.copyOf(issues);
    }

    public boolean isBlocking() {
        return blocking;
    }

    public List<ArcQuestPlayerMigrationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public List<ArcQuestPlayerMigrationIssue> getIssues(ArcQuestPlayerMigrationSeverity severity) {
        return issues.stream().filter(issue -> issue.severity() == severity).collect(Collectors.toList());
    }

    public int count(ArcQuestPlayerMigrationSeverity severity) {
        return (int) issues.stream().filter(issue -> issue.severity() == severity).count();
    }
}
