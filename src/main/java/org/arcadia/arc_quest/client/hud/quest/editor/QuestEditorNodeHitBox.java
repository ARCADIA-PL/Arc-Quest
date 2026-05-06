package org.arcadia.arc_quest.client.hud.quest.editor;

public record QuestEditorNodeHitBox(String nodeId, int x, int y, int width, int height) {
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
