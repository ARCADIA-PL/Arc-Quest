package org.arcadia.arc_quest.mutil.overlay;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.mutil.core.ArcHudRoot;

public class ArcOverlayRoot extends ArcHudRoot {
    private final String overlayId;
    private boolean active = true;
    private int zIndex = 0;

    public ArcOverlayRoot(Minecraft minecraft, String overlayId) {
        super(minecraft);
        this.overlayId = overlayId;
    }

    public String getOverlayId() {
        return overlayId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        setVisible(active);
    }

    public int getZIndex() {
        return zIndex;
    }

    public ArcOverlayRoot setZIndex(int zIndex) {
        this.zIndex = zIndex;
        return this;
    }
}
