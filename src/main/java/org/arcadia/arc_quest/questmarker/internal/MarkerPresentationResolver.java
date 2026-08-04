package org.arcadia.arc_quest.questmarker.internal;

import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerPresentation;

import java.util.Locale;
import java.util.Map;

public final class MarkerPresentationResolver {

    private MarkerPresentationResolver() {
    }

    public static MarkerPresentation resolve(MarkSpec spec) {
        Map<String, String> hints = spec.styleHints();
        return new MarkerPresentation(hints.getOrDefault("label", spec.id()), spec.markerType(),
                parseColor(hints.get("color"), 0xFFFFFFFF),
                parseBoolean(hints.get("showDistance"), true),
                parseBoolean(hints.get("allowOffscreenArrow"), true), spec.priority(), hints);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("#")) normalized = normalized.substring(1);
            if (normalized.startsWith("0x")) normalized = normalized.substring(2);
            long parsed = Long.parseUnsignedLong(normalized, 16);
            if (normalized.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
