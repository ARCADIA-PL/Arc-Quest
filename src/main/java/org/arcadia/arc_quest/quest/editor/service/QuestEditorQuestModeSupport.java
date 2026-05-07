package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;

public final class QuestEditorQuestModeSupport {
    public boolean isProgression(EditableQuest quest) {
        return quest != null && quest.meta != null && quest.meta.mode == QuestMode.PROGRESSION;
    }

    public boolean isCollection(EditableQuest quest) {
        return quest != null && quest.meta != null && quest.meta.mode == QuestMode.COLLECTION;
    }

    public QuestMode normalizeMode(QuestMode mode) {
        return mode == null ? QuestMode.PROGRESSION : mode;
    }
}
