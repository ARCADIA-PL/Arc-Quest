package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress;
import org.arcadia.arc_quest.quest.api.CollectionRewardNode;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.data.CollectionBindingProgress;

/** Badges describe a manual action the player can take now, never general completion. */
final class CollectionRewardFeedback {
    private CollectionRewardFeedback() {}

    static boolean canClaim(CollectionBindingProgress binding) {
        return binding != null && binding.visible() && binding.revealed()
                && binding.entryRewards().stream().anyMatch(CollectionEntryRewardProgress::canClaim);
    }

    static boolean canClaim(CollectionRewardNode node, boolean unlocked, boolean claimed) {
        return node != null && node.getGrantMode() == EntryRewardGrantMode.MANUAL && unlocked && !claimed;
    }
}
