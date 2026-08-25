package org.arcadia.arc_quest.dialogue.io;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;
import org.arcadia.arc_quest.dialogue.spec.compile.DialogueSpecCompiler;
import org.arcadia.arc_quest.dialogue.spec.validate.DialogueSpecValidator;
import org.arcadia.arc_quest.dialogue.spec.validate.DialogueValidationIssue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DialogueDatapackHotReloadService {
    private final DialogueDatapackResourceLoader resourceLoader = new DialogueDatapackResourceLoader();
    private final DialogueSpecValidator validator = new DialogueSpecValidator();
    private final DialogueSpecCompiler compiler = new DialogueSpecCompiler();

    public ReloadResult reload() {
        var report = resourceLoader.loadFromDatapack();

        Map<ResourceLocation, DialogueSpec> specs = new LinkedHashMap<>();
        for (Map.Entry<Path, DialogueSpec> e : report.specs().entrySet()) {
            DialogueSpec spec = e.getValue();
            ResourceLocation id = ResourceLocation.tryParse(spec.id);
            if (id == null) {
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Invalid dialogue id '{}' from file {}", spec.id, e.getKey());
                continue;
            }
            specs.put(id, spec);
        }

        int loaded = 0;
        int failed = report.failedCount();
        List<DialogueTree> stagedTrees = new ArrayList<>();
        Map<String, String> stagedNpcBindings = new LinkedHashMap<>();
        Map<EntityType<?>, String> stagedEntityBindings = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, DialogueSpec> entry : specs.entrySet()) {
            var validation = validator.validate(entry.getValue());
            if (validation.hasErrors()) {
                failed++;
                for (var issue : validation.getIssues()) {
                    if (issue.severity == DialogueValidationIssue.Severity.ERROR) {
                        ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    } else {
                        ArcQuestLog.warn(ArcQuestLog.Category.QUEST_RELOAD, "{} -> {}", issue.path, issue.message);
                    }
                }
                continue;
            }
            try {
                DialogueTree tree = compiler.compile(entry.getValue());
                stagedTrees.add(tree);

                if (entry.getValue().npcBindings != null) {
                    for (var binding : entry.getValue().npcBindings) {
                        stagedNpcBindings.put(binding.npcId, binding.dialogueId);
                    }
                }
                if (entry.getValue().entityBindings != null) {
                    for (var binding : entry.getValue().entityBindings) {
                        var entityType = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(binding.entityType));
                        if (entityType != null) {
                            stagedEntityBindings.put(entityType, binding.dialogueId);
                        } else {
                            ArcQuestLog.warn(ArcQuestLog.Category.QUEST_RELOAD, "Unknown entity type '{}' in dialogue '{}' binding",
                                    binding.entityType, entry.getValue().id);
                        }
                    }
                }

                loaded++;
            } catch (Exception ex) {
                failed++;
                ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Compile/register failed for {}", entry.getKey(), ex);
            }
        }

        long epoch = DialogueRegistry.INSTANCE.getDatapackEpoch();
        if (failed == 0) {
            epoch = DialogueRegistry.INSTANCE.replaceDatapack(
                    stagedTrees, stagedNpcBindings, stagedEntityBindings);
        } else {
            ArcQuestLog.error(ArcQuestLog.Category.QUEST_RELOAD, "Datapack reload rejected; retaining previous snapshot epoch={} because failed={}",
                    epoch, failed);
        }

        ArcQuestLog.info(ArcQuestLog.Category.QUEST_RELOAD, "Datapack reload complete. scanned={}, loaded={}, failed={}, activeDatapack={}, epoch={}",
                report.scannedFiles(), loaded, failed, DialogueRegistry.INSTANCE.datapackSize(), epoch);

        return new ReloadResult(report.scannedFiles(), loaded, failed, DialogueRegistry.INSTANCE.datapackSize());
    }

    public record ReloadResult(int scanned, int discovered, int failed, int activeDatapack) {
    }
}
