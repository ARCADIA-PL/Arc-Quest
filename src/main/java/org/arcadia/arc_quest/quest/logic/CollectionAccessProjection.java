package org.arcadia.arc_quest.quest.logic;

import java.util.*;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.data.sync.CollectionContentDisclosure;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.api.collection.CollectionViews.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

/** Framework-internal authorized handles. Public consumers use CollectionViews instead. */
public final class CollectionAccessProjection {
    public record Binding(QuestDefinition quest, PhaseDefinition phase, QuestRuntimeData runtime,
                          CollectionEntryDefinition entry, EntryView view) {}
    public record Relation(CollectionSourceSetDefinition set, CollectionSourceSetDefinition.Relation definition,
                           Binding source, Binding target, Set<String> variants) {
        public Relation { variants = Set.copyOf(variants); }
        public boolean matches(String variant) { return definition.variantIds().isEmpty() || variants.contains(variant); }
    }
    private final List<Binding> bindings;
    private final List<Relation> relations;

    public CollectionAccessProjection(ServerPlayer player, ArcQuestPlayer data,
            Function<QuestRuntimeData, QuestDefinition> definitions) {
        var rows = new ArrayList<Binding>();
        for (var run : knownRuns(data)) {
            var quest = definitions.apply(run);
            if (quest == null || quest.getCollectionConfig() == null) continue;
            var live = QuestRegistry.getServerDefinition(quest.getId());
            boolean refresh = CollectionPresentationPolicy.accepts(quest, live);
            for (var phase : quest.getAllPhases()) {
                if (!phaseAvailable(run, phase)) continue;
                var sheet = CollectionProgressProjector.project(quest, phase, run, data.getCollectionRecords());
                for (var row : sheet.bindings()) {
                    var entry = quest.getCollectionConfig().getEntry(row.entryId());
                    if (!row.visible() || !row.revealed() || entry == null || !categoryVisible(player, data, quest, entry)) continue;
                    if (refresh) entry = entry.withPresentation(CollectionPresentationPolicy.presentation(entry,
                            live.getCollectionConfig().getEntry(entry.getEntryId())));
                    var disclosed = new LinkedHashSet<String>();
                    for (var variant : entry.getPresentation().variants())
                        if (CollectionContentDisclosure.variantRevealed(entry, variant, data.getCollectionRecords())) disclosed.add(variant.variantId());
                    var ref = new EntryRef(new RunRef(run.getQuestId(), run.getCollectionData().getRunId()), phase.getPhaseId(), row.bindingId());
                    var view = new EntryView(ref, row.entryId(), entry.getCategoryId(), disclosed, row.discovered(), row.researchComplete(),
                            entry.getPresentation().obtainable(data::hasFlag, row.discovered()));
                    rows.add(new Binding(quest, phase, run, entry, view));
                }
            }
        }
        bindings = List.copyOf(rows);
        var links = new ArrayList<Relation>();
        var byEntry = new LinkedHashMap<ResourceLocation, Binding>();
        bindings.forEach(b -> byEntry.putIfAbsent(b.entry().getEntryId(), b));
        for (var target : bindings) {
            var live = QuestRegistry.getServerDefinition(target.quest().getId());
            var config = CollectionPresentationPolicy.accepts(target.quest(), live) ? live.getCollectionConfig() : target.quest().getCollectionConfig();
            for (var set : config.getSourceSets()) for (var relation : set.relations()) {
                if (!relation.targetEntryId().equals(target.entry().getEntryId())) continue;
                var source = byEntry.get(relation.sourceEntryId());
                if (source == null) continue;
                var variants = new LinkedHashSet<>(relation.variantIds());
                variants.retainAll(target.view().variantIds());
                if (!relation.variantIds().isEmpty() && variants.isEmpty()) continue;
                links.add(new Relation(set, relation, source, target, variants));
            }
        }
        relations = List.copyOf(links);
    }
    public List<Binding> bindings() { return bindings; }
    public List<Relation> relations() { return relations; }
    public Binding resolve(EntryRef ref, String variant) {
        return bindings.stream().filter(b -> b.view().ref().equals(ref)
                && (variant == null || variant.isEmpty() || b.view().variantIds().contains(variant))).findFirst().orElse(null);
    }
    public static boolean phaseAvailable(QuestRuntimeData run, PhaseDefinition phase) {
        return phase.hasCollectionSheet() && (run.isPhaseActive(phase.getPhaseId()) || run.isPhaseCompleted(phase.getPhaseId())
                || run.isPhasePendingManualAdvance(phase.getPhaseId()));
    }
    public static boolean categoryVisible(ServerPlayer player, ArcQuestPlayer data, QuestDefinition quest, CollectionEntryDefinition entry) {
        var context = new QuestConditionContext(player, data == null ? Set.of() : data.getCompletedQuestLocations(), data == null ? Set.of() : data.getAllFlags(), data == null ? Map.of() : data.getAllVariables());
        return quest.getCollectionConfig().getCategories().stream().filter(c -> c.getCategoryId().equals(entry.getCategoryId()))
                .flatMap(c -> c.getVisibilityConditions().stream()).allMatch(c -> c != null && CoreProcessors.get().conditions()
                        .evaluateSafely(c, context, false, null, "collection category visibility"));
    }
    private static List<QuestRuntimeData> knownRuns(ArcQuestPlayer data) {
        var runs = new LinkedHashMap<RunRef, QuestRuntimeData>();
        // Active first; an archive of the same run may be a stale snapshot.
        for (var run : data.getAllActiveQuests().values()) if (run.hasCollectionData())
            runs.put(new RunRef(run.getQuestId(), run.getCollectionData().getRunId()), run);
        for (var id : data.getCollectionArchives().questIds()) for (var run : data.getCollectionArchives().allRuns(id))
            runs.putIfAbsent(new RunRef(id, run.getCollectionData().getRunId()), run);
        return List.copyOf(runs.values());
    }
}
