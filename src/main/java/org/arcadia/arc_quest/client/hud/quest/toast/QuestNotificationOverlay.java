package org.arcadia.arc_quest.client.hud.quest.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Sole renderer for quest, phase, collection, and pending-action notifications on the left. */
public final class QuestNotificationOverlay implements IGuiOverlay {
    public static final QuestNotificationOverlay INSTANCE = new QuestNotificationOverlay();
    private final QuestNotificationToast renderer = new QuestNotificationToast();

    private QuestNotificationOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!QuestToastManager.canDisplay(minecraft)) return;
        renderer.render(graphics, minecraft.font, QuestToastManager.currentDisplay(),
                screenWidth, screenHeight, partialTick);
    }
}
