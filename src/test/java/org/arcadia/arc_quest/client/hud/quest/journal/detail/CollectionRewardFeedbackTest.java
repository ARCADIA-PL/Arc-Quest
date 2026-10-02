package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardDefinition;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardTrigger;
import org.arcadia.arc_quest.quest.api.CollectionRewardPreviewVisibility;
import org.arcadia.arc_quest.quest.api.CollectionRewardNode;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.RewardScope;
import org.arcadia.arc_quest.quest.data.CollectionBindingProgress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectionRewardFeedbackTest {
    @Test void onlyReadyUnclaimedManualEntryRewardsLightTheBadge() {
        for (var trigger : CollectionEntryRewardTrigger.values()) {
            assertTrue(CollectionRewardFeedback.canClaim(binding(true, true,
                    row(trigger, EntryRewardGrantMode.MANUAL, true, false))));
            assertFalse(CollectionRewardFeedback.canClaim(binding(true, true,
                    row(trigger, EntryRewardGrantMode.MANUAL, false, false))));
            assertFalse(CollectionRewardFeedback.canClaim(binding(true, true,
                    row(trigger, EntryRewardGrantMode.MANUAL, true, true))));
            assertFalse(CollectionRewardFeedback.canClaim(binding(true, true,
                    row(trigger, EntryRewardGrantMode.AUTO, true, false))));
        }
    }

    @Test void hiddenOrUnrevealedEntriesCannotAdvertiseRewardReceipts() {
        var ready = row(CollectionEntryRewardTrigger.RESEARCH_COMPLETE, EntryRewardGrantMode.MANUAL, true, false);
        assertFalse(CollectionRewardFeedback.canClaim(binding(false, true, ready)));
        assertFalse(CollectionRewardFeedback.canClaim(binding(true, false, ready)));
        assertFalse(CollectionRewardFeedback.canClaim(binding(true, true)));
        assertFalse(CollectionRewardFeedback.canClaim((CollectionBindingProgress) null));
    }

    @Test void aPreviousRunManualRewardRemainsActionableUntilItsOwnReceiptIsClaimed() {
        var definition = new CollectionEntryRewardDefinition("investigation", CollectionEntryRewardTrigger.BINDING_COMPLETE,
                EntryRewardGrantMode.MANUAL, List.of());
        var current = new CollectionEntryRewardProgress(definition, true, true, "current");
        var previous = new CollectionEntryRewardProgress(definition, true, false, "previous");
        assertTrue(CollectionRewardFeedback.canClaim(binding(true, true, current, previous)));
        assertFalse(CollectionRewardFeedback.canClaim(binding(true, true, current,
                new CollectionEntryRewardProgress(definition, true, true, "previous"))));
    }

    @Test void automaticOrLockedMilestonesNeverAdvertiseAManualClaim() {
        var manual = new CollectionRewardNode("manual", RewardScope.QUEST, EntryRewardGrantMode.MANUAL, List.of(), List.of(), null);
        var automatic = new CollectionRewardNode("automatic", RewardScope.QUEST, EntryRewardGrantMode.AUTO, List.of(), List.of(), null);
        assertTrue(CollectionRewardFeedback.canClaim(manual, true, false));
        assertFalse(CollectionRewardFeedback.canClaim(manual, false, false));
        assertFalse(CollectionRewardFeedback.canClaim(manual, true, true));
        assertFalse(CollectionRewardFeedback.canClaim(automatic, true, false));
        assertFalse(CollectionRewardFeedback.canClaim(null, true, false));
    }

    private static CollectionEntryRewardProgress row(CollectionEntryRewardTrigger trigger, EntryRewardGrantMode mode,
                                                    boolean unlocked, boolean claimed) {
        String outcomeId = trigger == CollectionEntryRewardTrigger.OUTCOME ? "investigation" : "";
        return new CollectionEntryRewardProgress(new CollectionEntryRewardDefinition("reward", trigger, mode,
                List.of(), outcomeId, CollectionRewardPreviewVisibility.UNLOCKED_ONLY), unlocked, claimed);
    }

    private static CollectionBindingProgress binding(boolean visible, boolean revealed, CollectionEntryRewardProgress... rewards) {
        return new CollectionBindingProgress("subject", ResourceLocation.parse("test:subject"), visible, revealed,
                true, false, true, List.of(), List.of(), List.of(rewards));
    }
}
