package org.arcadia.arc_quest.guide.spec.compile;

public final class GuideCompileException extends RuntimeException {
    public GuideCompileException(String message) {
        super(message);
    }

    public GuideCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
