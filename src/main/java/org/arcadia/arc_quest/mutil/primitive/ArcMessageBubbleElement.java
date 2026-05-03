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

public class ArcMessageBubbleElement extends ArcGuiElement {
    private final Font font;
    private String speaker = "";
    private String message = "";
    private List<String> lines = new ArrayList<>();
    private int accentColor = 0xFFE8E8E8;
    private int backgroundColor = 0x66000000;
    private int textColor = 0xFFFFFFFF;
    private int padding = 8;
    private boolean layoutDirty = true;

    public ArcMessageBubbleElement(int x, int y, int width) {
        super(x, y, width, 0);
        this.font = Minecraft.getInstance().font;
    }

    public ArcMessageBubbleElement setContent(String speaker, String message) {
        this.speaker = speaker == null ? "" : speaker;
        this.message = message == null ? "" : message;
        layoutDirty = true;
        return this;
    }

    public ArcMessageBubbleElement setColors(int accentColor, int backgroundColor, int textColor) {
        this.accentColor = accentColor;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        return this;
    }

    private void rebuildLayout() {
        lines = ArcDrawUtil.wrapText(message, Math.max(1, width - padding * 2), font);
        height = padding * 2 + (speaker.isEmpty() ? 0 : font.lineHeight + 4) + lines.size() * font.lineHeight;
        layoutDirty = false;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        if (layoutDirty) rebuildLayout();
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible) return;
        if (layoutDirty) rebuildLayout();
        int drawX = refX + x;
        int drawY = refY + y;
        float finalOpacity = inheritedOpacity * opacity;
        graphics.fill(drawX, drawY, drawX + width, drawY + height, ArcGuiColor.withOpacity(backgroundColor, finalOpacity));
        graphics.fill(drawX, drawY, drawX + 2, drawY + height, ArcGuiColor.withOpacity(accentColor, finalOpacity));
        int textY = drawY + padding;
        if (!speaker.isEmpty()) {
            graphics.drawString(font, speaker, drawX + padding, textY, ArcGuiColor.withOpacity(accentColor, finalOpacity), false);
            textY += font.lineHeight + 4;
        }
        int color = ArcGuiColor.withOpacity(textColor, finalOpacity);
        for (String line : lines) {
            graphics.drawString(font, line, drawX + padding, textY, color, false);
            textY += font.lineHeight;
        }
    }
}
