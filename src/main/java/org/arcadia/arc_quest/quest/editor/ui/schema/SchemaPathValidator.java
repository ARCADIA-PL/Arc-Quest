package org.arcadia.arc_quest.quest.editor.ui.schema;

import java.util.ArrayList;
import java.util.List;

public class SchemaPathValidator {

    private final SchemaRegistry registry;
    private final SchemaPathAccessor accessor;

    public SchemaPathValidator(SchemaRegistry registry, SchemaPathAccessor accessor) {
        this.registry = registry;
        this.accessor = accessor;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        for (FormSchema form : registry.all().values()) {
            Object target = sampleTarget(form.objectType);
            if (target == null) continue;
            for (FieldSchema field : form.fields) {
                if (field.path == null || field.path.isBlank()) continue;
                if ("phase.objective".equals(form.formId) && ("collection.categoryId".equals(field.key) || "condition.compareOp".equals(field.key))) continue;
                boolean canSet = accessor.setFromString(target, field.path, field.defaultValue == null ? "" : field.defaultValue);
                String read = accessor.getAsString(target, field.path);
                if (!canSet && (read == null || read.isBlank())) {
                    errors.add(form.formId + ":" + field.key + " -> unresolved path: " + field.path);
                }
            }
        }
        return errors;
    }

    private Object sampleTarget(String objectType) {
        if ("OBJECTIVE".equalsIgnoreCase(objectType)) return new org.arcadia.arc_quest.quest.editor.model.EditableObjective();
        if ("CONNECTION".equalsIgnoreCase(objectType)) return new org.arcadia.arc_quest.quest.editor.model.EditableConnection();
        if ("REWARD_NODE".equalsIgnoreCase(objectType)) return new org.arcadia.arc_quest.quest.editor.model.EditableCollectionRewardNode();
        return null;
    }
}
