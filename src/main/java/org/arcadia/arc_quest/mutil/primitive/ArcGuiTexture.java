package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcGuiTexture extends ArcGuiElement {
    protected final ResourceLocation texture;
    protected int textureWidth = 256;
    protected int textureHeight = 256;
    protected int u;
    protected int v;
    protected int color = 0xFFFFFFFF;

    public ArcGuiTexture(int x, int y, int width, int height, ResourceLocation texture) {
        this(x, y, width, height, 0, 0, texture);
    }

    public ArcGuiTexture(int x, int y, int width, int height, int u, int v, ResourceLocation texture) {
        super(x, y, width, height);
        this.texture = texture;
        this.u = u;
        this.v = v;
    }

    public ArcGuiTexture setTextureCoordinates(int u, int v) {
        this.u = u;
        this.v = v;
        return this;
    }

    public ArcGuiTexture setSpriteSize(int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        return this;
    }

    public ArcGuiTexture setColor(int color) {
        this.color = color;
        return this;
    }

    public int getColor() {
        return color;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        float finalOpacity = inheritedOpacity * opacity;
        int drawX = refX + x;
        int drawY = refY + y;
        graphics.blit(texture, drawX, drawY, 0, u, v, width, height, textureWidth, textureHeight);
        drawChildren(graphics, context, drawX, drawY, finalOpacity);
    }
}
