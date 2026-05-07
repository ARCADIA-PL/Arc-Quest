package org.arcadia.arc_quest.client.hud.quest.editor;

public class GraphBounds {
    public double minX;
    public double minY;
    public double maxX;
    public double maxY;

    public boolean isEmpty() {
        return maxX <= minX || maxY <= minY;
    }
}
