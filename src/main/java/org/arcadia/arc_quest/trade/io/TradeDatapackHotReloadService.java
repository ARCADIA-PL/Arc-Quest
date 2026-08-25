package org.arcadia.arc_quest.trade.io;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;
import org.arcadia.arc_quest.trade.spec.compile.TradeSpecCompiler;
import org.arcadia.arc_quest.trade.spec.validate.TradeSpecValidator;
import org.arcadia.arc_quest.trade.spec.validate.TradeValidationIssue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TradeDatapackHotReloadService {
    private final TradeDatapackResourceLoader resourceLoader = new TradeDatapackResourceLoader();
    private final TradeSpecValidator validator = new TradeSpecValidator();
    private final TradeSpecCompiler compiler = new TradeSpecCompiler();

    public ReloadResult reload() {
        var report = resourceLoader.loadFromDatapack();

        Map<ResourceLocation, TradeShopSpec> specs = new LinkedHashMap<>();
        for (Map.Entry<Path, TradeShopSpec> e : report.specs().entrySet()) {
            TradeShopSpec spec = e.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.shopId);
            if (id == null) {
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Invalid shop id '{}' from file {}", spec.shopId, e.getKey());
                continue;
            }
            specs.put(id, spec);
        }

        Map<String, TradeShopDefinition> newDatapackShops = new LinkedHashMap<>();
        int loaded = 0;
        int failed = report.failedCount();

        for (Map.Entry<ResourceLocation, TradeShopSpec> entry : specs.entrySet()) {
            var validation = validator.validate(entry.getValue());
            if (validation.hasErrors()) {
                failed++;
                for (var issue : validation.getIssues()) {
                    if (issue.severity == TradeValidationIssue.Severity.ERROR) {
                        ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    } else {
                        ArcQuestLog.warn(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                TradeShopDefinition shop = compiler.compile(entry.getValue());
                newDatapackShops.put(shop.getShopId(), shop);
                loaded++;
            } catch (Exception ex) {
                failed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Compile/register failed for {}", entry.getKey(), ex);
            }
        }

        if (failed == 0) {
            TradeRegistry.replaceDatapackSnapshot(newDatapackShops);
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
