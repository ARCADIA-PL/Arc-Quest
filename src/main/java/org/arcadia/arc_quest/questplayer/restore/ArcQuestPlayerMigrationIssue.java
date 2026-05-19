package org.arcadia.arc_quest.questplayer.restore;

public record ArcQuestPlayerMigrationIssue(
        ArcQuestPlayerMigrationSeverity severity,
        String code,
        String message,
        String section,
        String referenceId) {
}
