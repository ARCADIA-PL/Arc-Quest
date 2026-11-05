package org.arcadia.arc_quest.client.quest.collection;

/** Camera motion must never manufacture a hover underneath a stationary pointer. */
public final class AtlasHoverGuard {
  private boolean blocked;
  private double anchorX, anchorY;
  private double pointerX, pointerY;
  private boolean hasPointer;

  public void suppress(double x, double y) {
    blocked = true;
    // Render coordinates are rounded and can move with the host layout. Only native
    // pointer events establish intent; a later callback at the same position is not motion.
    anchorX = hasPointer ? pointerX : x;
    anchorY = hasPointer ? pointerY : y;
  }

  public void pointerMoved(double x, double y, boolean cameraMoving) {
    pointerX = x;
    pointerY = y;
    hasPointer = true;
    if (cameraMoving) suppress(x, y);
    else if (Math.hypot(x - anchorX, y - anchorY) >= 3) blocked = false;
  }

  public boolean blocked() { return blocked; }
}
