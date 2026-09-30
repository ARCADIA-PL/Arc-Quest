package org.arcadia.arc_quest.client.compat.jei.screen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JeiScreenSuspensionTest {
    @Test void ordinaryScreenRemovalStillClearsPanels() {
        var suspension = new JeiScreenSuspension();
        assertFalse(suspension.removed());
        assertFalse(suspension.resume());
    }
    @Test void successfulQueryPreservesPanelsExactlyOnce() {
        var suspension = new JeiScreenSuspension();
        suspension.arm();
        assertTrue(suspension.removed());
        assertTrue(suspension.resume());
        assertFalse(suspension.resume());
        assertFalse(suspension.removed());
    }
    @Test void NoRecipesFoundDoesNotSuppressLaterCleanup() {
        var suspension = new JeiScreenSuspension();
        suspension.arm();
        suspension.cancel();
        assertFalse(suspension.removed());
    }
    @Test void abandoningJeiOrDisconnectingDropsTheReturnState() {
        var suspension = new JeiScreenSuspension();
        suspension.arm();
        assertTrue(suspension.removed());
        suspension.cancel();
        assertFalse(suspension.resume());
    }
    @Test void aSecondQueryCanPreserveTheSameScreenAgain() {
        var suspension = new JeiScreenSuspension();
        for (int attempt = 0; attempt < 4; attempt++) {
            suspension.arm();
            assertTrue(suspension.removed());
            assertTrue(suspension.resume());
        }
        assertFalse(suspension.removed());
    }
}
