package org.arcadia.arc_quest.client.hud.quest.editor.hud;

public final class IndexedObjectNavigator {
    private IndexedObjectNavigator() {
    }

    public static int clampIndex(int index, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(index, size - 1));
    }

    public static String indexText(int index, int size) {
        if (size <= 0) return "0/0";
        return (clampIndex(index, size) + 1) + "/" + size;
    }
}
