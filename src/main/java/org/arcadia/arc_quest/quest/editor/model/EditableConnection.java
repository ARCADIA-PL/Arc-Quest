package org.arcadia.arc_quest.quest.editor.model;

import org.arcadia.arc_quest.quest.spec.ConditionSpec;

public class EditableConnection {
    public String connectionId = "";
    public String sourcePhaseNodeId = "";
    public String targetPhaseNodeId = "";
    public EditableConnectionType connectionType = EditableConnectionType.TRANSITION;
    public ConditionSpec condition = null;
    public int priority = 0;
    public String choiceId = "";
}
