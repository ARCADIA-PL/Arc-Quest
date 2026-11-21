package org.arcadia.arc_quest.client.hud.quest.journal;

/** Shared journal typography. Both lines of a collection row form one centered block. */
final class QuestListRowTextLayout {
    record Layout(float titleY, float titleScale, int titleWidth, boolean progress,
                  float progressY, float progressScale, int progressWidth) {}
    private QuestListRowTextLayout() {}
    static float titleScale(int lineHeight) {
        return Math.min(1f, 9f / Math.max(1, lineHeight));
    }

    static float groupSubtitleScale(int lineHeight) {
        return titleScale(lineHeight) * .85f;
    }

    static Layout measure(float top, float bottom, int lineHeight, int width, boolean collection) {
        float height = Math.max(0f, bottom - top);
        lineHeight = Math.max(1, lineHeight);
        width = Math.max(1, width);
        float titleScale = Math.min(titleScale(lineHeight), height / lineHeight);
        float titleHeight = lineHeight * titleScale;
        float gap = 1f;
        float progressScale = Math.min(.75f, (height - titleHeight - gap) / lineHeight);
        boolean progress = collection && progressScale * lineHeight >= Math.min(4.5f, lineHeight * .5f);
        float blockHeight = titleHeight + (progress ? gap + lineHeight * progressScale : 0f);
        float titleY = top + (height - blockHeight) / 2f;
        return new Layout(titleY, titleScale, titleScale == 0 ? width : (int) (width / titleScale), progress,
                progress ? titleY + titleHeight + gap : 0f, progress ? progressScale : 0f,
                progress ? (int) (width / progressScale) : 0);
    }
}
