package org.arcadia.arc_quest.client.hud.dialogue;

/** Shared by drawing and mouse hit testing, in the dialogue's scaled coordinates. */
record DialogueChoiceLayout(int startY, int height, int gap) {
    static DialogueChoiceLayout fit(int viewportHeight, int count, int lineHeight) {
        int margin = Math.max(8, viewportHeight / 12);
        int available = Math.max(1, viewportHeight - 2 * margin);
        int gap = count * 42 - 8 <= available ? 8 : 4;
        int height = count <= 0 ? 34 : Math.min(34, Math.max(lineHeight + 4,
                (available - Math.max(0, count - 1) * gap) / count));
        int total = count * (height + gap) - gap;
        int preferred = (viewportHeight - total) / 2 + viewportHeight / 10;
        int start = Math.max(margin, Math.min(preferred, viewportHeight - margin - total));
        return new DialogueChoiceLayout(start, height, gap);
    }
}
