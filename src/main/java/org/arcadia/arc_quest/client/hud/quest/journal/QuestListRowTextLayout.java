package org.arcadia.arc_quest.client.hud.quest.journal;

/** Both text lines are measured as one block; widths include the hover scale. */
final class QuestListRowTextLayout {
    record Layout(float titleY, float titleScale, int titleWidth, boolean progress,
                  float progressY, float progressScale, int progressWidth) {}
    private QuestListRowTextLayout() {}
    static Layout measure(float rowHeight, int lineHeight, int width, int titleWidth, float hover, boolean collection) {
        rowHeight = Math.max(0f, rowHeight);
        lineHeight = Math.max(1, lineHeight);
        width = Math.max(1, width);
        float base = titleWidth > width ? Math.max(.75f, width / (float) Math.max(1, titleWidth)) : 1f;
        float scale = base * (1f + .03f * Math.max(0f, Math.min(1f, hover)));
        float availableHeight = rowHeight > 2f ? rowHeight - 2f : rowHeight;
        scale = Math.min(scale, availableHeight / lineHeight);
        scale = Math.min(scale, width);
        float titleHeight = lineHeight * scale, progressScale = .75f;
        float progressHeight = lineHeight * progressScale;
        boolean progress = collection && rowHeight >= titleHeight + progressHeight + 4f;
        float blockHeight = titleHeight + (progress ? 2f + progressHeight : 0f);
        float titleY = Math.max(0f, (rowHeight - blockHeight) / 2f);
        return new Layout(titleY, scale, scale == 0 ? width : Math.max(1, (int) Math.floor(width / scale)), progress,
                titleY + titleHeight + 2f, progressScale, Math.max(1, (int) Math.floor(width / progressScale)));
    }
}
