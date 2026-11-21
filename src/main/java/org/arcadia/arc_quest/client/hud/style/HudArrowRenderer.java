package org.arcadia.arc_quest.client.hud.style;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.config.ArcQuestConfig;

/** Native pixel silhouettes. Masks face right and rotate around their visible bounds. */
public final class HudArrowRenderer {
    private static final String[] DEFAULT = {"#....", ".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#...", "#...."};
    private static final String[] ORE = {"##...", "###..", "####.", "#####", "####.", "###..", "##..."};
    private static final String[] MODERNA = {".#....", "..#...", "...#..", "######", "...#..", "..#...", ".#...."};
    private static final String[] CYBERPUNK = {"##....", ".###..", "...##.", "....##", "...##.", ".###..", "##...."};
    private static final String[] WYRMHEART = {"..#...", ".#.#..", "#...#.", ".#...#", "#...#.", ".#.#..", "..#..."};

    public enum Direction { RIGHT, DOWN, LEFT }

    private HudArrowRenderer() {}

    public static void expandable(GuiGraphics graphics, int x, int y, float expansion, int color) {
        draw(graphics, x, y, expansion >= .5f ? Direction.DOWN : Direction.RIGHT, color);
    }

    /** Draw a pagination arrow on the same face as a button label. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, Direction direction,
                              boolean hovered, boolean enabled, float alpha, int theme) {
        var action = HudStyleRenderer.Action.SECONDARY;
        int offset = HudStyleRenderer.button(g, x, y, w, h, action, hovered, enabled, alpha, theme);
        int center = HudDraw.isOre()
                ? Math.round(HudStyleRenderer.oreButtonLabelTop(h, offset, 7, 0)) + 3
                : offset + (h - offset) / 2;
        draw(g, x + w / 2, y + center, direction, HudStyleRenderer.buttonText(action, enabled, alpha));
    }

    public static void draw(GuiGraphics graphics, int x, int y, Direction direction, int color) {
        String[] mask = switch (ArcQuestConfig.hudArtStyle()) {
            case DEFAULT -> DEFAULT;
            case ORE -> ORE;
            case MODERNA -> MODERNA;
            case CYBERPUNK -> CYBERPUNK;
            case WYRMHEART -> WYRMHEART;
        };
        int w = mask[0].length(), h = mask.length;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                if (mask[row].charAt(col) != '#') continue;
                int px = x + switch (direction) {
                    case RIGHT -> col - w / 2;
                    case DOWN -> h / 2 - row;
                    case LEFT -> w - 1 - col - w / 2;
                };
                int py = y + (direction == Direction.DOWN ? col - w / 2 : row - h / 2);
                HudDraw.fill(graphics, px, py, px + 1, py + 1, color);
            }
        }
    }
}
