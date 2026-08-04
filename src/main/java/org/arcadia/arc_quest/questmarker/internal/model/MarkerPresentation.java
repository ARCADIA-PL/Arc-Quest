package org.arcadia.arc_quest.questmarker.internal.model;

import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.Map;

public record MarkerPresentation(String label,
                                 QuestMarkerType type,
                                 int colorArgb,
                                 boolean showDistance,
                                 boolean allowOffscreenArrow,
                                 int priority,
                                 Map<String, String> extensionHints) {

    public MarkerPresentation {
        label = label == null ? "" : label;
        type = type == null ? QuestMarkerType.CUSTOM : type;
        extensionHints = extensionHints == null ? Map.of() : Map.copyOf(extensionHints);
    }
}
