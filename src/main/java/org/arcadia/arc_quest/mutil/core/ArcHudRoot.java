package org.arcadia.arc_quest.mutil.core;

import net.minecraft.client.Minecraft;

public class ArcHudRoot extends ArcGuiRoot {
    public ArcHudRoot(Minecraft minecraft) {
        super(minecraft);
    }

    public int getCenterX() {
        return getWidth() / 2;
    }

    public int getCenterY() {
        return getHeight() / 2;
    }
}
