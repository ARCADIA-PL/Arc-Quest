package org.arcadia.arc_quest.data.reload;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record ModulePreparation<T>(String module, T snapshot, Set<Path> scannedFiles, long parsedBytes,
                                   List<ReloadDiagnostic> diagnostics) {
    public ModulePreparation {
        scannedFiles = Set.copyOf(scannedFiles);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasBlockingErrors() {
        return diagnostics.stream().anyMatch(ReloadDiagnostic::isBlocking);
    }
}
