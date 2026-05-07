package org.arcadia.arc_quest.client.hud.quest.editor;

import java.util.ArrayDeque;
import java.util.Deque;

public class EditorCommandBus {
    private final Deque<EditorCommand> undoStack = new ArrayDeque<>();
    private final Deque<EditorCommand> redoStack = new ArrayDeque<>();

    public boolean execute(EditorCommand command) {
        if (command == null) return false;
        boolean applied = command.apply();
        if (!applied) return false;
        undoStack.push(command);
        redoStack.clear();
        return true;
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        EditorCommand command = undoStack.pop();
        command.revert();
        redoStack.push(command);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        EditorCommand command = redoStack.pop();
        boolean applied = command.apply();
        if (!applied) return false;
        undoStack.push(command);
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public String lastUndoDescription() {
        return undoStack.isEmpty() ? "" : undoStack.peek().description();
    }

    public String lastRedoDescription() {
        return redoStack.isEmpty() ? "" : redoStack.peek().description();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
