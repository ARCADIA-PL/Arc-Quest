package org.arcadia.arc_quest.npc.spec.validate;

public final class NpcValidationIssue {

    public enum Severity {
        ERROR,
        WARN
    }

    public final Severity severity;
    public final String path;
    public final String message;

    public NpcValidationIssue(Severity severity, String path, String message) {
        this.severity = severity;
        this.path = path;
        this.message = message;
    }
}