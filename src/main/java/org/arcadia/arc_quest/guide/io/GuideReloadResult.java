package org.arcadia.arc_quest.guide.io;

public record GuideReloadResult(
        int categorySpecFiles,
        int compiledCategories,
        int failedCategories,
        int guideSpecFiles,
        int loadedGuides,
        int failedGuides,
        int activeCategories,
        int activeDatapackGuides,
        int visibleCategories,
        int mergedGuides
) {
}
