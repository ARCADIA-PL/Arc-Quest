package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import org.arcadia.arc_quest.client.hud.quest.editor.QuestEditorController;

public class RewardNodeForm {
    public boolean apply(QuestEditorController controller, String entryId, String rewardNodeId, String scope, String grantMode) {
        var e = controller.collectionEntry(entryId);
        if (e != null && e.rewardNodes.isEmpty()) controller.addCollectionEntryRewardNode(entryId, rewardNodeId);
        return controller.updateCollectionEntryRewardNodeAdvanced(entryId, rewardNodeId, scope, grantMode);
    }
}
