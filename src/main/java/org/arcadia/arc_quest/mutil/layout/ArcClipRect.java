package org.arcadia.arc_quest.mutil.layout;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.screen.ArcScissorStack;

public class ArcClipRect extends ArcGuiElement {
    private final ArcScissorStack scissorStack = new ArcScissorStack();

    public ArcClipRect(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        scissorStack.push(graphics, refX, refY, width, height);
        super.drawChildren(graphics, context, refX, refY, inheritedOpacity);
        scissorStack.pop(graphics);
    }

    @Override
    public void updateFocusState(int refX, int refY, int mouseX, int mouseY) {
        boolean nextFocused = contains(refX, refY, mouseX, mouseY);
        if (nextFocused != focused) {
            focused = nextFocused;
            if (focused) onFocus();
            else onBlur();
        }
        if (!focused) return;
        for (ArcGuiElement child : children) {
            if (!child.isVisible()) continue;
            child.updateFocusState(
                    refX + x + getXOffset(this, child.getAttachmentAnchor()) - getXOffset(child, child.getAttachmentPoint()),
                    refY + y + getYOffset(this, child.getAttachmentAnchor()) - getYOffset(child, child.getAttachmentPoint()),
                    mouseX, mouseY);
        }
    }
}
