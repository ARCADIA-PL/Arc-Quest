package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorController;

import java.util.Map;

public class ConnectionForm {
    public boolean apply(QuestEditorController controller, String connectionId, Map<String, String> values) {
        if (connectionId == null || connectionId.isBlank()) return false;
        controller.selectConnection(connectionId);
        return controller.updateSelectedConnectionFromSchema(values);
    }
}
