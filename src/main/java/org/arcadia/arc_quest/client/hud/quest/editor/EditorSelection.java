package org.arcadia.arc_quest.client.hud.quest.editor;

public record EditorSelection(EditorSelectionType type, String phaseNodeId, String objectiveId, String choiceId,
                              String connectionId) {
    public static EditorSelection none() {
        return new EditorSelection(EditorSelectionType.NONE, "", "", "", "");
    }

    public static EditorSelection quest() {
        return new EditorSelection(EditorSelectionType.QUEST, "", "", "", "");
    }

    public static EditorSelection phase(String phaseNodeId) {
        return new EditorSelection(EditorSelectionType.PHASE, phaseNodeId == null ? "" : phaseNodeId, "", "", "");
    }

    public static EditorSelection objective(String phaseNodeId, String objectiveId) {
        return new EditorSelection(EditorSelectionType.OBJECTIVE, phaseNodeId == null ? "" : phaseNodeId, objectiveId == null ? "" : objectiveId, "", "");
    }

    public static EditorSelection choice(String phaseNodeId, String choiceId) {
        return new EditorSelection(EditorSelectionType.CHOICE, phaseNodeId == null ? "" : phaseNodeId, "", choiceId == null ? "" : choiceId, "");
    }
}
