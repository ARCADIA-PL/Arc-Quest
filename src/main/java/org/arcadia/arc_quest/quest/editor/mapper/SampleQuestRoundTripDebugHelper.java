package org.arcadia.arc_quest.quest.editor.mapper;

import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SampleQuestRoundTripDebugHelper {
    public static final String SAMPLE_QUEST_PATH = "src/main/resources/data/arc_quest/arc_quest/quests/datapack_sample_quest.json";

    private SampleQuestRoundTripDebugHelper() {
    }

    public static QuestEditorRoundTripVerifier.RoundTripReport verifySampleQuestFromWorkspace(Path workspaceRoot) throws IOException {
        Path path = workspaceRoot.resolve(SAMPLE_QUEST_PATH);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        QuestSpec spec = QuestSpecJsonReader.read(json);
        return new QuestEditorRoundTripVerifier().verify(spec);
    }
}
