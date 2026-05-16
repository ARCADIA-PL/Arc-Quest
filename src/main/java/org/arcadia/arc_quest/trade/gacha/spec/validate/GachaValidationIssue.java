package org.arcadia.arc_quest.trade.gacha.spec.validate;

public final class GachaValidationIssue {

    public enum Severity {
        ERROR,
        WARN
    }

    public final Severity severity;
    public final String path;
    public final String message;

    public GachaValidationIssue(Severity severity, String path, String message) {
        this.severity = severity;
        this.path = path;
        this.message = message;
    }
}
