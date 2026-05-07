package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.model.EditableConnectionType;

public class CreateConnectionReport {
    public boolean created;
    public boolean requiresPolicySelection;
    public String message = "";
    public String connectionId = "";
    public EditableConnectionType connectionType;
}
