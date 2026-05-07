package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.service.QuestEditorDocument;
import org.arcadia.arc_quest.quest.editor.service.SaveReport;
import org.arcadia.arc_quest.quest.editor.service.EditableValidationReport;

import java.util.LinkedHashSet;
import java.util.Set;

public class QuestEditorState {
    public QuestEditorDocument document = new QuestEditorDocument();
    public EditorSelection selection = EditorSelection.none();
    public QuestEditorMode mode = QuestEditorMode.IDLE;
    public double cameraX = 0;
    public double cameraY = 0;
    public double zoom = 1.0;
    public Set<String> pendingDeletePhaseNodeIds = new LinkedHashSet<>();
    public Set<String> pendingDeleteCollectionObjectRefs = new LinkedHashSet<>();
    public String selectedConnectionId = "";
    public String connectSourcePhaseNodeId = "";
    public String draggingObjectId = "";
    public String statusText = "";
    public EditableValidationReport lastValidationReport = new EditableValidationReport();
    public SaveReport lastSaveReport = new SaveReport();
}
