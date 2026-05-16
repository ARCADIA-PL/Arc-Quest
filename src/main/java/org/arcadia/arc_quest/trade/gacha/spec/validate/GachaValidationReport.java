package org.arcadia.arc_quest.trade.gacha.spec.validate;

import java.util.ArrayList;
import java.util.List;

public final class GachaValidationReport {
    private final List<GachaValidationIssue> issues = new ArrayList<>();

    public void add(GachaValidationIssue.Severity severity, String path, String message) {
        issues.add(new GachaValidationIssue(severity, path, message));
    }

    public List<GachaValidationIssue> getIssues() {
        return issues;
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity == GachaValidationIssue.Severity.ERROR);
    }

    public int errorCount() {
        return (int) issues.stream().filter(i -> i.severity == GachaValidationIssue.Severity.ERROR).count();
    }

    public int warnCount() {
        return (int) issues.stream().filter(i -> i.severity == GachaValidationIssue.Severity.WARN).count();
    }
}
