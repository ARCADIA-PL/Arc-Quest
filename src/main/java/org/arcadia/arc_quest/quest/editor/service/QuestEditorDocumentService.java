package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.editor.mapper.QuestSpecToEditableQuestMapper;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;
import org.arcadia.arc_quest.quest.spec.ObjectiveSpec;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;

import java.nio.file.Path;

public class QuestEditorDocumentService {
    private static final String QUEST_DIR = "arc_quest/datapack";

    private final QuestDatapackScanService scanService = new QuestDatapackScanService();
    private final QuestSpecToEditableQuestMapper mapper = new QuestSpecToEditableQuestMapper();

    public QuestEditorDocument createNew(CreateQuestRequest request, Path workspaceRoot) {
        QuestEditorDocument document = new QuestEditorDocument();
        QuestSpec spec = new QuestSpec();

        String questId = request == null || request.questId == null || request.questId.isBlank() ? "new_quest" : request.questId.trim();
        String display = request == null || request.displayName == null || request.displayName.isBlank() ? questId : request.displayName.trim();
        QuestMode mode = request == null || request.mode == null ? QuestMode.PROGRESSION : request.mode;

        spec.id = questId;
        spec.displayName = QuestTextSpec.literal(display);
        spec.description = QuestTextSpec.literal(request == null ? "" : request.description);
        spec.mode = mode;

        if (mode == QuestMode.PROGRESSION) {
            PhaseSpec phase = new PhaseSpec();
            phase.phaseId = "start";
            phase.displayName = QuestTextSpec.literal("start");
            ObjectiveSpec objective = new ObjectiveSpec();
            objective.type = ObjectiveType.CUSTOM;
            objective.targetId = "arcquest:placeholder";
            objective.requiredCount = 1;
            objective.displayText = QuestTextSpec.literal("placeholder");
            phase.objectives.add(objective);
            spec.phases.add(phase);
            spec.initialPhaseId = "start";
        }

        EditableQuest editable = mapper.map(spec);
        if (mode == QuestMode.COLLECTION) {
            editable.initialPhaseNodeId = "";
            editable.phases.clear();
            editable.connections.clear();
            editable.layout.phasePositions.clear();
            CollectionQuestCreateOptions options = (request != null && request.collectionOptions != null) ? request.collectionOptions : new CollectionQuestCreateOptions();
            editable.collectionConfig.showCategories = options.showCategories;
            editable.collectionConfig.revealAllEntriesByDefault = options.revealAllEntriesByDefault;
            editable.collectionConfig.allowManualRewardClaim = options.allowManualRewardClaim;
            editable.collectionConfig.trackerMode = options.trackerMode;
            editable.collectionConfig.journalMode = options.journalMode;

            var category = new org.arcadia.arc_quest.quest.editor.model.EditableCollectionCategory();
            category.categoryId = (options.defaultCategoryId == null || options.defaultCategoryId.isBlank()) ? "default" : options.defaultCategoryId;
            category.displayName = QuestTextSpec.literal(category.categoryId);
            editable.collectionConfig.categories.add(category);

            var entry = new org.arcadia.arc_quest.quest.editor.model.EditableCollectionEntry();
            entry.entryId = (options.defaultEntryId == null || options.defaultEntryId.isBlank()) ? "entry_1" : options.defaultEntryId;
            entry.categoryId = category.categoryId;
            entry.completionTarget = 1;
            editable.collectionEntries.add(entry);
        }

        document.quest = editable;
        document.imported = false;
        document.newlyCreated = true;
        document.sourceFile = null;
        document.saveFile = defaultQuestFile(workspaceRoot, questId);
        return document;
    }

    public QuestEditorDocument importFromFile(Path file) throws java.io.IOException {
        QuestSpec spec = scanService.loadSpec(file);
        QuestEditorDocument document = new QuestEditorDocument();
        document.quest = mapper.map(spec);
        document.sourceFile = file;
        document.saveFile = file;
        document.imported = true;
        document.newlyCreated = false;
        return document;
    }

    public Path defaultQuestFile(Path workspaceRoot, String questId) {
        String id = questId == null || questId.isBlank() ? "new_quest" : questId.trim();
        return workspaceRoot.resolve(QUEST_DIR).resolve(id + ".json");
    }
}
