package org.arcadia.arc_quest.client.hud.component;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class HudCursorManager {
    private static long pointerCursorHandle;
    private static boolean pointerRequested;
    private static boolean pointerApplied;
    private static int frameDepth;

    private HudCursorManager() {
    }

    public static void beginFrame() {
        if (frameDepth++ == 0) {
            pointerRequested = false;
        }
    }

    public static void requestPointer() {
        pointerRequested = true;
    }

    public static void requestPointer(boolean hovered) {
        if (hovered) requestPointer();
    }

    public static void requestPointer(double mouseX, double mouseY,
                                      double x, double y, double width, double height) {
        requestPointer(mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height);
    }

    public static void requestPointer(double mouseX, double mouseY, HudRect bounds) {
        requestPointer(mouseX, mouseY, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    public static void apply() {
        if (frameDepth > 0 && --frameDepth > 0) return;

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
        frameDepth = 0;
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
