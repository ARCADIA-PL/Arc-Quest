package org.arcadia.arc_quest.client.hud.quest.icon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectiveIconAlphaTest {
    @Test void zeroAndInvalidAlphaDoNotDraw() {
        for (float alpha : new float[] {0, -1, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
            assertEquals(0, ObjectiveIconAlpha.normalizedAlpha(alpha));
    }

    @Test void animationLowAlphaHasNoCutoutThreshold() {
        for (float alpha : new float[] {1f / 255f, .05f, .1f, .5f, 253f / 255f})
            assertEquals(alpha, ObjectiveIconAlpha.normalizedAlpha(alpha));
    }

    @Test void steadyStateRoundingUsesOpaqueFastPath() {
        for (float alpha : new float[] {254f / 255f, 1, 2})
            assertEquals(1, ObjectiveIconAlpha.normalizedAlpha(alpha));
    }
}
