package org.arcadia.arc_quest.data.reload;

public record ReloadLimits(int maxFiles, long maxFileBytes, int maxDirectoryDepth, int maxCollectionElements, long maxParsedBytes) {
    public static ReloadLimits configured() {
        return new ReloadLimits(
                integerProperty("arcquest.reload.maxFiles", 4096),
                longProperty("arcquest.reload.maxFileBytes", 4L * 1024L * 1024L),
                integerProperty("arcquest.reload.maxDirectoryDepth", 8),
                integerProperty("arcquest.reload.maxCollectionElements", 100_000),
                longProperty("arcquest.reload.maxParsedBytes", 64L * 1024L * 1024L));
    }

    private static int integerProperty(String key, int fallback) {
        try { return Math.max(1, Integer.parseInt(System.getProperty(key, Integer.toString(fallback)))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static long longProperty(String key, long fallback) {
        try { return Math.max(1L, Long.parseLong(System.getProperty(key, Long.toString(fallback)))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
