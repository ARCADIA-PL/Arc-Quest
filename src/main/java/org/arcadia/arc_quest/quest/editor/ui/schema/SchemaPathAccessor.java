package org.arcadia.arc_quest.quest.editor.ui.schema;

import org.arcadia.arc_quest.quest.editor.model.EditableCollectionRewardNode;
import org.arcadia.arc_quest.quest.editor.model.EditableConnection;
import org.arcadia.arc_quest.quest.editor.model.EditableObjective;

public class SchemaPathAccessor {

    public String getAsString(Object target, String path) {
        if (target instanceof EditableObjective o) return getObjectiveString(o, path);
        if (target instanceof EditableConnection c) return getConnectionString(c, path);
        if (target instanceof EditableCollectionRewardNode r) return getRewardNodeString(r, path);
        return "";
    }

    public boolean setFromString(Object target, String path, String value) {
        if (target instanceof EditableObjective o) return setObjectiveString(o, path, value);
        if (target instanceof EditableConnection c) return setConnectionString(c, path, value);
        if (target instanceof EditableCollectionRewardNode r) return setRewardNodeString(r, path, value);
        return false;
    }

    private String getObjectiveString(EditableObjective o, String path) {
        return switch (path) {
            case "targetId" -> o.targetId;
            case "requiredCount" -> Integer.toString(o.requiredCount);
            case "displayText.value" -> o.displayText == null ? "" : o.displayText.value;
            case "type" -> o.type == null ? "" : o.type.name();
            case "hidden" -> Boolean.toString(o.hidden);
            case "optional" -> Boolean.toString(o.optional);
            case "npcId" -> o.npcId;
            case "itemTag" -> o.itemTag;
            case "radius" -> o.radius == null ? "" : Integer.toString(o.radius);
            case "countMode" -> o.countMode;
            case "x" -> o.x == null ? "" : Integer.toString(o.x);
            case "y" -> o.y == null ? "" : Integer.toString(o.y);
            case "z" -> o.z == null ? "" : Integer.toString(o.z);
            case "countBase" -> o.countBase == null ? "" : Integer.toString(o.countBase);
            case "countPerLevel" -> o.countPerLevel == null ? "" : Integer.toString(o.countPerLevel);
            case "countMin" -> o.countMin == null ? "" : Integer.toString(o.countMin);
            case "countMax" -> o.countMax == null ? "" : Integer.toString(o.countMax);
            case "collectionEntryConfig.categoryId" -> o.collectionEntryConfig == null ? "" : o.collectionEntryConfig.getCategoryId();
            case "compareOp" -> "";
            default -> "";
        };
    }

    private boolean setObjectiveString(EditableObjective o, String path, String value) {
        try {
            switch (path) {
                case "targetId" -> o.targetId = nvl(value);
                case "requiredCount" -> o.requiredCount = parseInt(value, 1);
                case "displayText.value" -> {
                    if (o.displayText == null) o.displayText = org.arcadia.arc_quest.quest.spec.QuestTextSpec.literal("");
                    o.displayText = org.arcadia.arc_quest.quest.spec.QuestTextSpec.literal(nvl(value));
                }
                case "type" -> o.type = org.arcadia.arc_quest.quest.api.ObjectiveType.valueOf(nvl(value).toUpperCase());
                case "hidden" -> o.hidden = Boolean.parseBoolean(value);
                case "optional" -> o.optional = Boolean.parseBoolean(value);
                case "npcId" -> o.npcId = nvl(value);
                case "itemTag" -> o.itemTag = nvl(value);
                case "radius" -> o.radius = value == null || value.isBlank() ? null : parseInt(value, 0);
                case "countMode" -> o.countMode = nvl(value);
                case "x" -> o.x = value == null || value.isBlank() ? null : parseInt(value, 0);
                case "y" -> o.y = value == null || value.isBlank() ? null : parseInt(value, 0);
                case "z" -> o.z = value == null || value.isBlank() ? null : parseInt(value, 0);
                case "countBase" -> o.countBase = value == null || value.isBlank() ? null : parseInt(value, 0);
                case "countPerLevel" -> o.countPerLevel = value == null || value.isBlank() ? null : parseInt(value, 0);
                case "countMin" -> o.countMin = value == null || value.isBlank() ? null : parseInt(value, 0);
                case "countMax" -> o.countMax = value == null || value.isBlank() ? null : parseInt(value, 0);
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private String getConnectionString(EditableConnection c, String path) {
        return switch (path) {
            case "connectionId" -> c.connectionId;
            case "targetPhaseNodeId" -> c.targetPhaseNodeId;
            case "connectionType" -> c.connectionType == null ? "" : c.connectionType.name();
            case "priority" -> Integer.toString(c.priority);
            case "condition.type" -> c.condition == null ? "" : c.condition.type;
            case "condition.flag" -> c.condition == null ? "" : c.condition.flag;
            case "condition.questId" -> c.condition == null ? "" : c.condition.questId;
            case "condition.variable" -> c.condition == null ? "" : c.condition.variable;
            case "condition.compareOp" -> c.condition == null || c.condition.compareOp == null ? "" : c.condition.compareOp.name();
            case "condition.value" -> c.condition == null ? "0" : Integer.toString(c.condition.value);
            default -> "";
        };
    }

    private boolean setConnectionString(EditableConnection c, String path, String value) {
        try {
            switch (path) {
                case "connectionId" -> c.connectionId = nvl(value);
                case "targetPhaseNodeId" -> c.targetPhaseNodeId = nvl(value);
                case "connectionType" -> c.connectionType = org.arcadia.arc_quest.quest.editor.model.EditableConnectionType.valueOf(nvl(value).toUpperCase());
                case "priority" -> c.priority = parseInt(value, 0);
                case "condition.type" -> { ensureCond(c); c.condition.type = nvl(value); }
                case "condition.flag" -> { ensureCond(c); c.condition.flag = nvl(value); }
                case "condition.questId" -> { ensureCond(c); c.condition.questId = nvl(value); }
                case "condition.variable" -> { ensureCond(c); c.condition.variable = nvl(value); }
                case "condition.compareOp" -> { ensureCond(c); c.condition.compareOp = org.arcadia.arc_quest.quest.api.CompareOp.valueOf(nvl(value).toUpperCase()); }
                case "condition.value" -> { ensureCond(c); c.condition.value = parseInt(value, 0); }
                default -> { return false; }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void ensureCond(EditableConnection c) {
        if (c.condition == null) c.condition = new org.arcadia.arc_quest.quest.spec.ConditionSpec();
    }

    private String getRewardNodeString(EditableCollectionRewardNode r, String path) {
        return switch (path) {
            case "rewardNodeId" -> r.rewardNodeId;
            case "scope" -> r.scope == null ? "" : r.scope.name();
            case "grantMode" -> r.grantMode == null ? "" : r.grantMode.name();
            default -> "";
        };
    }

    private boolean setRewardNodeString(EditableCollectionRewardNode r, String path, String value) {
        try {
            switch (path) {
                case "rewardNodeId" -> r.rewardNodeId = nvl(value);
                case "scope" -> r.scope = org.arcadia.arc_quest.quest.api.RewardScope.valueOf(nvl(value).toUpperCase());
                case "grantMode" -> r.grantMode = org.arcadia.arc_quest.quest.api.EntryRewardGrantMode.valueOf(nvl(value).toUpperCase());
                default -> { return false; }
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return fallback; }
    }

    private String nvl(String s) { return s == null ? "" : s; }
}
