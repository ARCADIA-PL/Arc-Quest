package org.arcadia.arc_quest.trade.spec.validate;

public final class TradeValidationIssue {

    public enum Severity {
        ERROR,
        WARN
    }

    public final Severity severity;
    public final String path;
    public final String message;

    public TradeValidationIssue(Severity severity, String path, String message) {
        this.severity = severity;
        this.path = path;
        this.message = message;
    }
}
