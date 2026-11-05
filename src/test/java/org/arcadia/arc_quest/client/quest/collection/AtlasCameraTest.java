package org.arcadia.arc_quest.client.quest.collection;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AtlasCameraTest {
  private static void finish(AtlasCamera camera) {
    for (int i = 0; i < 10; i++) camera.advance(.05);
    assertFalse(camera.isAnimating());
  }

  @Test
  void animatedZoomKeepsCursorAnchorOnEveryFrameAndAccumulatesWheelInput() {
    var c = new AtlasCamera();
    c.x = 13; c.y = -9;
    double wx = (123 - c.x) / c.zoom, wy = (87 - c.y) / c.zoom;
    c.zoomAtAnimated(1.12, 123, 87);
    assertEquals(1, c.zoom);
    c.advance(.04);
    assertTrue(c.zoom > 1 && c.zoom < 1.12);
    c.zoomAtAnimated(1.12, 123, 87);
    assertEquals(1.12 * 1.12, c.destinationZoom(), 1e-8);
    for (int i = 0; i < 10; i++) {
      c.advance(.03);
      assertEquals(123, c.x + wx * c.zoom, 1e-8);
      assertEquals(87, c.y + wy * c.zoom, 1e-8);
    }
    assertFalse(c.isAnimating());
    assertEquals(1.12 * 1.12, c.zoom, 1e-8);
  }

  @Test
  void changingSelectionRetainsManualZoomAndClosingRestoresOriginalView() {
    var c = new AtlasCamera();
    c.x = 20; c.y = 10;
    c.focusRegionAnimated(20, 20, 100, 100, 20, 20, 28, 300, 240);
    assertEquals(1, c.zoom);
    finish(c);
    assertEquals(1.12, c.zoom, 1e-8);
    c.zoomAtAnimated(1.3, 150, 120);
    double manualZoom = c.destinationZoom();
    // Select while a wheel transition is still pending, a common real interaction.
    c.advance(.03);
    c.focusRegionAnimated(20, 20, 100, 100, 60, 60, 28, 300, 240);
    finish(c);
    assertEquals(manualZoom, c.zoom, 1e-8);
    c.restoreAnimated();
    assertEquals(manualZoom, c.zoom, 1e-8);
    c.advance(.04);
    assertTrue(c.zoom > 1 && c.zoom < manualZoom);
    finish(c);
    assertEquals(20, c.x, 1e-8);
    assertEquals(10, c.y, 1e-8);
    assertEquals(1, c.zoom, 1e-8);
  }

  @Test
  void rapidCloseAndReselectUsesRestoredViewWithoutScaleDrift() {
    var c = new AtlasCamera();
    c.focusRegionAnimated(0, 0, 100, 100, 20, 20, 28, 300, 240);
    c.advance(.04);
    c.restoreAnimated();
    c.advance(.02);
    c.focusRegionAnimated(0, 0, 100, 100, 60, 60, 28, 300, 240);
    finish(c);
    assertEquals(1.12, c.zoom, 1e-8);
    c.restoreAnimated();
    finish(c);
    assertEquals(1, c.zoom, 1e-8);
  }

  @Test
  void draggingCancelsTransitionAndOverviewClearsFocus() {
    var c = new AtlasCamera();
    c.focusRegionAnimated(0, 0, 100, 100, 20, 20, 28, 300, 240);
    c.advance(.03);
    double x = c.x;
    c.pan(17, 0);
    assertFalse(c.isAnimating());
    c.advance(.1);
    assertEquals(x + 17, c.x, 1e-8);
    c.overviewAnimated(300, 240, 1000, 1000, 1, 1.35);
    finish(c);
    double overviewZoom = c.zoom, overviewX = c.x;
    c.restore();
    assertEquals(overviewZoom, c.zoom);
    assertEquals(overviewX, c.x);
  }

  @Test
  void cursorAnchorSurvivesZoom() {
    AtlasCamera camera = new AtlasCamera();
    camera.x = 13;
    camera.y = -9;
    double worldX = (123 - camera.x) / camera.zoom, worldY = (87 - camera.y) / camera.zoom;
    camera.zoomAt(1.7, 123, 87);
    assertEquals(123, camera.x + worldX * camera.zoom, 1e-8);
    assertEquals(87, camera.y + worldY * camera.zoom, 1e-8);
  }

  @Test
  void closingInspectorRestoresCameraBeforeFirstFocus() {
    AtlasCamera camera = new AtlasCamera();
    camera.x = 34;
    camera.y = 62;
    camera.zoom = 1.2;
    camera.focus(200, 300, 400, 300);
    camera.focus(500, 600, 400, 300);
    camera.restore();
    assertEquals(34, camera.x);
    assertEquals(62, camera.y);
    assertEquals(1.2, camera.zoom);
  }

  @Test
  void explicitNavigationCancelsInertia() {
    AtlasCamera camera = new AtlasCamera();
    camera.velocityX = 500;
    camera.velocityY = 200;
    camera.zoomAt(1.1, 20, 20);
    double x = camera.x, y = camera.y;
    camera.advance(.05);
    assertEquals(x, camera.x);
    assertEquals(y, camera.y);
  }

  @Test
  void smallContentSettleKeepsRestoredBrowsePositionButStillCorrectsOverscroll() {
    var camera = new AtlasCamera();
    camera.x = 64; camera.y = 60;
    camera.focusRegionAnimated(0, 0, 300, 220, 20, 20, 28, 250, 400);
    finish(camera);
    camera.restoreAnimated();
    finish(camera);
    for (int i = 0; i < 20; i++) camera.settle(0, 0, 300, 220, 600, 400, 32, 180, .05, false);
    assertEquals(64, camera.x, 1e-8);
    assertEquals(60, camera.y, 1e-8);
    camera.pan(-1000, 1000);
    camera.settle(0, 0, 300, 220, 600, 400, 32, 180, .05, true);
    assertEquals(32, camera.x, 1e-8);
    assertEquals(148, camera.y, 1e-8);
  }

  @Test
  void overviewRetainsOperableLowerBoundOnLargeCategories() {
    AtlasCamera camera = new AtlasCamera();
    camera.overview(300, 200, 100000, 100000);
    assertEquals(.65, camera.zoom);
    assertTrue(Double.isFinite(camera.x));
  }

  @Test
  void contentFitChangesLowerBoundButLargeCanvasKeepsNodesOperable() {
    var profile = org.arcadia.arc_quest.quest.api.CollectionAtlasProfile.defaults().zoom();
    var small = AtlasCamera.limits(420, 300, 180, 120, 36, 86, 64, 100, profile);
    var large = AtlasCamera.limits(420, 300, 5000, 4000, 36, 86, 64, 100, profile);
    assertTrue(small.minimum() > large.minimum());
    assertEquals(Math.max(profile.hardMin(), profile.minOperableNode() / 36), large.minimum(), 1e-8);
    assertTrue(small.maximum() <= profile.maxNodeSize() / 36);
  }

  @Test
  void resizeConstrainsMaximumAndNearestSpacingConstrainsMinimum() {
    var profile = org.arcadia.arc_quest.quest.api.CollectionAtlasProfile.defaults().zoom();
    var wide = AtlasCamera.limits(420, 300, 500, 300, 36, 86, 64, 100, profile);
    var narrow = AtlasCamera.limits(90, 90, 500, 300, 36, 86, 64, 100, profile);
    var dense = AtlasCamera.limits(420, 300, 500, 300, 36, 86, 64, 20, profile);
    assertTrue(narrow.maximum() < wide.maximum());
    double readable = Math.max(profile.hardMin(), Math.max(profile.minOperableNode() / 36, profile.minOperableSpacing() / 20));
    assertTrue(dense.minimum() >= readable);
    assertEquals(Math.min(dense.maximum(), Math.max(readable,
        Math.min(profile.baselineMax(), profile.fitMultiplier() * dense.fit()))), dense.minimum(), 1e-8);
    assertTrue(dense.minimum() <= dense.maximum());
  }
  @Test
  void oversizedOverviewKeepsFirstRegionInsideCanvas() {
    AtlasCamera c = new AtlasCamera();
    c.configure(.65, 2.4, 10);
    c.overview(291, 60, 2000, 1000);
    assertTrue(c.x >= 0);
    assertTrue(c.y >= 0);
    assertTrue(c.x + 28 * c.zoom <= 291);
    assertTrue(c.y + 28 * c.zoom <= 60);
  }

  @Test
  void unobscuredGroupOnlyGetsSlightNonCumulativeZoomAndRestores() {
    AtlasCamera c = new AtlasCamera();
    c.x = 20; c.y = 10; c.zoom = 1;
    c.focusMultiplier(1.04);
    c.focusRegion(20, 20, 100, 100, 20, 20, 28, 300, 240);
    double firstX = c.x, firstY = c.y;
    assertEquals(1.04, c.zoom, 1e-8);
    assertTrue(Math.abs(c.x - 20) < 10);
    c.focusRegion(20, 20, 100, 100, 60, 60, 28, 300, 240);
    assertEquals(firstX, c.x, 1e-8);
    assertEquals(firstY, c.y, 1e-8);
    c.restore();
    assertEquals(20, c.x);
    assertEquals(10, c.y);
    assertEquals(1, c.zoom);
  }

  @Test
  void oversizedGroupKeepsSelectedPlateOutFromUnderOverlay() {
    AtlasCamera c = new AtlasCamera();
    c.focusRegion(0, 0, 2000, 1000, 900, 400, 28, 100, 120);
    assertTrue(c.x + 900 * c.zoom >= 0);
    assertTrue(c.x + 928 * c.zoom <= 100);
    assertTrue(c.y + 400 * c.zoom >= 0);
    assertTrue(c.y + 428 * c.zoom <= 120);
  }
}
