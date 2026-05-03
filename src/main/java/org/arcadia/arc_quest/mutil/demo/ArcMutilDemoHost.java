package org.arcadia.arc_quest.mutil.demo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayHost;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRegistry;

public final class ArcMutilDemoHost {
    public static final String DEMO_OVERLAY_ID = "arc_demo_overlay";

    private static final ArcOverlayHost HOST = new ArcOverlayHost();
    private static boolean initialized = false;

    private ArcMutilDemoHost() {
    }

    public static void ensureInitialized() {
        if (initialized) return;
        ArcDemoOverlayRoot overlay = new ArcDemoOverlayRoot(Minecraft.getInstance());
        ArcOverlayRegistry.getInstance().register(overlay);
        HOST.register(overlay);
        initialized = true;
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        ensureInitialized();
        HOST.renderAll(graphics, partialTick);
    }

    public static void tick() {
        ensureInitialized();
        HOST.tickAll();
    }

    public static ArcOverlayHost host() {
        ensureInitialized();
        return HOST;
    }

    public static void setDemoEnabled(boolean enabled) {
        ensureInitialized();
        HOST.setOverlayActive(DEMO_OVERLAY_ID, enabled);
    }

    public static boolean isDemoEnabled() {
        ensureInitialized();
        return HOST.get(DEMO_OVERLAY_ID) != null && HOST.get(DEMO_OVERLAY_ID).isActive();
    }
}
