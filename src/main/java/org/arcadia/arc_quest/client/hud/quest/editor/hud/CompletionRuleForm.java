package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorController;

public class CompletionRuleForm {
    public boolean apply(QuestEditorController controller, String categoryId, int index, String type, String expression) {
        var c = controller.collectionCategory(categoryId);
        if (c != null && c.completionRules.isEmpty()) {
            return controller.addCollectionCategoryCompletionRule(categoryId, type, expression) != null;
        }
        return controller.updateCollectionCategoryCompletionRule(categoryId, index, type, expression);
    }
}
