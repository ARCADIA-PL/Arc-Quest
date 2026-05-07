package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.editor.mapper.EditableQuestToQuestSpecMapper;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class QuestEditorSaveService {
    private final EditableQuestValidationService validationService = new EditableQuestValidationService();
    private final EditableQuestToQuestSpecMapper mapper = new EditableQuestToQuestSpecMapper();

    public SaveReport autosave(QuestEditorDocument document) {
        if (document == null) {
            SaveReport report = new SaveReport();
            report.written = false;
            report.errorMessage = "document is null";
            return report;
        }
        return save(document.saveFile, document.quest);
    }

    public SaveReport export(QuestEditorDocument document) {
        return autosave(document);
    }

    public SaveReport save(Path file, org.arcadia.arc_quest.quest.editor.model.EditableQuest quest) {
        SaveReport report = new SaveReport();
        report.file = file;
        if (file == null) {
            report.written = false;
            report.errorMessage = "save file is null";
            return report;
        }

        try {
            var validation = validationService.validate(quest);
            report.issues.addAll(validation.issues());
            if (validation.hasErrors()) {
                report.warnings.add("validation has errors, but save is not blocked");
            }

            var spec = mapper.map(quest);
            String json = QuestSpecJsonWriter.write(spec);
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
            report.written = true;
        } catch (IOException ex) {
            report.written = false;
            report.errorMessage = ex.getMessage() == null ? "io error" : ex.getMessage();
        } catch (Exception ex) {
            report.written = false;
            report.errorMessage = ex.getMessage() == null ? "mapping/serialization error" : ex.getMessage();
        }

        return report;
    }
}
