package org.arcadia.arc_quest.mutil.core;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.perf.ArcGuiProfiler;
import org.arcadia.arc_quest.mutil.render.ArcRenderQueue;
import org.arcadia.arc_quest.mutil.render.ArcRenderQueueContext;

public class ArcGuiRoot extends ArcGuiElement {
    protected final Minecraft minecraft;
    private long lastFrameTime = 0L;
    private ArcGuiContext lastContext;
    private final ArcRenderQueue renderQueue = new ArcRenderQueue();

    public ArcGuiRoot(Minecraft minecraft) {
        super(0, 0, 0, 0);
        this.minecraft = minecraft;
    }

    public void drawRoot(GuiGraphics graphics, float partialTick) {
        if (!visible) return;
        ArcGuiProfiler.beginFrame();
        renderQueue.clear();
        ArcRenderQueueContext.begin(renderQueue);
        ArcGuiContext context = createContext(partialTick);
        updateTree(context, 0, 0);
        updateFocusState(0, 0, context.mouseX(), context.mouseY());
        drawChildren(graphics, context, 0, 0, 1f);
        renderQueue.flush(graphics);
        ArcRenderQueueContext.end();
        ArcGuiProfiler.endFrame();
    }

    protected ArcGuiContext createContext(float partialTick) {
        Window window = minecraft.getWindow();
        width = window.getGuiScaledWidth();
        height = window.getGuiScaledHeight();

        int mouseX = (int) (minecraft.mouseHandler.xpos() * width / window.getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos() * height / window.getScreenHeight());
        long now = Util.getMillis();
        if (lastFrameTime == 0L) lastFrameTime = now;
        float deltaTime = Math.min((now - lastFrameTime) / 1000f, 0.1f);
        lastFrameTime = now;

        lastContext = new ArcGuiContext(minecraft, width, height, mouseX, mouseY, partialTick, deltaTime, now);
        return lastContext;
    }

    public ArcGuiContext getLastContext() {
        return lastContext;
    }

    public ArcRenderQueue getRenderQueue() {
        return renderQueue;
    }

    public Minecraft getMinecraft() {
        return minecraft;
    }

    public void resetFrameClock() {
        lastFrameTime = 0L;
    }
}
