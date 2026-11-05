package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import java.util.List;
import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Read-only GUI-coordinate inspection data; no input or mutation shortcuts. */
public record AtlasInspectionSnapshot(
    int originX,
    int originY,
    float uiScale,
    HudRect canvas,
    HudRect inspector,
    String category,
    String binding,
    String variant,
    String query,
    double cameraX,
    double cameraY,
    double zoom,
    double destinationZoom,
    boolean cameraMoving,
    float inspectorReveal,
    boolean hoverSuppressed,
    boolean tooltipPending,
    List<Node> nodes,
    List<Control> controls,
    int inspectorContentHeight,
    int inspectorViewportHeight, String detailTab, int detailPage, int detailPageCount, List<String> visibleDetailText) {
  public AtlasInspectionSnapshot {
    visibleDetailText = List.copyOf(visibleDetailText);
    nodes = List.copyOf(nodes);
    controls = List.copyOf(controls);
  }

  public record Node(String bindingId, String entryId, HudRect bounds, boolean obtainable, boolean discovered) {}

  public record Control(String label, HudRect bounds) {}

  public static AtlasInspectionSnapshot empty() {
    return new AtlasInspectionSnapshot(
        0, 0, 1, new HudRect(0, 0, 0, 0), null, "", "", "", "", 0, 0, 1, 1, false, 0, false, false, List.of(), List.of(), 0, 0, "", 0, 0, List.of());
  }
}
