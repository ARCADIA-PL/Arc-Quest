package org.arcadia.arc_quest.client.hud.quest.icon;

/** Shared geometry for single-phase and compact parallel objective rows. */
public record ObjectiveRowLayout(int iconSize, int textX, int textWidth, int progressY,
                                 int barWidth, int countX, int height) {
    public static ObjectiveRowLayout measure(int width, int lineHeight, int lines,
                                             int countWidth, boolean icon, boolean compact) {
        int size = icon ? (compact ? 20 : 24) : 0;
        int inset = icon ? size + 6 : 0;
        int available = Math.max(1, width - inset);
        int textHeight = Math.max(1, lines) * (lineHeight + 1);
        int progressY = textHeight + 4;
        int countSpace = countWidth > 0 ? Math.min(countWidth + 6, Math.max(0, available - 8)) : 0;
        int barWidth = Math.max(1, available - countSpace);
        return new ObjectiveRowLayout(size, inset, available, progressY, barWidth,
                inset + available - Math.min(countWidth, Math.max(0, available - 8)),
                Math.max(size, progressY + Math.max(4, lineHeight - 3)) + 5);
    }
}
