package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.client.gui.GuiGraphics;

/** A client-only picture. Its appearance never grants an item or JEI ingredient identity. */
public interface ObjectiveIconVisual {
    void render(GuiGraphics graphics, int x, int y, int size);

    /** Fades the completed picture as one group, including overlapping UV layers and cached heads. */
    default void render(GuiGraphics graphics, int x, int y, int size, float alpha) {
        if (available()) ObjectiveIconAlpha.renderVisual(graphics, this, x, y, size, alpha);
    }

    /** False while a portrait is preparing, after invalidation, or when its source is unavailable. */
    default boolean available() { return true; }
}
