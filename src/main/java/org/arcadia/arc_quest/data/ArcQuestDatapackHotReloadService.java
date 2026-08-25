package org.arcadia.arc_quest.data;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecResourceLoader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecResourceLoader2;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.quest.spec.validate.ValidationIssue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArcQuestDatapackHotReloadService {
    private final QuestSpecResourceLoader2 datapackLoader = new QuestSpecResourceLoader2();
    private final QuestSpecResourceLoader fallbackLoader = new QuestSpecResourceLoader();
    private final QuestSpecValidator validator = new QuestSpecValidator();
    private final QuestSpecCompiler compiler = new QuestSpecCompiler();

    public ReloadResult reload(ResourceManager manager) {
        var report = datapackLoader.loadFromDatapack();

        Map<ResourceLocation, QuestSpec> specs = new LinkedHashMap<>();
        for (Map.Entry<Path, QuestSpec> e : report.specs().entrySet()) {
            QuestSpec spec = e.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.id);
            if (id == null) {
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Invalid quest id '{}' from file {}", spec.id, e.getKey());
                continue;
            }
            specs.put(id, spec);
        }

        boolean usedFallback = false;
        if (specs.isEmpty() && manager != null) {
            specs.putAll(fallbackLoader.load(manager));
            usedFallback = true;
            ArcQuestLog.info(ArcQuestLog.Category.QUEST_RELOAD, "Hot reload fallback to resource-manager source.");
        }

        int loaded = 0;
        int failed = report.failedCount();
        Map<ResourceLocation, QuestDefinition> stagedDefinitions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, QuestSpec> entry : specs.entrySet()) {
            var validation = validator.validate(entry.getValue());
            if (validation.hasErrors()) {
                failed++;
                for (var issue : validation.getIssues()) {
                    if (issue.severity == ValidationIssue.Severity.ERROR) {
                        ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    } else {
                        ArcQuestLog.warn(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                stagedDefinitions.put(entry.getKey(), compiler.compile(entry.getValue()));
                loaded++;
            } catch (Exception ex) {
                failed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Compile/register failed for {}", entry.getKey(), ex);
            }
        }

        if (failed == 0) {
            QuestRegistry.replaceDatapackSnapshot(stagedDefinitions);
        } else {
            ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Legacy quest reload rejected; retaining previous datapack snapshot because failed={}", failed);
        }

        return new ReloadResult(report.scannedFiles(), loaded, failed, QuestRegistry.datapackSize(), QuestRegistry.size(), usedFallback);
    }

    public record ReloadResult(int scanned, int loaded, int failed, int activeDatapack, int merged,
                               boolean usedFallback) {
    }
}
