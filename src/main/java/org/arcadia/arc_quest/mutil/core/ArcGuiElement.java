package org.arcadia.arc_quest.mutil.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.animation.ArcAnimation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArcGuiElement {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected float opacity = 1f;
    protected boolean visible = true;
    protected boolean focused = false;
    protected boolean removePending = false;
    protected ArcGuiAttachment attachmentPoint = ArcGuiAttachment.TOP_LEFT;
    protected ArcGuiAttachment attachmentAnchor = ArcGuiAttachment.TOP_LEFT;
    protected ArcGuiElement parent;
    protected final List<ArcGuiElement> children = new ArrayList<>();
    protected final List<ArcAnimation> activeAnimations = new ArrayList<>();

    public ArcGuiElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public final void updateTree(ArcGuiContext context, int refX, int refY) {
        if (!visible) return;
        tickAnimations(context);
        update(context, refX, refY);
        for (ArcGuiElement child : children) {
            if (!child.visible) continue;
            child.updateTree(context, refX + x + getXOffset(this, child.attachmentAnchor) - getXOffset(child, child.attachmentPoint),
                    refY + y + getYOffset(this, child.attachmentAnchor) - getYOffset(child, child.attachmentPoint));
        }
    }

    protected void update(ArcGuiContext context, int refX, int refY) {
    }

    protected void tickAnimations(ArcGuiContext context) {
        if (activeAnimations.isEmpty()) return;
        for (int i = activeAnimations.size() - 1; i >= 0; i--) {
            ArcAnimation animation = activeAnimations.get(i);
            animation.update(this, context);
            if (animation.isFinished()) {
                animation.finish(this, context);
                activeAnimations.remove(i);
            }
        }
    }

    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        drawChildren(graphics, context, refX + x, refY + y, inheritedOpacity * opacity);
    }

    protected void drawChildren(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        children.removeIf(ArcGuiElement::isRemovePending);
        for (ArcGuiElement child : children) {
            if (!child.visible) continue;
            child.draw(graphics, context,
                    refX + getXOffset(this, child.attachmentAnchor) - getXOffset(child, child.attachmentPoint),
                    refY + getYOffset(this, child.attachmentAnchor) - getYOffset(child, child.attachmentPoint),
                    inheritedOpacity);
        }
    }

    public void updateFocusState(int refX, int refY, int mouseX, int mouseY) {
        for (ArcGuiElement child : children) {
            if (!child.visible) continue;
            child.updateFocusState(
                    refX + x + getXOffset(this, child.attachmentAnchor) - getXOffset(child, child.attachmentPoint),
                    refY + y + getYOffset(this, child.attachmentAnchor) - getYOffset(child, child.attachmentPoint),
                    mouseX, mouseY);
        }

        boolean nextFocused = contains(refX, refY, mouseX, mouseY);
        if (nextFocused != focused) {
            focused = nextFocused;
            if (focused) onFocus();
            else onBlur();
        }
    }

    protected boolean contains(int refX, int refY, int mouseX, int mouseY) {
        return mouseX >= x + refX && mouseX < x + refX + width && mouseY >= y + refY && mouseY < y + refY + height;
    }

    protected void onFocus() {
    }

    protected void onBlur() {
    }

    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ArcGuiElement child = children.get(i);
            if (child.visible && child.onMouseClick(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    public void onMouseRelease(double mouseX, double mouseY, int button) {
        for (ArcGuiElement child : children) {
            if (child.visible) child.onMouseRelease(mouseX, mouseY, button);
        }
    }

    public boolean onMouseScroll(double mouseX, double mouseY, double distance) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ArcGuiElement child = children.get(i);
            if (child.visible && child.onMouseScroll(mouseX, mouseY, distance)) return true;
        }
        return false;
    }

    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ArcGuiElement child = children.get(i);
            if (child.visible && child.onKeyPress(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean onKeyRelease(int keyCode, int scanCode, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ArcGuiElement child = children.get(i);
            if (child.visible && child.onKeyRelease(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean onCharTyped(char character, int modifiers) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ArcGuiElement child = children.get(i);
            if (child.visible && child.onCharTyped(character, modifiers)) return true;
        }
        return false;
    }

    @Nullable
    public List<Component> getTooltipLines() {
        if (!visible) return null;
        for (ArcGuiElement child : children) {
            List<Component> tooltip = child.getTooltipLines();
            if (tooltip != null && !tooltip.isEmpty()) return tooltip;
        }
        return null;
    }

    public void addChild(ArcGuiElement child) {
        if (child == null) return;
        if (child.parent != null) {
            child.parent.removeChild(child);
        }
        child.parent = this;
        children.add(child);
    }

    public void removeChild(ArcGuiElement child) {
        if (child == null) return;
        if (children.remove(child)) {
            child.parent = null;
        }
    }

    public void clearChildren() {
        for (ArcGuiElement child : children) {
            child.parent = null;
        }
        children.clear();
    }

    public List<ArcGuiElement> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Nullable
    public ArcGuiElement getParent() {
        return parent;
    }

    public ArcGuiElement findFirstChild(Class<? extends ArcGuiElement> type) {
        for (ArcGuiElement child : children) {
            if (type.isInstance(child)) return child;
            ArcGuiElement nested = child.findFirstChild(type);
            if (nested != null) return nested;
        }
        return null;
    }

    public ArcGuiElement setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public ArcGuiElement setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public int getAbsoluteX() {
        if (parent == null) return x;
        return parent.getAbsoluteX() + x + getXOffset(parent, attachmentAnchor) - getXOffset(this, attachmentPoint);
    }

    public int getAbsoluteY() {
        if (parent == null) return y;
        return parent.getAbsoluteY() + y + getYOffset(parent, attachmentAnchor) - getYOffset(this, attachmentPoint);
    }

    public ArcGuiElement setAttachment(ArcGuiAttachment attachment) {
        this.attachmentPoint = attachment;
        this.attachmentAnchor = attachment;
        return this;
    }

    public ArcGuiElement setAttachmentPoint(ArcGuiAttachment attachment) {
        this.attachmentPoint = attachment;
        return this;
    }

    public ArcGuiElement setAttachmentAnchor(ArcGuiAttachment attachment) {
        this.attachmentAnchor = attachment;
        return this;
    }

    public ArcGuiElement setOpacity(float opacity) {
        this.opacity = opacity;
        return this;
    }

    public ArcGuiElement addAnimation(ArcAnimation animation, ArcGuiContext context) {
        if (animation == null) return this;
        animation.start(this, context);
        activeAnimations.add(animation);
        return this;
    }

    public ArcGuiElement addAnimation(ArcAnimation animation) {
        if (animation == null) return this;
        activeAnimations.add(animation);
        return this;
    }

    public void clearAnimations() {
        activeAnimations.clear();
    }

    public boolean hasActiveAnimations() {
        return !activeAnimations.isEmpty();
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) return;
        this.visible = visible;
        if (visible) onShow();
        else onHide();
    }

    public ArcGuiElement withVisible(boolean visible) {
        setVisible(visible);
        return this;
    }

    protected void onShow() {
    }

    protected void onHide() {
        focused = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isFocused() {
        return focused;
    }

    public float getOpacity() {
        return opacity;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public ArcGuiElement withX(int x) {
        this.x = x;
        return this;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public ArcGuiElement withY(int y) {
        this.y = y;
        return this;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public ArcGuiElement withWidth(int width) {
        this.width = width;
        return this;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public ArcGuiElement withHeight(int height) {
        this.height = height;
        return this;
    }

    public ArcGuiAttachment getAttachmentPoint() {
        return attachmentPoint;
    }

    public ArcGuiAttachment getAttachmentAnchor() {
        return attachmentAnchor;
    }

    public boolean isRemovePending() {
        return removePending;
    }

    public void markForRemoval() {
        this.removePending = true;
        for (ArcGuiElement child : children) {
            child.markForRemoval();
        }
    }

    protected static int getXOffset(ArcGuiElement element, ArcGuiAttachment attachment) {
        return switch (attachment) {
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> 0;
            case TOP_CENTER, MIDDLE_CENTER, BOTTOM_CENTER -> element.getWidth() / 2;
            case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> element.getWidth();
        };
    }

    protected static int getYOffset(ArcGuiElement element, ArcGuiAttachment attachment) {
        return switch (attachment) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0;
            case MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT -> element.getHeight() / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> element.getHeight();
        };
    }
}
