package org.arcadia.arc_quest.quest.editor;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.nio.file.Path;
import java.util.Objects;

public record QuestAuthoringEntry(ResourceLocation questId, Path sourcePath, QuestSpec spec) {
    public QuestAuthoringEntry {
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(spec, "spec");
    }
}
