package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.quest.api.QuestMode;

public class CreateQuestRequest {
    public String questId = "";
    public String displayName = "";
    public String description = "";
    public QuestMode mode = QuestMode.PROGRESSION;
    public CollectionQuestCreateOptions collectionOptions = new CollectionQuestCreateOptions();
}
