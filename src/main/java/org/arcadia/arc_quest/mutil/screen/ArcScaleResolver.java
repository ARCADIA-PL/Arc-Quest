package org.arcadia.arc_quest.mutil.screen;

import net.minecraft.client.Minecraft;

public final class ArcScaleResolver {
    private ArcScaleResolver() {
    }

    public static float resolveUniversalUiScale(int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return 1.0f;
        double guiScale = mc.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;

        float scale = (float) (3.0 / guiScale);
        float sw = screenWidth / scale;
        float sh = screenHeight / scale;
        float minW = 480f;
        float minH = 260f;

        if (sw < minW) {
            scale = screenWidth / minW;
            sh = screenHeight / scale;
        }
        if (sh < minH) {
            scale = screenHeight / minH;
        }
        return scale;
    }
}
