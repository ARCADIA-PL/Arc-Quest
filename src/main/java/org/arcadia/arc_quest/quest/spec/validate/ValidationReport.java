package org.arcadia.arc_quest.quest.spec.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationReport {
    private final List<ValidationIssue> issues = new ArrayList<>();

    public void add(ValidationIssue.Severity severity, String path, String message) {
        issues.add(new ValidationIssue(severity, path, message));
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity == ValidationIssue.Severity.ERROR);
    }

    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}
