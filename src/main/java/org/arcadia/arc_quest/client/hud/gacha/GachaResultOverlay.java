package org.arcadia.arc_quest.client.hud.gacha;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.DeltaTracker;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;

public class GachaResultOverlay implements LayeredDraw.Layer {

    public static final GachaResultOverlay INSTANCE = new GachaResultOverlay();

    private long lastRenderTime = 0;

    private GachaResultOverlay() {
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        int screenWidth = g.guiWidth();
        int screenHeight = g.guiHeight();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        if (Minecraft.getInstance().screen != null) {
            lastRenderTime = 0;
            return;
        }
        if (!GachaResultRenderer.INSTANCE.isActive()) {
            lastRenderTime = 0;
            return;
        }

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        HudCursorManager.renderHud(() -> GachaResultRenderer.INSTANCE.render(g, screenWidth, screenHeight, dt));
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
