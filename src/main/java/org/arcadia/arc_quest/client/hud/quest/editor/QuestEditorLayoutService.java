package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;

import java.util.Set;

public class QuestEditorLayoutService {
    private final QuestEditorTreeLayoutService treeLayoutService = new QuestEditorTreeLayoutService();
    private final QuestEditorCollectionLayoutService collectionLayoutService = new QuestEditorCollectionLayoutService();

    public QuestEditorLayoutEnvelope layout(EditableQuest quest) {
        return layout(quest, Set.of());
    }

    public QuestEditorLayoutEnvelope layout(EditableQuest quest, Set<String> collapsedCategoryIds) {
        QuestEditorLayoutEnvelope out = new QuestEditorLayoutEnvelope();
        if (quest == null || quest.meta == null || quest.meta.mode == null || quest.meta.mode == QuestMode.PROGRESSION) {
            out.mode = "PROGRESSION";
            out.graphLayout = treeLayoutService.layout(quest);
            out.bounds = out.graphLayout.bounds;
            return out;
        }

        out.mode = "COLLECTION";
        out.collectionLayout = collectionLayoutService.layout(quest, collapsedCategoryIds);
        out.bounds = out.collectionLayout.bounds;
        return out;
    }
}
