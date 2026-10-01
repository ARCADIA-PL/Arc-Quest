package org.arcadia.arc_quest.client.hud.component;

/** Cursor requests only; kept independent of Minecraft/GLFW for lifecycle regression tests. */
final class HudCursorState {
    private boolean pointerRequested;
    private int frameDepth;
    private boolean hudPointerRequested;
    private int hudRenderDepth;

    void beginFrame() {
        if (hudRenderDepth > 0) return;
        if (frameDepth++ == 0) pointerRequested = false;
    }

    void requestPointer() {
        if (hudRenderDepth > 0) hudPointerRequested = true;
        else pointerRequested = true;
    }

    boolean endFrame() {
        if (hudRenderDepth > 0) return false;
        return frameDepth == 0 || --frameDepth == 0;
    }

    boolean pointerRequested() {
        return pointerRequested;
    }

    /** No frame is opened here: Forge can cancel Pre without ever issuing Post. */
    void beginHudFrame() {
        hudPointerRequested = false;
    }

    void renderHud(Runnable render) {
        hudRenderDepth++;
        try {
            render.run();
        } finally {
            hudRenderDepth--;
        }
    }

    boolean endHudFrame() {
        boolean requested = hudPointerRequested;
        hudPointerRequested = false;
        if (frameDepth > 0 || hudRenderDepth > 0) return false;
        pointerRequested = requested;
        return true;
    }

    void reset() {
        frameDepth = 0;
        pointerRequested = false;
        hudPointerRequested = false;
        // A renderer may reset while closing. Its synchronous finally still owns hudRenderDepth.
    }
}
