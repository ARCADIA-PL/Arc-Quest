package org.arcadia.arc_quest.client.hud.gacha;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GachaDrawRequestStateTest {
    @Test void timeoutKeepsPendingAndAcceptsLateResultOnce() {
        var state = new GachaDrawRequestState();
        assertTrue(state.start(100));
        assertFalse(state.pollSlowNotice(5099, 5000));
        assertTrue(state.pollSlowNotice(5100, 5000));
        assertTrue(state.pending());
        assertFalse(state.start(5101), "A timeout must not allow another draw");
        assertFalse(state.pollSlowNotice(15000, 5000), "A slow request emits only one safe refresh");
        assertTrue(state.acceptResult(), "A late result must still start the original draw animation");
        assertFalse(state.acceptResult(), "Duplicate results must not restart the animation");
        assertTrue(state.pending(), "Showing a result does not confirm its reward");
        assertFalse(state.fail(), "A stale failure must not undo an accepted result");
        assertTrue(state.confirm());
        assertFalse(state.confirm(), "Normal and fallback confirmation must be idempotent");
    }

    @Test void explicitFailureReleasesRequestAndResetsNoticeForNextDraw() {
        var state = new GachaDrawRequestState();
        assertTrue(state.start(0));
        assertTrue(state.pollSlowNotice(6000, 5000));
        assertTrue(state.fail());
        assertFalse(state.pending());
        assertFalse(state.acceptResult());
        assertTrue(state.start(7000));
        assertFalse(state.pollSlowNotice(11999, 5000));
        assertTrue(state.pollSlowNotice(12000, 5000));
    }

    @Test void closingUnansweredRequestConfirmsExactlyOnce() {
        var state = new GachaDrawRequestState();
        assertTrue(state.start(0));
        assertTrue(state.pollSlowNotice(6000, 5000));
        assertTrue(state.confirm());
        assertFalse(state.confirm());
        assertFalse(state.pending());
        assertFalse(state.acceptResult(), "A closed UI must not resurrect an animation");
        assertFalse(state.pollSlowNotice(9000, 5000));
    }
}
