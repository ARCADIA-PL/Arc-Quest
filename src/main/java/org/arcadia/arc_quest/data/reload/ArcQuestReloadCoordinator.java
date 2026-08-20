package org.arcadia.arc_quest.data.reload;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;
import org.arcadia.arc_quest.dialogue.spec.compile.DialogueSpecCompiler;
import org.arcadia.arc_quest.dialogue.spec.io.DialogueSpecJsonReader;
import org.arcadia.arc_quest.dialogue.spec.io.DialogueSpecJsonWriter;
import org.arcadia.arc_quest.dialogue.spec.validate.DialogueSpecValidator;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;
import org.arcadia.arc_quest.guide.spec.GuideSpec;
import org.arcadia.arc_quest.guide.spec.compile.GuideCategorySpecCompiler;
import org.arcadia.arc_quest.guide.spec.compile.GuideSpecCompiler;
import org.arcadia.arc_quest.guide.spec.io.GuideCategorySpecJsonReader;
import org.arcadia.arc_quest.guide.spec.io.GuideCategorySpecJsonWriter;
import org.arcadia.arc_quest.guide.spec.io.GuideSpecJsonReader;
import org.arcadia.arc_quest.guide.spec.io.GuideSpecJsonWriter;
import org.arcadia.arc_quest.guide.spec.validate.GuideCategorySpecValidator;
import org.arcadia.arc_quest.guide.spec.validate.GuideSpecValidator;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.npc.spec.io.NpcSpecJsonReader;
import org.arcadia.arc_quest.npc.spec.io.NpcSpecJsonWriter;
import org.arcadia.arc_quest.npc.spec.validate.NpcSpecValidator;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringEntry;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringSnapshotRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;
import org.arcadia.arc_quest.trade.gacha.spec.compile.GachaSpecCompiler;
import org.arcadia.arc_quest.trade.gacha.spec.io.GachaSpecJsonReader;
import org.arcadia.arc_quest.trade.gacha.spec.io.GachaSpecJsonWriter;
import org.arcadia.arc_quest.trade.gacha.spec.validate.GachaSpecValidator;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;
import org.arcadia.arc_quest.trade.spec.compile.TradeSpecCompiler;
import org.arcadia.arc_quest.trade.spec.io.TradeSpecJsonReader;
import org.arcadia.arc_quest.trade.spec.io.TradeSpecJsonWriter;
import org.arcadia.arc_quest.trade.spec.validate.TradeSpecValidator;
import org.arcadia.arc_quest.data.sync.DatapackContentModule;
import org.arcadia.arc_quest.data.sync.DatapackContentSnapshot;
import org.arcadia.arc_quest.data.sync.DatapackContentSyncService;
import org.arcadia.arc_quest.data.sync.DatapackContentTransfer;
import org.arcadia.arc_quest.data.sync.ClientQuestSnapshotProjector;
import org.arcadia.arc_quest.websocket.ArcQuestWebSocketServer;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class ArcQuestReloadCoordinator {
    public static final ArcQuestReloadCoordinator INSTANCE = new ArcQuestReloadCoordinator();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MODULE_COUNT = 6;
    private final AtomicLong epochSequence = new AtomicLong();
    private final Object commitLock = new Object();
    private volatile long committedEpoch;
    private volatile ReloadSummary lastSummary = new ReloadSummary(0, 0, 0, 0, 0, false, 0, null, null);

    private ArcQuestReloadCoordinator() {
    }

    public ReloadPlan prepare() {
        return prepare(null);
    }

    public ReloadPlan prepare(ResourceManager resourceManager) {
        long startedNanos = System.nanoTime();
        ReloadLimits limits = ReloadLimits.configured();
        SafeDatapackScanner scanner = new SafeDatapackScanner(limits);
        CompletableFuture<ModulePreparation<QuestSnapshot>> quests = CompletableFuture.supplyAsync(() -> prepareQuests(scanner, resourceManager));
        CompletableFuture<ModulePreparation<DialogueSnapshot>> dialogues = CompletableFuture.supplyAsync(() -> prepareDialogues(scanner, resourceManager));
        CompletableFuture<ModulePreparation<NpcSnapshot>> npcs = CompletableFuture.supplyAsync(() -> prepareNpcs(scanner, resourceManager));
        CompletableFuture<ModulePreparation<TradeSnapshot>> trades = CompletableFuture.supplyAsync(() -> prepareTrades(scanner, resourceManager));
        CompletableFuture<ModulePreparation<GachaSnapshot>> gachas = CompletableFuture.supplyAsync(() -> prepareGachas(scanner, resourceManager));
        CompletableFuture<ModulePreparation<GuideSnapshot>> guides = CompletableFuture.supplyAsync(() -> prepareGuides(scanner, resourceManager));
        CompletableFuture.allOf(quests, dialogues, npcs, trades, gachas, guides).join();

        ReloadPlan plan = new ReloadPlan(quests.join(), dialogues.join(), npcs.join(), trades.join(), gachas.join(), guides.join(),
                startedNanos, new ArrayList<>());
        validateGlobalLimits(plan, limits);
        validateCrossModuleReferences(plan);
        return plan;
    }

    public ReloadSummary apply(ReloadPlan plan) {
        long epoch = epochSequence.incrementAndGet();
        List<ReloadDiagnostic> diagnostics = plan.allDiagnostics();
        boolean hasErrors = diagnostics.stream().anyMatch(ReloadDiagnostic::blocksReload);
        DatapackContentTransfer contentTransfer = null;
        if (!hasErrors) {
            try {
                contentTransfer = DatapackContentSyncService.prepare(plan.contentSnapshot(epoch));
            } catch (Exception exception) {
                plan.coordinatorDiagnostics().add(ReloadDiagnostic.error("coordinator", null, "client_sync",
                        "Failed to build client datapack snapshot: " + exception.getMessage(), exception));
                hasErrors = true;
            }
        }
        boolean applied = false;
        if (!hasErrors) {
            DatapackContentTransfer preparedTransfer = contentTransfer;
            synchronized (commitLock) {
                var previousQuests = QuestRegistry.getDatapackSnapshot();
                var previousAuthoringQuests = QuestAuthoringSnapshotRegistry.getDatapackSnapshot();
                var previousDialogues = DialogueRegistry.INSTANCE.getDatapackSnapshot();
                var previousNpcs = NpcBindingRegistry.INSTANCE.getDatapackSnapshot();
                long previousNpcEpoch = NpcBindingRegistry.INSTANCE.getSnapshotEpoch();
                var previousTrades = TradeRegistry.getDatapackSnapshot();
                var previousGachas = GachaRegistry.getDatapackSnapshot();
                var previousGuides = GuideRegistry.getDatapackSnapshot();
                try {
                    QuestRegistry.replaceDatapackSnapshot(plan.quests().snapshot().definitions());
                    QuestAuthoringSnapshotRegistry.replaceDatapackSnapshot(plan.quests().snapshot().authoringEntries());
                    DialogueRegistry.INSTANCE.replaceDatapackSnapshot(plan.dialogues().snapshot().trees(),
                            plan.dialogues().snapshot().npcBindings(), plan.dialogues().snapshot().entityBindings(), epoch);
                    NpcBindingRegistry.INSTANCE.replaceDatapackSnapshot(plan.npcs().snapshot().specs(), epoch);
                    TradeRegistry.replaceDatapackSnapshot(plan.trades().snapshot().definitions());
                    GachaRegistry.replaceDatapackSnapshot(plan.gachas().snapshot().definitions());
                    GuideRegistry.replaceDatapackSnapshot(plan.guides().snapshot().definitions());
                    DatapackContentSyncService.commit(preparedTransfer);
                    committedEpoch = epoch;
                    applied = true;
                } catch (RuntimeException exception) {
                    QuestRegistry.replaceDatapackSnapshot(previousQuests);
                    QuestAuthoringSnapshotRegistry.replaceDatapackSnapshot(previousAuthoringQuests);
                    DialogueRegistry.INSTANCE.replaceDatapackSnapshot(previousDialogues.trees(), previousDialogues.npcBindings(),
                            previousDialogues.entityBindings(), previousDialogues.epoch());
                    NpcBindingRegistry.INSTANCE.replaceDatapackSnapshot(previousNpcs, previousNpcEpoch);
                    TradeRegistry.replaceDatapackSnapshot(previousTrades);
                    GachaRegistry.replaceDatapackSnapshot(previousGachas);
                    GuideRegistry.replaceDatapackSnapshot(previousGuides);
                    plan.coordinatorDiagnostics().add(ReloadDiagnostic.error("coordinator", null, "apply",
                            "Atomic apply failed and previous snapshots were restored: " + exception.getMessage(), exception));
                }
            }
            if (applied) {
                DatapackContentSyncService.broadcastCurrent();
                if (!ArcQuestNetwork.tryBroadcastDatapackReloadEpoch(epoch)) {
                    LOGGER.debug("[ArcQuestReload] epoch={} committed before server availability; client notification deferred until login",
                            epoch);
                }
                ArcQuestWebSocketServer.rebuildAndBroadcast(epoch);
            }
        }
        diagnostics = plan.allDiagnostics();
        ReloadSummary summary = summarize(plan, epoch, applied);
        lastSummary = summary;
        logDiagnostics(epoch, diagnostics);
        LOGGER.info("[ArcQuestReload] epoch={} scanned={} successfulModules={} warnings={} failedModules={} applied={} durationMs={}",
                summary.epoch(), summary.scannedFiles(), summary.successfulModules(), summary.warningCount(),
                summary.failedModules(), summary.applied(), summary.durationMillis());
        return summary;
    }

    public long getCommittedEpoch() {
        return committedEpoch;
    }

    public ReloadSummary getLastSummary() {
        return lastSummary;
    }

    private ModulePreparation<QuestSnapshot> prepareQuests(SafeDatapackScanner scanner, ResourceManager manager) {
        String module = "quest";
        var scan = scanner.scan(module, DatapackPathResolver.resolveQuestsDir(), manager, "arc_quest/quests",
                (json, tree) -> QuestSpecJsonReader.read(tree));
        List<ReloadDiagnostic> diagnostics = new ArrayList<>(scan.diagnostics());
        Map<ResourceLocation, QuestDefinition> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, QuestAuthoringEntry> authoringEntries = new LinkedHashMap<>();
        List<String> documents = new ArrayList<>();
        Map<ResourceLocation, Path> sources = new LinkedHashMap<>();
        QuestSpecValidator validator = new QuestSpecValidator();
        QuestSpecCompiler compiler = new QuestSpecCompiler();
        for (Map.Entry<Path, QuestSpec> entry : scan.values().entrySet()) {
            QuestSpec spec = entry.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.id);
            if (!acceptUnique(module, entry.getKey(), id, spec.id, sources, diagnostics)) continue;
            var report = validator.validate(spec);
            report.getIssues().forEach(issue -> diagnostics.add(diagnostic(module, entry.getKey(), issue.path, issue.message,
                    issue.severity.name().equals("ERROR"))));
            if (report.hasErrors()) continue;
            try {
                definitions.put(id, compiler.compile(spec));
                if (!ResourceDatapackScanner.isResourcePath(entry.getKey())) {
                    authoringEntries.put(id, new QuestAuthoringEntry(id, entry.getKey(), spec));
                }
                documents.add(QuestSpecJsonWriter.write(spec));
            }
            catch (Exception exception) { diagnostics.add(ReloadDiagnostic.error(module, entry.getKey(), "$", "Compile failed: " + exception.getMessage(), exception)); }
        }
        return new ModulePreparation<>(module, new QuestSnapshot(Map.copyOf(definitions), Map.copyOf(authoringEntries),
                List.copyOf(documents)), scan.scannedFiles(), scan.parsedBytes(), diagnostics);
    }

    private ModulePreparation<DialogueSnapshot> prepareDialogues(SafeDatapackScanner scanner, ResourceManager manager) {
        String module = "dialogue";
        var scan = scanner.scan(module, DatapackPathResolver.resolveDialoguesDir(), manager, "arc_quest/dialogues",
                (json, tree) -> DialogueSpecJsonReader.read(tree));
        List<ReloadDiagnostic> diagnostics = new ArrayList<>(scan.diagnostics());
        Map<ResourceLocation, Path> sources = new LinkedHashMap<>();
        List<DialogueTree> trees = new ArrayList<>();
        Map<String, String> npcBindings = new LinkedHashMap<>();
        Map<EntityType<?>, String> entityBindings = new LinkedHashMap<>();
        Set<String> dialogueIds = new LinkedHashSet<>();
        List<String> documents = new ArrayList<>();
        DialogueSpecValidator validator = new DialogueSpecValidator();
        DialogueSpecCompiler compiler = new DialogueSpecCompiler();
        for (Map.Entry<Path, DialogueSpec> entry : scan.values().entrySet()) {
            DialogueSpec spec = entry.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.id);
            if (!acceptUnique(module, entry.getKey(), id, spec.id, sources, diagnostics)) continue;
            var report = validator.validate(spec);
            report.getIssues().forEach(issue -> diagnostics.add(diagnostic(module, entry.getKey(), issue.path, issue.message,
                    issue.severity.name().equals("ERROR"))));
            if (report.hasErrors()) continue;
            try {
                trees.add(compiler.compile(spec));
                dialogueIds.add(spec.id);
                documents.add(DialogueSpecJsonWriter.write(spec));
                if (spec.npcBindings != null) spec.npcBindings.forEach(binding -> npcBindings.put(binding.npcId, binding.dialogueId));
                if (spec.entityBindings != null) spec.entityBindings.forEach(binding -> {
                    ResourceLocation entityId = ResourceLocation.tryParse(binding.entityType);
                    EntityType<?> type = entityId == null ? null : BuiltInRegistries.ENTITY_TYPE.get(entityId);
                    if (type == null) diagnostics.add(ReloadDiagnostic.warning(module, entry.getKey(), "entity_bindings", "Unknown entity type: " + binding.entityType));
                    else entityBindings.put(type, binding.dialogueId);
                });
            } catch (Exception exception) {
                diagnostics.add(ReloadDiagnostic.error(module, entry.getKey(), "$", "Compile failed: " + exception.getMessage(), exception));
            }
        }
        for (Map.Entry<String, String> binding : npcBindings.entrySet()) {
            if (!dialogueIds.contains(binding.getValue()) && DialogueRegistry.INSTANCE.getCodeDefinition(binding.getValue()) == null)
                diagnostics.add(ReloadDiagnostic.error(module, null, "npc_bindings." + binding.getKey(), "Unknown dialogue id: " + binding.getValue()));
        }
        return new ModulePreparation<>(module, new DialogueSnapshot(List.copyOf(trees), Map.copyOf(npcBindings),
                Map.copyOf(entityBindings), Set.copyOf(dialogueIds), List.copyOf(documents)),
                scan.scannedFiles(), scan.parsedBytes(), diagnostics);
    }

    private ModulePreparation<NpcSnapshot> prepareNpcs(SafeDatapackScanner scanner, ResourceManager manager) {
        String module = "npc";
        var scan = scanner.scan(module, DatapackPathResolver.resolveNpcDir(), manager, "arc_quest/npc",
                (json, tree) -> NpcSpecJsonReader.read(tree));
        List<ReloadDiagnostic> diagnostics = new ArrayList<>(scan.diagnostics());
        List<NpcSpec> specs = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        Set<String> bindingIds = new LinkedHashSet<>();
        NpcSpecValidator validator = new NpcSpecValidator();
        for (Map.Entry<Path, NpcSpec> entry : scan.values().entrySet()) {
            NpcSpec spec = entry.getValue();
            var report = validator.validate(spec);
            report.getIssues().forEach(issue -> diagnostics.add(diagnostic(module, entry.getKey(), issue.path, issue.message,
                    issue.severity.name().equals("ERROR"))));
            if (spec.bindings != null) spec.bindings.forEach(binding -> {
                if (binding.bindingId != null && !binding.bindingId.isBlank() && !bindingIds.add(binding.bindingId))
                    diagnostics.add(ReloadDiagnostic.error(module, entry.getKey(), "bindings", "Duplicate binding id: " + binding.bindingId));
            });
            if (!report.hasErrors()) {
                specs.add(spec);
                documents.add(NpcSpecJsonWriter.write(spec));
            }
        }
        return new ModulePreparation<>(module, new NpcSnapshot(List.copyOf(specs), List.copyOf(documents)),
                scan.scannedFiles(), scan.parsedBytes(), diagnostics);
    }

    private ModulePreparation<TradeSnapshot> prepareTrades(SafeDatapackScanner scanner, ResourceManager manager) {
        String module = "trade";
        var scan = scanner.scan(module, DatapackPathResolver.resolveTradesDir(), manager, "arc_quest/trades", (json, tree) -> {
            if (isGacha(tree)) throw SafeDatapackScanner.SkipFileException.INSTANCE;
            return TradeSpecJsonReader.read(tree);
        });
        List<ReloadDiagnostic> diagnostics = new ArrayList<>(scan.diagnostics());
        Map<String, TradeShopDefinition> definitions = new LinkedHashMap<>();
        List<String> documents = new ArrayList<>();
        Map<String, Path> sources = new LinkedHashMap<>();
        TradeSpecValidator validator = new TradeSpecValidator();
        TradeSpecCompiler compiler = new TradeSpecCompiler();
        for (Map.Entry<Path, TradeShopSpec> entry : scan.values().entrySet()) {
            TradeShopSpec spec = entry.getValue();
            if (!acceptUniqueString(module, entry.getKey(), spec.shopId, sources, diagnostics)) continue;
            var report = validator.validate(spec);
            report.getIssues().forEach(issue -> diagnostics.add(diagnostic(module, entry.getKey(), issue.path, issue.message,
                    issue.severity.name().equals("ERROR"))));
            if (report.hasErrors()) continue;
            try {
                TradeShopDefinition definition = compiler.compile(spec);
                definitions.put(definition.getShopId(), definition);
                documents.add(TradeSpecJsonWriter.write(spec));
            }
            catch (Exception exception) { diagnostics.add(ReloadDiagnostic.error(module, entry.getKey(), "$", "Compile failed: " + exception.getMessage(), exception)); }
        }
        return new ModulePreparation<>(module, new TradeSnapshot(Map.copyOf(definitions), List.copyOf(documents)),
                scan.scannedFiles(), scan.parsedBytes(), diagnostics);
    }

    private ModulePreparation<GachaSnapshot> prepareGachas(SafeDatapackScanner scanner, ResourceManager manager) {
        String module = "gacha";
        var scan = scanner.scan(module, DatapackPathResolver.resolveTradesDir(), manager, "arc_quest/trades", (json, tree) -> {
            if (!isGacha(tree)) throw SafeDatapackScanner.SkipFileException.INSTANCE;
            return GachaSpecJsonReader.read(tree);
        });
        List<ReloadDiagnostic> diagnostics = new ArrayList<>(scan.diagnostics());
        Map<String, GachaShopDefinition> definitions = new LinkedHashMap<>();
        List<String> documents = new ArrayList<>();
        Map<String, Path> sources = new LinkedHashMap<>();
        GachaSpecValidator validator = new GachaSpecValidator();
        GachaSpecCompiler compiler = new GachaSpecCompiler();
        for (Map.Entry<Path, GachaShopSpec> entry : scan.values().entrySet()) {
            GachaShopSpec spec = entry.getValue();
            if (!acceptUniqueString(module, entry.getKey(), spec.shopId, sources, diagnostics)) continue;
            var report = validator.validate(spec);
            report.getIssues().forEach(issue -> diagnostics.add(diagnostic(module, entry.getKey(), issue.path, issue.message,
                    issue.severity.name().equals("ERROR"))));
            if (report.hasErrors()) continue;
            try {
                GachaShopDefinition definition = compiler.compile(spec);
                definitions.put(definition.getShopId(), definition);
                documents.add(GachaSpecJsonWriter.write(spec));
            }
            catch (Exception exception) { diagnostics.add(ReloadDiagnostic.error(module, entry.getKey(), "$", "Compile failed: " + exception.getMessage(), exception)); }
        }
        return new ModulePreparation<>(module, new GachaSnapshot(Map.copyOf(definitions), List.copyOf(documents)),
                scan.scannedFiles(), scan.parsedBytes(), diagnostics);
    }

    private ModulePreparation<GuideSnapshot> prepareGuides(SafeDatapackScanner scanner, ResourceManager manager) {
        String module = "guide";
        Path root = DatapackPathResolver.resolveDatapackRoot();
        var categoryScan = scanner.scan(module, root.resolve("guide_categories"), manager, "arc_quest/guide_categories",
                (json, tree) -> GuideCategorySpecJsonReader.read(tree));
        var guideScan = scanner.scan(module, root.resolve("guides"), manager, "arc_quest/guides",
                (json, tree) -> GuideSpecJsonReader.read(tree));
        List<ReloadDiagnostic> diagnostics = new ArrayList<>(categoryScan.diagnostics());
        diagnostics.addAll(guideScan.diagnostics());
        Map<ResourceLocation, GuideCategory> categories = new LinkedHashMap<>();
        List<String> categoryDocuments = new ArrayList<>();
        Map<ResourceLocation, Path> categorySources = new LinkedHashMap<>();
        GuideCategorySpecValidator categoryValidator = new GuideCategorySpecValidator();
        GuideCategorySpecCompiler categoryCompiler = new GuideCategorySpecCompiler();
        for (Map.Entry<Path, GuideCategorySpec> entry : categoryScan.values().entrySet()) {
            GuideCategorySpec spec = entry.getValue();
            ResourceLocation id = GuideCategorySpecValidator.parseCategoryId(spec.id);
            if (!acceptUnique(module, entry.getKey(), id, spec.id, categorySources, diagnostics)) continue;
            var report = categoryValidator.validate(spec);
            report.getIssues().forEach(issue -> diagnostics.add(diagnostic(module, entry.getKey(), issue.path, issue.message,
                    issue.severity.name().equals("ERROR"))));
            if (!report.hasErrors()) {
                try {
                    categories.put(id, categoryCompiler.compile(spec));
                    categoryDocuments.add(GuideCategorySpecJsonWriter.write(spec));
                }
                catch (Exception exception) { diagnostics.add(ReloadDiagnostic.error(module, entry.getKey(), "$", "Category compile failed: " + exception.getMessage(), exception)); }
            }
        }
        Map<ResourceLocation, GuideDefinition> definitions = new LinkedHashMap<>();
        List<String> guideDocuments = new ArrayList<>();
        Map<ResourceLocation, Path> guideSources = new LinkedHashMap<>();
        GuideSpecValidator guideValidator = new GuideSpecValidator();
        GuideSpecCompiler guideCompiler = new GuideSpecCompiler(categories);
        for (Map.Entry<Path, GuideSpec> entry : guideScan.values().entrySet()) {
            GuideSpec spec = entry.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.id);
            if (!acceptUnique(module, entry.getKey(), id, spec.id, guideSources, diagnostics)) continue;
            var report = guideValidator.validate(spec);
            report.getIssues().forEach(issue -> diagnostics.add(diagnostic(module, entry.getKey(), issue.path, issue.message,
                    issue.severity.name().equals("ERROR"))));
            if (!report.hasErrors()) {
                try {
                    definitions.put(id, guideCompiler.compile(spec));
                    guideDocuments.add(GuideSpecJsonWriter.write(spec));
                }
                catch (Exception exception) { diagnostics.add(ReloadDiagnostic.error(module, entry.getKey(), "$", "Guide compile failed: " + exception.getMessage(), exception)); }
            }
        }
        Set<Path> files = new LinkedHashSet<>(categoryScan.scannedFiles());
        files.addAll(guideScan.scannedFiles());
        return new ModulePreparation<>(module, new GuideSnapshot(Map.copyOf(definitions), Set.copyOf(categories.keySet()),
                List.copyOf(categoryDocuments), List.copyOf(guideDocuments)),
                files, categoryScan.parsedBytes() + guideScan.parsedBytes(), diagnostics);
    }

    private void validateGlobalLimits(ReloadPlan plan, ReloadLimits limits) {
        Set<Path> files = plan.allScannedFiles();
        if (files.size() > limits.maxFiles()) plan.coordinatorDiagnostics().add(ReloadDiagnostic.error("coordinator", null, "$", "Total file count exceeds limit " + limits.maxFiles()));
        long bytes = 0L;
        for (Path file : files) {
            try { bytes += Files.size(file); }
            catch (Exception exception) { plan.coordinatorDiagnostics().add(ReloadDiagnostic.error("coordinator", file, "$", "Failed to determine file size", exception)); }
        }
        if (bytes > limits.maxParsedBytes()) plan.coordinatorDiagnostics().add(ReloadDiagnostic.error("coordinator", null, "$", "Total parsed bytes " + bytes + " exceed limit " + limits.maxParsedBytes()));
    }

    private void validateCrossModuleReferences(ReloadPlan plan) {
        Set<String> dialogueIds = plan.dialogues().snapshot().dialogueIds();
        for (NpcSpec npc : plan.npcs().snapshot().specs()) {
            if (npc.bindings == null) continue;
            npc.bindings.forEach(binding -> {
                if (binding.dialogueId != null && !binding.dialogueId.isBlank() && !dialogueIds.contains(binding.dialogueId)
                        && DialogueRegistry.INSTANCE.getCodeDefinition(binding.dialogueId) == null)
                    plan.coordinatorDiagnostics().add(ReloadDiagnostic.error("npc", null, "bindings.dialogue_id", "Unknown dialogue id: " + binding.dialogueId));
            });
        }
        Set<String> tradeIds = plan.trades().snapshot().definitions().keySet();
        Set<String> gachaIds = plan.gachas().snapshot().definitions().keySet();
        Set<ResourceLocation> guideIds = plan.guides().snapshot().definitions().keySet();
        for (QuestDefinition quest : plan.quests().snapshot().definitions().values()) {
            if (quest.hasChapterShop()) {
                Set<String> shops = quest.getChapterShopType().name().equals("GACHA") ? gachaIds : tradeIds;
                boolean codeShopExists = quest.getChapterShopType().name().equals("GACHA")
                        ? GachaRegistry.getCodeDefinition(quest.getChapterShopId()) != null
                        : TradeRegistry.getCodeDefinition(quest.getChapterShopId()) != null;
                if (!shops.contains(quest.getChapterShopId()) && !codeShopExists)
                    plan.coordinatorDiagnostics().add(ReloadDiagnostic.warning("quest", null, "chapter_shop_id",
                            "Optional chapter shop id is unavailable; quest remains loadable: " + quest.getChapterShopId()));
            }
            for (PhaseDefinition phase : quest.getAllPhases()) {
                if (phase.hasTradeShop() && !tradeIds.contains(phase.getTradeShopId())
                        && TradeRegistry.getCodeDefinition(phase.getTradeShopId()) == null)
                    plan.coordinatorDiagnostics().add(ReloadDiagnostic.warning("quest", null,
                            "phases." + phase.getPhaseId() + ".trade_shop_id",
                            "Optional phase trade shop id is unavailable; phase remains loadable: " + phase.getTradeShopId()));
                validateGuideReferences(plan, quest, phase.getGuidesToGrantOnEnter(), guideIds, "guides_to_grant_on_enter");
                validateGuideReferences(plan, quest, phase.getGuidesToGrantOnComplete(), guideIds, "guides_to_grant_on_complete");
            }
        }
    }

    private void validateGuideReferences(ReloadPlan plan, QuestDefinition quest, Collection<ResourceLocation> references,
                                         Set<ResourceLocation> guideIds, String field) {
        for (ResourceLocation guideId : references) {
            if (!guideIds.contains(guideId) && GuideRegistry.getCodeDefinition(guideId) == null)
                plan.coordinatorDiagnostics().add(ReloadDiagnostic.error("quest", null, quest.getId() + "." + field, "Unknown guide id: " + guideId));
        }
    }

    private static boolean isGacha(JsonElement tree) {
        if (!tree.isJsonObject()) return false;
        var object = tree.getAsJsonObject();
        return object.has("pools") || object.has("drawCost") || object.has("draw_cost") || object.has("drawCosts") || object.has("draw_costs");
    }

    private static <K> boolean acceptUnique(String module, Path file, K id, String rawId, Map<K, Path> sources,
                                            List<ReloadDiagnostic> diagnostics) {
        if (id == null) {
            diagnostics.add(ReloadDiagnostic.error(module, file, "id", "Invalid id: " + rawId));
            return false;
        }
        Path previous = sources.putIfAbsent(id, file);
        if (previous != null) {
            diagnostics.add(ReloadDiagnostic.error(module, file, "id", "Duplicate id " + id + "; first declared in " + previous));
            return false;
        }
        return true;
    }

    private static boolean acceptUniqueString(String module, Path file, String id, Map<String, Path> sources,
                                              List<ReloadDiagnostic> diagnostics) {
        if (id == null || id.isBlank()) {
            diagnostics.add(ReloadDiagnostic.error(module, file, "id", "ID must not be blank"));
            return false;
        }
        return acceptUnique(module, file, id, id, sources, diagnostics);
    }

    private static ReloadDiagnostic diagnostic(String module, Path file, String path, String message, boolean error) {
        return error ? ReloadDiagnostic.error(module, file, path, message) : ReloadDiagnostic.warning(module, file, path, message);
    }

    private ReloadSummary summarize(ReloadPlan plan, long epoch, boolean applied) {
        List<ReloadDiagnostic> diagnostics = plan.allDiagnostics();
        int warnings = (int) diagnostics.stream().filter(d -> d.severity() == ReloadDiagnostic.Severity.WARNING).count();
        int failedModules = (int) plan.modules().stream().filter(ModulePreparation::hasBlockingErrors).count();
        if (plan.coordinatorDiagnostics().stream().anyMatch(ReloadDiagnostic::isBlocking)) failedModules = Math.max(1, failedModules);
        ReloadDiagnostic firstError = diagnostics.stream().filter(ReloadDiagnostic::isBlocking).findFirst().orElse(null);
        return new ReloadSummary(epoch, plan.allScannedFiles().size(), applied ? MODULE_COUNT : MODULE_COUNT - failedModules,
                warnings, failedModules, applied, (System.nanoTime() - plan.startedNanos()) / 1_000_000L,
                firstError == null || firstError.file() == null ? null : firstError.file().toString(),
                firstError == null ? null : firstError.message());
    }

    private static void logDiagnostics(long epoch, List<ReloadDiagnostic> diagnostics) {
        for (ReloadDiagnostic diagnostic : diagnostics) {
            String file = diagnostic.file() == null ? "<none>" : diagnostic.file().toString();
            if (diagnostic.isBlocking()) LOGGER.error("[ArcQuestReload] epoch={} module={} file={} path={} message={}", epoch, diagnostic.module(), file, diagnostic.location(), diagnostic.message(), diagnostic.cause());
            else LOGGER.warn("[ArcQuestReload] epoch={} module={} file={} path={} message={}", epoch, diagnostic.module(), file, diagnostic.location(), diagnostic.message());
        }
    }

    public record QuestSnapshot(Map<ResourceLocation, QuestDefinition> definitions,
                                Map<ResourceLocation, QuestAuthoringEntry> authoringEntries,
                                List<String> documents) { }
    public record DialogueSnapshot(List<DialogueTree> trees, Map<String, String> npcBindings,
                                   Map<EntityType<?>, String> entityBindings, Set<String> dialogueIds,
                                   List<String> documents) { }
    public record NpcSnapshot(List<NpcSpec> specs, List<String> documents) { }
    public record TradeSnapshot(Map<String, TradeShopDefinition> definitions, List<String> documents) { }
    public record GachaSnapshot(Map<String, GachaShopDefinition> definitions, List<String> documents) { }
    public record GuideSnapshot(Map<ResourceLocation, GuideDefinition> definitions, Set<ResourceLocation> categoryIds,
                                List<String> categoryDocuments, List<String> guideDocuments) { }

    public record ReloadPlan(ModulePreparation<QuestSnapshot> quests,
                             ModulePreparation<DialogueSnapshot> dialogues,
                             ModulePreparation<NpcSnapshot> npcs,
                             ModulePreparation<TradeSnapshot> trades,
                             ModulePreparation<GachaSnapshot> gachas,
                             ModulePreparation<GuideSnapshot> guides,
                             long startedNanos,
                             List<ReloadDiagnostic> coordinatorDiagnostics) {
        public List<ModulePreparation<?>> modules() {
            return List.of(quests, dialogues, npcs, trades, gachas, guides);
        }

        public Set<Path> allScannedFiles() {
            Set<Path> files = new LinkedHashSet<>();
            modules().forEach(module -> files.addAll(module.scannedFiles()));
            return files;
        }

        public List<ReloadDiagnostic> allDiagnostics() {
            List<ReloadDiagnostic> diagnostics = new ArrayList<>();
            modules().forEach(module -> diagnostics.addAll(module.diagnostics()));
            diagnostics.addAll(coordinatorDiagnostics);
            return diagnostics;
        }

        public DatapackContentSnapshot contentSnapshot(long epoch) {
            return new DatapackContentSnapshot(epoch, Map.of(
                    DatapackContentModule.QUEST, quests.snapshot().documents(),
                    DatapackContentModule.DIALOGUE, dialogues.snapshot().documents(),
                    DatapackContentModule.NPC, npcs.snapshot().documents(),
                    DatapackContentModule.TRADE, trades.snapshot().documents(),
                    DatapackContentModule.GACHA, gachas.snapshot().documents(),
                    DatapackContentModule.GUIDE_CATEGORY, guides.snapshot().categoryDocuments(),
                    DatapackContentModule.GUIDE, guides.snapshot().guideDocuments()),
                    ClientQuestSnapshotProjector.projectObjectiveTypes(quests.snapshot().definitions()));
        }
    }
}
