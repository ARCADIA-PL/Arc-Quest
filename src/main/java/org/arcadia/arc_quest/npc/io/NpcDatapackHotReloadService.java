package org.arcadia.arc_quest.npc.io;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.npc.spec.validate.NpcSpecValidator;
import org.arcadia.arc_quest.npc.spec.validate.NpcValidationIssue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class NpcDatapackHotReloadService {
    private final NpcDatapackResourceLoader resourceLoader = new NpcDatapackResourceLoader();
    private final NpcSpecValidator validator = new NpcSpecValidator();

    public ReloadResult reload() {
        var report = resourceLoader.loadFromDatapack();

        int loaded = 0;
        int failed = report.failedCount();
        List<NpcSpec> stagedSpecs = new ArrayList<>();

        for (Map.Entry<Path, NpcSpec> entry : report.specs().entrySet()) {
            NpcSpec spec = entry.getValue();
            var validation = validator.validate(spec);
            if (validation.hasErrors()) {
                failed++;
                for (var issue : validation.getIssues()) {
                    if (issue.severity == NpcValidationIssue.Severity.ERROR) {
                        ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    } else {
                        ArcQuestLog.warn(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                stagedSpecs.add(spec);
                loaded++;
            } catch (Exception ex) {
                failed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Register failed for {}", entry.getKey(), ex);
            }
        }

        long epoch = NpcBindingRegistry.INSTANCE.getSnapshotEpoch();
        if (failed == 0) {
            NpcBindingRegistry.INSTANCE.replaceAll(stagedSpecs);
            epoch = NpcBindingRegistry.INSTANCE.getSnapshotEpoch();
        } else {
            ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Datapack reload rejected; retaining previous snapshot epoch={} because failed={}",
                    epoch, failed);
        }

        ArcQuestLog.info(ArcQuestLog.Category.QUEST_RELOAD, "Datapack reload complete. scanned={}, loaded={}, failed={}, activeBindings={}, epoch={}",
                report.scannedFiles(), loaded, failed, NpcBindingRegistry.INSTANCE.size(), epoch);

        return new ReloadResult(report.scannedFiles(), loaded, failed, NpcBindingRegistry.INSTANCE.size());
    }

    public record ReloadResult(int scanned, int discovered, int failed, int activeBindings) {
    }
}
