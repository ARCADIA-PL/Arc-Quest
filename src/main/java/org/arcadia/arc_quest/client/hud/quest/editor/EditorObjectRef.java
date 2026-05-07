package org.arcadia.arc_quest.client.hud.quest.editor;

public record EditorObjectRef(EditorObjectType type, String id, String ownerId) {
    public static EditorObjectRef category(String categoryId) {
        return new EditorObjectRef(EditorObjectType.CATEGORY, categoryId == null ? "" : categoryId, "");
    }

    public static EditorObjectRef entry(String entryId) {
        return new EditorObjectRef(EditorObjectType.ENTRY, entryId == null ? "" : entryId, "");
    }

    public static EditorObjectRef rewardNode(String ownerId, String rewardNodeId) {
        return new EditorObjectRef(EditorObjectType.REWARD_NODE, rewardNodeId == null ? "" : rewardNodeId, ownerId == null ? "" : ownerId);
    }

    public static EditorObjectRef completionRule(String ownerId, String ruleId) {
        return new EditorObjectRef(EditorObjectType.COMPLETION_RULE, ruleId == null ? "" : ruleId, ownerId == null ? "" : ownerId);
    }
}
