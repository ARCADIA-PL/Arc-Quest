package org.arcadia.arc_quest.quest.editor.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class EditorLayoutState {
    public Map<String, EditorNodePosition> phasePositions = new LinkedHashMap<>();
    public double zoom = 1.0;
    public double panX = 0.0;
    public double panY = 0.0;
}
