package org.arcadia.arc_quest.data;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.registry.EpicDialogueTrees;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecResourceLoader;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.quest.spec.validate.ValidationIssue;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArcQuestReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, QuestSpec>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final QuestSpecResourceLoader STATIC_LOADER = new QuestSpecResourceLoader();
    private static final QuestSpecValidator STATIC_VALIDATOR = new QuestSpecValidator();
    private static final QuestSpecCompiler STATIC_COMPILER = new QuestSpecCompiler();

    private final QuestSpecResourceLoader loader = new QuestSpecResourceLoader();
    private final QuestSpecValidator validator = new QuestSpecValidator();
    private final QuestSpecCompiler compiler = new QuestSpecCompiler();

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ArcQuestReloadListener());
        LOGGER.info("[ArcQuest] Registered datapack reload listener.");
    }

    public static int reloadArcQuestDatapacksOnly(@NotNull ResourceManager manager) {
        Map<ResourceLocation, QuestSpec> specs = STATIC_LOADER.load(manager);
        EpicDialogueTrees.registerAll();
        QuestRegistry.clearDatapack();

        int loaded = 0;
        int failed = 0;
        for (Map.Entry<ResourceLocation, QuestSpec> entry : specs.entrySet()) {
            QuestSpec spec = entry.getValue();
            var report = STATIC_VALIDATOR.validate(spec);
            if (report.hasErrors()) {
                failed++;
                for (var issue : report.getIssues()) {
                    if (issue.severity == ValidationIssue.Severity.ERROR) {
                        LOGGER.error("[ArcQuest]   - {}: {}", issue.path, issue.message);
                    } else {
                        LOGGER.warn("[ArcQuest]   - {}: {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                QuestRegistry.registerDatapack(STATIC_COMPILER.compile(spec), entry.getKey().toString());
                loaded++;
            } catch (Exception e) {
                failed++;
                LOGGER.error("[ArcQuest] Failed to compile datapack quest '{}': {}", entry.getKey(), e.getMessage(), e);
            }
        }

        LOGGER.info("[ArcQuest] ArcQuest-only datapack reload complete. loaded={}, failed={}, activeDatapack={}, merged={}",
                loaded, failed, QuestRegistry.datapackSize(), QuestRegistry.size());
        return loaded;
    }

    @Override
    protected @NotNull Map<ResourceLocation, QuestSpec> prepare(@NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        profiler.startTick();
        Map<ResourceLocation, QuestSpec> specs = loader.load(manager);
        LOGGER.info("[ArcQuest] Prepared {} datapack quest spec resource(s).", specs.size());
        profiler.endTick();
        return specs;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, QuestSpec> specs, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        profiler.startTick();
        EpicDialogueTrees.registerAll();
        QuestRegistry.clearDatapack();
        int loaded = 0;
        int failed = 0;
        for (Map.Entry<ResourceLocation, QuestSpec> entry : specs.entrySet()) {
            QuestSpec spec = entry.getValue();
            LOGGER.info("[ArcQuest] Loading quest spec resource '{}' with quest id '{}'", entry.getKey(), spec == null ? "<null>" : spec.id);
            var report = validator.validate(spec);
            if (report.hasErrors()) {
                failed++;
                LOGGER.error("[ArcQuest] Failed to load quest spec '{}': validation errors", entry.getKey());
                for (var issue : report.getIssues()) {
                    if (issue.severity == ValidationIssue.Severity.ERROR) {
                        LOGGER.error("[ArcQuest]   - {}: {}", issue.path, issue.message);
                    } else {
                        LOGGER.warn("[ArcQuest]   - {}: {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                QuestRegistry.registerDatapack(compiler.compile(spec), entry.getKey().toString());
                loaded++;
            } catch (Exception e) {
                failed++;
                LOGGER.error("[ArcQuest] Failed to compile datapack quest '{}': {}", entry.getKey(), e.getMessage(), e);
            }
        }
        LOGGER.info("[ArcQuest] Datapack quest reload complete. loaded={}, failed={}, activeDatapack={}, merged={}", loaded, failed, QuestRegistry.datapackSize(), QuestRegistry.size());
        LOGGER.info("[ArcQuest] Epic dialogues re-registered after reload.");
        profiler.endTick();
    }
}
