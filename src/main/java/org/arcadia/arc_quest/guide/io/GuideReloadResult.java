package org.arcadia.arc_quest.guide.io;

public record GuideReloadResult(
        int categoryScanned,
        int categoryLoaded,
        int categoryFailed,
        int guideScanned,
        int guideLoaded,
        int guideFailed,
        int activeDatapackCategories,
        int activeDatapackGuides,
        int mergedCategories,
        int mergedGuides
) {
}
