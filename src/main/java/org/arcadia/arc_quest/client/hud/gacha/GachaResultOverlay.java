package org.arcadia.arc_quest.client.hud.gacha;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class GachaResultOverlay implements IGuiOverlay {

    public static final GachaResultOverlay INSTANCE = new GachaResultOverlay();

    private long lastRenderTime = 0;

    private GachaResultOverlay() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        if (!GachaResultRenderer.INSTANCE.isActive()) {
            lastRenderTime = 0;
            return;
        }

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        GachaResultRenderer.INSTANCE.render(g, screenWidth, screenHeight, dt);
    }

    @SubscribeEvent
    public void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (GachaResultRenderer.INSTANCE.isActive() && event.getButton() == 0) {
            if (GachaResultRenderer.INSTANCE.mouseClicked()) {
                event.setCanceled(true);
            }
        }
    }
}