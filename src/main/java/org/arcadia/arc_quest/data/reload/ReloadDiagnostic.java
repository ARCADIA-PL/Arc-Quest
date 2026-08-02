package org.arcadia.arc_quest.data.reload;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record ReloadDiagnostic(Severity severity, String module, @Nullable Path file, String location, String message, @Nullable Throwable cause) {
    public enum Severity { WARNING, ERROR }

    public boolean isBlocking() { return severity == Severity.ERROR; }

    public static ReloadDiagnostic error(String module, @Nullable Path file, String location, String message) {
        return new ReloadDiagnostic(Severity.ERROR, module, file, location, message, null);
    }

    public static ReloadDiagnostic error(String module, @Nullable Path file, String location, String message, Throwable cause) {
        return new ReloadDiagnostic(Severity.ERROR, module, file, location, message, cause);
    }

    public static ReloadDiagnostic warning(String module, @Nullable Path file, String location, String message) {
        return new ReloadDiagnostic(Severity.WARNING, module, file, location, message, null);
    }
}
