package org.arcadia.arc_quest.quest.spec.validate;

public class ValidationIssue {
    public enum Severity { ERROR, WARNING }

    public final Severity severity;
    public final String path;
    public final String message;

    public ValidationIssue(Severity severity, String path, String message) {
        this.severity = severity;
        this.path = path;
        this.message = message;
    }
}
