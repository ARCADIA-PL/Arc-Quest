package org.arcadia.arc_quest.integration.jei.trade;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JeiAvailabilityTest {
    @Test void unspentQuotaIgnoresOldCooldown() {
        var state = JeiAvailability.project(2, 5, true, false, true);
        assertEquals(3, state.remaining());
        assertFalse(state.cooldownActive());
        assertTrue(state.meetsStateConditions());
    }
    @Test void fullQuotaAndUnlimitedOffersRespectCooldown() {
        assertFalse(JeiAvailability.project(5, 5, true, false, true).meetsStateConditions());
        var unlimited = JeiAvailability.project(100, -1, true, false, true);
        assertEquals(-1, unlimited.remaining());
        assertFalse(unlimited.meetsStateConditions());
    }
    @Test void dueResetProjectsWithoutChangingTheRecordedCount() {
        int recorded = 7;
        var state = JeiAvailability.project(recorded, 5, true, true, true);
        assertEquals(7, recorded);
        assertEquals(0, state.used());
        assertEquals(5, state.remaining());
        assertTrue(state.meetsStateConditions());
    }
    @Test void resetDoesNotBypassQualification() {
        var state = JeiAvailability.project(5, 5, true, true, false);
        assertEquals(5, state.remaining());
        assertFalse(state.meetsStateConditions());
    }
    @Test void countOverflowAndNoLimitRemainWellDefined() {
        assertEquals(0, JeiAvailability.project(Integer.MAX_VALUE, 1, false, false, true).remaining());
        assertEquals(-1, JeiAvailability.project(0, 0, false, false, true).remaining());
    }
}
