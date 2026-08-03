package org.arcadia.arc_quest.client.hud.component;

public record HudRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
    }

    public HudRect inset(int amount) {
        int insetWidth = Math.max(0, width - amount * 2);
        int insetHeight = Math.max(0, height - amount * 2);
        return new HudRect(x + amount, y + amount, insetWidth, insetHeight);
    }
}
