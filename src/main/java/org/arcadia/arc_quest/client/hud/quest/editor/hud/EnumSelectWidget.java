package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class EnumSelectWidget {
    public int x;
    public int y;
    public int width;
    public int itemHeight;
    public boolean expanded;
    public String label = "";
    public String value = "";
    public List<String> options = new ArrayList<>();

    public EnumSelectWidget(int x, int y, int width, int itemHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.itemHeight = itemHeight;
    }

    public void setOptions(List<String> options) {
        this.options.clear();
        if (options != null) this.options.addAll(options);
    }

    public void render(GuiGraphics g, Font f, int mx, int my) {
        int h = itemHeight;
        boolean hover = inside(mx, my, x, y, width, h);
        g.fill(x, y, x + width, y + h, hover ? 0xFF334455 : 0xFF112233);
        outline(g, x, y, width, h, 0xFF5AD7FF);
        String text = (label == null || label.isBlank() ? "" : (label + ": ")) + (value == null ? "" : value);
        g.drawString(f, text, x + 6, y + 5, 0xFFDDDDDD, false);
        if (!expanded) return;
        for (int i = 0; i < options.size(); i++) {
            int oy = y + h + i * h;
            boolean rowHover = inside(mx, my, x, oy, width, h);
            boolean selected = options.get(i).equalsIgnoreCase(value == null ? "" : value);
            g.fill(x, oy, x + width, oy + h, selected ? 0xFF224433 : (rowHover ? 0xFF334455 : 0xFF1A2233));
            outline(g, x, oy, width, h, 0xFF5AD7FF);
            g.drawString(f, options.get(i), x + 6, oy + 5, selected ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }
    }

    public boolean mouseClicked(double mx, double my) {
        int h = itemHeight;
        if (inside(mx, my, x, y, width, h)) {
            expanded = !expanded;
            return true;
        }
        if (!expanded) return false;
        for (int i = 0; i < options.size(); i++) {
            int oy = y + h + i * h;
            if (inside(mx, my, x, oy, width, h)) {
                value = options.get(i);
                expanded = false;
                return true;
            }
        }
        expanded = false;
        return false;
    }

    private static boolean inside(double px, double py, double x, double y, double w, double h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }
}
