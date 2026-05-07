package org.arcadia.arc_quest.quest.editor.model;

import java.util.ArrayList;
import java.util.List;

public class EditableQuest {
    public EditableQuestMeta meta = new EditableQuestMeta();
    public String initialPhaseNodeId = "";
    public List<EditablePhase> phases = new ArrayList<>();
    public List<EditableConnection> connections = new ArrayList<>();
    public List<EditableCollectionEntry> collectionEntries = new ArrayList<>();
    public EditableCollectionConfig collectionConfig = new EditableCollectionConfig();
    public EditorLayoutState layout = new EditorLayoutState();
}
