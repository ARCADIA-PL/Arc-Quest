package org.arcadia.arc_quest.mutil.primitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.core.ArcGuiColor;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

import java.util.ArrayList;
import java.util.List;

public class ArcGuiTypewriterText extends ArcGuiElement {
    private final Font font;
    private String text = "";
    private List<String> lines = new ArrayList<>();
    private float charsPerSecond = 45f;
    private float progress;
    private int color = 0xFFFFFFFF;
    private boolean shadow = true;
    private boolean layoutDirty = true;
    private boolean done;

    public ArcGuiTypewriterText(int x, int y, int width, String text) {
        super(x, y, width, 0);
        this.font = Minecraft.getInstance().font;
        setText(text);
    }

    public ArcGuiTypewriterText setText(String text) {
        this.text = text == null ? "" : text;
        this.progress = 0f;
        this.done = false;
        this.layoutDirty = true;
        return this;
    }

    public ArcGuiTypewriterText setCharsPerSecond(float charsPerSecond) {
        this.charsPerSecond = Math.max(1f, charsPerSecond);
        return this;
    }

    public ArcGuiTypewriterText setColor(int color) {
        this.color = color;
        return this;
    }

    public ArcGuiTypewriterText setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    public void skip() {
        this.progress = text.length();
        this.done = true;
    }

    public boolean isDone() {
        return done;
    }

    @Override
    public void setWidth(int width) {
        if (this.width != width) {
            super.setWidth(width);
            layoutDirty = true;
        }
    }

    private void rebuildLayout() {
        lines = ArcDrawUtil.wrapText(text, Math.max(1, width), font);
        height = lines.isEmpty() ? 0 : lines.size() * font.lineHeight;
        layoutDirty = false;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        if (layoutDirty) rebuildLayout();
        if (!done) {
            progress += charsPerSecond * context.deltaTime();
            if (progress >= text.length()) {
                progress = text.length();
                done = true;
            }
        }
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        if (layoutDirty) rebuildLayout();
        int remaining = Math.max(0, Math.min(text.length(), (int) progress));
        int finalColor = ArcGuiColor.withOpacity(color, inheritedOpacity * opacity);
        int drawY = refY + y;
        for (String line : lines) {
            if (remaining <= 0) break;
            String draw = line.length() <= remaining ? line : line.substring(0, Math.max(0, remaining));
            graphics.drawString(font, draw, refX + x, drawY, finalColor, shadow);
            remaining -= line.length();
            drawY += font.lineHeight;
        }
        drawChildren(graphics, context, refX + x, refY + y, inheritedOpacity * opacity);
    }
}
