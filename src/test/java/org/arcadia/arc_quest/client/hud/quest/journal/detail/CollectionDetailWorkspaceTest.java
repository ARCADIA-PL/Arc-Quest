package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollectionDetailWorkspaceTest {
    @Test void rewardStripCannotGrowIntoTheReadingAreaWhenManyRewardsAreAdded() {
        for (int[] size : new int[][]{{320, 180}, {480, 260}, {640, 360}, {900, 540}}) {
            HudRect panel = CollectionJournalLayout.modal(size[0], size[1]);
            var layout = CollectionDetailWorkspace.measure(panel, true);
            assertTrue(layout.identity().bottom() <= layout.tabs().y());
            assertTrue(layout.tabs().bottom() < layout.viewport().y());
            assertTrue(layout.viewport().bottom() < layout.rewards().y());
            assertTrue(layout.rewards().bottom() < panel.bottom());
            assertEquals(CollectionDetailWorkspace.REWARD_HEIGHT, layout.rewards().height());
            assertTrue(layout.viewport().height() >= 20, "A short window retains a scrollable reading area");
            var scrollbar = CollectionJournalLayout.scrollbarTrack(layout.viewport());
            assertTrue(scrollbar.x() > layout.viewport().right());
            assertTrue(scrollbar.right() < panel.right());
        }
        var pages = CollectionRewardStripPages.pages(List.of(200, 40, 1), 4);
        assertEquals(61, pages.size());
        assertEquals(new CollectionRewardStripPages.Page(0, 196, 200), pages.get(49));
        assertEquals(new CollectionRewardStripPages.Page(2, 0, 1), pages.get(60));
    }

    @Test void absentRewardsReleaseTheirSpaceAndTextRewardsRemainReadableOnTheirOwnPage() {
        var panel = new HudRect(20, 30, 380, 300);
        var rewarded = CollectionDetailWorkspace.measure(panel, true);
        var plain = CollectionDetailWorkspace.measure(panel, false);
        assertEquals(0, plain.rewards().height());
        assertEquals(CollectionDetailWorkspace.REWARD_HEIGHT + 6, plain.viewport().height() - rewarded.viewport().height());
        var pages = CollectionRewardStripPages.pages(List.of(3, 7), List.of(true, false), 4);
        assertEquals(5, pages.size());
        assertEquals(new CollectionRewardStripPages.Page(0, 1, 2), pages.get(1));
        assertEquals(new CollectionRewardStripPages.Page(1, 0, 4), pages.get(3));
        assertEquals(new CollectionRewardStripPages.Page(1, 4, 7), pages.get(4));
        assertEquals(4, CollectionRewardStripPages.clamp(200, pages.size()));
        assertEquals(0, CollectionRewardStripPages.clamp(-1, 0));
    }

    @Test void liveCompletionAndClaimReceiptsCannotReplaceTheChosenViewOrScrollPosition() {
        var state = new CollectionJournalState();
        state.select("zombie");
        state.chooseInitialDetailView(true);
        assertFalse(state.archiveView);
        state.detailScroll = 28;
        state.showArchive(true);
        state.detailScroll = 110;
        state.firstRewardPage = 3;
        state.chooseInitialDetailView(false);
        state.select("zombie");
        assertTrue(state.archiveView);
        assertEquals(110, state.detailScroll);
        assertEquals(3, state.firstRewardPage);
        state.showArchive(false);
        assertEquals(28, state.detailScroll);
        state.showArchive(true);
        assertEquals(110, state.detailScroll);
        state.select("coal");
        state.chooseInitialDetailView(false);
        assertTrue(state.archiveView);
        assertEquals(0, state.detailScroll);
        assertEquals(0, state.firstRewardPage);
    }
}
