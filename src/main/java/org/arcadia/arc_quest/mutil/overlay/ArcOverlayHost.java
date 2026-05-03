package org.arcadia.arc_quest.mutil.overlay;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArcOverlayHost {
    private final List<ArcOverlayRoot> overlays = new ArrayList<>();
    private boolean dirty = false;
    private boolean enabled = true;

    public void register(ArcOverlayRoot overlay) {
        if (overlay == null || overlays.contains(overlay)) return;
        overlays.add(overlay);
        dirty = true;
    }

    public void unregister(String overlayId) {
        if (overlayId == null || overlayId.isEmpty()) return;
        if (overlays.removeIf(overlay -> overlayId.equals(overlay.getOverlayId()))) {
            dirty = true;
        }
    }

    public ArcOverlayRoot get(String overlayId) {
        if (overlayId == null || overlayId.isEmpty()) return null;
        for (ArcOverlayRoot overlay : overlays) {
            if (overlayId.equals(overlay.getOverlayId())) return overlay;
        }
        return null;
    }

    public boolean setOverlayActive(String overlayId, boolean active) {
        ArcOverlayRoot overlay = get(overlayId);
        if (overlay == null) return false;
        overlay.setActive(active);
        return true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void clear() {
        overlays.clear();
        dirty = false;
    }

    public void renderAll(GuiGraphics graphics, float partialTick) {
        if (!enabled) return;
        if (dirty) {
            overlays.sort(Comparator.comparingInt(ArcOverlayRoot::getZIndex));
            dirty = false;
        }
        for (ArcOverlayRoot overlay : overlays) {
            if (overlay != null && overlay.isActive()) {
                overlay.drawRoot(graphics, partialTick);
            }
        }
    }

    public List<ArcOverlayRoot> getOverlays() {
        return List.copyOf(overlays);
    }
}
