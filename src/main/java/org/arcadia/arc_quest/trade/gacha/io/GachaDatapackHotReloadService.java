package org.arcadia.arc_quest.trade.gacha.io;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;
import org.arcadia.arc_quest.trade.gacha.spec.compile.GachaSpecCompiler;
import org.arcadia.arc_quest.trade.gacha.spec.validate.GachaSpecValidator;
import org.arcadia.arc_quest.trade.gacha.spec.validate.GachaValidationIssue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GachaDatapackHotReloadService {
    private final GachaDatapackResourceLoader resourceLoader = new GachaDatapackResourceLoader();
    private final GachaSpecValidator validator = new GachaSpecValidator();
    private final GachaSpecCompiler compiler = new GachaSpecCompiler();

    public ReloadResult reload() {
        var report = resourceLoader.loadFromDatapack();

        Map<ResourceLocation, GachaShopSpec> specs = new LinkedHashMap<>();
        for (Map.Entry<Path, GachaShopSpec> e : report.specs().entrySet()) {
            GachaShopSpec spec = e.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.shopId);
            if (id == null) {
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Invalid shop id '{}' from file {}", spec.shopId, e.getKey());
                continue;
            }
            specs.put(id, spec);
        }

        Map<String, GachaShopDefinition> newDatapackShops = new LinkedHashMap<>();
        int loaded = 0;
        int failed = report.failedCount();

        for (Map.Entry<ResourceLocation, GachaShopSpec> entry : specs.entrySet()) {
            var validation = validator.validate(entry.getValue());
            if (validation.hasErrors()) {
                failed++;
                for (var issue : validation.getIssues()) {
                    if (issue.severity == GachaValidationIssue.Severity.ERROR) {
                        ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    } else {
                        ArcQuestLog.warn(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                GachaShopDefinition shop = compiler.compile(entry.getValue());
                newDatapackShops.put(shop.getShopId(), shop);
                loaded++;
            } catch (Exception ex) {
                failed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Compile/register failed for {}", entry.getKey(), ex);
            }
        }

        if (failed == 0) {
            GachaRegistry.replaceDatapackSnapshot(newDatapackShops);
        } else {
            ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Legacy reload rejected; retaining previous datapack snapshot because failed={}", failed);
        }

        ArcQuestLog.info(ArcQuestLog.Category.QUEST_RELOAD, "Datapack reload complete. scanned={}, loaded={}, failed={}",
                report.scannedFiles(), loaded, failed);

        return new ReloadResult(report.scannedFiles(), loaded, failed);
    }

    public record ReloadResult(int scanned, int loaded, int failed) {
    }
}
