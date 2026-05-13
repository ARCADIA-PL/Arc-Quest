package org.arcadia.arc_quest.dialogue.spec.validate;

public class DialogueValidationIssue {
    public final Severity severity;
    public final String path;
    public final String message;

    public DialogueValidationIssue(Severity severity, String path, String message) {
        this.severity = severity;
        this.path = path;
        this.message = message;
    }

    public enum Severity {ERROR, WARNING}
}