package org.arcadia.arc_quest.quest.editor.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class QuestDatapackEntry {
    public Path file;
    public String fileName = "";
    public String questId = "";
    public String displayName = "";
    public String questMode = "PROGRESSION";
    public int phaseCount;
    public boolean parseable;
    public List<EditableValidationIssue> issues = new ArrayList<>();
    public String errorMessage = "";
}
