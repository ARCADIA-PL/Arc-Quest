package org.arcadia.arc_quest.client.quest.collection;

/** Finite, cursor anchored category camera. All navigation paths share these limits. */
public final class AtlasCamera {
  public record ZoomLimits(double minimum, double maximum, double fit) {}

  /** Content fit is a preference; actual node and spacing readability remain lower bounds. */
  public static ZoomLimits limits(
      double viewportWidth,
      double viewportHeight,
      double contentWidth,
      double contentHeight,
      double nodeSize,
      double nodeWidth,
      double nodeHeight,
      double nearestSpacing,
      org.arcadia.arc_quest.quest.api.CollectionAtlasProfile.Zoom profile) {
    double vw = Math.max(1, viewportWidth), vh = Math.max(1, viewportHeight);
    double fit = Math.min(vw / Math.max(1, contentWidth), vh / Math.max(1, contentHeight));
    double readable =
        Math.max(profile.hardMin(), profile.minOperableNode() / Math.max(1, nodeSize));
    if (nearestSpacing > 0)
      readable = Math.max(readable, profile.minOperableSpacing() / nearestSpacing);
    double hardMaximum = Math.min(profile.hardMax(), profile.maxNodeSize() / Math.max(1, nodeSize));
    if (readable > hardMaximum)
      throw new IllegalArgumentException("Atlas operable size conflicts with maximum node size");
    double viewportMaximum =
        Math.min(vw / Math.max(1, nodeWidth), vh / Math.max(1, nodeHeight)) * .9;
    double maximum = Math.min(hardMaximum, Math.max(readable, viewportMaximum));
    double minimum =
        Math.min(
            maximum,
            Math.max(readable, Math.min(profile.baselineMax(), profile.fitMultiplier() * fit)));
    return new ZoomLimits(minimum, maximum, fit);
  }

  public double x, y, zoom = 1;
  public double velocityX, velocityY;
  private double minimumZoom = .65, maximumZoom = 2.4, friction = 10, focusMultiplier = 1.04;

  public void configure(double minimum, double maximum, double damping) {
    minimumZoom = Math.max(.05, Math.min(8, minimum));
    maximumZoom = Math.max(minimumZoom, Math.min(8, maximum));
    friction = Math.max(1, Math.min(40, damping));
    zoom = Math.max(minimumZoom, Math.min(maximumZoom, zoom));
  }

  private double restoreX, restoreY, restoreZoom;
  private boolean focused;

  public void focusMultiplier(double value) {
    focusMultiplier = Math.max(1, Math.min(2, value));
  }

  public void discardFocus() {
    focused = false;
    cancelMotion();
  }

  public void cancelMotion() {
    velocityX = velocityY = 0;
  }

  public void pan(double dx, double dy) {
    cancelMotion();
    x += dx;
    y += dy;
  }

  public void zoomAt(double factor, double cursorX, double cursorY) {
    cancelMotion();
    double next = Math.max(minimumZoom, Math.min(maximumZoom, zoom * factor));
    x = cursorX - (cursorX - x) * next / zoom;
    y = cursorY - (cursorY - y) * next / zoom;
    zoom = next;
  }

  public void focus(double targetX, double targetY, double viewportWidth, double viewportHeight) {
    if (!focused) {
      restoreX = x;
      restoreY = y;
      restoreZoom = zoom;
      focused = true;
    }
    cancelMotion();
    zoom = Math.max(minimumZoom,Math.min(maximumZoom, restoreZoom * focusMultiplier));
    x = viewportWidth * .40 - targetX * zoom;
    y = viewportHeight * .45 - targetY * zoom;
  }

  /** Correct only the overlap with the inspector; keep the user's original region in view. */
  public void focusRegion(double minX, double minY, double maxX, double maxY,
                          double selectedX, double selectedY, double nodeSize,
                          double availableWidth, double height) {
    if (!focused) {
      restoreX = x; restoreY = y; restoreZoom = zoom; focused = true;
    }
    cancelMotion();
    double oldZoom = zoom;
    zoom = Math.max(minimumZoom, Math.min(maximumZoom, restoreZoom * focusMultiplier));
    double anchorX = availableWidth / 2, anchorY = height / 2;
    x = anchorX - (anchorX - x) * zoom / oldZoom;
    y = anchorY - (anchorY - y) * zoom / oldZoom;
    double margin = availableWidth < 150 ? 6 : 12;
    boolean fits = (maxX - minX) * zoom <= availableWidth - 2 * margin
        && (maxY - minY) * zoom <= height - 2 * margin;
    if (!fits) {
      minX = selectedX; maxX = selectedX + nodeSize;
      minY = selectedY; maxY = selectedY + nodeSize;
    }
    double left = x + minX * zoom, right = x + maxX * zoom;
    double top = y + minY * zoom, bottom = y + maxY * zoom;
    x += left < margin ? margin - left : right > availableWidth - margin ? availableWidth - margin - right : 0;
    y += top < margin ? margin - top : bottom > height - margin ? height - margin - bottom : 0;
  }

  public void restore() {
    cancelMotion();
    if (focused) {
      x = restoreX;
      y = restoreY;
      zoom = Math.max(minimumZoom,Math.min(maximumZoom,restoreZoom));
      focused = false;
    }
  }

  public void overview(double width, double height, double contentWidth, double contentHeight) {
    overview(width, height, contentWidth, contentHeight, 1, 1);
  }

  public void overview(
      double width,
      double height,
      double contentWidth,
      double contentHeight,
      double fitMultiplier,
      double baseline) {
    focused = false;
    cancelMotion();
    double fit = Math.min(width / Math.max(1, contentWidth), height / Math.max(1, contentHeight));
    zoom = Math.max(minimumZoom, Math.min(maximumZoom, Math.min(baseline, fitMultiplier * fit)));
    // V6 starts an oversized category at its first region, rather than cropping the first groups.
    x = Math.max(12, (width - contentWidth * zoom) / 2);
    y = Math.max(12, (height - contentHeight * zoom) / 2);
  }

  /** Keeps some content reachable while allowing a bounded elastic drag margin. */
  public void settle(
      double minX,
      double minY,
      double maxX,
      double maxY,
      double width,
      double height,
      double overscroll,
      double decayMs,
      double seconds,
      boolean immediate) {
    if (focused) return;
    double lowerX = width - overscroll - maxX * zoom, upperX = overscroll - minX * zoom;
    double lowerY = height - overscroll - maxY * zoom, upperY = overscroll - minY * zoom;
    if (lowerX > upperX) {
      double center = (lowerX + upperX) / 2;
      lowerX = center;
      upperX = center;
    }
    if (lowerY > upperY) {
      double center = (lowerY + upperY) / 2;
      lowerY = center;
      upperY = center;
    }
    double targetX = Math.max(lowerX, Math.min(upperX, x)),
        targetY = Math.max(lowerY, Math.min(upperY, y));
    double blend =
        immediate
            ? 1
            : 1 - Math.exp(-Math.max(0, Math.min(.1, seconds)) * 1000 / Math.max(16, decayMs));
    x += (targetX - x) * blend;
    y += (targetY - y) * blend;
    if (Math.abs(targetX - x) > .1) velocityX = 0;
    if (Math.abs(targetY - y) > .1) velocityY = 0;
  }

  public void advance(double seconds) {
    seconds = Math.max(0, Math.min(.05, seconds));
    x += velocityX * seconds;
    y += velocityY * seconds;
    double decay = Math.exp(-friction * seconds);
    velocityX *= decay;
    velocityY *= decay;
    if (Math.abs(velocityX) + Math.abs(velocityY) < 2) cancelMotion();
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(zoom)) {
      x = y = 0;
      zoom = 1;
      cancelMotion();
    }
  }
}
