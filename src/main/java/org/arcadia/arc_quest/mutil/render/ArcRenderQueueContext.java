package org.arcadia.arc_quest.mutil.render;

public final class ArcRenderQueueContext {
    private static ArcRenderQueue current;

    private ArcRenderQueueContext() {
    }

    public static void begin(ArcRenderQueue queue) {
        current = queue;
    }

    public static void end() {
        current = null;
    }

    public static ArcRenderQueue current() {
        return current;
    }
}
