package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorController;

import java.util.Map;

public class ObjectiveForm {
    public boolean apply(QuestEditorController controller, Map<String, String> values, String extraKey, String extraValue) {
        if (controller.selectedObjective() == null) controller.addObjectiveToSelectedPhase();
        return controller.updateSelectedObjectiveFromSchema(values, extraKey, extraValue);
    }
}
