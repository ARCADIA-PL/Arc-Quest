package org.arcadia.arc_quest.mutil.core;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

public final class ArcGuiTickContextFactory {
    private static long lastTickTime;

    private ArcGuiTickContextFactory() {
    }

    public static ArcGuiTickContext create(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int width = window.getGuiScaledWidth();
        int height = window.getGuiScaledHeight();
        int mouseX = (int) (minecraft.mouseHandler.xpos() * width / window.getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos() * height / window.getScreenHeight());
        long now = Util.getMillis();
        if (lastTickTime == 0L) lastTickTime = now;
        float deltaTime = Math.min((now - lastTickTime) / 1000f, 0.1f);
        lastTickTime = now;
        return new ArcGuiTickContext(minecraft, width, height, mouseX, mouseY, deltaTime, now);
    }

    public static void reset() {
        lastTickTime = 0L;
    }
}
