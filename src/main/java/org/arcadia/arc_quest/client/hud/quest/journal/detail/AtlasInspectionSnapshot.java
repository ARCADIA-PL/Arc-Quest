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
    List<Node> nodes,
    List<Control> controls,
    int inspectorContentHeight,
    int inspectorViewportHeight) {
  public AtlasInspectionSnapshot {
    nodes = List.copyOf(nodes);
    controls = List.copyOf(controls);
  }

  public record Node(String bindingId, String entryId, HudRect bounds) {}

  public record Control(String label, HudRect bounds) {}

  public static AtlasInspectionSnapshot empty() {
    return new AtlasInspectionSnapshot(
        0, 0, 1, new HudRect(0, 0, 0, 0), null, "", "", "", "", 0, 0, 1, List.of(), List.of(), 0, 0);
  }
}
