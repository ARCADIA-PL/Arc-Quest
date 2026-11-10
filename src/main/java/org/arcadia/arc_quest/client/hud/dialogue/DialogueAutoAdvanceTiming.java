package org.arcadia.arc_quest.client.hud.dialogue;

/** Active on-screen reading budget, including the typewriter; explicit input still advances immediately. */
final class DialogueAutoAdvanceTiming {
    private static final int MIN_DELAY_MILLIS = 3_000;
    private static final int MAX_READING_MILLIS = 24_000;
    private static final int MAX_AUTHORED_MILLIS = 30_000;
    private static final int READING_START_MILLIS = 1_200;
    private static final int MILLIS_PER_CHARACTER = 120;
    private static final int FULL_TEXT_HOLD_MILLIS = 600;

    private long visibleMillis;
    private long fullTextMillis;
    private boolean wasFullyShown;

    void reset() {
        visibleMillis = 0;
        fullTextMillis = 0;
        wasFullyShown = false;
    }

    static int delay(String text, int configuredMillis) {
        String visible = text.replaceAll("\u00a7.", "");
        long characters = visible.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
                .count();
        long readingMillis = characters == 0 ? FULL_TEXT_HOLD_MILLIS
                : Math.max(MIN_DELAY_MILLIS,
                        Math.min(MAX_READING_MILLIS, READING_START_MILLIS + characters * MILLIS_PER_CHARACTER));
        // Keep explicit longer story pauses, with the existing 30-second authored limit.
        return (int) Math.max(readingMillis, Math.min(MAX_AUTHORED_MILLIS, Math.max(0, configuredMillis)));
    }

    boolean tick(long frameMillis, boolean paused, boolean fullyShown, int budgetMillis) {
        if (paused) return false;
        // Match the typewriter's frame cap: a stalled frame must not consume a whole page.
        long activeMillis = Math.max(0L, Math.min(100L, frameMillis));
        visibleMillis += activeMillis;
        if (fullyShown) {
            if (wasFullyShown) fullTextMillis += activeMillis;
        } else {
            fullTextMillis = 0;
        }
        wasFullyShown = fullyShown;
        return fullyShown && visibleMillis >= budgetMillis && fullTextMillis >= FULL_TEXT_HOLD_MILLIS;
    }
}
