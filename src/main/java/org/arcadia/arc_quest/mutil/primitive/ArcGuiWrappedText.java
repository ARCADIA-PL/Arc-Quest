package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.perf.ArcGuiProfiler;
import org.arcadia.arc_quest.mutil.text.ArcTextLayoutCache;

import java.util.ArrayList;
import java.util.List;

public class ArcGuiWrappedText extends ArcGuiElement {
    protected final Font font;
    protected String text;
    protected int color = 0xFFFFFFFF;
    protected boolean shadow = true;
    protected List<String> lines = new ArrayList<>();
    protected boolean layoutDirty = true;
    protected final ArcTextLayoutCache textLayoutCache = new ArcTextLayoutCache();

    public ArcGuiWrappedText(int x, int y, int width, String text) {
        super(x, y, width, 0);
        this.font = Minecraft.getInstance().font;
        this.text = text == null ? "" : text;
    }

    public ArcGuiWrappedText setColor(int color) {
        this.color = color;
        return this;
    }

    public ArcGuiWrappedText setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public ArcGuiWrappedText setText(String text) {
        String next = text == null ? "" : text;
        if (next.equals(this.text)) return this;
        this.text = next;
        this.layoutDirty = true;
        invalidateLayout();
        return this;
    }

    @Override
    public void setWidth(int width) {
        if (this.width != width) {
            super.setWidth(width);
            this.layoutDirty = true;
        }
    }

    protected void rebuildLayout() {
        lines = textLayoutCache.layout(font, text, Math.max(1, width));
        int nextHeight = textLayoutCache.getHeight();
        if (height != nextHeight) setMeasuredSize(width, nextHeight);
        layoutDirty = false;
        ArcGuiProfiler.textLaidOut();
        ArcGuiProfiler.layoutRebuilt();
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        if (layoutDirty) rebuildLayout();
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        if (layoutDirty) rebuildLayout();
        int finalColor = ArcGuiColor.withOpacity(color, inheritedOpacity * opacity);
        if ((finalColor & 0xFC000000) != 0) {
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), refX + x, refY + y + i * font.lineHeight, finalColor, shadow);
            }
        }
        drawChildren(graphics, context, refX + x, refY + y, inheritedOpacity * opacity);
    }
}
