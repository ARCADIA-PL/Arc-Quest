package org.arcadia.arc_quest.mutil.input;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

import javax.annotation.Nullable;
import java.util.List;

public final class ArcInputRouter {
    private ArcInputRouter() {
    }

    public static boolean mouseClicked(ArcGuiElement root, double mouseX, double mouseY, int button) {
        return root != null && root.onMouseClick(mouseX, mouseY, button);
    }

    public static void mouseReleased(ArcGuiElement root, double mouseX, double mouseY, int button) {
        if (root != null) root.onMouseRelease(mouseX, mouseY, button);
    }

    public static boolean mouseScrolled(ArcGuiElement root, double mouseX, double mouseY, double distance) {
        return root != null && root.onMouseScroll(mouseX, mouseY, distance);
    }

    public static boolean keyPressed(ArcGuiElement root, int keyCode, int scanCode, int modifiers) {
        return root != null && root.onKeyPress(keyCode, scanCode, modifiers);
    }

    public static boolean keyReleased(ArcGuiElement root, int keyCode, int scanCode, int modifiers) {
        return root != null && root.onKeyRelease(keyCode, scanCode, modifiers);
    }

    public static boolean charTyped(ArcGuiElement root, char character, int modifiers) {
        return root != null && root.onCharTyped(character, modifiers);
    }

    @Nullable
    public static List<Component> tooltip(ArcGuiElement root) {
        return root != null ? root.getTooltipLines() : null;
    }
}
