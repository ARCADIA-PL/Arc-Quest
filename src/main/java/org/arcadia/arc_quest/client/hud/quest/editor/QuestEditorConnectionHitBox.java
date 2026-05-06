package org.arcadia.arc_quest.client.hud.quest.editor;

public record QuestEditorConnectionHitBox(String connectionId, int x1, int y1, int x2, int y2) {
    public boolean near(double mouseX, double mouseY) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-4) return false;
        double t = ((mouseX - x1) * dx + (mouseY - y1) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double px = x1 + t * dx;
        double py = y1 + t * dy;
        double distSq = (mouseX - px) * (mouseX - px) + (mouseY - py) * (mouseY - py);
        return distSq <= 16.0;
    }
}
