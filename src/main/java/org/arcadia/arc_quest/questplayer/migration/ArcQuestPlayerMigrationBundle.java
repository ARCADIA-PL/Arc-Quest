package org.arcadia.arc_quest.questplayer.migration;

import java.util.Objects;

public final class ArcQuestPlayerMigrationBundle {

    public static final String FORMAT = "arc_quest:player_migration";
    public static final int VERSION = 1;

    private final String format;
    private final int version;
    private final ArcQuestPlayerMigrationMeta meta;
    private final ArcQuestPlayerMigrationSections sections;

    public ArcQuestPlayerMigrationBundle(String format,
                                         int version,
                                         ArcQuestPlayerMigrationMeta meta,
                                         ArcQuestPlayerMigrationSections sections) {
        this.format = (format == null || format.isBlank()) ? FORMAT : format;
        this.version = version;
        this.meta = Objects.requireNonNull(meta);
        this.sections = Objects.requireNonNull(sections);
    }

    public String getFormat() {
        return format;
    }

    public int getVersion() {
        return version;
    }

    public ArcQuestPlayerMigrationMeta getMeta() {
        return meta;
    }

    public ArcQuestPlayerMigrationSections getSections() {
        return sections;
    }
}
