package org.arcadia.arc_quest.quest.logic.profile.collection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.tracking.ObjectiveKey;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CollectionObjectiveDispatcher {

    private CollectionObjectiveDispatcher() {
    }

    public static int dispatch(ServerPlayer player, ObjectiveKey key, int amount) {
        return dispatch(player, key, amount, null);
    }

    public static int dispatch(ServerPlayer player, ObjectiveKey key, int amount, @Nullable String uniqueKey) {
        if (player == null || key == null || amount <= 0) return 0;
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return 0;

        int changed = 0;
        for (CollectionObjectiveBinding binding : findBindings(cap, key)) {
            if (!shouldDispatch(cap, binding)) continue;
            switch (binding.getCountingMode()) {
                case BINARY ->
                        QuestProgressHandler.incrementCollectionEntry(player, binding.getQuestId(), binding.getPhaseId(), 1);
                case ACCUMULATE ->
                        QuestProgressHandler.incrementCollectionEntry(player, binding.getQuestId(), binding.getPhaseId(), amount);
                case UNIQUE_SET -> {
                    if (uniqueKey != null && !uniqueKey.isEmpty()) {
                        QuestProgressHandler.addCollectionUniqueKey(player, binding.getQuestId(), binding.getPhaseId(), uniqueKey);
                    } else {
                        QuestProgressHandler.incrementCollectionEntry(player, binding.getQuestId(), binding.getPhaseId(), amount);
                    }
                }
            }
            changed++;
        }
        return changed;
    }

    public static List<CollectionObjectiveBinding> findBindings(IQuestCapability cap, ObjectiveKey key) {
        if (cap == null || key == null) return List.of();
        List<CollectionObjectiveBinding> bindings = new ArrayList<>();
        for (Map.Entry<String, QuestRuntimeData> activeEntry : cap.getAllActiveQuests().entrySet()) {
            QuestRuntimeData runtime = activeEntry.getValue();
            if (runtime == null || runtime.getState() != QuestState.ACTIVE || !runtime.hasCollectionData()) continue;
            ResourceLocation questLocation = ResourceLocation.tryParse(activeEntry.getKey());
            if (questLocation == null) continue;
            QuestDefinition def = QuestRegistry.get(questLocation);
            if (def == null || !def.isCollectionQuest()) continue;
            collectBindings(def, runtime, key, bindings);
        }
        return List.copyOf(bindings);
    }

    private static boolean shouldDispatch(IQuestCapability cap, CollectionObjectiveBinding binding) {
        if (cap == null || binding == null) return false;
        QuestRuntimeData runtime = cap.getActiveQuest(binding.getQuestId());
        if (runtime == null || runtime.getState() != QuestState.ACTIVE) return false;
        CollectionRuntimeData collectionData = runtime.getCollectionData();
        if (collectionData == null || !collectionData.isVisible(binding.getPhaseId())) return false;
        if (!runtime.isPhaseCompleted(binding.getPhaseId())) return true;
        return binding.isRepeatableProgress() || binding.isRepeatableCompletion();
    }

    private static void collectBindings(QuestDefinition def,
                                        QuestRuntimeData runtime,
                                        ObjectiveKey key,
                                        List<CollectionObjectiveBinding> bindings) {
        for (String phaseId : def.getPhaseIds()) {
            if (!runtime.isPhaseActive(phaseId)) continue;
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) continue;
            for (ObjectiveEntry objectiveEntry : phase.getObjectives()) {
                if (objectiveEntry.getType() == key.getType() && key.getTargetId().equals(objectiveEntry.getTargetId())) {
                    bindings.add(new CollectionObjectiveBinding(def.getId().toString(), phaseId, objectiveEntry, entryConfig));
                }
            }
        }
    }
}
