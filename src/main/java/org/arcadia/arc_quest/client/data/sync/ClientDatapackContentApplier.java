package org.arcadia.arc_quest.client.data.sync;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.data.sync.DatapackContentModule;
import org.arcadia.arc_quest.data.sync.DatapackContentSnapshot;
import org.arcadia.arc_quest.data.sync.DatapackRegistrySnapshotLifecycle;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;
import org.arcadia.arc_quest.dialogue.spec.compile.DialogueSpecCompiler;
import org.arcadia.arc_quest.dialogue.spec.io.DialogueSpecJsonReader;
import org.arcadia.arc_quest.dialogue.spec.validate.DialogueSpecValidator;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;
import org.arcadia.arc_quest.guide.spec.GuideSpec;
import org.arcadia.arc_quest.guide.spec.compile.GuideCategorySpecCompiler;
import org.arcadia.arc_quest.guide.spec.compile.GuideSpecCompiler;
import org.arcadia.arc_quest.guide.spec.io.GuideCategorySpecJsonReader;
import org.arcadia.arc_quest.guide.spec.io.GuideSpecJsonReader;
import org.arcadia.arc_quest.guide.spec.validate.GuideCategorySpecValidator;
import org.arcadia.arc_quest.guide.spec.validate.GuideSpecValidator;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.npc.spec.io.NpcSpecJsonReader;
import org.arcadia.arc_quest.npc.spec.validate.NpcSpecValidator;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.registry.ObjectiveTypeRegistry;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;
import org.arcadia.arc_quest.trade.gacha.spec.compile.GachaSpecCompiler;
import org.arcadia.arc_quest.trade.gacha.spec.io.GachaSpecJsonReader;
import org.arcadia.arc_quest.trade.gacha.spec.validate.GachaSpecValidator;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;
import org.arcadia.arc_quest.trade.spec.compile.TradeSpecCompiler;
import org.arcadia.arc_quest.trade.spec.io.TradeSpecJsonReader;
import org.arcadia.arc_quest.trade.spec.validate.TradeSpecValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientDatapackContentApplier {
    private ClientDatapackContentApplier() {
    }

    public static ApplyResult apply(DatapackContentSnapshot snapshot) {
        List<String> failures = new ArrayList<>();
        applyModule("quest", failures, () -> QuestRegistry.replaceDatapackSnapshot(compileQuests(snapshot)));
        applyModule("dialogue", failures, () -> {
            DialogueCompilation compiled = compileDialogues(snapshot.documents(DatapackContentModule.DIALOGUE));
            DialogueRegistry.INSTANCE.replaceDatapackSnapshot(compiled.trees(), compiled.npcBindings(),
                    compiled.entityBindings(), snapshot.epoch());
        });
        applyModule("npc", failures, () -> NpcBindingRegistry.INSTANCE.replaceDatapackSnapshot(
                compileNpcs(snapshot.documents(DatapackContentModule.NPC)), snapshot.epoch()));
        applyModule("trade", failures, () -> TradeRegistry.replaceDatapackSnapshot(
                compileTrades(snapshot.documents(DatapackContentModule.TRADE))));
        applyModule("gacha", failures, () -> GachaRegistry.replaceDatapackSnapshot(
                compileGachas(snapshot.documents(DatapackContentModule.GACHA))));
        applyModule("guide", failures, () -> GuideRegistry.replaceDatapackSnapshot(compileGuides(
                snapshot.documents(DatapackContentModule.GUIDE_CATEGORY),
                snapshot.documents(DatapackContentModule.GUIDE))));
        return new ApplyResult(snapshot.epoch(), List.copyOf(failures));
    }

    public static void clear(long epoch) {
        DatapackRegistrySnapshotLifecycle.clear(epoch);
    }

    private static Map<ResourceLocation, QuestDefinition> compileQuests(DatapackContentSnapshot snapshot) {
        Map<ResourceLocation, ObjectiveType> objectiveTypes = new LinkedHashMap<>();
        snapshot.objectiveTypes().forEach(descriptor -> objectiveTypes.put(descriptor.id(),
                ObjectiveTypeRegistry.get(descriptor.id()) != null
                        ? ObjectiveTypeRegistry.get(descriptor.id())
                        : descriptor.createClientType()));
        Map<ResourceLocation, QuestDefinition> definitions = new LinkedHashMap<>();
        for (String document : snapshot.documents(DatapackContentModule.QUEST)) {
            QuestSpec spec = QuestSpecJsonReader.read(document);
            if (spec == null || spec.phases == null || spec.phases.isEmpty()) {
                throw new IllegalArgumentException("Invalid synchronized quest structure");
            }
            ResourceLocation id = requireId(spec.id, "quest");
            if (definitions.putIfAbsent(id, QuestSpecCompiler.compileClientPresentation(spec, objectiveTypes)) != null) {
                throw new IllegalArgumentException("Duplicate synchronized quest: " + id);
            }
        }
        return Map.copyOf(definitions);
    }

    private static DialogueCompilation compileDialogues(List<String> documents) {
        DialogueSpecValidator validator = new DialogueSpecValidator();
        DialogueSpecCompiler compiler = new DialogueSpecCompiler();
        List<DialogueTree> trees = new ArrayList<>();
        Map<String, String> npcBindings = new LinkedHashMap<>();
        Map<EntityType<?>, String> entityBindings = new LinkedHashMap<>();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String document : documents) {
            DialogueSpec spec = DialogueSpecJsonReader.read(document);
            if (validator.validate(spec).hasErrors()) throw new IllegalArgumentException("Invalid synchronized dialogue: " + spec.id);
            ResourceLocation id = requireId(spec.id, "dialogue");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate synchronized dialogue: " + id);
            trees.add(compiler.compile(spec));
            if (spec.npcBindings != null) {
                spec.npcBindings.forEach(binding -> npcBindings.put(binding.npcId, binding.dialogueId));
            }
            if (spec.entityBindings != null) {
                spec.entityBindings.forEach(binding -> {
                    ResourceLocation entityId = ResourceLocation.tryParse(binding.entityType);
                    EntityType<?> entityType = entityId == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(entityId);
                    if (entityType == null) throw new IllegalArgumentException("Unknown synchronized entity type: " + binding.entityType);
                    entityBindings.put(entityType, binding.dialogueId);
                });
            }
        }
        return new DialogueCompilation(List.copyOf(trees), Map.copyOf(npcBindings), Map.copyOf(entityBindings));
    }

    private static List<NpcSpec> compileNpcs(List<String> documents) {
        NpcSpecValidator validator = new NpcSpecValidator();
        List<NpcSpec> specs = new ArrayList<>();
        Set<String> bindingIds = new LinkedHashSet<>();
        for (String document : documents) {
            NpcSpec spec = NpcSpecJsonReader.read(document);
            if (validator.validate(spec).hasErrors()) throw new IllegalArgumentException("Invalid synchronized NPC spec");
            if (spec.bindings != null) {
                spec.bindings.forEach(binding -> {
                    if (binding.bindingId != null && !binding.bindingId.isBlank() && !bindingIds.add(binding.bindingId)) {
                        throw new IllegalArgumentException("Duplicate synchronized NPC binding: " + binding.bindingId);
                    }
                });
            }
            specs.add(spec);
        }
        return List.copyOf(specs);
    }

    private static Map<String, TradeShopDefinition> compileTrades(List<String> documents) {
        TradeSpecValidator validator = new TradeSpecValidator();
        TradeSpecCompiler compiler = new TradeSpecCompiler();
        Map<String, TradeShopDefinition> definitions = new LinkedHashMap<>();
        for (String document : documents) {
            TradeShopSpec spec = TradeSpecJsonReader.read(document);
            if (validator.validate(spec).hasErrors()) throw new IllegalArgumentException("Invalid synchronized trade: " + spec.shopId);
            if (spec.shopId == null || spec.shopId.isBlank()) throw new IllegalArgumentException("Blank synchronized trade id");
            TradeShopDefinition definition = compiler.compile(spec);
            if (definitions.putIfAbsent(definition.getShopId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate synchronized trade: " + definition.getShopId());
            }
        }
        return Map.copyOf(definitions);
    }

    private static Map<String, GachaShopDefinition> compileGachas(List<String> documents) {
        GachaSpecValidator validator = new GachaSpecValidator();
        GachaSpecCompiler compiler = new GachaSpecCompiler();
        Map<String, GachaShopDefinition> definitions = new LinkedHashMap<>();
        for (String document : documents) {
            GachaShopSpec spec = GachaSpecJsonReader.read(document);
            if (validator.validate(spec).hasErrors()) throw new IllegalArgumentException("Invalid synchronized gacha: " + spec.shopId);
            if (spec.shopId == null || spec.shopId.isBlank()) throw new IllegalArgumentException("Blank synchronized gacha id");
            GachaShopDefinition definition = compiler.compile(spec);
            if (definitions.putIfAbsent(definition.getShopId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate synchronized gacha: " + definition.getShopId());
            }
        }
        return Map.copyOf(definitions);
    }

    private static Map<ResourceLocation, GuideDefinition> compileGuides(List<String> categoryDocuments,
                                                                        List<String> guideDocuments) {
        GuideCategorySpecValidator categoryValidator = new GuideCategorySpecValidator();
        GuideCategorySpecCompiler categoryCompiler = new GuideCategorySpecCompiler();
        Map<ResourceLocation, GuideCategory> categories = new LinkedHashMap<>();
        for (String document : categoryDocuments) {
            GuideCategorySpec spec = GuideCategorySpecJsonReader.read(document);
            if (categoryValidator.validate(spec).hasErrors()) {
                throw new IllegalArgumentException("Invalid synchronized guide category: " + spec.id);
            }
            ResourceLocation id = requireId(spec.id, "guide category");
            if (categories.putIfAbsent(id, categoryCompiler.compile(spec)) != null) {
                throw new IllegalArgumentException("Duplicate synchronized guide category: " + id);
            }
        }
        GuideSpecValidator guideValidator = new GuideSpecValidator();
        GuideSpecCompiler guideCompiler = new GuideSpecCompiler(categories);
        Map<ResourceLocation, GuideDefinition> definitions = new LinkedHashMap<>();
        for (String document : guideDocuments) {
            GuideSpec spec = GuideSpecJsonReader.read(document);
            if (guideValidator.validate(spec).hasErrors()) throw new IllegalArgumentException("Invalid synchronized guide: " + spec.id);
            ResourceLocation id = requireId(spec.id, "guide");
            if (definitions.putIfAbsent(id, guideCompiler.compile(spec)) != null) {
                throw new IllegalArgumentException("Duplicate synchronized guide: " + id);
            }
        }
        return Map.copyOf(definitions);
    }

    private static ResourceLocation requireId(String rawId, String type) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) throw new IllegalArgumentException("Invalid synchronized " + type + " id: " + rawId);
        return id;
    }

    private static void applyModule(String module, List<String> failures, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            failures.add(module);
            ArcQuestLog.error(ArcQuestLog.Category.DATA, "Failed to apply client module {}", module, exception);
        }
    }

    public record ApplyResult(long epoch, List<String> failedModules) {
        public boolean fullyApplied() {
            return failedModules.isEmpty();
        }
    }

    private record DialogueCompilation(List<DialogueTree> trees, Map<String, String> npcBindings,
                                       Map<EntityType<?>, String> entityBindings) {
    }

}
