package org.arcadia.arc_quest.client.tutorial;

/** Tooltip anchors are inputs to a positioner, not the final top-left corner. */
public final class TutorialTooltipPlacement {
    private static final int GAP = 6;

    public record Box(int x, int y, int w, int h) {
        public boolean overlaps(Box other) {
            return x < other.x + other.w && x + w > other.x && y < other.y + other.h && y + h > other.y;
        }
    }
    public record Anchor(int x, int y) {}
    @FunctionalInterface public interface Positioner { Box position(int x, int y); }

    private TutorialTooltipPlacement() {}

    /** Keep the card stationary and move the tooltip to the nearest available side. */
    public static Anchor avoid(int x, int y, int screenWidth, int screenHeight, Box card, Positioner positioner) {
        Box original = positioner.position(x, y);
        if (!original.overlaps(card)) return new Anchor(x, y);
        int maxX = screenWidth - original.w - GAP, maxY = screenHeight - original.h - GAP;
        if (maxX < GAP || maxY < GAP) return null;
        int[] columns = {clamp(original.x, GAP, maxX), card.x - original.w - GAP,
                card.x + card.w + GAP, GAP, maxX};
        int[] rows = {clamp(original.y, GAP, maxY), card.y + card.h + GAP,
                card.y - original.h - GAP, GAP, maxY};
        Anchor best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int column : columns) for (int row : rows) {
            int ax = x, ay = y;
            for (int attempt = 0; attempt < 4; attempt++) {
                Box actual = positioner.position(ax, ay);
                if (!actual.overlaps(card) && actual.x >= GAP && actual.y >= GAP
                        && actual.x + actual.w <= screenWidth - GAP && actual.y + actual.h <= screenHeight - GAP) {
                    long dx = actual.x - original.x, dy = actual.y - original.y;
                    long distance = dx * dx + dy * dy;
                    if (distance < bestDistance) { bestDistance = distance; best = new Anchor(ax, ay); }
                    break;
                }
                // Re-evaluate after each correction: native positioners can flip at screen edges.
                ax += clamp(column, GAP, maxX) - actual.x;
                ay += clamp(row, GAP, maxY) - actual.y;
            }
        }
        return best;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
