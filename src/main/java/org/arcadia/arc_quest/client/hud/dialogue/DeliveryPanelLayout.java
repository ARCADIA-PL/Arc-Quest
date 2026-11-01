package org.arcadia.arc_quest.client.hud.dialogue;

/** Logical-pixel bounds shared by rendering and every input path. */
public record DeliveryPanelLayout(int rows, int height, int navigationHeight, int feedbackHeight) {
    public static DeliveryPanelLayout measure(int screenHeight, int entries) {
        return measure(screenHeight, entries, 0);
    }
    public static DeliveryPanelLayout measure(int screenHeight, int entries, int feedbackLines) {
        int bars = Math.max(24, (int) (screenHeight * .08f));
        int available = Math.max(158, Math.min(420, screenHeight - bars * 2 - 12));
        int feedback = feedbackLines <= 0 ? 0 : 4 + Math.min(2, feedbackLines) * 10;
        int rows = Math.min(8, Math.max(1, entries));
        while (rows > 1 && 82 + rows * 24 + (entries > rows ? 30 : 0) + feedback > available) rows--;
        int navigation = entries > rows ? 30 : 0;
        return new DeliveryPanelLayout(rows, 82 + rows * 24 + navigation + feedback, navigation, feedback);
    }
    public int toolbarY() { return 24 + rows * 24 + navigationHeight + feedbackHeight + 2; }
    public int maxOffset(int entries) { return Math.max(0, entries - rows); }
    public int offsetForThumb(double y, int trackTop, int trackHeight, int entries) {
        int thumb = Math.max(14, trackHeight * rows / Math.max(rows, entries));
        double fraction = (y - trackTop - thumb / 2.0) / Math.max(1, trackHeight - thumb);
        return (int) Math.round(Math.max(0, Math.min(1, fraction)) * maxOffset(entries));
    }
}
