package org.arcadia.arc_quest.guide.io;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;
import org.arcadia.arc_quest.guide.spec.GuideSpec;
import org.arcadia.arc_quest.guide.spec.compile.GuideCategorySpecCompiler;
import org.arcadia.arc_quest.guide.spec.compile.GuideSpecCompiler;
import org.arcadia.arc_quest.guide.spec.validate.GuideCategorySpecValidator;
import org.arcadia.arc_quest.guide.spec.validate.GuideSpecValidator;
import org.arcadia.arc_quest.guide.spec.validate.GuideValidationIssue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuideDatapackHotReloadService {
    private final GuideDatapackResourceLoader resourceLoader = new GuideDatapackResourceLoader();
    private final GuideCategorySpecValidator categoryValidator = new GuideCategorySpecValidator();
    private final GuideSpecValidator guideValidator = new GuideSpecValidator();
    private final GuideCategorySpecCompiler categoryCompiler = new GuideCategorySpecCompiler();

    public GuideReloadResult reload() {
        var report = resourceLoader.loadFromDatapack();
        int categoryLoaded = 0;
        int guideLoaded = 0;
        int categoryFailed = report.failedCount();
        int guideFailed = 0;

        Map<ResourceLocation, GuideCategory> categories = new LinkedHashMap<>();
        Map<ResourceLocation, GuideCategorySpec> categorySpecs = new LinkedHashMap<>();
        for (Map.Entry<Path, GuideCategorySpec> entry : report.categories().entrySet()) {
            GuideCategorySpec spec = entry.getValue();
            ResourceLocation id = GuideCategorySpecValidator.parseCategoryId(spec.id);
            if (id == null) {
                categoryFailed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Invalid guide category id '{}' from file {}", spec.id, entry.getKey());
                continue;
            }
            categorySpecs.put(id, spec);
        }

        for (Map.Entry<ResourceLocation, GuideCategorySpec> entry : categorySpecs.entrySet()) {
            var validation = categoryValidator.validate(entry.getValue());
            if (validation.hasErrors()) {
                categoryFailed++;
                logIssues("GuideCategory", validation.getIssues());
                continue;
            }
            try {
                GuideCategory category = categoryCompiler.compile(entry.getValue());
                categories.put(entry.getKey(), category);
                categoryLoaded++;
            } catch (Exception ex) {
                categoryFailed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Compile failed for {}", entry.getKey(), ex);
            }
        }

        GuideSpecCompiler guideCompiler = new GuideSpecCompiler(categories);
        Map<ResourceLocation, GuideSpec> guideSpecs = new LinkedHashMap<>();
        Map<ResourceLocation, GuideDefinition> stagedGuides = new LinkedHashMap<>();
        for (Map.Entry<Path, GuideSpec> entry : report.guides().entrySet()) {
            GuideSpec spec = entry.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.id);
            if (id == null) {
                guideFailed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Invalid guide id '{}' from file {}", spec.id, entry.getKey());
                continue;
            }
            guideSpecs.put(id, spec);
        }

        for (Map.Entry<ResourceLocation, GuideSpec> entry : guideSpecs.entrySet()) {
            var validation = guideValidator.validate(entry.getValue());
            if (validation.hasErrors()) {
                guideFailed++;
                logIssues("GuideRegistry", validation.getIssues());
                continue;
            }
            try {
                GuideDefinition guide = guideCompiler.compile(entry.getValue());
                stagedGuides.put(entry.getKey(), guide);
                guideLoaded++;
            } catch (Exception ex) {
                guideFailed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Compile/register failed for {}", entry.getKey(), ex);
            }
        }

        if (categoryFailed == 0 && guideFailed == 0) {
            GuideRegistry.replaceDatapackSnapshot(stagedGuides);
        } else {
            ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Legacy guide reload rejected; retaining previous datapack snapshot because failedCategories={}, failedGuides={}",
                    categoryFailed, guideFailed);
        }

        ArcQuestLog.info(ArcQuestLog.Category.QUEST_RELOAD, "Datapack reload complete. categorySpecs={}, compiledCategories={}, failedCategories={}, guideSpecs={}, loadedGuides={}, failedGuides={}",
                report.categoryScannedFiles(), categoryLoaded, categoryFailed,
                report.guideScannedFiles(), guideLoaded, guideFailed);

        return new GuideReloadResult(
                report.categoryScannedFiles(), categoryLoaded, categoryFailed,
                report.guideScannedFiles(), guideLoaded, guideFailed,
                categories.size(), GuideRegistry.datapackSize(),
                GuideRegistry.getAllCategories().size(), GuideRegistry.size()
        );
    }

    private void logIssues(String prefix, Iterable<GuideValidationIssue> issues) {
        for (GuideValidationIssue issue : issues) {
            if (issue.severity == GuideValidationIssue.Severity.ERROR) {
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", prefix, issue.path, issue.message);
            } else {
                ArcQuestLog.warn(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", prefix, issue.path, issue.message);
            }
        }
    }
}
