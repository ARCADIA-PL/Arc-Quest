package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.model.EditableQuest;

import java.util.LinkedHashSet;
import java.util.Set;

public class QuestEditorSnapshot {
    public EditableQuest quest;
    public EditorSelection selection = EditorSelection.none();
    public QuestEditorMode mode = QuestEditorMode.IDLE;
    public double cameraX;
    public double cameraY;
    public double zoom = 1.0;
    public Set<String> pendingDeletePhaseNodeIds = new LinkedHashSet<>();
    public Set<String> pendingDeleteCollectionObjectRefs = new LinkedHashSet<>();
    public String selectedConnectionId = "";
    public String connectSourcePhaseNodeId = "";
    public String draggingObjectId = "";
    public String statusText = "";
}
