package org.arcadia.arc_quest.guide.registry;

public record GuideSourceInfo(
        GuideSourceType sourceType,
        String sourceId,
        int loadOrder,
        String ignoredReason
) {
}
