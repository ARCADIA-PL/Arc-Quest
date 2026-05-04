package org.arcadia.arc_quest.mutil.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiRoot;
import org.arcadia.arc_quest.mutil.render.ArcRenderQueue;
import org.arcadia.arc_quest.mutil.theme.ArcTooltipRenderer;

import java.util.List;

public class ArcScreenRoot extends ArcGuiRoot {
    private float uiScale = 1.0f;

    public ArcScreenRoot(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected ArcGuiContext createContext(float partialTick) {
        ArcGuiContext base = super.createContext(partialTick);
        int scaledWidth = Math.round(base.screenWidth() / uiScale);
        int scaledHeight = Math.round(base.screenHeight() / uiScale);
        return new ArcGuiContext(
                base.minecraft(),
                scaledWidth,
                scaledHeight,
                Math.round(base.mouseX() / uiScale),
                Math.round(base.mouseY() / uiScale),
                base.partialTick(),
                base.deltaTime(),
                base.nowMs()
        );
    }

    public void drawScaledRoot(GuiGraphics graphics, float partialTick) {
        if (!visible) return;
        ArcGuiContext context = createContext(partialTick);
        updateTree(context, 0, 0);
        updateFocusState(0, 0, context.mouseX(), context.mouseY());
        ArcScreenInput.beginScaled(graphics, uiScale);
        drawChildren(graphics, context, 0, 0, 1f);
        ArcScreenInput.endScaled(graphics);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return onMouseClick(ArcScreenInput.toScaledMouseX(mouseX, uiScale), ArcScreenInput.toScaledMouseY(mouseY, uiScale), button);
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        onMouseRelease(ArcScreenInput.toScaledMouseX(mouseX, uiScale), ArcScreenInput.toScaledMouseY(mouseY, uiScale), button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double distance) {
        return onMouseScroll(ArcScreenInput.toScaledMouseX(mouseX, uiScale), ArcScreenInput.toScaledMouseY(mouseY, uiScale), distance);
    }

    public void renderTooltip(GuiGraphics graphics) {
        List<Component> lines = getTooltipLines();
        ArcGuiContext context = getLastContext();
        if (context == null || lines == null || lines.isEmpty()) return;
        int tooltipX = Math.round(context.mouseX() * uiScale);
        int tooltipY = Math.round(context.mouseY() * uiScale);
        ArcRenderQueue queue = getRenderQueue();
        queue.tooltip(taskGraphics -> ArcTooltipRenderer.render(taskGraphics, minecraft.font, lines, tooltipX, tooltipY, context.screenWidth(), context.screenHeight()));
    }

    public void setUiScale(float uiScale) {
        this.uiScale = Math.max(0.1f, uiScale);
    }

    public float getUiScale() {
        return uiScale;
    }

    public int getScaledWidth() {
        return Math.round(getWidth() / uiScale);
    }

    public int getScaledHeight() {
        return Math.round(getHeight() / uiScale);
    }
}
