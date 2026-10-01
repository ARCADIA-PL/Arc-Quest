package org.arcadia.arc_quest.client.hud.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudCursorStateTest {
    @Test
    void separateHudOverlaysAccumulateRequestsUntilTheSingleEndOfFrame() {
        HudCursorState state = new HudCursorState();
        state.beginHudFrame();
        state.renderHud(state::requestPointer);
        state.renderHud(() -> {
            // A popup's local frame must not erase a lower registered overlay's request.
            state.beginFrame();
            assertFalse(state.endFrame());
        });

        assertTrue(state.endHudFrame());
        assertTrue(state.pointerRequested());
    }

    @Test
    void anInactiveOrCancelledOverlayDoesNotKeepLastFramesHandCursor() {
        HudCursorState state = new HudCursorState();
        state.beginHudFrame();
        state.renderHud(state::requestPointer);
        assertTrue(state.endHudFrame());
        assertTrue(state.pointerRequested());

        state.beginHudFrame();
        assertTrue(state.endHudFrame());
        assertFalse(state.pointerRequested());
    }

    @Test
    void cancelledHudPreWithoutPostDoesNotOpenAnyScreenCursorFrame() {
        HudCursorState state = new HudCursorState();
        for (int i = 0; i < 100; i++) state.beginHudFrame();

        state.beginFrame();
        state.requestPointer();
        assertTrue(state.endFrame(), "A real Screen frame must commit even after cancelled HUD passes");
        assertTrue(state.pointerRequested());
        state.beginFrame();
        assertTrue(state.endFrame());
        assertFalse(state.pointerRequested());
    }

    @Test
    void existingNestedScreenFramesRetainRequestsUntilTheOutermostApply() {
        HudCursorState state = new HudCursorState();
        state.beginFrame();
        state.requestPointer();
        state.beginFrame();
        assertFalse(state.endFrame());
        assertTrue(state.pointerRequested());
        assertTrue(state.endFrame());
        assertTrue(state.pointerRequested());
    }

    @Test
    void aHudRendererExceptionDoesNotCaptureFollowingScreenRequests() {
        HudCursorState state = new HudCursorState();
        state.beginHudFrame();
        assertThrows(IllegalStateException.class, () -> state.renderHud(() -> {
            state.beginFrame();
            throw new IllegalStateException("Broken overlay");
        }));

        state.beginFrame();
        state.requestPointer();
        assertTrue(state.endFrame());
        assertTrue(state.pointerRequested());
        state.beginFrame();
        assertTrue(state.endFrame());
        assertFalse(state.pointerRequested());
    }

    @Test
    void nestedHudScopesOnlyContributeToTheHudAccumulator() {
        HudCursorState state = new HudCursorState();
        state.beginHudFrame();
        state.renderHud(() -> {
            state.renderHud(state::requestPointer);
            state.beginFrame();
            assertFalse(state.endFrame());
        });
        assertTrue(state.endHudFrame());
        assertTrue(state.pointerRequested());

        state.beginFrame();
        assertTrue(state.endFrame());
        assertFalse(state.pointerRequested());
    }

    @Test
    void finishingHudCannotOverwriteAnOpenScreenFrame() {
        HudCursorState state = new HudCursorState();
        state.beginFrame();
        state.requestPointer();
        state.beginHudFrame();
        assertFalse(state.endHudFrame());
        assertTrue(state.endFrame());
        assertTrue(state.pointerRequested());
    }

    @Test
    void closingAndResettingInsideAHudRendererStillUnwindsItsScope() {
        HudCursorState state = new HudCursorState();
        state.beginHudFrame();
        state.renderHud(() -> {
            state.requestPointer();
            state.reset();
        });
        assertTrue(state.endHudFrame());
        assertFalse(state.pointerRequested());

        state.beginFrame();
        state.requestPointer();
        assertTrue(state.endFrame());
        assertTrue(state.pointerRequested());
    }
}
