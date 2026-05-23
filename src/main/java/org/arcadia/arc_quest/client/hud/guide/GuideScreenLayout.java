package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;

public final class GuideScreenLayout {
    private static int cachedLineHeight = -1;

    public static int textLineHeight() {
        if (cachedLineHeight < 0) {
            cachedLineHeight = Minecraft.getInstance().font.lineHeight + 1;
        }
        return cachedLineHeight;
    }

    private GuideScreenLayout() {}

    public static TerminalLayout computeTerminal(int width, int height) {
        int padding = 20;
        int listW = Math.min(220, Math.max(140, (int) (width * 0.18)));
        int gap = 14;

        int lx = padding;
        int ly = 36;
        int lh = height - 56;

        int cx = lx + listW + gap;
        int cy = ly;
        int cw = width - padding - cx;
        int ch = lh;

        return new TerminalLayout(lx, ly, listW, lh, cx, cy, cw, ch);
    }

    public static SidebarLayout computeSidebar(int width, int height) {
        int baseW = Math.min((int) (width * 0.35F), Math.max(190, (int) (width * 0.26F)));
        int slant = 20;
        return new SidebarLayout(0, 0, baseW, slant, height);
    }

    public record TerminalLayout(int lx, int ly, int lw, int lh, int cx, int cy, int cw, int ch) {}
    public record SidebarLayout(int x, int y, int baseW, int slant, int h) {
        public int totalW() { return baseW + slant; }
    }
}
