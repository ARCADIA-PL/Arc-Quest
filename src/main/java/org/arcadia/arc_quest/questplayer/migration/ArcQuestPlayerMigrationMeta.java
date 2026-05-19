package org.arcadia.arc_quest.questplayer.migration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ArcQuestPlayerMigrationMeta {

    private final UUID sourcePlayerUuid;
    private final String sourcePlayerName;
    private final long exportedAt;
    private final int sourceArcQuestDataVersion;
    private final String sourceModVersion;
    private final String sourceWorldHint;
    private final Set<String> exportedSections;

    public ArcQuestPlayerMigrationMeta(UUID sourcePlayerUuid,
                                       String sourcePlayerName,
                                       long exportedAt,
                                       int sourceArcQuestDataVersion,
                                       String sourceModVersion,
                                       String sourceWorldHint,
                                       Set<String> exportedSections) {
        this.sourcePlayerUuid = Objects.requireNonNull(sourcePlayerUuid);
        this.sourcePlayerName = sourcePlayerName == null ? "" : sourcePlayerName;
        this.exportedAt = exportedAt;
        this.sourceArcQuestDataVersion = sourceArcQuestDataVersion;
        this.sourceModVersion = sourceModVersion == null ? "" : sourceModVersion;
        this.sourceWorldHint = sourceWorldHint == null ? "" : sourceWorldHint;
        this.exportedSections = Collections.unmodifiableSet(new LinkedHashSet<>(exportedSections == null ? Set.of() : exportedSections));
    }

    public UUID getSourcePlayerUuid() {
        return sourcePlayerUuid;
    }

    public String getSourcePlayerName() {
        return sourcePlayerName;
    }

    public long getExportedAt() {
        return exportedAt;
    }

    public int getSourceArcQuestDataVersion() {
        return sourceArcQuestDataVersion;
    }

    public String getSourceModVersion() {
        return sourceModVersion;
    }

    public String getSourceWorldHint() {
        return sourceWorldHint;
    }

    public Set<String> getExportedSections() {
        return exportedSections;
    }
}
