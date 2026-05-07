package org.arcadia.arc_quest.quest.editor.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SaveReport {
    public boolean written;
    public Path file;
    public List<EditableValidationIssue> issues = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public String errorMessage = "";
}
