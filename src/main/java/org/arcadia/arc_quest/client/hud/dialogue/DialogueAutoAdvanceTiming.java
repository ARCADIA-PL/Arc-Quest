package org.arcadia.arc_quest.client.hud.dialogue;

/** Reading time starts after the typewriter finishes; explicit input can still advance immediately. */
final class DialogueAutoAdvanceTiming {
    private static final int MIN_DELAY_MILLIS = 7_000;
    private static final int MAX_DELAY_MILLIS = 30_000;
    private static final int READING_START_MILLIS = 2_000;
    private static final int MILLIS_PER_CHARACTER = 180;

    private DialogueAutoAdvanceTiming() {}

    static int delay(String text, int configuredMillis) {
        String visible = text.replaceAll("\u00a7.", "");
        long characters = visible.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
                .count();
        long readingMillis = READING_START_MILLIS + characters * MILLIS_PER_CHARACTER;
        return (int) Math.min(MAX_DELAY_MILLIS,
                Math.max(MIN_DELAY_MILLIS, Math.max(configuredMillis, readingMillis)));
    }
}
