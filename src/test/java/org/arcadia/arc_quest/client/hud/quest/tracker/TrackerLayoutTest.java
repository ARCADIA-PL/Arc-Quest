package org.arcadia.arc_quest.client.hud.quest.tracker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackerLayoutTest {
    private static final double EPSILON = 0.0001;

    @Test
    void defaultKeepsLegacyUpperRightLayoutAtNormalResolution() {
        TrackerLayout.Frame frame = TrackerLayout.resolve(640, 360, 3, 100, null, 0);

        assertEquals(new TrackerLayout.Settings(1, 0, 1), TrackerLayout.DEFAULT);
        assertEquals(173, frame.contentWidth());
        assertEquals(153.6, frame.width(), EPSILON);
        assertEquals(478.4, frame.x(), EPSILON);
        assertEquals(20, frame.y(), EPSILON);
        assertEquals(632, frame.right(), EPSILON);
        assertEquals(100, frame.contentHeight());
        assertFits(frame, 640, 360);
    }

    @Test
    void changingMinecraftGuiScalePreservesPhysicalPanelSize() {
        TrackerLayout.Frame reference = TrackerLayout.resolve(1920, 1080, 1, 120, null, 0);

        for (int guiScale : new int[]{2, 3, 4, 6}) {
            TrackerLayout.Frame frame = TrackerLayout.resolve(1920 / guiScale, 1080 / guiScale,
                    guiScale, 120, null, 0);
            assertEquals(reference.width(), frame.width() * guiScale, EPSILON);
            assertEquals(reference.height(), frame.height() * guiScale, EPSILON);
            assertEquals(reference.contentWidth(), frame.contentWidth());
            assertEquals(120, frame.contentHeight());
        }
    }

    @Test
    void savedPositionRetainsItsFractionOfAvailableSpaceAcrossResolutions() {
        TrackerLayout.Settings settings = new TrackerLayout.Settings(0.3, 0.65, 1.2);

        for (int[] size : new int[][]{{320, 240}, {640, 360}, {1280, 720}, {2560, 1440}}) {
            TrackerLayout.Frame frame = TrackerLayout.resolve(size[0], size[1], 2, 100, settings, 0);
            TrackerLayout.Frame start = TrackerLayout.resolve(size[0], size[1], 2, 100,
                    new TrackerLayout.Settings(0, 0, settings.scale()), 0);
            TrackerLayout.Frame end = TrackerLayout.resolve(size[0], size[1], 2, 100,
                    new TrackerLayout.Settings(1, 1, settings.scale()), 0);

            assertEquals(0.3, (frame.x() - start.x()) / (end.x() - start.x()), EPSILON);
            assertEquals(0.65, (frame.y() - start.y()) / (end.y() - start.y()), EPSILON);
            assertFits(frame, size[0], size[1]);
        }
    }

    @Test
    void allFourCornersStayInsideTheHudSafeArea() {
        for (int x : new int[]{0, 1}) {
            for (int y : new int[]{0, 1}) {
                TrackerLayout.Frame frame = TrackerLayout.resolve(640, 360, 3, 100,
                        new TrackerLayout.Settings(x, y, 1), 0);
                assertEquals(x == 0 ? 2 : 632, x == 0 ? frame.x() : frame.right(), EPSILON);
                assertEquals(y == 0 ? 20 : 356, y == 0 ? frame.y() : frame.bottom(), EPSILON);
                assertFits(frame, 640, 360);
            }
        }
    }

    @Test
    void draggingBeyondEveryCornerClampsPositionAndPreservesSizePreference() {
        TrackerLayout.Settings original = new TrackerLayout.Settings(0.4, 0.6, 1.25);

        for (double mouseX : new double[]{-10_000, 10_000}) {
            for (double mouseY : new double[]{-10_000, 10_000}) {
                TrackerLayout.Settings moved = TrackerLayout.moveTo(original, 640, 360, 3, 100, mouseX, mouseY);
                assertEquals(mouseX < 0 ? 0 : 1, moved.x(), EPSILON);
                assertEquals(mouseY < 0 ? 0 : 1, moved.y(), EPSILON);
                assertEquals(original.scale(), moved.scale(), EPSILON);
                assertFits(TrackerLayout.resolve(640, 360, 3, 100, moved, 0), 640, 360);
            }
        }
    }

    @Test
    void draggingToAnInteriorPointPlacesThePanelAtThatPoint() {
        TrackerLayout.Settings moved = TrackerLayout.moveTo(
                new TrackerLayout.Settings(1, 0, 1.3), 640, 360, 3, 100, 120, 90);
        TrackerLayout.Frame frame = TrackerLayout.resolve(640, 360, 3, 100, moved, 0);

        assertEquals(120, frame.x(), EPSILON);
        assertEquals(90, frame.y(), EPSILON);
        assertEquals(1.3, moved.scale(), EPSILON);
    }

    @Test
    void resolvingThenDraggingToTheSamePointPreservesSavedSettings() {
        for (int[] size : new int[][]{{320, 240}, {640, 360}, {1280, 720}}) {
            for (double scale : new double[]{0.5, 1, 2}) {
                TrackerLayout.Settings settings = new TrackerLayout.Settings(0.35, 0.7, scale);
                TrackerLayout.Frame frame = TrackerLayout.resolve(size[0], size[1], 3, 90, settings, 0);
                TrackerLayout.Settings moved = TrackerLayout.moveTo(settings, size[0], size[1],
                        3, 90, frame.x(), frame.y());
                assertEquals(settings.x(), moved.x(), EPSILON);
                assertEquals(settings.y(), moved.y(), EPSILON);
                assertEquals(settings.scale(), moved.scale(), EPSILON);
            }
        }
    }

    @Test
    void sizeControlScalesBothDimensionsWithoutChangingTheSavedPosition() {
        TrackerLayout.Frame base = TrackerLayout.resolve(1280, 720, 3, 80,
                new TrackerLayout.Settings(0.5, 0.5, 1), 0);

        for (double scale : new double[]{0.5, 1.25, 2}) {
            TrackerLayout.Frame resized = TrackerLayout.resolve(1280, 720, 3, 80,
                    new TrackerLayout.Settings(0.5, 0.5, scale), 0);
            assertEquals(base.width() * scale, resized.width(), EPSILON);
            assertEquals(base.height() * scale, resized.height(), EPSILON);
            assertEquals(base.x() + base.width() / 2, resized.x() + resized.width() / 2, EPSILON);
            assertEquals(base.y() + base.height() / 2, resized.y() + resized.height() / 2, EPSILON);
            assertEquals(base.contentWidth(), resized.contentWidth());
            assertEquals(80, resized.contentHeight());
        }
    }

    @Test
    void longQuestShrinksToFitASmallScreenWithoutCroppingContent() {
        TrackerLayout.Frame frame = TrackerLayout.resolve(320, 180, 3, 1800,
                new TrackerLayout.Settings(1, 1, 2), 0);

        assertFits(frame, 320, 180);
        assertEquals(1800, frame.contentHeight());
        assertEquals(176, frame.bottom(), EPSILON);
        assertEquals((double) frame.contentWidth() / 1800, frame.width() / frame.height(), EPSILON);
        assertTrue(frame.uiScale() < TrackerLayout.MIN_SCALE,
                "Screen fitting may reduce the render scale below the user's size multiplier range");
    }

    @Test
    void draggingWhenContentFillsAnAxisKeepsItsSavedPlacementForLater() {
        TrackerLayout.Settings settings = new TrackerLayout.Settings(0.25, 0.75, 1.5);
        TrackerLayout.Settings moved = TrackerLayout.moveTo(settings, 320, 180, 3, 1800, 80, -10_000);

        assertEquals(settings.y(), moved.y(), EPSILON);
        assertEquals(settings.scale(), moved.scale(), EPSILON);
        TrackerLayout.Frame shortQuest = TrackerLayout.resolve(320, 180, 3, 80, moved, 0);
        TrackerLayout.Settings roundTrip = TrackerLayout.moveTo(moved, 320, 180, 3, 80,
                shortQuest.x(), shortQuest.y());
        assertEquals(0.75, roundTrip.y(), EPSILON);
    }

    @Test
    void toastPushMovesTheTopAnchorDownButNeverOffScreen() {
        TrackerLayout.Frame baseline = TrackerLayout.resolve(640, 360, 3, 100, null, 0);
        TrackerLayout.Frame pushed = TrackerLayout.resolve(640, 360, 3, 100, null, 35);

        assertEquals(baseline.y() + 35, pushed.y(), EPSILON);
        assertEquals(baseline.width(), pushed.width(), EPSILON);
        for (float push : new float[]{-100, 0, 100, 10_000, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertFits(TrackerLayout.resolve(640, 360, 3, 100, null, push), 640, 360);
        }
    }

    @Test
    void tinyOrInvalidViewportDimensionsStillProduceAFiniteVisiblePanel() {
        for (int[] size : new int[][]{{1, 1}, {2, 3}, {3, 2}, {16, 16}, {120, 50}, {0, 0}, {-20, -10}}) {
            for (float push : new float[]{0, 1000}) {
                TrackerLayout.Frame frame = TrackerLayout.resolve(size[0], size[1], 3, 1000,
                        new TrackerLayout.Settings(1, 1, 2), push);
                assertFits(frame, Math.max(1, size[0]), Math.max(1, size[1]));
                assertEquals(1000, frame.contentHeight());
            }
        }
    }

    @Test
    void corruptedConfigurationNormalizesToFiniteDefaultsAndLimits() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(TrackerLayout.DEFAULT, new TrackerLayout.Settings(invalid, invalid, invalid));
            assertFits(TrackerLayout.resolve(640, 360, invalid, 100, null, 0), 640, 360);
        }
        assertEquals(new TrackerLayout.Settings(0, 1, 0.5), new TrackerLayout.Settings(-2, 3, -100));
        assertEquals(new TrackerLayout.Settings(1, 0, 2), new TrackerLayout.Settings(2, -3, 100));
    }

    @Test
    void contentWidthKeepsEstablishedMinimumAndMaximumWrappingWidths() {
        assertEquals(160, TrackerLayout.contentWidth(320, 1));
        assertEquals(173, TrackerLayout.contentWidth(640, 3));
        assertEquals(220, TrackerLayout.contentWidth(1280, 3));
        assertEquals(220, TrackerLayout.contentWidth(7680, 4));
    }

    @Test
    void hitTestingUsesTheVisibleFrameAndExcludesTheOutsideEdges() {
        TrackerLayout.Frame frame = TrackerLayout.resolve(640, 360, 3, 100, null, 0);

        assertTrue(frame.contains(frame.x(), frame.y()));
        assertTrue(frame.contains(frame.x() + frame.width() / 2, frame.y() + frame.height() / 2));
        assertFalse(frame.contains(frame.right(), frame.y()));
        assertFalse(frame.contains(frame.x(), frame.bottom()));
        assertFalse(frame.contains(frame.x() - 1, frame.y()));
        assertFalse(frame.contains(frame.x(), frame.y() - 1));
    }

    private static void assertFits(TrackerLayout.Frame frame, int screenWidth, int screenHeight) {
        assertTrue(Double.isFinite(frame.x()) && Double.isFinite(frame.y()));
        assertTrue(Double.isFinite(frame.width()) && Double.isFinite(frame.height()));
        assertTrue(Double.isFinite(frame.uiScale()) && frame.uiScale() > 0);
        assertTrue(frame.width() > 0 && frame.height() > 0);
        assertTrue(frame.x() >= -EPSILON && frame.y() >= -EPSILON);
        assertTrue(frame.right() <= screenWidth + EPSILON, "Panel overflows the right edge: " + frame);
        assertTrue(frame.bottom() <= screenHeight + EPSILON, "Panel overflows the bottom edge: " + frame);
    }
}
