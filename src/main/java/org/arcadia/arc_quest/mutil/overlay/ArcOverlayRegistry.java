package org.arcadia.arc_quest.mutil.overlay;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArcOverlayRegistry {
    private static final ArcOverlayRegistry INSTANCE = new ArcOverlayRegistry();

    private final Map<String, ArcOverlayRoot> overlays = new LinkedHashMap<>();

    public static ArcOverlayRegistry getInstance() {
        return INSTANCE;
    }

    public void register(ArcOverlayRoot overlay) {
        if (overlay == null) return;
        overlays.put(overlay.getOverlayId(), overlay);
    }

    public ArcOverlayRoot get(String overlayId) {
        return overlays.get(overlayId);
    }

    public void unregister(String overlayId) {
        overlays.remove(overlayId);
    }

    public void clear() {
        overlays.clear();
    }

    public Collection<ArcOverlayRoot> values() {
        return overlays.values();
    }
}
