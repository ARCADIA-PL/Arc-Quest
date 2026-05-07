package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.editor.model.EditableQuest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestEditorDocument {
    public EditableQuest quest = new EditableQuest();
    public Path sourceFile;
    public Path saveFile;
    public boolean imported;
    public boolean newlyCreated;
    public long lastSavedAt;
    public List<EditableValidationIssue> lastIssues = new ArrayList<>();
}
