package org.arcadia.arc_quest.client.quest.collection;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AtlasHoverGuardTest {
  @Test
  void roundedOrShiftedRenderCoordinatesCannotManufacturePointerIntent() {
    var guard = new AtlasHoverGuard();
    guard.pointerMoved(120.3, 130.7, false);
    guard.suppress(114, 124);
    guard.pointerMoved(120.3, 130.7, false);
    assertTrue(guard.blocked());
    guard.pointerMoved(124, 131, false);
    assertFalse(guard.blocked());
  }

  @Test
  void stationaryPointerNeverReactivatesHoverAfterCameraMotion() {
    var guard = new AtlasHoverGuard();
    guard.suppress(20, 30);
    for (int i = 0; i < 60; i++) guard.pointerMoved(20, 30, false);
    assertTrue(guard.blocked());
    guard.pointerMoved(21, 31, false);
    assertTrue(guard.blocked());
    guard.pointerMoved(24, 30, false);
    assertFalse(guard.blocked());
  }

  @Test
  void movementDuringTransitionDoesNotCountAsIntentAfterItStops() {
    var guard = new AtlasHoverGuard();
    guard.suppress(20, 30);
    guard.pointerMoved(120, 130, true);
    guard.pointerMoved(120, 130, false);
    assertTrue(guard.blocked());
    guard.pointerMoved(120, 134, false);
    assertFalse(guard.blocked());
  }
}
