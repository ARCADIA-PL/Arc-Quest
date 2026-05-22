package org.arcadia.arc_quest.guide.spec.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GuideValidationReport {
    private final List<GuideValidationIssue> issues = new ArrayList<>();

    public void add(GuideValidationIssue.Severity severity, String path, String message) {
        issues.add(new GuideValidationIssue(severity, path, message));
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity == GuideValidationIssue.Severity.ERROR);
    }

    public List<GuideValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}
