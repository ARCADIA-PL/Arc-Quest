package org.arcadia.arc_quest.guide.io;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuideDatapackHotReloadService {

    private static final Logger LOGGER = LogUtils.getLogger();

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
                LOGGER.error("[GuideRegistry] Invalid guide category id '{}' from file {}", spec.id, entry.getKey());
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
                LOGGER.error("[GuideCategory] Compile failed for {}", entry.getKey(), ex);
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
                LOGGER.error("[GuideRegistry] Invalid guide id '{}' from file {}", spec.id, entry.getKey());
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
                LOGGER.error("[GuideRegistry] Compile/register failed for {}", entry.getKey(), ex);
            }
        }

        if (categoryFailed == 0 && guideFailed == 0) {
            GuideRegistry.replaceDatapackSnapshot(stagedGuides);
        } else {
            LOGGER.error("[GuideRegistry] Legacy guide reload rejected; retaining previous datapack snapshot because failedCategories={}, failedGuides={}",
                    categoryFailed, guideFailed);
        }

        LOGGER.info("[GuideRegistry] Datapack reload complete. categorySpecs={}, compiledCategories={}, failedCategories={}, guideSpecs={}, loadedGuides={}, failedGuides={}",
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
                LOGGER.error("[{}] {} -> {}", prefix, issue.path, issue.message);
            } else {
                LOGGER.warn("[{}] {} -> {}", prefix, issue.path, issue.message);
            }
        }
    }
}
