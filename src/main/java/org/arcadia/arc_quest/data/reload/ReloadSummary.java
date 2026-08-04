package org.arcadia.arc_quest.data.reload;

import org.jetbrains.annotations.Nullable;

public record ReloadSummary(long epoch, int scannedFiles, int successfulModules, int warningCount, int failedModules,
                            boolean applied, long durationMillis, @Nullable String firstErrorFile,
                            @Nullable String firstErrorMessage) {
    public String formatForCommand() {
        StringBuilder message = new StringBuilder()
                .append("ArcQuest reload epoch=").append(epoch)
                .append(" scanned=").append(scannedFiles)
                .append(" modules=").append(successfulModules)
                .append(" warnings=").append(warningCount)
                .append(" failedModules=").append(failedModules)
                .append(" applied=").append(applied)
                .append(" durationMs=").append(durationMillis);
        if (firstErrorMessage != null) {
            message.append(" firstError=");
            if (firstErrorFile != null) message.append(firstErrorFile).append(": ");
            message.append(firstErrorMessage);
        }
        return message.toString();
    }
}
