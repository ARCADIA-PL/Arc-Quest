package org.arcadia.arc_quest.client.hud.quest.editor;

public interface EditorCommand {
    boolean apply();
    void revert();
    String description();
}
