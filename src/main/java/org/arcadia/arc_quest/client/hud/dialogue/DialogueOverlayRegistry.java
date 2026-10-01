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

    static boolean hasChoiceReplacement(DialogueScreen screen, String dialogueId) {
        for (Overlay overlay : OVERLAYS) {
            if (overlay.replacesChoices() && overlay.shouldRender(screen, dialogueId)) return true;
        }
        return false;
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

        /** 临时以扩展内容替换选项区；旧扩展默认继续叠加，选项及会话数据保持不变。 */
        default boolean replacesChoices() { return false; }

        void render(GuiGraphics graphics, DialogueScreen screen, String dialogueId,
                    int mouseX, int mouseY, float partialTick);
    }
}
