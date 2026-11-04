package org.arcadia.arc_quest.integration.jei.collection;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.integration.jei.api.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.logic.CollectionRunAccess;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.data.sync.CollectionContentDisclosure;
import java.util.*;
import java.util.function.Consumer;

/** Catalog rows come from the same frozen, player-authorized binding projection as the journal. */
public final class CollectionJeiCatalogProvider implements JeiCatalogProvider {
    @Override public void collect(ServerPlayer player, ArcQuestPlayer data, Consumer<JeiCatalogEntry> sink) {
        if (player == null) return;
        collectKnown(player, data, runtime -> CollectionRunAccess.resolve(player.server, runtime), sink);
    }

    static void collectKnown(ServerPlayer player, ArcQuestPlayer data,
            java.util.function.Function<org.arcadia.arc_quest.quest.data.QuestRuntimeData, QuestDefinition> definitions,
            Consumer<JeiCatalogEntry> sink) {
        Set<String> ids = new TreeSet<>(data.getAllActiveQuests().keySet());
        ids.addAll(data.getCompletedQuests()); ids.addAll(data.getFailedQuests());
        Map<net.minecraft.resources.ResourceLocation, CollectionEntryDefinition> authorizedSources = new HashMap<>();
        for (String knownId : ids) {
            var knownRun = data.getActiveQuest(knownId);
            if (knownRun == null) knownRun = data.getCollectionArchives().get(knownId);
            if (knownRun == null || !knownRun.hasCollectionData()) continue;
            var knownQuest = definitions.apply(knownRun);
            if (knownQuest == null || knownQuest.getCollectionConfig() == null) continue;
            for (var entryId : visibleEntries(player, knownQuest, knownRun, data)) {
                var source = knownQuest.getCollectionConfig().getEntry(entryId);
                if (source != null && CollectionContentDisclosure.entryRevealed(source, data.getCollectionRecords()))
                    authorizedSources.putIfAbsent(entryId, source);
            }
        }
        for (String id : ids) {
            var runtime = data.getActiveQuest(id);
            if (runtime == null) runtime = data.getCollectionArchives().get(id);
            if (runtime == null || !runtime.hasCollectionData()) continue;
            var quest = definitions.apply(runtime);
            if (quest == null || quest.getCollectionConfig() == null) continue;
            for (var phase : quest.getAllPhases()) {
                if (!phase.hasCollectionSheet() || !(runtime.isPhaseActive(phase.getPhaseId())
                        || runtime.isPhaseCompleted(phase.getPhaseId()) || runtime.isPhasePendingManualAdvance(phase.getPhaseId()))) continue;
                var projected = CollectionProgressProjector.project(quest, phase, runtime, data.getCollectionRecords());
                for (var binding : phase.getCollectionSheet().getBindings()) {
                    var row = projected.binding(binding.getBindingId());
                    if (row == null || !row.visible() || !row.revealed()) continue;
                    var entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
                    if (entry == null || !categoryVisible(player, data, quest, entry)) continue;
                    var presentation = entry.getPresentation();
                    if (presentation == null) continue;
                    emit(sink, id, phase.getPhaseId(), binding.getBindingId(), entry, "", presentation.subject(),
                            runtime.getCollectionData().getRunId(), List.of());
                    emitSources(sink, player, data, quest, runtime, authorizedSources, phase, binding.getBindingId(), entry, "", presentation.subject());
                    for (var variant : presentation.variants()) {
                        if (!CollectionContentDisclosure.variantRevealed(entry, variant, data.getCollectionRecords())) continue;
                        emit(sink, id, phase.getPhaseId(), binding.getBindingId(), entry, variant.variantId(), variant.subject(),
                                runtime.getCollectionData().getRunId(), List.of(variant.label().resolve(player,
                                        new QuestTextContext(quest, phase, phase.getPhaseId(), Map.of()))));
                        emitSources(sink, player, data, quest, runtime, authorizedSources, phase, binding.getBindingId(), entry, variant.variantId(), variant.subject());
                    }
                }
            }
        }
    }

    private static void emitSources(Consumer<JeiCatalogEntry> sink, ServerPlayer player, ArcQuestPlayer data,
            QuestDefinition quest, org.arcadia.arc_quest.quest.data.QuestRuntimeData runtime,
            Map<net.minecraft.resources.ResourceLocation, CollectionEntryDefinition> authorizedSources, PhaseDefinition phase,
            String binding, CollectionEntryDefinition targetEntry, String variant, CollectionSubjectSpec subject) {
        if (subject == null) return;
        var stack = subject.resolveStack();
        if (stack.isEmpty()) return;
        var context = new QuestTextContext(quest, phase, phase.getPhaseId(), Map.of());
        for (var set : quest.getCollectionConfig().getSourceSets()) {
            for (var relation : set.relations().stream().sorted(Comparator.comparingInt(CollectionSourceSetDefinition.Relation::sortOrder)).toList()) {
                if (!relation.targetEntryId().equals(targetEntry.getEntryId())
                        || (!relation.variantIds().isEmpty() && !relation.variantIds().contains(variant))) continue;
                var source = authorizedSources.get(relation.sourceEntryId());
                if (source == null || !CollectionContentDisclosure.entryRevealed(source, data.getCollectionRecords())) continue;
                List<Component> notes = new ArrayList<>();
                notes.add(Component.translatableWithFallback("arc_quest.jei.collection.source", "来源：%s", source.getDisplayName()));
                if (!relation.method().isBlank()) notes.add(Component.literal(relation.method()));
                notes.add(relation.description().resolve(player, context));
                var ingredient = new JeiIngredient(List.of(stack), 1, false, stack.getHoverName(), false, subject.identity());
                var address = new CollectionNavigationTarget(quest.getId().toString(), phase.getPhaseId(), binding,
                        targetEntry.getEntryId().toString(), variant, runtime.getCollectionData().getRunId());
                sink.accept(new JeiCatalogEntry("collection_source/" + quest.getId() + "/" + phase.getPhaseId() + "/"
                        + binding + "/" + variant + "/" + set.id() + "/" + relation.relationId(),
                        JeiCatalogEntry.Kind.COLLECTION_SOURCE, targetEntry.getDisplayName(), List.of(), List.of(ingredient),
                        notes, quest.getId().toString(), phase.getPhaseId(), List.of(), address));
            }
        }
    }

    private static Set<net.minecraft.resources.ResourceLocation> visibleEntries(ServerPlayer player, QuestDefinition quest, org.arcadia.arc_quest.quest.data.QuestRuntimeData runtime,
            ArcQuestPlayer data) {
        Set<net.minecraft.resources.ResourceLocation> result = new HashSet<>();
        for (var phase : quest.getAllPhases()) {
            if (!phase.hasCollectionSheet() || !(runtime.isPhaseActive(phase.getPhaseId())
                    || runtime.isPhaseCompleted(phase.getPhaseId()) || runtime.isPhasePendingManualAdvance(phase.getPhaseId()))) continue;
            var sheet = CollectionProgressProjector.project(quest, phase, runtime, data.getCollectionRecords());
            for (var binding : phase.getCollectionSheet().getBindings()) {
                var row = sheet.binding(binding.getBindingId());
                var entry = quest.getCollectionConfig().getEntry(binding.getEntryId());
                if (row != null && row.visible() && row.revealed() && entry != null && categoryVisible(player, data, quest, entry)) result.add(binding.getEntryId());
            }
        }
        return result;
    }

    private static boolean categoryVisible(ServerPlayer player, ArcQuestPlayer data, QuestDefinition quest, CollectionEntryDefinition entry) {
        var context = new QuestConditionContext(player, data.getCompletedQuestLocations(), data.getAllFlags(), data.getAllVariables());
        return quest.getCollectionConfig().getCategories().stream().filter(category -> category.getCategoryId().equals(entry.getCategoryId()))
                .flatMap(category -> category.getVisibilityConditions().stream()).allMatch(condition -> condition != null
                        && org.arcadia.arc_quest.core.CoreProcessors.get().conditions().evaluateSafely(condition, context, false, null, "JEI atlas category visibility"));
    }

    private static void emit(Consumer<JeiCatalogEntry> sink, String quest, String phase, String binding,
            CollectionEntryDefinition entry, String variant, CollectionSubjectSpec subject, String run, List<Component> notes) {
        if (subject == null) return;
        var stack = subject.resolveStack();
        if (stack.isEmpty()) return;
        var ingredient = new JeiIngredient(List.of(stack), 1, false, stack.getHoverName(), false, subject.identity());
        var target = new CollectionNavigationTarget(quest, phase, binding, entry.getEntryId().toString(), variant, run);
        String key = "collection/" + quest + "/" + phase + "/" + binding + "/" + variant;
        sink.accept(new JeiCatalogEntry(key, JeiCatalogEntry.Kind.COLLECTION_INFO, entry.getDisplayName(), List.of(),
                List.of(), notes, quest, phase, List.of(ingredient), target));
    }
}
