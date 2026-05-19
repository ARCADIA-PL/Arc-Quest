package org.arcadia.arc_quest.quest.logic.profile.collection;

import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class CollectionCategoryStateResolver {

    private CollectionCategoryStateResolver() {
    }

    public static CollectionCategorySnapshot snapshot(CollectionRuleContext context, String categoryId) {
        return snapshot(context.getQuestDefinition(), context.getQuestRuntimeData(), context.getCollectionRuntimeData(), categoryId);
    }

    public static CollectionCategorySnapshot snapshot(QuestDefinition def,
                                                      QuestRuntimeData runtime,
                                                      @Nullable CollectionRuntimeData collectionData,
                                                      String categoryId) {
        int total = 0;
        int completed = 0;
        int visible = 0;
        int discovered = 0;
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null || !categoryId.equals(entryConfig.getCategoryId())) continue;
            total++;
            if (runtime.isPhaseCompleted(phaseId)) completed++;
            if (collectionData != null && collectionData.isVisible(phaseId)) visible++;
            if (collectionData != null && collectionData.isDiscovered(phaseId)) discovered++;
        }
        return new CollectionCategorySnapshot(categoryId, total, completed, visible, discovered);
    }

    public static List<CollectionCategorySnapshot> snapshots(CollectionRuleContext context) {
        return snapshots(context.getQuestDefinition(), context.getQuestRuntimeData(), context.getCollectionRuntimeData());
    }

    public static List<CollectionCategorySnapshot> snapshots(QuestDefinition def,
                                                             QuestRuntimeData runtime,
                                                             @Nullable CollectionRuntimeData collectionData) {
        CollectionQuestConfig config = def.getCollectionConfig();
        if (config == null || config.getCategories().isEmpty()) return List.of();
        List<CollectionCategorySnapshot> snapshots = new ArrayList<>();
        for (CollectionCategoryDefinition category : config.getCategories()) {
            snapshots.add(snapshot(def, runtime, collectionData, category.getCategoryId()));
        }
        return List.copyOf(snapshots);
    }

    public static int countCompletedCategories(CollectionRuleContext context) {
        int completed = 0;
        for (CollectionCategorySnapshot snapshot : snapshots(context)) {
            if (snapshot.isCompleted()) completed++;
        }
        return completed;
    }

    public static int countCategoriesWithEntries(CollectionRuleContext context) {
        int total = 0;
        for (CollectionCategorySnapshot snapshot : snapshots(context)) {
            if (snapshot.hasEntries()) total++;
        }
        return total;
    }

    public static float completedCategoryRatio(CollectionRuleContext context) {
        int total = 0;
        int completed = 0;
        for (CollectionCategorySnapshot snapshot : snapshots(context)) {
            if (!snapshot.hasEntries()) continue;
            total++;
            if (snapshot.isCompleted()) completed++;
        }
        return total <= 0 ? 0f : completed / (float) total;
    }
}
