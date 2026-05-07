package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.model.EditorNodePosition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuestEditorGraphLayout {
    public Map<String, EditorNodePosition> nodePositions = new LinkedHashMap<>();
    public List<EditorEdgeLayout> edges = new ArrayList<>();
    public List<String> unreachableNodeIds = new ArrayList<>();
    public GraphBounds bounds = new GraphBounds();
}
