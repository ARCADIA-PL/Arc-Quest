package org.arcadia.arc_quest.quest.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ObjectiveTypeDefinition {

    private final boolean counting;
    private final boolean builtin;
    private final String displayKey;
    private final boolean requireTargetId;
    private final String defaultTargetKind;
    private final Map<String, String> hints;

    private ObjectiveTypeDefinition(Builder builder) {
        this.counting = builder.counting;
        this.builtin = builder.builtin;
        this.displayKey = builder.displayKey;
        this.requireTargetId = builder.requireTargetId;
        this.defaultTargetKind = builder.defaultTargetKind;
        this.hints = Collections.unmodifiableMap(new LinkedHashMap<>(builder.hints));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean counting() {
        return counting;
    }

    public boolean builtin() {
        return builtin;
    }

    public String displayKey() {
        return displayKey;
    }

    public boolean requireTargetId() {
        return requireTargetId;
    }

    public String defaultTargetKind() {
        return defaultTargetKind;
    }

    public Map<String, String> hints() {
        return hints;
    }

    public static final class Builder {
        private boolean counting;
        private boolean builtin;
        private String displayKey = "";
        private boolean requireTargetId = true;
        private String defaultTargetKind = "generic";
        private final Map<String, String> hints = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder counting(boolean counting) {
            this.counting = counting;
            return this;
        }

        public Builder builtin(boolean builtin) {
            this.builtin = builtin;
            return this;
        }

        public Builder displayKey(String displayKey) {
            this.displayKey = displayKey == null ? "" : displayKey;
            return this;
        }

        public Builder requireTargetId(boolean requireTargetId) {
            this.requireTargetId = requireTargetId;
            return this;
        }

        public Builder defaultTargetKind(String defaultTargetKind) {
            this.defaultTargetKind = defaultTargetKind == null ? "generic" : defaultTargetKind;
            return this;
        }

        public Builder hint(String key, String value) {
            if (key != null && !key.isBlank() && value != null) {
                this.hints.put(key, value);
            }
            return this;
        }

        public ObjectiveTypeDefinition build() {
            return new ObjectiveTypeDefinition(this);
        }
    }
}
