package org.arcadia.arc_quest.client.hud.quest.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.DeltaTracker;

/** Sole renderer for quest, phase, collection, and pending-action notifications on the left. */
public final class QuestNotificationOverlay implements LayeredDraw.Layer {
    public static final QuestNotificationOverlay INSTANCE = new QuestNotificationOverlay();
    private final QuestNotificationToast renderer = new QuestNotificationToast();

    private QuestNotificationOverlay() {}

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Minecraft minecraft = Minecraft.getInstance();
        if (!QuestToastManager.canDisplay(minecraft)) return;
        renderer.render(graphics, minecraft.font, QuestToastManager.currentDisplay(),
                screenWidth, screenHeight, partialTick);
    }
}
