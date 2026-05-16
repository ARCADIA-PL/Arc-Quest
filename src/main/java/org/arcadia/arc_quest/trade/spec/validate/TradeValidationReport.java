package org.arcadia.arc_quest.trade.spec.validate;

import java.util.ArrayList;
import java.util.List;

public final class TradeValidationReport {
    private final List<TradeValidationIssue> issues = new ArrayList<>();

    public void add(TradeValidationIssue.Severity severity, String path, String message) {
        issues.add(new TradeValidationIssue(severity, path, message));
    }

    public List<TradeValidationIssue> getIssues() {
        return issues;
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity == TradeValidationIssue.Severity.ERROR);
    }

    public int errorCount() {
        return (int) issues.stream().filter(i -> i.severity == TradeValidationIssue.Severity.ERROR).count();
    }

    public int warnCount() {
        return (int) issues.stream().filter(i -> i.severity == TradeValidationIssue.Severity.WARN).count();
    }
}
