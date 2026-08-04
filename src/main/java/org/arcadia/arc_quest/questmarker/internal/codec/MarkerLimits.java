package org.arcadia.arc_quest.questmarker.internal.codec;

public final class MarkerLimits {

    public static final int MAX_MARKERS = 4096;
    public static final int MAX_ID_LENGTH = 512;
    public static final int MAX_LABEL_LENGTH = 1024;
    public static final int MAX_STYLE_HINTS = 64;
    public static final int MAX_STYLE_STRING_LENGTH = 256;

    private MarkerLimits() {
    }

    public static String limit(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public static boolean validId(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_ID_LENGTH;
    }
}
