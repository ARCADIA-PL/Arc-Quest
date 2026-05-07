package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.editor.mapper.QuestSpecToEditableQuestMapper;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QuestDatapackScanService {
    private static final String QUEST_DIR = "arc_quest/datapack";

    private final QuestSpecToEditableQuestMapper mapper = new QuestSpecToEditableQuestMapper();
    private final EditableQuestValidationService validationService = new EditableQuestValidationService();

    public List<QuestDatapackEntry> scanWorkspace(Path workspaceRoot) {
        List<QuestDatapackEntry> list = new ArrayList<>();
        Path dir = workspaceRoot.resolve(QUEST_DIR);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return list;
        }

        try (var stream = Files.list(dir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .forEach(p -> list.add(preview(p)));
        } catch (IOException ignored) {
        }

        list.sort(Comparator.comparing((QuestDatapackEntry e) -> e.questId == null ? "" : e.questId)
                .thenComparing(e -> e.fileName == null ? "" : e.fileName));
        return list;
    }

    public QuestDatapackEntry preview(Path file) {
        QuestDatapackEntry entry = new QuestDatapackEntry();
        entry.file = file;
        entry.fileName = file.getFileName().toString();

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            QuestSpec spec = QuestSpecJsonReader.read(json);
            entry.parseable = spec != null;
            if (spec != null) {
                entry.questId = spec.id == null ? "" : spec.id;
                entry.displayName = spec.displayName == null ? "" : spec.displayName.value;
                entry.questMode = spec.mode == null ? "PROGRESSION" : spec.mode.name();
                entry.phaseCount = spec.phases == null ? 0 : spec.phases.size();
                var editable = mapper.map(spec);
                var report = validationService.validate(editable);
                entry.issues.addAll(report.issues());
            }
        } catch (Exception ex) {
            entry.parseable = false;
            entry.errorMessage = ex.getMessage() == null ? "unknown error" : ex.getMessage();
        }

        return entry;
    }

    public QuestSpec loadSpec(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return QuestSpecJsonReader.read(json);
    }
}
