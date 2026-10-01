package org.arcadia.arc_quest.client.hud.quest.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.trackingmenu.QuestTrackingMenuScreen;

/** Owns the shared quest/collection notification slots independently of the tracked quest. */
public final class QuestNotificationOverlay implements IGuiOverlay {
    public static final QuestNotificationOverlay INSTANCE = new QuestNotificationOverlay();

    private QuestNotificationOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || QuestSplashRenderer.isActive()
                || minecraft.screen instanceof QuestJournalScreen
                || minecraft.screen instanceof QuestTrackingMenuScreen
                || minecraft.screen instanceof DialogueScreen) return;
        QuestToastManager.render(graphics, screenWidth, screenHeight);
    }
}
