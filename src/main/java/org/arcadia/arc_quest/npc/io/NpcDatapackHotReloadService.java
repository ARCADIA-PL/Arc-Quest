package org.arcadia.arc_quest.npc.io;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.npc.spec.validate.NpcSpecValidator;
import org.arcadia.arc_quest.npc.spec.validate.NpcValidationIssue;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;

public final class NpcDatapackHotReloadService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final NpcDatapackResourceLoader resourceLoader = new NpcDatapackResourceLoader();
    private final NpcSpecValidator validator = new NpcSpecValidator();

    public ReloadResult reload() {
        var report = resourceLoader.loadFromDatapack();

        NpcBindingRegistry.INSTANCE.clear();

        int loaded = 0;
        int failed = report.failedCount();

        for (Map.Entry<Path, NpcSpec> entry : report.specs().entrySet()) {
            NpcSpec spec = entry.getValue();
            var validation = validator.validate(spec);
            if (validation.hasErrors()) {
                failed++;
                for (var issue : validation.getIssues()) {
                    if (issue.severity == NpcValidationIssue.Severity.ERROR) {
                        LOGGER.error("[NpcRegistry] {} -> {}", issue.path, issue.message);
                    } else {
                        LOGGER.warn("[NpcRegistry] {} -> {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                NpcBindingRegistry.INSTANCE.register(spec);
                loaded++;
            } catch (Exception ex) {
                failed++;
                LOGGER.error("[NpcRegistry] Register failed for {}", entry.getKey(), ex);
            }
        }

        LOGGER.info("[NpcRegistry] Datapack reload complete. scanned={}, loaded={}, failed={}, activeBindings={}",
                report.scannedFiles(), loaded, failed, NpcBindingRegistry.INSTANCE.size());

        return new ReloadResult(report.scannedFiles(), loaded, failed, NpcBindingRegistry.INSTANCE.size());
    }

    public record ReloadResult(int scanned, int discovered, int failed, int activeBindings) {
    }
}