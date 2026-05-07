package org.arcadia.arc_quest.client.hud.quest.editor;

import java.util.ArrayDeque;
import java.util.Deque;

public class QuestEditorSnapshotHistory {
    private static final int MAX_UNDO = 15;
    private final Deque<QuestEditorSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<QuestEditorSnapshot> redoStack = new ArrayDeque<>();

    public void pushUndo(QuestEditorSnapshot snapshot) {
        if (snapshot == null) return;
        undoStack.addLast(snapshot);
        while (undoStack.size() > MAX_UNDO) undoStack.removeFirst();
        redoStack.clear();
    }

    public QuestEditorSnapshot undo(QuestEditorSnapshot currentSnapshot) {
        if (undoStack.isEmpty()) return null;
        if (currentSnapshot != null) redoStack.addLast(currentSnapshot);
        return undoStack.removeLast();
    }

    public QuestEditorSnapshot redo(QuestEditorSnapshot currentSnapshot) {
        if (redoStack.isEmpty()) return null;
        if (currentSnapshot != null) undoStack.addLast(currentSnapshot);
        while (undoStack.size() > MAX_UNDO) undoStack.removeFirst();
        return redoStack.removeLast();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public int undoSize() { return undoStack.size(); }
    public int redoSize() { return redoStack.size(); }
    public void clear() { undoStack.clear(); redoStack.clear(); }
}
