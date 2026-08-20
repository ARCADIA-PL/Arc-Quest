package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class HudCursorManager {
    private static long pointerCursorHandle;
    private static boolean pointerRequested;
    private static boolean pointerApplied;

    private HudCursorManager() {
    }

    public static void beginFrame() {
        pointerRequested = false;
    }

    public static void requestPointer() {
        pointerRequested = true;
    }

    public static void requestPointer(boolean hovered) {
        if (hovered) requestPointer();
    }

    public static void apply() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null || pointerRequested == pointerApplied) return;

        long windowHandle = minecraft.getWindow().getWindow();
        if (pointerRequested) {
            if (pointerCursorHandle == 0L) {
                pointerCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
            }
            if (pointerCursorHandle == 0L) return;
            GLFW.glfwSetCursor(windowHandle, pointerCursorHandle);
        } else {
            GLFW.glfwSetCursor(windowHandle, 0L);
        }
        pointerApplied = pointerRequested;
    }

    public static void reset() {
        pointerRequested = false;
        if (!pointerApplied) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null) {
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0L);
        }
        pointerApplied = false;
    }

    public static void release() {
        reset();
        if (pointerCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(pointerCursorHandle);
            pointerCursorHandle = 0L;
        }
    }
}
