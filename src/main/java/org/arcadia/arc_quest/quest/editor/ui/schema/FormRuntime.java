package org.arcadia.arc_quest.quest.editor.ui.schema;

import java.util.LinkedHashMap;
import java.util.Map;

public class FormRuntime {

    private final SchemaRegistry registry;
    private final SchemaPathAccessor accessor;

    public FormRuntime(SchemaRegistry registry, SchemaPathAccessor accessor) {
        this.registry = registry;
        this.accessor = accessor;
    }

    public Map<String, String> readValues(String formId, Object target) {
        Map<String, String> values = new LinkedHashMap<>();
        FormSchema schema = registry.get(formId);
        if (schema == null || target == null) return values;
        for (FieldSchema field : schema.fields) {
            String value = accessor.getAsString(target, field.path);
            if ((value == null || value.isBlank()) && field.defaultValue != null) value = field.defaultValue;
            values.put(field.key, value == null ? "" : value);
        }
        return values;
    }

    public ApplyResult applyValues(String formId, Object target, Map<String, String> values) {
        ApplyResult result = new ApplyResult();
        FormSchema schema = registry.get(formId);
        if (schema == null || target == null) {
            result.errors.put("$form", "form or target not found");
            return result;
        }
        for (FieldSchema field : schema.fields) {
            String value = values.getOrDefault(field.key, field.defaultValue == null ? "" : field.defaultValue);
            if (field.required && (value == null || value.isBlank())) {
                result.errors.put(field.key, "required");
                continue;
            }
            if (field.type == FieldType.ENUM && !field.enumOptions.isEmpty() && value != null && !value.isBlank()) {
                boolean ok = field.enumOptions.stream().anyMatch(e -> e.equalsIgnoreCase(value));
                if (!ok) {
                    result.errors.put(field.key, "invalid enum");
                    continue;
                }
            }
            boolean changed = accessor.setFromString(target, field.path, value);
            result.applied.put(field.key, changed);
        }
        return result;
    }

    public static class ApplyResult {
        public final Map<String, Boolean> applied = new LinkedHashMap<>();
        public final Map<String, String> errors = new LinkedHashMap<>();

        public boolean success() {
            return errors.isEmpty();
        }
    }
}
