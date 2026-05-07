package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.model.EditableConnectionType;

public class ConnectionSummary {
    public String connectionId = "";
    public EditableConnectionType connectionType;
    public String sourcePhaseNodeId = "";
    public String targetPhaseNodeId = "";
    public String choiceId = "";
    public int priority;
}
