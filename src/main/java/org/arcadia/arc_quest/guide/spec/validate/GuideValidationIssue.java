package org.arcadia.arc_quest.guide.spec.validate;

public final class GuideValidationIssue {
    public final Severity severity;
    public final String path;
    public final String message;

    public GuideValidationIssue(Severity severity, String path, String message) {
        this.severity = severity;
        this.path = path;
        this.message = message;
    }

    public enum Severity {
        ERROR,
        WARNING
    }
}
