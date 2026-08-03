package org.arcadia.arc_quest.questmarker.api;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MarkTriggers {
    public static final int DEFAULT_TRIGGER_DURATION_TICKS = 100;

    private static final String TRIGGER_KEY = "arcq.trigger";
    private static final String DURATION_KEY = "arcq.duration_ticks";
    private static final String NODE_ID_KEY = "arcq.dialogue_node_id";

    private MarkTriggers() {
    }

    public static MarkTrigger trigger(MarkSpec spec) {
        if (spec == null) return MarkTrigger.CONTINUOUS;
        String value = spec.styleHints().get(TRIGGER_KEY);
        if (value == null || value.isBlank()) return MarkTrigger.CONTINUOUS;
        try {
            return MarkTrigger.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return MarkTrigger.CONTINUOUS;
        }
    }

    public static boolean isContinuous(MarkSpec spec) {
        return trigger(spec) == MarkTrigger.CONTINUOUS;
    }

    public static int durationTicks(MarkSpec spec) {
        if (spec == null) return DEFAULT_TRIGGER_DURATION_TICKS;
        String value = spec.styleHints().get(DURATION_KEY);
        if (value == null || value.isBlank()) return DEFAULT_TRIGGER_DURATION_TICKS;
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return DEFAULT_TRIGGER_DURATION_TICKS;
        }
    }

    public static String dialogueNodeId(MarkSpec spec) {
        return spec == null ? "" : spec.styleHints().getOrDefault(NODE_ID_KEY, "");
    }

    public static MarkSpec withTrigger(MarkSpec spec, MarkTrigger trigger, int durationTicks) {
        Map<String, String> hints = new LinkedHashMap<>(spec.styleHints());
        MarkTrigger resolvedTrigger = trigger == null ? MarkTrigger.CONTINUOUS : trigger;
        if (resolvedTrigger == MarkTrigger.CONTINUOUS) {
            hints.remove(TRIGGER_KEY);
            hints.remove(DURATION_KEY);
        } else {
            hints.put(TRIGGER_KEY, resolvedTrigger.name());
            hints.put(DURATION_KEY, Integer.toString(Math.max(1, durationTicks)));
        }
        return copy(spec, hints);
    }

    public static MarkSpec forDialogueNode(MarkSpec spec, String nodeId) {
        Map<String, String> hints = new LinkedHashMap<>(spec.styleHints());
        if (nodeId == null || nodeId.isBlank()) hints.remove(NODE_ID_KEY);
        else hints.put(NODE_ID_KEY, nodeId);
        return copy(spec, hints);
    }

    private static MarkSpec copy(MarkSpec spec, Map<String, String> hints) {
        return new MarkSpec(spec.id(), spec.target(), spec.activateWhen(), spec.deactivateWhen(),
                spec.markerType(), spec.priority(), spec.maxDistance(), spec.refreshTicks(),
                spec.trackMovingEntity(), spec.oneShot(), hints);
    }
}
