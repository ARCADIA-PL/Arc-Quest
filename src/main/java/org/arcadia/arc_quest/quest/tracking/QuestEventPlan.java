package org.arcadia.arc_quest.quest.tracking;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectMode;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.BiPredicate;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;

/** Immutable recipients of every signal originating from one real event. */
final class QuestEventPlan {
    /** Frozen definitions are immutable; live tags are still resolved by ObjectiveTypeIndex.find. */
    private static final Map<QuestDefinition, ObjectiveTypeIndex> DEFINITION_INDICES =
            Collections.synchronizedMap(new WeakHashMap<>());
    record Signal(ObjectiveType type, ResourceLocation target, int amount, boolean crafted) { }
    record Recipient(QuestRuntimeData run, ObjectiveTypeIndex.ObjectiveRef reference,
                     QuestDefinition definition, int amount) {
        boolean stillEligible(ArcQuestPlayer data) {
            return data.getActiveQuest(reference.questId().toString()) == run
                    && run.getState() == QuestState.ACTIVE && run.isPhaseActive(reference.phaseId())
                    && !objectiveFinalized(run, reference, definition);
        }
    }

    private QuestEventPlan() { }

    static List<Recipient> freeze(ArcQuestPlayer data, List<Signal> signals,
                                  Function<QuestRuntimeData, QuestDefinition> definitions) {
        List<Recipient> recipients = new ArrayList<>();
        for (var runtime : List.copyOf(data.getAllActiveQuests().values())) {
            if (runtime.getState() != QuestState.ACTIVE) continue;
            QuestDefinition definition = definitions.apply(runtime);
            if (definition == null) continue;
            ObjectiveTypeIndex index = DEFINITION_INDICES.computeIfAbsent(definition,
                    def -> ObjectiveTypeIndex.build(Map.of(def.getId(), def)));
            recipients.addAll(freeze(data, signals, index,
                    id -> id.equals(definition.getId()) ? definition : null,
                    (objective, target) -> ObjectiveItemResolver.matches(objective, target, runtime)));
        }
        return List.copyOf(recipients);
    }

    static List<Recipient> freeze(ArcQuestPlayer data, List<Signal> signals, ObjectiveTypeIndex index,
                                  Function<ResourceLocation, QuestDefinition> definitions) {
        return freeze(data, signals, index, definitions, ObjectiveItemResolver::matches);
    }

    private static List<Recipient> freeze(ArcQuestPlayer data, List<Signal> signals, ObjectiveTypeIndex index,
                                  Function<ResourceLocation, QuestDefinition> definitions,
                                  BiPredicate<ObjectiveEntry, ResourceLocation> tagMatch) {
        List<Recipient> recipients = new ArrayList<>();
        for (Signal signal : signals) {
            if (signal.amount() <= 0) continue;
            var refs = index.find(signal.type(), signal.target(), objective -> tagMatch.test(objective, signal.target()));
            for (var ref : refs == null ? List.<ObjectiveTypeIndex.ObjectiveRef>of() : refs) {
                var runtime = data.getActiveQuest(ref.questId().toString());
                if (runtime == null || runtime.getState() != QuestState.ACTIVE
                        || !runtime.isPhaseActive(ref.phaseId())) continue;
                var definition = definitions.apply(ref.questId());
                if (definition == null || definition.isCollectionQuest() && !definition.hasCollectionSheets()) continue;
                if (objectiveFinalized(runtime, ref, definition)) continue;
                var objective = definition.getPhase(ref.phaseId()).getObjectives().get(ref.objIndex());
                if (signal.type().equals(ObjectiveType.COLLECT)
                        && !CollectMode.from(objective).acceptsAcquisition(signal.crafted())) continue;
                recipients.add(new Recipient(runtime, ref, definition, signal.amount()));
            }
        }
        return List.copyOf(recipients);
    }

    static boolean sheetSettled(QuestRuntimeData runtime, String phaseId) {
        return runtime.getCollectionData() != null && runtime.getCollectionData().isSheetSettled(phaseId);
    }

    static boolean objectiveFinalized(QuestRuntimeData runtime, ObjectiveTypeIndex.ObjectiveRef ref,
                                       QuestDefinition definition) {
        if (definition == null || runtime.getCollectionData() == null) return false;
        var phase = definition.getPhase(ref.phaseId());
        if (phase == null || !phase.hasCollectionSheet()) return false;
        String objectiveId = phase.getObjectives().get(ref.objIndex()).getObjectiveId();
        return phase.getCollectionSheet().getBindings().stream().anyMatch(binding ->
                binding.getObjectiveIds().contains(objectiveId)
                        && (sheetSettled(runtime, ref.phaseId()) || runtime.getCollectionData()
                        .isBindingComplete(ref.phaseId(), binding.getBindingId())));
    }
}
