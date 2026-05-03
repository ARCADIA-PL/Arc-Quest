package org.arcadia.arc_quest.mutil.overlay;

import net.minecraft.client.Minecraft;

public class ArcBlockingOverlayRoot extends ArcOverlayRoot {
    private boolean blocksInput = true;
    private boolean dimsBackground = true;
    private int dimColor = 0x78000000;

    public ArcBlockingOverlayRoot(Minecraft minecraft, String overlayId) {
        super(minecraft, overlayId);
        setZIndex(10_000);
    }

    public boolean blocksInput() {
        return blocksInput && isActive();
    }

    public ArcBlockingOverlayRoot setBlocksInput(boolean blocksInput) {
        this.blocksInput = blocksInput;
        return this;
    }

    public boolean dimsBackground() {
        return dimsBackground;
    }

    public ArcBlockingOverlayRoot setDimsBackground(boolean dimsBackground) {
        this.dimsBackground = dimsBackground;
        return this;
    }

    public int getDimColor() {
        return dimColor;
    }

    public ArcBlockingOverlayRoot setDimColor(int dimColor) {
        this.dimColor = dimColor;
        return this;
    }
}
