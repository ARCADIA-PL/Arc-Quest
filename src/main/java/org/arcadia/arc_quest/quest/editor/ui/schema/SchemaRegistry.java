package org.arcadia.arc_quest.quest.editor.ui.schema;

import org.arcadia.arc_quest.quest.api.CompareOp;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.RewardScope;
import org.arcadia.arc_quest.quest.editor.model.EditableConnectionType;

import java.util.LinkedHashMap;
import java.util.Map;

public class SchemaRegistry {

    private final Map<String, FormSchema> schemas = new LinkedHashMap<>();

    public SchemaRegistry() {
        register(buildObjectiveSchema());
        register(buildConnectionSchema());
        register(buildRewardNodeSchema());
    }

    public void register(FormSchema schema) {
        if (schema == null || schema.formId == null || schema.formId.isBlank()) return;
        schemas.put(schema.formId, schema);
    }

    public FormSchema get(String formId) {
        return schemas.get(formId);
    }

    public Map<String, FormSchema> all() {
        return Map.copyOf(schemas);
    }

    private FormSchema buildObjectiveSchema() {
        FormSchema schema = new FormSchema();
        schema.formId = "phase.objective";
        schema.title = "Objective";
        schema.objectType = "OBJECTIVE";

        schema.fields.add(field("targetId", "Target ID", FieldType.TEXT, "targetId", "", true));
        schema.fields.add(field("requiredCount", "Required Count", FieldType.INTEGER, "requiredCount", "1", true));
        schema.fields.add(field("displayText", "Display Text", FieldType.TEXT, "displayText.value", "", false));

        FieldSchema type = field("type", "Objective Type", FieldType.ENUM, "type", ObjectiveType.CUSTOM.name(), true);
        for (ObjectiveType t : ObjectiveType.values()) type.enumOptions.add(t.name());
        schema.fields.add(type);

        schema.fields.add(field("hidden", "Hidden", FieldType.BOOLEAN, "hidden", "false", false));
        schema.fields.add(field("optional", "Optional", FieldType.BOOLEAN, "optional", "false", false));
        schema.fields.add(field("npcId", "NPC ID", FieldType.TEXT, "npcId", "", false));
        schema.fields.add(field("itemTag", "Item Tag", FieldType.TEXT, "itemTag", "", false));
        schema.fields.add(field("radius", "Radius", FieldType.INTEGER, "radius", "", false));
        schema.fields.add(field("countMode", "Count Mode", FieldType.TEXT, "countMode", "", false));

        schema.fields.add(field("x", "X", FieldType.INTEGER, "x", "", false));
        schema.fields.add(field("y", "Y", FieldType.INTEGER, "y", "", false));
        schema.fields.add(field("z", "Z", FieldType.INTEGER, "z", "", false));
        schema.fields.add(field("countBase", "Count Base", FieldType.INTEGER, "countBase", "", false));
        schema.fields.add(field("countPerLevel", "Count Per Level", FieldType.INTEGER, "countPerLevel", "", false));
        schema.fields.add(field("countMin", "Count Min", FieldType.INTEGER, "countMin", "", false));
        schema.fields.add(field("countMax", "Count Max", FieldType.INTEGER, "countMax", "", false));

        schema.fields.add(field("collection.categoryId", "Collection Category", FieldType.TEXT, "collectionEntryConfig.categoryId", "default", false));

        FieldSchema compareOp = field("condition.compareOp", "Compare Op", FieldType.ENUM, "compareOp", CompareOp.GREATER_OR_EQUAL.name(), false);
        for (CompareOp op : CompareOp.values()) compareOp.enumOptions.add(op.name());
        schema.fields.add(compareOp);

        schema.layoutHints.add("group:basic");
        schema.layoutHints.add("group:advanced");
        schema.layoutHints.add("group:collection");

        return schema;
    }

    private FormSchema buildConnectionSchema() {
        FormSchema schema = new FormSchema();
        schema.formId = "phase.connection";
        schema.title = "Connection";
        schema.objectType = "CONNECTION";

        schema.fields.add(field("connectionId", "Connection ID", FieldType.TEXT, "connectionId", "", false));
        schema.fields.add(field("targetNode", "Target Node", FieldType.TEXT, "targetPhaseNodeId", "", true));
        schema.fields.add(field("connectionType", "Connection Type", FieldType.ENUM, "connectionType", EditableConnectionType.TRANSITION.name(), true));
        schema.fields.add(field("priority", "Priority", FieldType.INTEGER, "priority", "0", false));
        schema.fields.add(field("condition.type", "Condition Type", FieldType.TEXT, "condition.type", "always", false));
        schema.fields.add(field("condition.flag", "Condition Flag", FieldType.TEXT, "condition.flag", "", false));
        schema.fields.add(field("condition.questId", "Condition Quest", FieldType.TEXT, "condition.questId", "", false));
        schema.fields.add(field("condition.variable", "Condition Variable", FieldType.TEXT, "condition.variable", "", false));

        FieldSchema compareOp = field("condition.compareOp", "Condition CompareOp", FieldType.ENUM, "condition.compareOp", CompareOp.GREATER_OR_EQUAL.name(), false);
        for (CompareOp op : CompareOp.values()) compareOp.enumOptions.add(op.name());
        schema.fields.add(compareOp);

        schema.fields.add(field("condition.value", "Condition Value", FieldType.INTEGER, "condition.value", "0", false));

        return schema;
    }

    private FormSchema buildRewardNodeSchema() {
        FormSchema schema = new FormSchema();
        schema.formId = "collection.reward_node";
        schema.title = "RewardNode";
        schema.objectType = "REWARD_NODE";

        schema.fields.add(field("rewardNodeId", "Reward Node ID", FieldType.TEXT, "rewardNodeId", "reward_1", true));

        FieldSchema scope = field("scope", "Reward Scope", FieldType.ENUM, "scope", RewardScope.ENTRY.name(), true);
        for (RewardScope s : RewardScope.values()) scope.enumOptions.add(s.name());
        schema.fields.add(scope);

        FieldSchema grant = field("grantMode", "Grant Mode", FieldType.ENUM, "grantMode", EntryRewardGrantMode.AUTO.name(), true);
        for (EntryRewardGrantMode m : EntryRewardGrantMode.values()) grant.enumOptions.add(m.name());
        schema.fields.add(grant);

        return schema;
    }

    private FieldSchema field(String key, String label, FieldType type, String path, String defaultValue, boolean required) {
        FieldSchema field = new FieldSchema();
        field.key = key;
        field.label = label;
        field.type = type;
        field.path = path;
        field.defaultValue = defaultValue == null ? "" : defaultValue;
        field.required = required;
        field.uiWidgetHint = type == FieldType.ENUM ? "enum_select" : "";
        return field;
    }
}
