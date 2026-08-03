package org.arcadia.arc_quest.client.hud.quest.graph;

import java.util.Collection;

public record GraphBounds(float minX, float minY, float maxX, float maxY) {
    public static GraphBounds of(Collection<GraphNodeLayout> nodes,
                                 float halfWidth, float halfHeight) {
        if (nodes.isEmpty()) return null;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (GraphNodeLayout node : nodes) {
            minX = Math.min(minX, node.x() - halfWidth);
            minY = Math.min(minY, node.y() - halfHeight);
            maxX = Math.max(maxX, node.x() + halfWidth);
            maxY = Math.max(maxY, node.y() + halfHeight);
        }
        return new GraphBounds(minX, minY, maxX, maxY);
    }

    public float width() {
        return Math.max(1f, maxX - minX);
    }

    public float height() {
        return Math.max(1f, maxY - minY);
    }
}
