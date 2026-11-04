package org.arcadia.arc_quest.client.quest.collection;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AtlasCameraTest {
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
