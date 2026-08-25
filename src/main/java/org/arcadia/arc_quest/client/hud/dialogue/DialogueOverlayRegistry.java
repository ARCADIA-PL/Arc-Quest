package org.arcadia.arc_quest.client.hud.dialogue;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 对话界面顶层叠加渲染扩展点。 */
public final class DialogueOverlayRegistry {
    private static final List<Overlay> OVERLAYS = new CopyOnWriteArrayList<>();

    private DialogueOverlayRegistry() {
    }

    public static void register(Overlay overlay) {
        if (overlay != null) OVERLAYS.add(overlay);
    }

    public static void render(GuiGraphics graphics, DialogueScreen screen,
                              String dialogueId, int mouseX, int mouseY, float partialTick) {
        for (Overlay overlay : OVERLAYS) {
            if (overlay.shouldRender(screen, dialogueId)) {
                overlay.render(graphics, screen, dialogueId, mouseX, mouseY, partialTick);
            }
        }
    }

    public interface Overlay {
        boolean shouldRender(DialogueScreen screen, String dialogueId);

        void render(GuiGraphics graphics, DialogueScreen screen, String dialogueId,
                    int mouseX, int mouseY, float partialTick);
    }
}