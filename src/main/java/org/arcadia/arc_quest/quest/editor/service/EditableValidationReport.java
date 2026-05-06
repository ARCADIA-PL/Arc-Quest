package org.arcadia.arc_quest.quest.editor.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EditableValidationReport {
    private final List<EditableValidationIssue> issues = new ArrayList<>();

    public void add(EditableValidationSeverity severity, String path, String message) {
        issues.add(new EditableValidationIssue(severity, path, message));
    }

    public List<EditableValidationIssue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == EditableValidationSeverity.ERROR);
    }
}
