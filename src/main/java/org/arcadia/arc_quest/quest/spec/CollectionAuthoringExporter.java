package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.api.*;

/**
 * Explicit Java authoring export. Runtime callbacks cannot be reconstructed; register frozen
 * factories for those.
 */
public final class CollectionAuthoringExporter {
    private CollectionAuthoringExporter() {}

    public static QuestSpec quest(QuestDefinition quest) {
        if (quest.getCollectionConfig() == null)
            throw new IllegalArgumentException("Collection configuration required");
        var registered =
                org.arcadia.arc_quest.quest.editor.QuestAuthoringSnapshotRegistry.get(
                        quest.getId());
        if (registered != null)
            return org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader.read(
                    org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter.write(
                            registered.spec()));
        if (!quest.getCompletionRewards().isEmpty()
                || !quest.getCollectionConfig().getQuestRewardNodes().isEmpty()
                || quest.getCollectionConfig().getCategories().stream()
                        .anyMatch(c -> !c.getRewardNodes().isEmpty())
                || quest.getCollectionConfig().getEntries().stream()
                        .anyMatch(e -> !e.getRewards().isEmpty())
                || quest.getAllPhases().stream()
                        .anyMatch(
                                p ->
                                        !p.getPhaseRewards().isEmpty()
                                                || p.hasCollectionSheet()
                                                        && p
                                                                .getCollectionSheet()
                                                                .getBindings()
                                                                .stream()
                                                                .anyMatch(
                                                                        b ->
                                                                                !b.getRewards()
                                                                                        .isEmpty())))
            throw new IllegalArgumentException(
                    "Executable rewards require original authoring/frozen factory");
        var spec = CollectionDefinitionSpecExporter.quest(quest, null);
        for (var entry : quest.getCollectionConfig().getEntries()) {
            if (!entry.getRecordConditions().isEmpty())
                throw new IllegalArgumentException(
                        "Callback conditions require original authoring/frozen factory");
            var target =
                    spec.collectionConfig.entries.stream()
                            .filter(e -> e.entryId.equals(entry.getEntryId().toString()))
                            .findFirst()
                            .orElseThrow();
            for (var rule : entry.getObservationRules()) {
                var value = new CollectionObservationSpecData();
                value.ruleId = rule.ruleId();
                value.ruleVersion = rule.ruleVersion();
                value.subject = CollectionAtlasCodec.subject(rule.subject());
                value.outcomeId = rule.outcomeId();
                target.observationRules.add(value);
            }
        }
        return spec;
    }
}
