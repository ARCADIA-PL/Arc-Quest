package org.arcadia.arc_quest.quest.editor;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.nio.file.Path;
import java.util.UUID;

public final class QuestEditorSession {
    private final UUID sessionId;
    private final UUID playerId;
    private final ResourceLocation questId;
    private final Path sourcePath;
    private QuestSpec draft;
    private long revision;

    public QuestEditorSession(UUID playerId, ResourceLocation questId, Path sourcePath, QuestSpec draft) {
        this.sessionId = UUID.randomUUID();
        this.playerId = playerId;
        this.questId = questId;
        this.sourcePath = sourcePath;
        this.draft = draft;
    }

    public UUID sessionId() { return sessionId; }
    public UUID playerId() { return playerId; }
    public ResourceLocation questId() { return questId; }
    public Path sourcePath() { return sourcePath; }
    public QuestSpec draft() { return draft; }
    public long revision() { return revision; }
    public void replaceDraft(QuestSpec draft) { this.draft = draft; this.revision++; }
}
