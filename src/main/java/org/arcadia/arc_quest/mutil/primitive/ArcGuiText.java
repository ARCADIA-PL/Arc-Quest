package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcGuiText extends ArcGuiElement {
    protected final Font font;
    protected String text;
    protected int color = 0xFFFFFFFF;
    protected boolean shadow = true;
    protected boolean fixedWidth = false;

    public ArcGuiText(int x, int y, String text) {
        super(x, y, 0, 9);
        this.font = Minecraft.getInstance().font;
        this.text = text;
        this.width = font.width(text);
    }

    public ArcGuiText(int x, int y, int width, String text) {
        super(x, y, width, 9);
        this.font = Minecraft.getInstance().font;
        this.fixedWidth = true;
        this.text = font.plainSubstrByWidth(text, width);
    }

    public ArcGuiText(int x, int y, Component component) {
        this(x, y, component.getString());
    }

    public ArcGuiText setColor(int color) {
        this.color = color;
        return this;
    }

    public ArcGuiText setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public ArcGuiText setText(String text) {
        if (text == null || text.equals(this.text)) return this;
        if (fixedWidth) {
            this.text = font.plainSubstrByWidth(text, width);
        } else {
            this.text = text;
            this.width = font.width(text);
        }
        return this;
    }

    public String getText() {
        return text;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible || text == null || text.isEmpty()) return;
        int finalColor = ArcGuiColor.withOpacity(color, inheritedOpacity * opacity);
        if ((finalColor & 0xFC000000) != 0) {
            graphics.drawString(font, text, refX + x, refY + y, finalColor, shadow);
        }
        drawChildren(graphics, context, refX + x, refY + y, inheritedOpacity * opacity);
    }
}
