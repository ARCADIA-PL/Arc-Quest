package org.arcadia.arc_quest.trade.io;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;
import org.arcadia.arc_quest.trade.spec.compile.TradeSpecCompiler;
import org.arcadia.arc_quest.trade.spec.validate.TradeSpecValidator;
import org.arcadia.arc_quest.trade.spec.validate.TradeValidationIssue;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TradeDatapackHotReloadService {

    private static final Logger LOGGER = LogUtils.getLogger();

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
                LOGGER.error("[TradeRegistry] Invalid shop id '{}' from file {}", spec.shopId, e.getKey());
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
                        LOGGER.error("[TradeRegistry] {} -> {}", issue.path, issue.message);
                    } else {
                        LOGGER.warn("[TradeRegistry] {} -> {}", issue.path, issue.message);
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
                LOGGER.error("[TradeRegistry] Compile/register failed for {}", entry.getKey(), ex);
            }
        }

        TradeRegistry.replaceDatapack(newDatapackShops);

        LOGGER.info("[TradeRegistry] Datapack reload complete. scanned={}, loaded={}, failed={}",
                report.scannedFiles(), loaded, failed);

        return new ReloadResult(report.scannedFiles(), loaded, failed);
    }

    public record ReloadResult(int scanned, int loaded, int failed) {
    }
}
