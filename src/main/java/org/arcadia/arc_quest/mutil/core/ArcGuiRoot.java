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
        ArcGuiContext context = beginFrame(graphics, partialTick);
        ArcGuiTickContext tickContext = createTickContext(context);
        tickTree(tickContext, 0, 0);
        updateTree(context, 0, 0);
        updateFocusState(0, 0, context.mouseX(), context.mouseY());
        drawChildren(graphics, context, 0, 0, 1f);
        endFrame(graphics);
    }

    public void tickRoot() {
        if (!visible) return;
        ArcGuiTickContext context = createTickContext();
        tickTree(context, 0, 0);
    }

    public void tickAndUpdateRoot() {
        if (!visible) return;
        ArcGuiTickContext tickContext = createTickContext();
        tickTree(tickContext, 0, 0);
        ArcGuiContext context = createContext(tickContext, 0f);
        updateTree(context, 0, 0);
    }

    public void drawRootOnly(GuiGraphics graphics, float partialTick) {
        if (!visible) return;
        ArcGuiContext context = beginDrawOnlyFrame(graphics, partialTick);
        updateFocusState(0, 0, context.mouseX(), context.mouseY());
        drawChildren(graphics, context, 0, 0, 1f);
        endFrame(graphics);
    }

    protected ArcGuiContext beginFrame(GuiGraphics graphics, float partialTick) {
        ArcGuiProfiler.beginFrame();
        renderQueue.clear();
        ArcRenderQueueContext.begin(renderQueue);
        return createContext(partialTick);
    }

    protected ArcGuiContext beginDrawOnlyFrame(GuiGraphics graphics, float partialTick) {
        ArcGuiProfiler.beginFrame();
        renderQueue.clear();
        ArcRenderQueueContext.begin(renderQueue);
        return createRenderContext(partialTick);
    }

    protected void endFrame(GuiGraphics graphics) {
        renderQueue.flush(graphics);
        ArcRenderQueueContext.end();
        ArcGuiProfiler.endFrame();
    }

    protected ArcGuiContext createContext(float partialTick) {
        Window window = minecraft.getWindow();
        updateRootSize(window);

        int mouseX = (int) (minecraft.mouseHandler.xpos() * width / window.getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos() * height / window.getScreenHeight());
        long now = Util.getMillis();
        if (lastFrameTime == 0L) lastFrameTime = now;
        float deltaTime = Math.min((now - lastFrameTime) / 1000f, 0.1f);
        lastFrameTime = now;

        lastContext = new ArcGuiContext(minecraft, width, height, mouseX, mouseY, partialTick, deltaTime, now);
        return lastContext;
    }

    protected ArcGuiContext createRenderContext(float partialTick) {
        Window window = minecraft.getWindow();
        updateRootSize(window);
        int mouseX = (int) (minecraft.mouseHandler.xpos() * width / window.getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos() * height / window.getScreenHeight());
        long now = Util.getMillis();
        float deltaTime = lastContext == null ? 0f : lastContext.deltaTime();
        lastContext = new ArcGuiContext(minecraft, width, height, mouseX, mouseY, partialTick, deltaTime, now);
        return lastContext;
    }

    protected ArcGuiTickContext createTickContext() {
        Window window = minecraft.getWindow();
        updateRootSize(window);
        int mouseX = (int) (minecraft.mouseHandler.xpos() * width / window.getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos() * height / window.getScreenHeight());
        long now = Util.getMillis();
        if (lastFrameTime == 0L) lastFrameTime = now;
        float deltaTime = Math.min((now - lastFrameTime) / 1000f, 0.1f);
        lastFrameTime = now;
        return new ArcGuiTickContext(minecraft, width, height, mouseX, mouseY, deltaTime, now);
    }

    protected ArcGuiContext createContext(ArcGuiTickContext tickContext, float partialTick) {
        lastContext = new ArcGuiContext(
                tickContext.minecraft(),
                tickContext.screenWidth(),
                tickContext.screenHeight(),
                tickContext.mouseX(),
                tickContext.mouseY(),
                partialTick,
                tickContext.deltaTime(),
                tickContext.nowMs()
        );
        return lastContext;
    }

    protected ArcGuiTickContext createTickContext(ArcGuiContext context) {
        return new ArcGuiTickContext(
                context.minecraft(),
                context.screenWidth(),
                context.screenHeight(),
                context.mouseX(),
                context.mouseY(),
                context.deltaTime(),
                context.nowMs()
        );
    }

    protected void updateRootSize(Window window) {
        width = window.getGuiScaledWidth();
        height = window.getGuiScaledHeight();
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
