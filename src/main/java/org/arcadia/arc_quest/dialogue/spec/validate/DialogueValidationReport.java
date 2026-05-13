package org.arcadia.arc_quest.dialogue.spec.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DialogueValidationReport {
    private final List<DialogueValidationIssue> issues = new ArrayList<>();

    public void add(DialogueValidationIssue.Severity severity, String path, String message) {
        issues.add(new DialogueValidationIssue(severity, path, message));
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity == DialogueValidationIssue.Severity.ERROR);
    }

    public List<DialogueValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}